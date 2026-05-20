package com.clhs.score.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clhs.score.data.GradeStandard
import com.clhs.score.data.SubjectAnalysis
import com.clhs.score.data.cleanSubjectName
import com.clhs.score.data.deltaText
import com.clhs.score.data.scoreDistributions
import com.clhs.score.data.shortenSubjectName
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.roundToInt

private val SubjectPositive = Color(0xFF0B7D3B)
private val SubjectWarning = Color(0xFFE87500)
private val SubjectNegative = Color(0xFFBA1A1A)
private val SubjectNeutral = Color(0xFF625B71)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SubjectCard(
    analysis: SubjectAnalysis,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val subject = analysis.subject
    val diffColor = diffColor(subject.diffValue)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(expanded) {
        if (expanded) {
            withFrameNanos { }
            bringIntoViewRequester.bringIntoView()
            delay(320)
            bringIntoViewRequester.bringIntoView()
        }
    }

    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = shortenSubjectName(subject.subjectName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "平均 ${"%.1f".format(subject.classAverageValue)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = subject.scoreDisplay.ifBlank { "%.1f".format(subject.scoreValue) },
                        color = scoreColor(subject.scoreValue),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${diffArrow(subject.diffValue)} ${signedValue(kotlin.math.abs(subject.diffValue))}",
                        color = diffColor,
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(
                    modifier = Modifier.weight(1f),
                    label = "百分位",
                    value = subjectPercentLabel(subject.classRank, subject.classRankCount),
                )
                InfoChip(
                    modifier = Modifier.weight(1f),
                    label = "班排",
                    value = formatRank(subject.classRank?.toDouble(), subject.classRankCount, true),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = 140,
                        delayMillis = 40,
                        easing = LinearOutSlowInEasing,
                    ),
                ) + expandVertically(
                    expandFrom = Alignment.Top,
                    clip = false,
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing,
                    ),
                ),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = 90,
                        easing = FastOutSlowInEasing,
                    ),
                ) + shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    clip = false,
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = FastOutSlowInEasing,
                    ),
                ),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    analysis.standard?.let { standard ->
                        StandardPositionBar(
                            score = subject.scoreValue,
                            standard = standard,
                        )
                        DistributionBar(
                            score = subject.scoreValue,
                            standard = standard,
                        )
                    } ?: EmptySubjectDetail("尚無五標與分布資料")

                    DetailRow("歷次成績", analysis.comparison?.let { deltaText("較上一考", it.scoreDelta, "分") } ?: "尚無上一考可比較")
                }
            }
        }
    }
}

@Composable
internal fun InfoChip(modifier: Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun DistributionBar(score: Double, standard: GradeStandard) {
    val distributions = scoreDistributions(score, standard)
    val displayDistributions = distributions.asReversed()
    val total = distributions.sumOf { it.count }.coerceAtLeast(1)
    val mine = distributions.firstOrNull { it.isMine }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = "分數分布",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = mine?.let { "我的級距 ${it.label}" } ?: "我的級距 --",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            displayDistributions.forEach { item ->
                val weight = (item.count.toFloat() / total).coerceAtLeast(0.02f)
                Box(
                    modifier = Modifier
                        .weight(weight)
                        .height(24.dp)
                        .background(distributionColor(item.label), RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.isMine) {
                        Text(
                            modifier = Modifier
                                .background(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFFCBD5E1),
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            text = "我",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.2.sp
                            ),
                            color = Color(0xFF334155)
                        )
                    }
                }
            }
        }
        displayDistributions.forEach { item ->
            DistributionRow(item = item, total = total)
        }
    }
}

