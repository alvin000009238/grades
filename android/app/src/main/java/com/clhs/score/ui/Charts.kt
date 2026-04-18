package com.clhs.score.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clhs.score.data.GradeAnalysis
import com.clhs.score.data.GradeReport
import com.clhs.score.data.GradeStandard
import com.clhs.score.data.SubjectAnalysis
import com.clhs.score.data.SubjectScore
import com.clhs.score.data.buildGradeAnalysis
import com.clhs.score.data.gradeLevel
import com.clhs.score.data.scoreDistributions
import com.clhs.score.data.shortenSubjectName
import com.clhs.score.data.standardFor
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val MyScoreColor = Color(0xFF6366F1)
private val AvgScoreColor = Color(0xFF10B981)

@Composable
fun ChartsTab(report: GradeReport) {
    AnalysisSection(report = report, analysis = buildGradeAnalysis(report))
}

@Composable
fun AnalysisSection(report: GradeReport, analysis: GradeAnalysis) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "分析模組",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        ChartCard(
            title = "雷達分析",
            summary = overviewChartSummary(analysis),
        ) {
            RadarScoreChart(subjects = report.subjects)
        }
        ChartCard(
            title = "成績比較",
            summary = barChartSummary(analysis),
        ) {
            BarScoreChart(subjects = report.subjects)
        }
        StandardsTable(analysis = analysis)
        DistributionSection(analysis = analysis)
    }
}

@Composable
private fun ChartCard(
    title: String,
    summary: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = summary,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
            Spacer(modifier = Modifier.height(8.dp))
            Legend()
        }
    }
}

@Composable
private fun Legend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(color = MyScoreColor, label = "我的成績")
        LegendItem(color = AvgScoreColor, label = "班級平均")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(4.dp)),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RadarScoreChart(subjects: List<SubjectScore>) {
    if (subjects.size < 3) {
        EmptyChartMessage("至少需要三個科目才能繪製雷達圖")
        return
    }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    val labels = remember(subjects) { subjects.map { shortenSubjectName(it.subjectName) } }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) * 0.34f
        val count = subjects.size
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = 12.dp.toPx()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        for (ring in 1..5) {
            val ringRadius = radius * ring / 5f
            drawPath(
                path = radarPath(center, ringRadius, count) { 1f },
                color = gridColor,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        for (i in 0 until count) {
            val point = radarPoint(center, radius, i, count, 1f)
            drawLine(gridColor, center, point, strokeWidth = 1.dp.toPx())
            val labelPoint = radarPoint(center, radius + 22.dp.toPx(), i, count, 1f)
            drawContext.canvas.nativeCanvas.drawText(labels[i], labelPoint.x, labelPoint.y + 4.dp.toPx(), textPaint)
        }

        val avgPath = radarPath(center, radius, count) { index -> (subjects[index].classAverageValue / 100.0).toFloat() }
        drawPath(avgPath, AvgScoreColor.copy(alpha = 0.22f))
        drawPath(avgPath, AvgScoreColor, style = Stroke(width = 2.dp.toPx()))

        val myPath = radarPath(center, radius, count) { index -> (subjects[index].scoreValue / 100.0).toFloat() }
        drawPath(myPath, MyScoreColor.copy(alpha = 0.24f))
        drawPath(myPath, MyScoreColor, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
fun BarScoreChart(subjects: List<SubjectScore>) {
    if (subjects.isEmpty()) {
        EmptyChartMessage("沒有可繪製的科目成績")
        return
    }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    val labels = remember(subjects) { subjects.map { shortenSubjectName(it.subjectName) } }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
    ) {
        val left = 34.dp.toPx()
        val right = 8.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = 46.dp.toPx()
        val plotWidth = size.width - left - right
        val plotHeight = size.height - top - bottom
        val origin = Offset(left, top + plotHeight)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = 11.dp.toPx()
        }
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textAlign = Paint.Align.RIGHT
            textSize = 10.dp.toPx()
        }

        for (step in 0..5) {
            val score = step * 20
            val y = origin.y - plotHeight * step / 5f
            drawLine(gridColor, Offset(left, y), Offset(size.width - right, y), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(score.toString(), left - 6.dp.toPx(), y + 4.dp.toPx(), axisPaint)
        }

        val groupWidth = plotWidth / subjects.size
        val barWidth = min(groupWidth * 0.28f, 20.dp.toPx())
        subjects.forEachIndexed { index, subject ->
            val groupCenter = left + groupWidth * index + groupWidth / 2f
            drawScoreBar(
                centerX = groupCenter - barWidth * 0.6f,
                baseY = origin.y,
                plotHeight = plotHeight,
                barWidth = barWidth,
                score = subject.scoreValue,
                color = MyScoreColor,
            )
            drawScoreBar(
                centerX = groupCenter + barWidth * 0.6f,
                baseY = origin.y,
                plotHeight = plotHeight,
                barWidth = barWidth,
                score = subject.classAverageValue,
                color = AvgScoreColor,
            )
            if (subjects.size <= 8 || index % 2 == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    labels[index],
                    groupCenter,
                    size.height - 18.dp.toPx(),
                    textPaint,
                )
            }
        }
    }
}

