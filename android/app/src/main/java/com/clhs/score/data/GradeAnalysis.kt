package com.clhs.score.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PercentileInfo(
    val rank: Int,
    val count: Int,
    val topPercent: Int,
) {
    val rankLabel: String = "$rank/$count"
    val percentLabel: String = "前 $topPercent%"
}

data class SubjectAnalysis(
    val subject: SubjectScore,
    val standard: GradeStandard?,
    val level: String?,
    val standardDistance: String?,
    val distributionSummary: String?,
    val comparison: SubjectComparison?,
)

data class GradeAnalysis(
    val weightedAverage: Double,
    val highestScore: Double?,
    val classPercentile: PercentileInfo?,
    val categoryPercentile: PercentileInfo?,
    val strengths: List<SubjectScore>,
    val weaknesses: List<SubjectScore>,
    val summaryText: String,
    val comparison: GradeComparison?,
    val subjects: List<SubjectAnalysis>,
)

data class GradeTrendPoint(
    val examName: String,
    val weightedAverage: Double,
    val classRank: Int?,
    val highestScore: Double?,
)

data class GradeTrend(
    val points: List<GradeTrendPoint>,
) {
    val averageLine: String = points.joinToString(" → ") { "%.1f".format(it.weightedAverage) }
}

data class RankProjection(
    val subjectName: String,
    val suggestedIncrease: Double,
    val weightedAverageGain: Double,
    val estimatedClassRank: Int?,
    val classCount: Int?,
)

enum class ScoreInsightTone {
    Positive,
    Warning,
    Neutral,
}

data class ScoreInsight(
    val title: String,
    val body: String,
    val tone: ScoreInsightTone,
)

data class ScoreInsightSet(
    val items: List<ScoreInsight>,
    val projection: RankProjection?,
)

interface ScoreInsightProvider {
    fun buildInsights(
        report: GradeReport,
        analysis: GradeAnalysis,
        trend: GradeTrend? = null,
    ): ScoreInsightSet
}

class LocalScoreInsightProvider : ScoreInsightProvider {
    override fun buildInsights(
        report: GradeReport,
        analysis: GradeAnalysis,
        trend: GradeTrend?,
    ): ScoreInsightSet {
        val focus = focusSubject(report)
        val strength = report.subjects.maxByOrNull { it.diffValue }
        val projection = focus?.let { buildRankProjection(report, analysis.comparison, it) }
        val items = buildList {
            focus?.let {
                val body = if (it.diffValue < -0.05) {
                    "${shortenSubjectName(it.subjectName)} 低於平均 ${"%.1f".format(abs(it.diffValue))} 分，先補這科最有感。"
                } else {
                    "${shortenSubjectName(it.subjectName)} 是目前最適合再拉高的科目，先提升 ${"%.1f".format(min(8.0, max(3.0, abs(it.diffValue))))} 分。"
                }
                add(
                    ScoreInsight(
                        title = "最值得補強",
                        body = body,
                        tone = ScoreInsightTone.Warning,
                    ),
                )
            }
            strength?.let {
                val label = if (it.diffValue >= 0.0) {
                    "高於平均 ${signedDiff(it.diffValue)} 分"
                } else {
                    "低於平均 ${"%.1f".format(abs(it.diffValue))} 分，但已是目前最接近平均的科目"
                }
                add(
                    ScoreInsight(
                        title = "最具優勢",
                        body = "${shortenSubjectName(it.subjectName)} $label。",
                        tone = ScoreInsightTone.Positive,
                    ),
                )
            }
            projection?.let {
                add(
                    ScoreInsight(
                        title = "排名推估",
                        body = projectionText(it),
                        tone = ScoreInsightTone.Neutral,
                    ),
                )
            }
            trend?.takeIf { it.points.size >= 2 }?.let {
                add(
                    ScoreInsight(
                        title = "近 ${it.points.size} 次平均",
                        body = it.averageLine,
                        tone = ScoreInsightTone.Neutral,
                    ),
                )
            }
        }
        return ScoreInsightSet(items = items, projection = projection)
    }
}

data class GradeComparison(
    val previousExamName: String,
    val averageDelta: Double,
    val highestScoreDelta: Double?,
    val classRankDelta: Int?,
    val categoryRankDelta: Int?,
    val subjectComparisons: Map<String, SubjectComparison>,
) {
    val summaryText: String
        get() {
            val averageText = deltaText("平均", averageDelta, unit = "分")
            val rankText = classRankDelta?.let { rankDeltaText("班排", it) }
            return listOfNotNull(averageText, rankText).joinToString("，").ifBlank {
                "已載入上一考比較"
            }
        }
}

data class SubjectComparison(
    val subjectName: String,
    val scoreDelta: Double,
)

