package com.clhs.score.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SchoolGradeClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: SchoolGradeClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = SchoolGradeClient(baseUrl = server.url("/CLHSTYC/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun prepareLoginCaptchaParsesTokensCookiesAndImage() = runTest {
        server.enqueue(
            htmlResponse(
                """
                <html>
                  <body>
                    <input name="__RequestVerificationToken" value="login-token" />
                    <input name="ShCaptchaGenCode" value="captcha-gen" />
                    <input name="DeviceToken" value="device-token" />
                    <img id="captcha" src="/CLHSTYC/Auth/Auth/GetCaptcha?seed=1" />
                  </body>
                </html>
                """.trimIndent(),
            ).addHeader("Set-Cookie", "ASP.NET_SessionId=abc; Path=/"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "image/png")
                .setBody("PNGDATA"),
        )

        val challenge = client.prepareLoginCaptcha()

        assertEquals("login-token", challenge.loginToken)
        assertEquals("captcha-gen", challenge.shCaptchaGenCode)
        assertEquals("device-token", challenge.deviceToken)
        assertEquals("image/png", challenge.contentType)
        assertArrayEquals("PNGDATA".toByteArray(), challenge.imageBytes)
        assertEquals("abc", challenge.cookies["ASP.NET_SessionId"])
        assertEquals("/CLHSTYC/Auth/Auth/CloudLogin", server.takeRequest().path)
        assertEquals("/CLHSTYC/Auth/Auth/GetCaptcha?seed=1", server.takeRequest().path)
    }

    @Test
    fun prepareLoginCaptchaSupportsHexImageFallback() = runTest {
        val hexPng = "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000a49444154789c636000000200015df4a8b00000000049454e44ae426082"
        server.enqueue(
            htmlResponse(
                """
                <input name="__RequestVerificationToken" value="token" />
                <input name="ShCaptchaGenCode" value="10" />
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "text/plain")
                .setBody(hexPng),
        )

        val challenge = client.prepareLoginCaptcha()

        assertEquals("image/png", challenge.contentType)
        assertTrue(challenge.imageBytes.size > 8)
        assertEquals(0x89.toByte(), challenge.imageBytes[0])
    }

    @Test
    fun loginSuccessReturnsSessionAndPostsExpectedForm() = runTest {
        val challenge = CaptchaChallenge(
            loginToken = "login-token",
            shCaptchaGenCode = "10",
            deviceToken = "device-token",
            cookies = mapOf("ASP.NET_SessionId" to "abc"),
            imageBytes = "PNG".toByteArray(),
            contentType = "image/png",
        )
        server.enqueue(
            jsonResponse(
                """
                {"Result":{"IsLoginSuccess":true,"DisplayMsg":"OK"}}
                """.trimIndent(),
            ),
        )
        server.enqueue(
            htmlResponse("""<input name="__RequestVerificationToken" value="api-token" />"""),
        )

        val session = client.login("310471", "secret", "1234", challenge)

        val loginRequest = server.takeRequest()
        assertEquals("/CLHSTYC/Auth/Auth/DoCloudLoginCheck", loginRequest.path)
        val form = loginRequest.body.readUtf8()
        assertTrue(form.contains("LoginId=310471"))
        assertTrue(form.contains("PassString=secret"))
        assertTrue(form.contains("ShCaptchaGenCode=1234"))
        assertTrue(form.contains("__RequestVerificationToken=login-token"))
        assertEquals("310471", session.studentNo)
        assertEquals("api-token", session.apiToken)
        assertFalse(session.cookies.isEmpty())
        assertEquals("/CLHSTYC/ICampus/StudentInfo/Index?page=%E6%88%90%E7%B8%BE%E6%9F%A5%E8%A9%A2", server.takeRequest().path)
    }

    @Test
    fun loginFailureRequiresCaptchaRefresh() = runTest {
        val challenge = CaptchaChallenge(
            loginToken = "login-token",
            shCaptchaGenCode = "10",
            deviceToken = "",
            cookies = emptyMap(),
            imageBytes = ByteArray(0),
            contentType = "image/png",
        )
        server.enqueue(jsonResponse("""{"Result":{"IsLoginSuccess":false,"DisplayMsg":"驗證碼錯誤"}}"""))

        val error = runCatching {
            client.login("310471", "secret", "0000", challenge)
        }.exceptionOrNull()

        assertTrue(error is SchoolException)
        assertTrue((error as SchoolException).refreshCaptcha)
        assertEquals("驗證碼錯誤", error.message)
    }

    @Test
    fun loadStructureAndFetchGradesMapResponses() = runTest {
        val session = AuthenticatedSession(
            studentNo = "310471",
            apiToken = "api-token",
            cookies = mapOf("ASP.NET_SessionId" to "abc"),
        )
        server.enqueue(jsonResponse("""[{"DisplayText":"114學年度 上學期","Value":"114_1"}]"""))
        server.enqueue(jsonResponse("""[{"DisplayText":"期末考","Value":"期末考"}]"""))
        server.enqueue(jsonResponse(gradeJson))

        val structure = client.loadStructure(session)
        val report = client.fetchGrades(session, "114_1", "期末考")

        assertEquals("114學年度 上學期", structure.single().text)
        assertEquals("期末考", structure.single().exams.single().text)
        assertEquals("高浚瑋", report.studentInfo.studentName)
        assertEquals("國語文", report.subjects.single().subjectName)
        assertEquals(78.0, report.subjects.single().scoreValue, 0.001)
        assertEquals(80.0, report.standards.single().top ?: 0.0, 0.001)
    }

    @Test
    fun parseYearTermMatchesFetcherBehavior() {
        assertEquals("114" to "1", parseYearTerm("114_1"))
        assertEquals("114" to "1", parseYearTerm("1141"))
        assertEquals("114" to "1", parseYearTerm("bad"))
    }

    @Test
    fun clearSessionClearsCookieJar() {
        val jar = SchoolCookieJar()
        val clientWithJar = SchoolGradeClient(
            baseUrl = server.url("/CLHSTYC/").toString(),
            cookieJar = jar,
        )
        jar.replace(mapOf("ASP.NET_SessionId" to "abc"), domain = server.url("/").host)

        assertFalse(jar.snapshot().isEmpty())
        clientWithJar.clearSession()
        assertTrue(jar.snapshot().isEmpty())
    }

    private fun htmlResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "text/html; charset=utf-8")
        .setBody(body)

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .setBody(body)

    private val gradeJson = """
        {
          "Message": "",
          "Result": {
            "StudentNo": "310471",
            "StudentName": "高浚瑋",
            "StudentClassName": "二年 11 班",
            "StudentSeatNo": "20",
            "Show班級排名": true,
            "Show班級排名人數": true,
            "Show類組排名": true,
            "Show類組排名人數": true,
            "ExamItem": {
              "Year": 114,
              "Term": "上",
              "ExamName": "期末考",
              "ClassRank": 15,
              "ClassCount": 37,
              "類組排名": 78,
              "類組排名Count": 221
            },
            "SubjectExamInfoList": [
              {
                "SubjectName": "國語文",
                "Score": 78,
                "ScoreDisplay": "78.00",
                "ClassAVGScore": 70.11,
                "ClassAVGScoreDisplay": "70.11",
                "ClassRank": 6,
                "ClassRankCount": 37,
                "YearTermDisplay": "114學年度 上學期"
              }
            ],
            "成績五標List": [
              {
                "SubjectName": "國語文",
                "頂標": 80,
                "前標": 75,
                "均標": 70,
                "後標": 60,
                "底標": 50,
                "標準差": 12,
                "大於90Count": 1,
                "大於80Count": 3,
                "大於70Count": 10,
                "大於60Count": 12,
                "大於50Count": 5,
                "大於40Count": 2,
                "大於30Count": 1,
                "大於20Count": 1,
                "大於10Count": 1,
                "大於0Count": 1
              }
            ]
          }
        }
    """.trimIndent()
}
