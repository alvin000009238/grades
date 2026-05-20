package com.clhs.score.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clhs.score.data.GradeAnalysis
import com.clhs.score.data.GradeReport
import com.clhs.score.data.GradeTrend
import com.clhs.score.data.SubjectScore
import com.clhs.score.data.cleanSubjectName
import com.clhs.score.data.shortenSubjectName
import com.clhs.score.data.simulateScores
import com.clhs.score.viewmodel.GradesUiState
import kotlin.math.roundToInt

@Composable
internal fun TrendChart(
    isLoadingTrend: Boolean,
    trendError: String?,
    trend: GradeTrend?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("歷次考試趨勢", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            when {
                isLoadingTrend -> ChartLoadingPlaceholder()
                trend != null && trend.points.size >= 2 -> {
                    trend.points.forEachIndexed { index, point ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        if (index == trend.points.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(999.dp),
                                    ),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(point.examName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "平均 ${"%.1f".format(point.weightedAverage)} ｜ 班排 ${point.classRank ?: "--"}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            point.highestScore?.let {
                                Text(
                                    text = "最高 ${"%.0f".format(it)}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                else -> EmptyAnalysisState(trendError ?: "目前沒有足夠歷次考試資料。")
            }
        }
    }
}

@Composable
internal fun ScoreSimulatorEntryCard(
    report: GradeReport,
    analysis: GradeAnalysis,
    isLoadingHistory: Boolean,
    historyLabel: String?,
    historyCount: Int,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("成績模擬器（beta）", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = "調整 ${report.subjects.size} 個科目分數，依歷次考試估算平均與班排。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(modifier = Modifier.weight(1f), label = "目前平均", value = "%.1f".format(analysis.weightedAverage))
                InfoChip(modifier = Modifier.weight(1f), label = "科目", value = "${report.subjects.size} 科")
            }
            Text(
                text = when {
                    isLoadingHistory -> "正在載入歷史資料..."
                    historyCount > 0 -> "預測依據：${historyLabel ?: "歷次考試"}"
                    else -> "尚無歷史資料，仍可調整目前平均"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScoreSimulatorScreen(
    state: GradesUiState,
    snackbarHost: @Composable () -> Unit,
    onBack: () -> Unit,
) {
    val report = state.report
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("成績模擬器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = snackbarHost,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.isLoadingSimulatorHistory) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (report == null) {
                EmptyAnalysisState("尚未載入成績資料。")
                return@Column
            }

            val initialScores = remember(report) {
                report.subjects.associate { cleanSubjectName(it.subjectName) to it.scoreValue }
            }
            var adjustedScores by remember(report) { mutableStateOf(initialScores) }
            val simulation = remember(report, state.simulatorHistoryReports, adjustedScores) {
                simulateScores(
                    currentReport = report,
                    historyReports = state.simulatorHistoryReports,
                    adjustedScores = adjustedScores,
                )
            }

            SimulatorSummaryCard(
                adjustedAverage = simulation.adjustedAverage,
                projectedAverage = simulation.projectedAverage,
                trendDelta = simulation.trendDelta,
                estimatedClassRank = simulation.estimatedClassRank,
                historyText = state.simulatorHistoryLabel ?: if (simulation.historyCount > 0) "歷次考試" else "無歷史資料",
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = "科目分數",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        TextButton(onClick = { adjustedScores = initialScores }) {
                            Text("恢復全部")
                        }
                    }
                    report.subjects.forEachIndexed { index, subject ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        SubjectScoreSlider(
                            subject = subject,
                            value = adjustedScores[cleanSubjectName(subject.subjectName)] ?: subject.scoreValue,
                            onValueChange = { score ->
                                adjustedScores = adjustedScores + (cleanSubjectName(subject.subjectName) to score)
                            },
                            onResetSubject = {
                                adjustedScores = adjustedScores + (cleanSubjectName(subject.subjectName) to subject.scoreValue)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimulatorSummaryCard(
    adjustedAverage: Double,
    projectedAverage: Double,
    trendDelta: Double,
    estimatedClassRank: Int?,
    historyText: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("預測結果", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(modifier = Modifier.weight(1f), label = "調整後平均", value = "%.1f".format(adjustedAverage))
                InfoChip(modifier = Modifier.weight(1f), label = "預測平均", value = "%.1f".format(projectedAverage))
                InfoChip(modifier = Modifier.weight(1f), label = "預估班排", value = estimatedClassRank?.let { "第 $it 名" } ?: "--")
            }
            Text(
                text = "歷史依據：$historyText｜趨勢 ${signedDelta(trendDelta)} 分",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = estimatedClassRank?.let { "預估班排：第 $it 名" } ?: "歷史資料不足，暫無法準確預測",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (estimatedClassRank != null) {
                Text(
                    text = "此結果根據歷史相對表現與班排趨勢估算，實際排名可能受全班成績分布影響。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SubjectScoreSlider(
    subject: SubjectScore,
    value: Double,
    onValueChange: (Double) -> Unit,
    onResetSubject: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val diff = value - subject.scoreValue
    fun updateScore(score: Double, haptic: Boolean = false) {
        if (haptic) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        onValueChange(score.coerceIn(0.0, 100.0))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = shortenSubjectName(subject.subjectName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "原始 ${"%.0f".format(subject.scoreValue)}｜調整 ${signedDelta(diff)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                modifier = Modifier.clickable(onClick = onResetSubject),
                text = "%.0f".format(value),
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScoreStepButton(label = "-5", onClick = { updateScore(value - 5.0, haptic = true) })
            }
            Slider(
                modifier = Modifier.weight(1f),
                value = value.toFloat(),
                onValueChange = { updateScore(it.roundToInt().toDouble(), haptic = true) },
                valueRange = 0f..100f,
                steps = 99,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScoreStepButton(label = "+5", onClick = { updateScore(value + 5.0, haptic = true) })
            }
        }
    }
}

@Composable
private fun ScoreStepButton(
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}

@Composable
private fun ChartLoadingPlaceholder() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun EmptyAnalysisState(message: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun signedDelta(value: Double): String = when {
    value > 0.05 -> "+${"%.1f".format(value)}"
    value < -0.05 -> "%.1f".format(value)
    else -> "0.0"
}