fun buildGradeAnalysis(
    report: GradeReport,
    comparisonReport: GradeReport? = null,
    previousExamName: String? = null,
): GradeAnalysis {
    val comparison = comparisonReport?.let {
        buildGradeComparison(
            current = report,
            previous = it,
            previousExamName = previousExamName ?: it.examSummary?.examName.orEmpty().ifBlank { "上一考" },
        )
    }
    val subjectComparisons = comparison?.subjectComparisons.orEmpty()
    val subjectAnalyses = report.subjects.mapIndexed { index, subject ->
        val standard = report.standardFor(subject, index)
        SubjectAnalysis(
            subject = subject,
            standard = standard,
            level = standard?.let { gradeLevel(subject.scoreValue, it) },
            standardDistance = standard?.let { standardDistance(subject.scoreValue, it) },
            distributionSummary = standard?.let { distributionSummary(subject.scoreValue, it) },
            comparison = subjectComparisons[cleanSubjectName(subject.subjectName)],
        )
    }
    val strengths = report.subjects
        .filter { it.diffValue >= 1.0 }
        .sortedByDescending { it.diffValue }
        .take(2)
    val weaknesses = report.subjects
        .filter { it.diffValue <= -1.0 }
        .sortedBy { it.diffValue }
        .take(2)

    return GradeAnalysis(
        weightedAverage = report.weightedAverage(),
        highestScore = report.highestScore(),
        classPercentile = percentile(report.examSummary?.classRank, report.examSummary?.classCount),
        categoryPercentile = percentile(report.examSummary?.categoryRank, report.examSummary?.categoryRankCount),
        strengths = strengths,
        weaknesses = weaknesses,
        summaryText = summaryText(report, strengths, weaknesses, comparison),
        comparison = comparison,
        subjects = subjectAnalyses,
    )
}

fun buildGradeComparison(
    current: GradeReport,
    previous: GradeReport,
    previousExamName: String,
): GradeComparison {
    val previousBySubject = previous.subjects.associateBy { cleanSubjectName(it.subjectName) }
    val subjectComparisons = current.subjects.mapNotNull { subject ->
        val key = cleanSubjectName(subject.subjectName)
        val previousSubject = previousBySubject[key] ?: return@mapNotNull null
        key to SubjectComparison(
            subjectName = subject.subjectName,
            scoreDelta = subject.scoreValue - previousSubject.scoreValue,
        )
    }.toMap()
    return GradeComparison(
        previousExamName = previousExamName,
        averageDelta = current.weightedAverage() - previous.weightedAverage(),
        highestScoreDelta = current.highestScore()?.let { currentHigh ->
            previous.highestScore()?.let { previousHigh -> currentHigh - previousHigh }
        },
        classRankDelta = rankDelta(current.examSummary?.classRank, previous.examSummary?.classRank),
        categoryRankDelta = rankDelta(current.examSummary?.categoryRank, previous.examSummary?.categoryRank),
        subjectComparisons = subjectComparisons,
    )
}

fun percentile(rank: Double?, count: Int?): PercentileInfo? {
    if (rank == null || count == null || count <= 0) return null
    val rankInt = rank.toInt()
    if (rankInt <= 0) return null
    val topPercent = ((rank / count) * 100.0).roundToInt().coerceIn(1, 100)
    return PercentileInfo(rank = rankInt, count = count, topPercent = topPercent)
}

fun YearTermOption.previousExamOf(examValue: String?): ExamOption? {
    if (examValue.isNullOrBlank()) return null
    val index = exams.indexOfFirst { it.value == examValue }
    return if (index > 0) exams[index - 1] else null
}

fun YearTermOption.previousExamsOf(examValue: String?, limit: Int = 2): List<ExamOption> {
    if (examValue.isNullOrBlank() || limit <= 0) return emptyList()
    val index = exams.indexOfFirst { it.value == examValue }
    if (index <= 0) return emptyList()
    return exams.subList(max(0, index - limit), index)
}

fun buildGradeTrend(
    currentExamName: String,
    currentReport: GradeReport,
    previousReports: List<Pair<String, GradeReport>>,
): GradeTrend {
    val previousPoints = previousReports.map { (examName, report) ->
        report.toTrendPoint(examName)
    }
    return GradeTrend(points = previousPoints + currentReport.toTrendPoint(currentExamName))
}

fun deltaText(label: String, delta: Double, unit: String = ""): String {
    val direction = when {
        delta > 0.05 -> "+"
        delta < -0.05 -> ""
        else -> ""
    }
    return "$label $direction${"%.1f".format(delta)}$unit"
}

fun rankDeltaText(label: String, delta: Int): String = when {
    delta > 0 -> "${label}進步 $delta 名"
    delta < 0 -> "${label}退步 ${abs(delta)} 名"
    else -> "${label}持平"
}

private fun rankDelta(current: Double?, previous: Double?): Int? {
    if (current == null || previous == null) return null
    return previous.toInt() - current.toInt()
}

private fun GradeReport.toTrendPoint(examName: String): GradeTrendPoint = GradeTrendPoint(
    examName = examName.ifBlank { examSummary?.examName.orEmpty().ifBlank { "考試" } },
    weightedAverage = weightedAverage(),
    classRank = examSummary?.classRank?.toInt(),
    highestScore = highestScore(),
)

