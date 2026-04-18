package com.clhs.score.data

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeAnalysisTest {
    @Test
    fun percentileFormatsRankAsTopPercent() {
        assertEquals("前 35%", percentile(13.0, 37)?.percentLabel)
        assertEquals("前 27%", percentile(60.0, 219)?.percentLabel)
    }

    @Test
    fun strengthAndWeaknessAreSortedByClassAverageDiff() {
        val analysis = buildGradeAnalysis(sampleReport())

        assertEquals("數學", analysis.strengths.first().subjectName)
        assertEquals("國語文", analysis.weaknesses.first().subjectName)
    }

    @Test
    fun summaryTextHandlesRankAndSubjectHighlights() {
        val summary = buildGradeAnalysis(sampleReport()).summaryText

        assertTrue(summary.contains("本次班排 13/37"))
        assertTrue(summary.contains("優勢科目為數學"))
        assertTrue(summary.contains("待加強為國語文"))
    }

    @Test
    fun previousExamLookupStaysInsideSameTerm() {
        val year = YearTermOption(
            text = "114上",
            value = "114_1",
            exams = listOf(
                ExamOption("第一次段考", "E1"),
                ExamOption("第二次段考", "E2"),
                ExamOption("期末考", "E3"),
            ),
        )

        assertEquals("E2", year.previousExamOf("E3")?.value)
        assertNull(year.previousExamOf("E1"))
        assertNull(year.previousExamOf("missing"))
    }

    @Test
    fun previousExamsLookupReturnsAtMostTwoInChronologicalOrder() {
        val year = YearTermOption(
            text = "114上",
            value = "114_1",
            exams = listOf(
                ExamOption("第一次段考", "E1"),
                ExamOption("第二次段考", "E2"),
                ExamOption("期末考", "E3"),
            ),
        )

        assertEquals(listOf("E1", "E2"), year.previousExamsOf("E3").map { it.value })
        assertEquals(listOf("E1"), year.previousExamsOf("E2").map { it.value })
        assertTrue(year.previousExamsOf("E1").isEmpty())
    }

    @Test
    fun comparisonCalculatesAverageRankAndSubjectDeltas() {
        val current = sampleReport()
        val previous = sampleReport(
            averageScore = 70.0,
            mathScore = 70.0,
            englishScore = 78.0,
            chineseScore = 62.0,
            classRank = 18.0,
        )

        val comparison = buildGradeComparison(current, previous, previousExamName = "第二次段考")

        assertEquals("第二次段考", comparison.previousExamName)
        assertTrue(comparison.averageDelta > 0.0)
        assertEquals(5, comparison.classRankDelta)
        assertEquals(14.0, comparison.subjectComparisons["數學"]?.scoreDelta ?: 0.0, 0.001)
        assertNotNull(buildGradeAnalysis(current, previous, "第二次段考").comparison)
    }

    @Test
    fun gradeTrendKeepsOldToNewOrder() {
        val trend = buildGradeTrend(
            currentExamName = "期末考",
            currentReport = sampleReport(mathScore = 84.0),
            previousReports = listOf(
                "第一次段考" to sampleReport(mathScore = 60.0, classRank = 22.0),
                "第二次段考" to sampleReport(mathScore = 70.0, classRank = 18.0),
            ),
        )

        assertEquals(listOf("第一次段考", "第二次段考", "期末考"), trend.points.map { it.examName })
        assertEquals(3, trend.points.size)
        assertTrue(trend.averageLine.contains("→"))
    }

    @Test
    fun localInsightsChooseFocusStrengthAndProjection() {
        val current = sampleReport()
        val previous = sampleReport(
            mathScore = 70.0,
            englishScore = 78.0,
            chineseScore = 62.0,
            classRank = 18.0,
        )
        val analysis = buildGradeAnalysis(current, previous, "第二次段考")
        val insights = LocalScoreInsightProvider().buildInsights(current, analysis)

        assertEquals("國語文", insights.projection?.subjectName)
        assertTrue(insights.items.any { it.title == "最值得補強" && it.body.contains("國語文") })
        assertTrue(insights.items.any { it.title == "最具優勢" && it.body.contains("數學") })
        assertTrue(insights.items.any { it.title == "排名推估" && it.body.contains("粗估") })
        assertTrue((insights.projection?.estimatedClassRank ?: 99) >= 1)
    }

    @Test
    fun localInsightsDoNotEstimateRankWhenRankDataMissing() {
        val current = sampleReport(classRank = null, classCount = null)
        val analysis = buildGradeAnalysis(current)
        val insights = LocalScoreInsightProvider().buildInsights(current, analysis)

        assertNull(insights.projection?.estimatedClassRank)
        assertTrue(insights.items.any { it.body.contains("排名資料不足") })
    }

    private fun sampleReport(
        averageScore: Double = 76.0,
        mathScore: Double = 84.0,
        englishScore: Double = 82.0,
        chineseScore: Double = 58.0,
        classRank: Double? = 13.0,
        classCount: Int? = 37,
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
            averageScoreDisplay = "%.2f".format(averageScore),
            classRank = classRank,
            classCount = classCount,
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
