package com.clhs.score.data

import kotlinx.serialization.json.JsonObject

data class CaptchaChallenge(
    val loginToken: String,
    val shCaptchaGenCode: String,
    val deviceToken: String,
    val cookies: Map<String, String>,
    val imageBytes: ByteArray,
    val contentType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CaptchaChallenge) return false
        return loginToken == other.loginToken &&
            shCaptchaGenCode == other.shCaptchaGenCode &&
            deviceToken == other.deviceToken &&
            cookies == other.cookies &&
            imageBytes.contentEquals(other.imageBytes) &&
            contentType == other.contentType
    }

    override fun hashCode(): Int {
        var result = loginToken.hashCode()
        result = 31 * result + shCaptchaGenCode.hashCode()
        result = 31 * result + deviceToken.hashCode()
        result = 31 * result + cookies.hashCode()
        result = 31 * result + imageBytes.contentHashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}

data class AuthenticatedSession(
    val studentNo: String,
    val apiToken: String,
    val cookies: Map<String, String>,
)

data class YearTermOption(
    val text: String,
    val value: String,
    val exams: List<ExamOption> = emptyList(),
)

data class ExamOption(
    val text: String,
    val value: String,
)

data class GradeReport(
    val message: String,
    val studentInfo: StudentInfo,
    val examSummary: ExamSummary?,
    val subjects: List<SubjectScore>,
    val standards: List<GradeStandard>,
    val rawResult: JsonObject,
)

data class StudentInfo(
    val studentNo: String,
    val studentName: String,
    val className: String,
    val seatNo: String,
    val updatedAt: String,
    val showClassRank: Boolean,
    val showClassRankCount: Boolean,
    val showCategoryRank: Boolean,
    val showCategoryRankCount: Boolean,
)

data class ExamSummary(
    val year: Int?,
    val termText: String,
    val examName: String,
    val totalScoreDisplay: String,
    val averageScoreDisplay: String,
    val classRank: Double?,
    val classCount: Int?,
    val categoryRank: Double?,
    val categoryRankCount: Int?,
    val flunkCount: Int?,
)

data class SubjectScore(
    val subjectName: String,
    val scoreDisplay: String,
    val score: Double?,
    val classAverageDisplay: String,
    val classAverage: Double?,
    val classRank: Int?,
    val classRankCount: Int?,
    val yearRank: Int?,
    val yearRankCount: Int?,
    val yearTermDisplay: String,
    val flunk: Boolean,
    val absent: Boolean,
    val cheating: Boolean,
) {
    val scoreValue: Double
        get() = scoreDisplay.toDoubleOrNull() ?: score ?: 0.0

    val classAverageValue: Double
        get() = classAverageDisplay.toDoubleOrNull() ?: classAverage ?: 0.0

    val diffValue: Double
        get() = scoreValue - classAverageValue
}

data class GradeStandard(
    val subjectName: String,
    val top: Double?,
    val front: Double?,
    val average: Double?,
    val back: Double?,
    val bottom: Double?,
    val standardDeviation: Double?,
    val above90Count: Int,
    val above80Count: Int,
    val above70Count: Int,
    val above60Count: Int,
    val above50Count: Int,
    val above40Count: Int,
    val above30Count: Int,
    val above20Count: Int,
    val above10Count: Int,
    val above0Count: Int,
)

data class ScoreDistribution(
    val label: String,
    val count: Int,
    val isMine: Boolean,
)

fun parseYearTerm(value: String?, defaultYear: String = "114", defaultTerm: String = "1"): Pair<String, String> {
    val raw = value.orEmpty()
    return when {
        "_" in raw -> raw.split("_", limit = 2).let { it[0] to it.getOrElse(1) { defaultTerm } }
        raw.length >= 4 -> raw.dropLast(1) to raw.takeLast(1)
        else -> defaultYear to defaultTerm
    }
}

fun cleanSubjectName(name: String): String = name.replace("<br/>", "")

fun shortenSubjectName(name: String): String {
    val cleaned = cleanSubjectName(name)
    return cleaned.substringBefore("-").trim().ifEmpty { cleaned }
}

fun subjectWeight(subjectName: String): Int {
    val weights = mapOf("國語文" to 4, "英語文" to 4, "數學" to 4)
    weights[subjectName]?.let { return it }
    return weights.entries.firstOrNull { (key, _) ->
        subjectName.contains(key) || key.contains(subjectName)
    }?.value ?: 2
}

fun GradeReport.weightedAverage(): Double {
    val totalWeight = subjects.sumOf { subjectWeight(it.subjectName) }
    if (totalWeight <= 0) return 0.0
    val weightedTotal = subjects.sumOf { it.scoreValue * subjectWeight(it.subjectName) }
    return weightedTotal / totalWeight
}

fun GradeReport.highestScore(): Double? = subjects.maxOfOrNull { it.scoreValue }

fun GradeReport.standardFor(subject: SubjectScore, index: Int): GradeStandard? {
    val cleanName = cleanSubjectName(subject.subjectName)
    return standards.firstOrNull { cleanSubjectName(it.subjectName) == cleanName } ?: standards.getOrNull(index)
}

fun gradeLevel(score: Double, standard: GradeStandard): String {
    val top = standard.top
    val front = standard.front
    val average = standard.average
    val back = standard.back
    return when {
        top != null && score >= top -> "頂標以上"
        front != null && score >= front -> "前標以上"
        average != null && score >= average -> "均標以上"
        back != null && score >= back -> "後標以上"
        else -> "底標以下"
    }
}

fun scoreDistributions(score: Double, standard: GradeStandard): List<ScoreDistribution> {
    val ranges = listOf(
        "90-100" to standard.above90Count,
        "80-89" to standard.above80Count,
        "70-79" to standard.above70Count,
        "60-69" to standard.above60Count,
        "50-59" to standard.above50Count,
        "0-49" to (
            standard.above40Count +
                standard.above30Count +
                standard.above20Count +
                standard.above10Count +
                standard.above0Count
            ),
    )
    val myRange = when {
        score >= 90.0 -> "90-100"
        score >= 80.0 -> "80-89"
        score >= 70.0 -> "70-79"
        score >= 60.0 -> "60-69"
        score >= 50.0 -> "50-59"
        else -> "0-49"
    }
    return ranges.map { (label, count) -> ScoreDistribution(label, count, label == myRange) }
}