private fun focusSubject(report: GradeReport): SubjectScore? {
    val belowAverage = report.subjects.filter { it.diffValue < -0.05 }
    return (belowAverage.ifEmpty { report.subjects }).minByOrNull { it.diffValue }
}

private fun buildRankProjection(
    report: GradeReport,
    comparison: GradeComparison?,
    subject: SubjectScore,
): RankProjection {
    val suggestedIncrease = min(8.0, max(3.0, abs(subject.diffValue)))
    val totalWeight = report.subjects.sumOf { subjectWeight(it.subjectName) }.coerceAtLeast(1)
    val weightedAverageGain = suggestedIncrease * subjectWeight(subject.subjectName) / totalWeight
    val currentRank = report.examSummary?.classRank?.toInt()
    val classCount = report.examSummary?.classCount
    val rankPerPoint = comparison?.let {
        if (abs(it.averageDelta) > 0.05 && it.classRankDelta != null) {
            abs(it.classRankDelta / it.averageDelta).coerceIn(0.25, 2.0)
        } else {
            null
        }
    } ?: 0.5
    val estimatedRank = if (currentRank != null && classCount != null && classCount > 0) {
        (currentRank - (weightedAverageGain * rankPerPoint).roundToInt()).coerceIn(1, classCount)
    } else {
        null
    }
    return RankProjection(
        subjectName = subject.subjectName,
        suggestedIncrease = suggestedIncrease,
        weightedAverageGain = weightedAverageGain,
        estimatedClassRank = estimatedRank,
        classCount = classCount,
    )
}

private fun projectionText(projection: RankProjection): String {
    val subject = shortenSubjectName(projection.subjectName)
    val gain = "%.1f".format(projection.suggestedIncrease)
    val weightedGain = "%.1f".format(projection.weightedAverageGain)
    val rank = projection.estimatedClassRank
    val count = projection.classCount
    return if (rank != null && count != null) {
        "粗估 $subject +$gain 分，加權平均約 +$weightedGain，班排可望接近 $rank/$count，僅供參考。"
    } else {
        "建議先把 $subject 拉高約 $gain 分，可改善整體平均；排名資料不足，暫不估名次。"
    }
}

private fun signedDiff(value: Double): String = "+${"%.1f".format(value)}"

private fun summaryText(
    report: GradeReport,
    strengths: List<SubjectScore>,
    weaknesses: List<SubjectScore>,
    comparison: GradeComparison?,
): String {
    val classRank = percentile(report.examSummary?.classRank, report.examSummary?.classCount)
    val rankPart = classRank?.let { "本次班排 ${it.rankLabel}" }
        ?: "本次加權平均 ${"%.1f".format(report.weightedAverage())}"
    val levelPart = classRank?.let { "整體屬${performanceLevel(it.topPercent)}" }
    val strengthPart = strengths.takeIf { it.isNotEmpty() }?.let {
        "優勢科目為${subjectListText(it)}"
    }
    val weaknessPart = weaknesses.takeIf { it.isNotEmpty() }?.let {
        "待加強為${subjectListText(it)}"
    }
    val comparePart = comparison?.let {
        if (it.averageDelta > 0.05) "較上一考進步 ${"%.1f".format(it.averageDelta)} 分"
        else if (it.averageDelta < -0.05) "較上一考下降 ${"%.1f".format(abs(it.averageDelta))} 分"
        else "與上一考表現接近"
    }
    return listOfNotNull(rankPart, levelPart, strengthPart, weaknessPart, comparePart)
        .joinToString("，")
        .plus("。")
}

private fun performanceLevel(topPercent: Int): String = when {
    topPercent <= 25 -> "班級前段"
    topPercent <= 50 -> "中上"
    topPercent <= 75 -> "中段"
    else -> "需要加強"
}

private fun subjectListText(subjects: List<SubjectScore>): String =
    subjects.joinToString("與") { shortenSubjectName(it.subjectName) }

private fun standardDistance(score: Double, standard: GradeStandard): String {
    val top = standard.top
    val front = standard.front
    val average = standard.average
    val back = standard.back
    return when {
        top != null && score >= top -> "已達頂標以上"
        top != null && front != null && score >= front -> "距頂標 ${"%.1f".format(top - score)} 分"
        front != null && score < front && average != null && score >= average -> "距前標 ${"%.1f".format(front - score)} 分"
        average != null && score < average && back != null && score >= back -> "距均標 ${"%.1f".format(average - score)} 分"
        back != null && score < back -> "低於後標 ${"%.1f".format(back - score)} 分"
        else -> "落點資料不足"
    }
}

private fun distributionSummary(score: Double, standard: GradeStandard): String {
    val mine = scoreDistributions(score, standard).firstOrNull { it.isMine } ?: return "分佈資料不足"
    val medianText = standard.average?.let {
        if (score >= it) "高於均標" else "低於均標"
    } ?: "均標資料不足"
    return "位於 ${mine.label}，該級距 ${mine.count} 人，$medianText"
}