@Composable
private fun DistributionRow(item: com.clhs.score.data.ScoreDistribution, total: Int) {
    val percentFraction = (item.count.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val percent = percentFraction * 100f
    val barShape = RoundedCornerShape(999.dp)
    val barColor = distributionColor(item.label)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            modifier = Modifier.width(58.dp),
            text = item.label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF3F3A46),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .clip(barShape)
                .background(barColor.copy(alpha = 0.14f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentFraction)
                    .height(18.dp)
                    .background(barColor),
            )
            Text(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
                text = "${"%.0f".format(percent)}%",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = Color(0xFF3F3A46),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            modifier = Modifier.width(48.dp),
            text = "${item.count}人",
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = Color(0xFF3F3A46),
            fontWeight = if (item.isMine) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
internal fun StandardPositionBar(score: Double, standard: GradeStandard) {
    val marks = listOfNotNull(
        standard.bottom?.let { StandardMarkSpec("底", it) },
        standard.back?.let { StandardMarkSpec("後", it) },
        standard.average?.let { StandardMarkSpec("均", it) },
        standard.front?.let { StandardMarkSpec("前", it) },
        standard.top?.let { StandardMarkSpec("頂", it) },
    )
    val low = marks.minOfOrNull { it.value } ?: 0.0
    val high = marks.maxOfOrNull { it.value }?.coerceAtLeast(low + 1.0) ?: 100.0
    val fraction = ((score - low) / (high - low)).coerceIn(0.0, 1.0).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("班級五標", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(fraction.coerceAtLeast(0.001f))
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(999.dp)),
            )
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp)),
            )
            Box(
                modifier = Modifier
                    .weight((1f - fraction).coerceAtLeast(0.001f))
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp)),
            )
        }
        ProportionalStandardMarks(marks = marks, low = low, high = high)
    }
}

private data class StandardMarkSpec(
    val label: String,
    val value: Double,
)

@Composable
private fun ProportionalStandardMarks(marks: List<StandardMarkSpec>, low: Double, high: Double) {
    val range = (high - low).takeIf { it > 0.0 } ?: 1.0
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            marks.forEach { mark ->
                StandardMark(modifier = Modifier, label = mark.label, value = mark.value)
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0))
        }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val mark = marks[index]
                val fraction = ((mark.value - low) / range).coerceIn(0.0, 1.0)
                val x = (fraction * width - placeable.width / 2.0)
                    .roundToInt()
                    .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.placeRelative(x, 0)
            }
        }
    }
}

@Composable
private fun StandardMark(modifier: Modifier, label: String, value: Double?) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value.formatCompactScore(),
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptySubjectDetail(message: String) {
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

private fun subjectPercentLabel(rank: Int?, count: Int?): String {
    if (rank == null || count == null || count <= 0) return "--"
    val percent = ((rank.toDouble() / count) * 100.0).toInt().coerceIn(1, 100)
    return "前 $percent%"
}

private fun formatRank(rank: Double?, count: Int?, showCount: Boolean): String {
    if (rank == null) return "--"
    val rankText = floor(rank).toInt().toString()
    return if (showCount && count != null && count > 0) "$rankText/$count" else rankText
}

private fun signedValue(value: Double): String = "%.1f".format(value)

private fun diffArrow(diff: Double): String = when {
    diff > 0.05 -> "⬆"
    diff < -0.05 -> "⬇"
    else -> "→"
}

private fun diffColor(diff: Double): Color = when {
    diff > 0.05 -> SubjectPositive
    diff < -0.05 -> SubjectNegative
    else -> SubjectNeutral
}

private fun scoreColor(score: Double): Color = when {
    score >= 80.0 -> SubjectPositive
    score >= 60.0 -> SubjectWarning
    else -> SubjectNegative
}

private fun Double?.formatCompactScore(): String = this?.let { "%.0f".format(it) } ?: "--"

private fun distributionColor(label: String): Color = when (label) {
    "90-100" -> Color(0xFF4F8F63)
    "80-89" -> Color(0xFF4B8F86)
    "70-79" -> Color(0xFF5A789A)
    "60-69" -> Color(0xFFC47A2C)
    "50-59" -> Color(0xFFC26A45)
    else -> Color(0xFFB75E62)
}
