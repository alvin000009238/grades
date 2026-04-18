package com.clhs.score.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.clhs.score.data.ExamOption
import com.clhs.score.data.ExamSummary
import com.clhs.score.data.GradeReport
import com.clhs.score.data.GradeStandard
import com.clhs.score.data.LocalScoreInsightProvider
import com.clhs.score.data.StudentInfo
import com.clhs.score.data.SubjectScore
import com.clhs.score.data.YearTermOption
import com.clhs.score.data.buildGradeAnalysis
import com.clhs.score.data.buildGradeTrend
import com.clhs.score.data.cleanSubjectName
import com.clhs.score.ui.theme.ScoreTheme
import com.clhs.score.viewmodel.GradesUiState
import com.clhs.score.viewmodel.LoginUiState
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.junit.Test

class ScoreUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginScreenShowsRequiredFields() {
        composeRule.setContent {
            ScoreTheme {
                LoginScreen(
                    state = LoginUiState(errorMessage = "驗證失敗，請重新輸入"),
                    snackbarHost = {},
                    onUsernameChange = {},
                    onPasswordChange = {},
                    onCaptchaChange = {},
                    onRefreshCaptcha = {},
                    onLogin = {},
                )
            }
        }

        composeRule.onNodeWithText("帳號").assertIsDisplayed()
        composeRule.onNodeWithText("密碼").assertIsDisplayed()
        composeRule.onNodeWithText("驗證碼").assertIsDisplayed()
        composeRule.onNodeWithText("驗證碼圖片").assertIsDisplayed()
        composeRule.onNodeWithText("刷新").assertIsDisplayed()
        composeRule.onNodeWithText("登入").assertIsDisplayed()
        composeRule.onNodeWithText("驗證失敗，請重新輸入").assertIsDisplayed()
    }

    @Test
    fun gradesScreenUsesBottomNavigationAndSegmentedOverview() {
        composeRule.setContent {
            ScoreTheme {
                TestGradesScreen()
            }
        }

        composeRule.onNodeWithText("總覽").assertIsDisplayed()
        composeRule.onNodeWithText("科目").assertIsDisplayed()
        composeRule.onNodeWithText("進階").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("總覽").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("科目").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("進階").assertIsDisplayed()
        composeRule.onAllNodesWithText("全部科目").assertCountEquals(0)
        composeRule.onAllNodesWithText("圖表").assertCountEquals(0)
        composeRule.onNodeWithText("測試學生", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("加權平均").assertIsDisplayed()
        composeRule.onNodeWithText("班排 13/37（前 35%）").assertIsDisplayed()
        composeRule.onNodeWithText("類排 60/219（前 27%）").assertIsDisplayed()
        composeRule.onNodeWithText("科目數 3 ｜ 最高分 84").assertIsDisplayed()
        composeRule.onNodeWithText("重點解讀").assertIsDisplayed()
        composeRule.onNodeWithText("最值得補強").assertIsDisplayed()
        composeRule.onNodeWithText("最具優勢").assertIsDisplayed()
        composeRule.onNodeWithText("排名推估").assertIsDisplayed()
        composeRule.onNodeWithText("查看科目分析").assertIsDisplayed()
        composeRule.onNodeWithText("查看圖表分析").assertIsDisplayed()
        composeRule.onNodeWithText("查看排名分布").assertIsDisplayed()
        composeRule.onNodeWithText("強弱科摘要").assertIsDisplayed()
        composeRule.onAllNodesWithText("科目分析").assertCountEquals(0)
        composeRule.onAllNodesWithText("圖表分析").assertCountEquals(0)
        composeRule.onAllNodesWithText("進階資料").assertCountEquals(0)
    }

    @Test
    fun subjectCardExpandsDetails() {
        composeRule.setContent {
            ScoreTheme {
                TestGradesScreen()
            }
        }

        composeRule.onNodeWithText("科目").performClick()
        composeRule.onAllNodesWithText("五標落點").assertCountEquals(0)
        composeRule.onNodeWithText("國語文").performScrollTo().performClick()
        composeRule.onNodeWithText("五標落點").assertIsDisplayed()
        composeRule.onNodeWithText("分佈摘要").assertIsDisplayed()
        composeRule.onNodeWithText("上一考比較").assertIsDisplayed()
        composeRule.onAllNodesWithText("班級平均").assertCountEquals(0)
        composeRule.onAllNodesWithText("校排").assertCountEquals(0)
    }

    @Test
    fun quickActionsNavigateToSubjectsAndAdvanced() {
        composeRule.setContent {
            ScoreTheme {
                TestGradesScreen()
            }
        }

        composeRule.onNodeWithText("查看科目分析").performScrollTo().performClick()
        composeRule.onNodeWithText("國語文").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("總覽").performClick()
        composeRule.onNodeWithText("查看圖表分析").performScrollTo().performClick()
        composeRule.onNodeWithText("五標分析").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun overviewShowsTrendLoadingAndNoHistoryStates() {
        composeRule.setContent {
            ScoreTheme {
                TestGradesScreen(isLoadingTrend = true, trendError = null)
            }
        }
        composeRule.onNodeWithText("正在載入歷次趨勢...").assertIsDisplayed()

        composeRule.setContent {
            ScoreTheme {
                TestGradesScreen(showTrend = false, trendError = "尚無歷次趨勢可比較")
            }
        }
        composeRule.onNodeWithText("尚無歷次趨勢可比較").assertIsDisplayed()
    }

    @Test
    fun analysisSectionShowsDistributionCollapsedThenExpanded() {
        composeRule.setContent {
            ScoreTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    AnalysisSection(report = sampleReport(), analysis = buildGradeAnalysis(sampleReport()))
                }
            }
        }

        composeRule.onNodeWithText("雷達分析").assertIsDisplayed()
        composeRule.onNodeWithText("成績比較").assertIsDisplayed()
        composeRule.onNodeWithText("五標分析").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("分數分布").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("展開完整分佈").performScrollTo().performClick()
        composeRule.onNodeWithText("收合完整分佈").assertIsDisplayed()
    }

    @Composable
    private fun TestGradesScreen(
        isLoadingTrend: Boolean = false,
        showTrend: Boolean = true,
        trendError: String? = null,
    ) {
        val report = sampleReport()
        val analysis = buildGradeAnalysis(report)
        val trend = if (showTrend) buildGradeTrend(
            currentExamName = "期末考",
            currentReport = report,
            previousReports = listOf("期中考" to sampleReport(mathScore = 78.0, englishScore = 80.0, chineseScore = 61.0)),
        ) else null
        var expanded by remember { mutableStateOf(emptySet<String>()) }
        GradesScreen(
            state = GradesUiState(
                isLoggedIn = true,
                studentNo = "310471",
                structure = listOf(
                    YearTermOption(
                        text = "114學年度 上學期",
                        value = "114_1",
                        exams = listOf(ExamOption("期中考", "E1"), ExamOption("期末考", "E2")),
                    ),
                ),
                selectedYearValue = "114_1",
                selectedExamValue = "E2",
                report = report,
                analysis = analysis,
                isLoadingTrend = isLoadingTrend,
                trendError = trendError,
                trend = trend,
                insights = LocalScoreInsightProvider().buildInsights(report, analysis, trend),
                expandedSubjectKeys = expanded,
            ),
            snackbarHost = {},
            onSelectYear = {},
            onSelectExam = {},
            onReload = {},
            onLogout = {},
            onToggleSubject = { subjectName ->
                val key = cleanSubjectName(subjectName)
                expanded = if (key in expanded) expanded - key else expanded + key
            },
        )
    }

    private fun sampleReport(
        mathScore: Double = 84.0,
        englishScore: Double = 82.0,
        chineseScore: Double = 58.0,
    ): GradeReport = GradeReport(
        message = "",
        studentInfo = StudentInfo(
            studentNo = "310471",
            studentName = "測試學生",
            className = "二年 11 班",
            seatNo = "20",
            updatedAt = "",
            showClassRank = true,
            showClassRankCount = true,
            showCategoryRank = true,
            showCategoryRankCount = true,
        ),
        examSummary = ExamSummary(
            year = 114,
            termText = "上",
            examName = "期末考",
            totalScoreDisplay = "300.00",
            averageScoreDisplay = "75.00",
            classRank = 13.0,
            classCount = 37,
            categoryRank = 60.0,
            categoryRankCount = 219,
            flunkCount = 0,
        ),
        subjects = listOf(
            SubjectScore("國語文", "%.2f".format(chineseScore), chineseScore, "70.00", 70.0, 25, 37, null, null, "114學年度 上學期", false, false, false),
            SubjectScore("英語文", "%.2f".format(englishScore), englishScore, "76.00", 76.0, 8, 37, null, null, "114學年度 上學期", false, false, false),
            SubjectScore("數學", "%.2f".format(mathScore), mathScore, "68.00", 68.0, 5, 37, null, null, "114學年度 上學期", false, false, false),
        ),
        standards = listOf(
            GradeStandard("國語文", 88.0, 80.0, 70.0, 60.0, 50.0, 12.0, 2, 6, 12, 10, 5, 2, 1, 1, 1, 0),
            GradeStandard("英語文", 90.0, 82.0, 70.0, 60.0, 50.0, 13.0, 3, 8, 10, 9, 5, 2, 1, 1, 1, 0),
            GradeStandard("數學", 85.0, 78.0, 68.0, 58.0, 48.0, 15.0, 1, 5, 11, 12, 6, 2, 1, 1, 1, 0),
        ),
        rawResult = JsonObject(emptyMap()),
    )
}
