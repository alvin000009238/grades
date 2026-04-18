package com.clhs.score.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.serialization.json.jsonObject

class SchoolException(
    message: String,
    val refreshCaptcha: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause)

class SchoolGradeClient(
    baseUrl: String = DEFAULT_BASE_URL,
    private val cookieJar: SchoolCookieJar = SchoolCookieJar(),
    okHttpClient: OkHttpClient? = null,
) {
    private val baseUrl: HttpUrl = baseUrl.ensureTrailingSlash().toHttpUrl()
    private val client: OkHttpClient = okHttpClient ?: OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun prepareLoginCaptcha(): CaptchaChallenge = withContext(Dispatchers.IO) {
        cookieJar.clear()
        val loginPage = loginPageUrl()
        val loginResponse = execute(
            Request.Builder()
                .url(loginPage)
                .headers(defaultHeaders(referer = null))
                .get()
                .build(),
        )
        val html = loginResponse.body.string()
        val loginToken = hiddenInput(html, "__RequestVerificationToken")
            ?: throw SchoolException("找不到登入 token")
        val shCaptchaGenCode = hiddenInput(html, "ShCaptchaGenCode").ifNullOrBlank("10")
        val deviceToken = hiddenInput(html, "DeviceToken").orEmpty()
        val captchaUrl = findCaptchaImageUrl(html) ?: buildCaptchaUrl()

        val imageResponse = runCatching { requestCaptcha(captchaUrl, loginPage) }
            .getOrElse { requestCaptcha(buildCaptchaUrl(), loginPage) }
        val contentType = imageResponse.body.contentType()?.toString() ?: "image/png"
        val imageBytes = imageResponse.body.bytes()
        val normalizedBytes = if ("image" in contentType.lowercase(Locale.ROOT)) {
            imageBytes
        } else {
            decodeHexImageBytes(imageBytes.toString(StandardCharsets.UTF_8))
                ?: throw SchoolException("學校驗證碼回應格式異常", refreshCaptcha = true)
        }

        CaptchaChallenge(
            loginToken = loginToken,
            shCaptchaGenCode = shCaptchaGenCode,
            deviceToken = deviceToken,
            cookies = cookieJar.snapshot(),
            imageBytes = normalizedBytes,
            contentType = if ("image" in contentType.lowercase(Locale.ROOT)) contentType else "image/png",
        )
    }

    suspend fun login(
        username: String,
        password: String,
        captchaCode: String,
        challenge: CaptchaChallenge,
    ): AuthenticatedSession = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isBlank()) {
            throw SchoolException("請輸入帳號密碼")
        }
        if (captchaCode.isBlank()) {
            throw SchoolException("請輸入驗證碼")
        }

        cookieJar.replace(challenge.cookies, domain = baseUrl.host)
        val form = FormBody.Builder()
            .add("SchoolCode", "030305")
            .add("LoginId", username.trim())
            .add("PassString", password)
            .add("LoginType", "Student")
            .add("IsKeepLogin", "false")
            .add("IdentityId", "6")
            .add("SchoolName", "國立中大壢中")
            .add("GoogleToken", "8")
            .add("isRegistration", "false")
            .add("ShCaptchaGenCode", captchaCode.trim().ifBlank { challenge.shCaptchaGenCode })
            .add("__RequestVerificationToken", challenge.loginToken)
            .apply {
                if (challenge.deviceToken.isNotBlank()) add("DeviceToken", challenge.deviceToken)
            }
            .build()

        val response = execute(
            Request.Builder()
                .url(resolve("Auth/Auth/DoCloudLoginCheck"))
                .headers(
                    defaultHeaders(referer = loginPageUrl().toString()).newBuilder()
                        .add("Origin", "https://shcloud2.k12ea.gov.tw")
                        .add("X-Requested-With", "XMLHttpRequest")
                        .build(),
                )
                .post(form)
                .build(),
        )
        val payload = response.body.string()
        val root = SchoolJson.parseToJsonElement(payload).jsonObject
        val result = root["Result"].asObjectOrNull()
        val ok = result?.boolean("IsLoginSuccess") == true
        if (!ok) {
            val message = result?.string("DisplayMsg")
                ?.takeIf { it.isNotBlank() }
                ?: root.string("Message", "登入失敗")
            throw SchoolException(message, refreshCaptcha = true)
        }

        val gradesPage = execute(
            Request.Builder()
                .url(gradesPageUrl())
                .headers(defaultHeaders(referer = loginPageUrl().toString()))
                .get()
                .build(),
        ).body.string()
        val apiToken = hiddenInput(gradesPage, "__RequestVerificationToken")
            ?: throw SchoolException("找不到成績 API token")

        AuthenticatedSession(
            studentNo = username.trim(),
            apiToken = apiToken,
            cookies = cookieJar.snapshot(),
        )
    }

    suspend fun loadStructure(session: AuthenticatedSession): List<YearTermOption> {
        cookieJar.replace(session.cookies, domain = baseUrl.host)
        val yearTerms = postOptions(
            path = "ICampus/CommonData/GetGradeCanQueryYearTermListByStudentNo",
            referer = gradesPageUrl().toString(),
            form = mapOf(
                "searchType" to "各次考試單科成績",
                "studentNo" to session.studentNo,
                "__RequestVerificationToken" to session.apiToken,
            ),
        )
        val semaphore = Semaphore(4)
        return coroutineScope {
            yearTerms.map { (text, value) ->
                async {
                    semaphore.withPermit {
                        YearTermOption(
                            text = text,
                            value = value,
                            exams = loadExams(session, value),
                        )
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun fetchGrades(
        session: AuthenticatedSession,
        yearValue: String,
        examValue: String,
    ): GradeReport = withContext(Dispatchers.IO) {
        cookieJar.replace(session.cookies, domain = baseUrl.host)
        val (year, term) = parseYearTerm(yearValue, defaultYear = "114", defaultTerm = "2")
        val body = postForm(
            path = "ICampus/TutorShGrade/GetScoreForStudentExamContent",
            referer = gradesPageUrl().toString(),
            form = mapOf(
                "StudentNo" to session.studentNo,
                "SearchType" to "單次考試所有成績",
                "__RequestVerificationToken" to session.apiToken,
                "Year" to year,
                "Term" to term,
                "ExamNo" to examValue,
            ),
        )
        parseGradeReport(body)
    }

    fun restoreSession(session: AuthenticatedSession) {
        cookieJar.replace(session.cookies, domain = baseUrl.host)
    }

    fun clearSession() {
        cookieJar.clear()
    }

    private suspend fun loadExams(session: AuthenticatedSession, yearValue: String): List<ExamOption> {
        val (year, term) = parseYearTerm(yearValue, defaultYear = "114", defaultTerm = "1")
        return postOptions(
            path = "ICampus/CommonData/GetGradeCanQueryExamNoListByStudentNo",
            referer = gradesPageUrl().toString(),
            form = mapOf(
                "searchType" to "單次考試所有成績",
                "studentNo" to session.studentNo,
                "year" to year,
                "term" to term,
                "__RequestVerificationToken" to session.apiToken,
            ),
        ).map { (text, value) -> ExamOption(text = text, value = value) }
    }

    private suspend fun postOptions(
        path: String,
        referer: String,
        form: Map<String, String>,
    ): List<Pair<String, String>> = parseOptions(postForm(path, referer, form))

    private suspend fun postForm(
        path: String,
        referer: String,
        form: Map<String, String>,
    ): String = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply {
            form.forEach { (name, value) -> add(name, value) }
        }.build()
        execute(
            Request.Builder()
                .url(resolve(path))
                .headers(
                    defaultHeaders(referer).newBuilder()
                        .add("Origin", "https://shcloud2.k12ea.gov.tw")
                        .add("X-Requested-With", "XMLHttpRequest")
                        .build(),
                )
                .post(body)
                .build(),
        ).body.string()
    }

    private fun requestCaptcha(url: HttpUrl, referer: HttpUrl): Response {
        val response = execute(
            Request.Builder()
                .url(url)
                .headers(
                    Headers.Builder()
                        .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                        .add("User-Agent", USER_AGENT)
                        .add("Referer", referer.toString())
                        .build(),
                )
                .get()
                .build(),
        )
        if (response.code == 403) {
            response.close()
            throw SchoolException("驗證碼網址被拒絕", refreshCaptcha = true)
        }
        return response
    }

    private fun execute(request: Request): Response {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw SchoolException("學校系統回應異常 HTTP $code")
        }
        return response
    }

    private fun hiddenInput(html: String, name: String): String? {
        val doc = Jsoup.parse(html)
        return doc.selectFirst("""input[name="$name"]""")?.attr("value")?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun findCaptchaImageUrl(html: String): HttpUrl? {
        val doc = Jsoup.parse(html)
        val node = doc.select("img").firstOrNull { img ->
            val src = img.attr("src")
            src.contains("/Auth/Auth/GetCaptcha") ||
                src.contains("GetCaptcha") ||
                img.id().contains("captcha", ignoreCase = true) ||
                img.classNames().any { it.contains("captcha", ignoreCase = true) }
        } ?: return null
        val src = node.attr("src").takeIf { it.isNotBlank() } ?: return null
        return loginPageUrl().resolve(src)
    }

    private fun decodeHexImageBytes(rawText: String): ByteArray? {
        var normalized = rawText.trim().replace(Regex("\\s+"), "")
        if (normalized.startsWith("0x", ignoreCase = true)) normalized = normalized.drop(2)
        if (normalized.length < 64 || normalized.length % 2 != 0) return null
        if (!normalized.matches(Regex("[0-9a-fA-F]+"))) return null
        return runCatching {
            normalized.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }.getOrNull()
    }

    private fun loginPageUrl(): HttpUrl = resolve("Auth/Auth/CloudLogin")

    private fun buildCaptchaUrl(): HttpUrl = resolve("Auth/Auth/GetCaptcha")
        .newBuilder()
        .addQueryParameter("t", System.currentTimeMillis().toString())
        .build()

    private fun gradesPageUrl(): HttpUrl = resolve("ICampus/StudentInfo/Index")
        .newBuilder()
        .addQueryParameter("page", "成績查詢")
        .build()

    private fun resolve(path: String): HttpUrl = baseUrl.resolve(path)
        ?: throw IllegalArgumentException("Invalid path: $path")

    private fun defaultHeaders(referer: String?): Headers = Headers.Builder()
        .add("Accept", "*/*")
        .add("User-Agent", USER_AGENT)
        .apply {
            if (!referer.isNullOrBlank()) add("Referer", referer)
        }
        .build()

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

    private fun String?.ifNullOrBlank(default: String): String = if (isNullOrBlank()) default else this

    companion object {
        const val DEFAULT_BASE_URL = "https://shcloud2.k12ea.gov.tw/CLHSTYC"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    }
}