@Composable
private fun StandardsTable(analysis: GradeAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "五標分析",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                StandardRow(
                    values = listOf("科目", "頂標", "前標", "均標", "後標", "底標", "我的", "落點", "距離"),
                    header = true,
                )
                analysis.subjects.forEach { subjectAnalysis ->
                    val subject = subjectAnalysis.subject
                    val standard = subjectAnalysis.standard ?: return@forEach
                    StandardRow(
                        values = listOf(
                            shortenSubjectName(subject.subjectName),
                            standard.top.formatScore(),
                            standard.front.formatScore(),
                            standard.average.formatScore(),
                            standard.back.formatScore(),
                            standard.bottom.formatScore(),
                            subject.scoreValue.formatScore(),
                            gradeLevel(subject.scoreValue, standard),
                            subjectAnalysis.standardDistance ?: "--",
                        ),
                        header = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun StandardRow(values: List<String>, header: Boolean) {
    Row(
        modifier = Modifier
            .background(
                if (header) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        values.forEachIndexed { index, value ->
            Text(
                modifier = Modifier.width(if (index == 0) 96.dp else 72.dp),
                text = value,
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                color = if (header) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DistributionSection(analysis: GradeAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "分數分布",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "預設只顯示自己所在級距；點擊科目可展開完整分布。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            analysis.subjects.forEach { subjectAnalysis ->
                val standard = subjectAnalysis.standard ?: return@forEach
                DistributionCard(analysis = subjectAnalysis, standard = standard)
            }
        }
    }
}

@Composable
private fun DistributionCard(analysis: SubjectAnalysis, standard: GradeStandard) {
    var expanded by remember { mutableStateOf(false) }
    val subject = analysis.subject
    val distributions = scoreDistributions(subject.scoreValue, standard)
    val visibleDistributions = if (expanded) distributions else distributions.filter { it.isMine }
    val total = max(distributions.sumOf { it.count }, 1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = shortenSubjectName(subject.subjectName),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        analysis.distributionSummary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        visibleDistributions.forEach { item ->
            val widthFraction = (item.count.toFloat() / total).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.width(58.dp),
                    text = item.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(6.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(widthFraction)
                            .height(18.dp)
                            .background(distributionColor(item.label), RoundedCornerShape(6.dp)),
                    )
                    if (item.isMine) {
                        Text(
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                            text = "我",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    modifier = Modifier.width(42.dp),
                    text = "${item.count}人",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = if (expanded) "收合完整分佈" else "展開完整分佈",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyChartMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun radarPath(
    center: Offset,
    radius: Float,
    count: Int,
    valueAt: (Int) -> Float,
): Path {
    val path = Path()
    for (index in 0 until count) {
        val point = radarPoint(center, radius, index, count, valueAt(index).coerceIn(0f, 1.2f))
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    return path
}

private fun radarPoint(center: Offset, radius: Float, index: Int, count: Int, scale: Float): Offset {
    val angle = Math.toRadians(-90.0 + 360.0 * index / count)
    return Offset(
        x = center.x + cos(angle).toFloat() * radius * scale,
        y = center.y + sin(angle).toFloat() * radius * scale,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScoreBar(
    centerX: Float,
    baseY: Float,
    plotHeight: Float,
    barWidth: Float,
    score: Double,
    color: Color,
) {
    val barHeight = plotHeight * (score / 100.0).coerceIn(0.0, 1.2).toFloat()
    drawRect(
        color = color,
        topLeft = Offset(centerX - barWidth / 2f, baseY - barHeight),
        size = Size(barWidth, barHeight),
    )
}

private fun Double?.formatScore(): String = this?.let { "%.2f".format(it) } ?: "--"

private fun distributionColor(label: String): Color = when (label) {
    "90-100", "80-89" -> Color(0xFF10B981)
    "70-79" -> Color(0xFF3B82F6)
    "60-69" -> Color(0xFFF59E0B)
    "50-59" -> Color(0xFFF97316)
    else -> Color(0xFFEF4444)
}

private fun overviewChartSummary(analysis: GradeAnalysis): String {
    val strengths = analysis.strengths.take(2).joinToString("、") { shortenSubjectName(it.subjectName) }
    val weaknesses = analysis.weaknesses.take(2).joinToString("、") { shortenSubjectName(it.subjectName) }
    return when {
        strengths.isNotBlank() && weaknesses.isNotBlank() -> "$strengths 高於平均；$weaknesses 需要留意。"
        strengths.isNotBlank() -> "$strengths 高於班平均，是本次主要優勢。"
        weaknesses.isNotBlank() -> "$weaknesses 低於班平均，建議優先檢視。"
        else -> "各科與班平均差距接近，整體表現均衡。"
    }
}

private fun barChartSummary(analysis: GradeAnalysis): String {
    val strongest = analysis.strengths.firstOrNull()
    val weakest = analysis.weaknesses.firstOrNull()
    return when {
        strongest != null && weakest != null -> "與班平均相比，${shortenSubjectName(strongest.subjectName)} 優勢較明顯，${shortenSubjectName(weakest.subjectName)} 差距最大。"
        strongest != null -> "${shortenSubjectName(strongest.subjectName)} 與班平均差距最有利。"
        weakest != null -> "${shortenSubjectName(weakest.subjectName)} 與班平均差距較不利。"
        else -> "各科分數與班平均落差不大。"
    }
}
