package com.clhs.score.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clhs.score.data.GradeAnalysis
import com.clhs.score.data.GradeReport
import com.clhs.score.data.GradeTrend
import com.clhs.score.data.ExamSummary
import com.clhs.score.data.ScoreInsightSet
import com.clhs.score.data.StudentInfo
import com.clhs.score.data.SubjectAnalysis
import com.clhs.score.data.SubjectScore
import com.clhs.score.data.cleanSubjectName
import com.clhs.score.data.shortenSubjectName
import com.clhs.score.viewmodel.GradesUiState
import kotlin.math.floor

private val PositiveColor = Color(0xFF059669)
private val NegativeColor = Color(0xFFDC2626)
private val NeutralColor = Color(0xFF64748B)

private enum class GradesDestination(
    val label: String,
    val icon: ImageVector,
) {
    Overview("總覽", Icons.Filled.Home),
    Subjects("科目", Icons.AutoMirrored.Filled.List),
    Advanced("進階", Icons.Filled.Star),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(
    state: GradesUiState,
    snackbarHost: @Composable () -> Unit,
    onSelectYear: (String) -> Unit,
    onSelectExam: (String) -> Unit,
    onReload: () -> Unit,
    onLogout: () -> Unit,
    onToggleSubject: (String) -> Unit,
    onOpenScoreSimulator: () -> Unit,
) {
    var selectedDestination by rememberSaveable { mutableIntStateOf(GradesDestination.Overview.ordinal) }
    val scrollState = rememberScrollState()

    LaunchedEffect(selectedDestination) {
        scrollState.scrollTo(0)
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 0.dp) {
                Column {
                    TopAppBar(
                        title = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "壢中成績",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = headerStudentText(state),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                enabled = !state.isLoadingStructure && !state.isLoadingGrades,
                                onClick = onReload,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "重新整理",
                                    tint = if (!state.isLoadingStructure && !state.isLoadingGrades) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                                    }
                                )
                            }
                            IconButton(onClick = onLogout) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = "登出",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                    GradeSelectors(
                        state = state,
                        onSelectYear = onSelectYear,
                        onSelectExam = onSelectExam,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
        snackbarHost = snackbarHost,
        bottomBar = {
            GradesBottomNavigation(
                selectedDestination = selectedDestination,
                onSelect = { selectedDestination = it.ordinal },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isLoadingStructure || state.isLoadingGrades || state.isLoadingComparison || state.isLoadingTrend) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
            ) {
                val report = state.report
                val analysis = state.analysis
                when {
                    report == null && (state.isLoadingStructure || state.isLoadingGrades) -> OverviewSkeleton()
                    report == null -> EmptyPanel(
                        message = if (state.structure.isEmpty()) "尚未取得可查詢考試" else "請選擇考試",
                        onReload = onReload,
                    )
                    analysis == null -> OverviewSkeleton()
                    else -> Crossfade(
                        targetState = selectedDestination,
                        label = "gradesDestinationFade",
                    ) { destination ->
                        when (destination) {
                            GradesDestination.Overview.ordinal -> OverviewTab(
                                report = report,
                                analysis = analysis,
                                isLoadingComparison = state.isLoadingComparison,
                                comparisonError = state.comparisonError,
                                isLoadingTrend = state.isLoadingTrend,
                                trendError = state.trendError,
                                trend = state.trend,
                                insights = state.insights,
                            )
                            GradesDestination.Subjects.ordinal -> SubjectsTab(
                                analyses = analysis.subjects,
                                expandedSubjectKeys = state.expandedSubjectKeys,
                                onToggleSubject = onToggleSubject,
                            )
                            GradesDestination.Advanced.ordinal -> AdvancedTab(
                                report = report,
                                analysis = analysis,
                                isLoadingTrend = state.isLoadingTrend,
                                trendError = state.trendError,
                                isLoadingSimulatorHistory = state.isLoadingSimulatorHistory,
                                simulatorHistoryLabel = state.simulatorHistoryLabel,
                                simulatorHistoryCount = state.simulatorHistoryReports.size,
                                trend = state.trend,
                                onOpenScoreSimulator = onOpenScoreSimulator,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun headerStudentText(state: GradesUiState): String {
    val student = state.report?.studentInfo
    if (student != null) {
        return listOf(
            student.studentName.takeIf { it.isNotBlank() },
            "${student.className.ifBlank { "--" }} ${student.seatNo.ifBlank { "--" }}",
            "${student.studentNo.ifBlank { state.studentNo.ifBlank { "--" } }}",
        ).filterNotNull().joinToString("｜")
    }
    return state.studentNo.takeIf { it.isNotBlank() }?.let { "學號 $it" } ?: "登入後顯示學生資訊"
}

@Composable
private fun GradesBottomNavigation(
    selectedDestination: Int,
    onSelect: (GradesDestination) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        GradesDestination.entries.forEach { destination ->
            val selected = selectedDestination == destination.ordinal
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun GradeSelectors(
    state: GradesUiState,
    onSelectYear: (String) -> Unit,
    onSelectExam: (String) -> Unit,
) {
    val selectedYear = state.structure.firstOrNull { it.value == state.selectedYearValue }
    val exams = selectedYear?.exams.orEmpty()
    val selectedExam = exams.firstOrNull { it.value == state.selectedExamValue }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OptionDropdown(
            modifier = Modifier.weight(1f),
            label = selectedYear?.text ?: "學年學期",
            enabled = state.structure.isNotEmpty() && !state.isLoadingStructure,
            options = state.structure,
            optionLabel = { it.text },
            onSelect = { onSelectYear(it.value) },
        )
        OptionDropdown(
            modifier = Modifier.weight(1f),
            label = selectedExam?.text ?: "考試",
            enabled = exams.isNotEmpty() && !state.isLoadingGrades,
            options = exams,
            optionLabel = { it.text },
            onSelect = { onSelectExam(it.value) },
        )
    }
}

@Composable
private fun <T> OptionDropdown(
    modifier: Modifier,
    label: String,
    enabled: Boolean,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            onClick = { expanded = true },
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun OverviewTab(
    report: GradeReport,
    analysis: GradeAnalysis,
    isLoadingComparison: Boolean,
    comparisonError: String?,
    isLoadingTrend: Boolean,
    trendError: String?,
    trend: GradeTrend?,
    insights: ScoreInsightSet?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        HeroCard(report, analysis)
        StrengthWeaknessCard(analysis)
        InsightCard(
            analysis = analysis,
            isLoadingComparison = isLoadingComparison,
            comparisonError = comparisonError,
            isLoadingTrend = isLoadingTrend,
            trendError = trendError,
            trend = trend,
            insights = insights,
        )
    }
}

@Composable
private fun AdvancedTab(
    report: GradeReport,
    analysis: GradeAnalysis,
    isLoadingTrend: Boolean,
    trendError: String?,
    isLoadingSimulatorHistory: Boolean,
    simulatorHistoryLabel: String?,
    simulatorHistoryCount: Int,
    trend: GradeTrend?,
    onOpenScoreSimulator: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(
            text = "進階",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        TrendChart(
            isLoadingTrend = isLoadingTrend,
            trendError = trendError,
            trend = trend,
        )
        ScoreSimulatorEntryCard(
            report = report,
            analysis = analysis,
            isLoadingHistory = isLoadingSimulatorHistory,
            historyLabel = simulatorHistoryLabel,
            historyCount = simulatorHistoryCount,
            onOpen = onOpenScoreSimulator,
        )
    }
}

@Composable
private fun HeroCard(report: GradeReport, analysis: GradeAnalysis) {
    val student = report.studentInfo
    val summary = report.examSummary
    val animatedAverage by animateFloatAsState(
        targetValue = analysis.weightedAverage.toFloat(),
        label = "weightedAverage",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = listOfNotNull(report.subjects.firstOrNull()?.yearTermDisplay, summary?.examName?.takeIf { it.isNotBlank() })
                    .joinToString(" ")
                    .ifBlank { "本次考試" },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "%.1f".format(animatedAverage),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "加權平均",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = heroRankLine(summary, student),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            )
            Text(
                text = analysis.classPercentile?.percentLabel ?: "尚無百分位資料",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = heroAverageDeltaText(analysis),
                    color = diffColor(analysis.comparison?.averageDelta ?: 0.0),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${trendGlyph(analysis.comparison?.averageDelta)} ${heroTrendStateText(analysis.comparison?.averageDelta)}",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun heroRankLine(summary: ExamSummary?, student: StudentInfo): String {
    val classRank = formatRank(summary?.classRank, summary?.classCount, student.showClassRankCount)
    val categoryRank = formatRank(summary?.categoryRank, summary?.categoryRankCount, student.showCategoryRankCount)
    return "班排 ${if (student.showClassRank) classRank else "--"} ・ 類排 ${if (student.showCategoryRank) categoryRank else "--"}"
}

private fun heroAverageDeltaText(analysis: GradeAnalysis): String {
    val delta = analysis.comparison?.averageDelta ?: return "尚無上次比較"
    return when {
        delta > 0.05 -> "較上次 +${"%.1f".format(delta)}"
        delta < -0.05 -> "較上次 ${"%.1f".format(delta)}"
        else -> "較上次 持平"
    }
}

private fun heroTrendStateText(delta: Double?): String = when {
    delta == null -> "等待歷次資料"
    delta > 0.05 -> "持續進步"
    delta < -0.05 -> "需要回穩"
    else -> "表現穩定"
}

@Composable
private fun InsightCard(
    analysis: GradeAnalysis,
    isLoadingComparison: Boolean,
    comparisonError: String?,
    isLoadingTrend: Boolean,
    trendError: String?,
    trend: GradeTrend?,
    insights: ScoreInsightSet?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("分析", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            DashboardInsightRow(
                label = "風險",
                text = riskInsightText(analysis, insights),
                color = NegativeColor,
            )
            DashboardInsightRow(
                label = "優勢",
                text = advantageInsightText(analysis),
                color = PositiveColor,
            )
            DashboardInsightRow(
                label = "ROI",
                text = roiInsightText(analysis, insights),
                color = MaterialTheme.colorScheme.primary,
            )
            when {
                isLoadingComparison -> InlineStatus("正在載入上一考比較...")
                analysis.comparison != null -> InlineStatus("${analysis.comparison.previousExamName}：${analysis.comparison.summaryText}")
                comparisonError != null -> InlineStatus(comparisonError)
            }
            when {
                isLoadingTrend -> InlineStatus("正在載入歷次趨勢...")
                trend != null && trend.points.size >= 2 -> InlineStatus("近 ${trend.points.size} 次平均：${trend.averageLine}")
                trendError != null -> InlineStatus(trendError)
            }
        }
    }
}

@Composable
private fun DashboardInsightRow(label: String, text: String, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun riskInsightText(analysis: GradeAnalysis, insights: ScoreInsightSet?): String {
    insights?.projection?.let { projection ->
        val subject = shortenSubjectName(projection.subjectName)
        return "$subject 目前為主要拉低科目，若提升至班均，加權平均約可提升 ${"%.1f".format(projection.weightedAverageGain)}。"
    }
    val weakness = analysis.weaknesses.firstOrNull()
    return weakness?.let { "${shortenSubjectName(it.subjectName)} 低於班平均 ${"%.1f".format(kotlin.math.abs(it.diffValue))} 分，建議優先處理。" }
        ?: "目前沒有明顯拉低科目，風險集中度低。"
}

private fun advantageInsightText(analysis: GradeAnalysis): String {
    val strength = analysis.strengths.firstOrNull()
    return strength?.let {
        "${shortenSubjectName(it.subjectName)} ${subjectPercentLabel(it.classRank, it.classRankCount)}，高於班平均 ${"%.1f".format(it.diffValue)} 分，建議維持目前節奏。"
    } ?: "尚無明顯優勢科目，先把各科穩定在班平均附近。"
}

private fun roiInsightText(analysis: GradeAnalysis, insights: ScoreInsightSet?): String {
    val projection = insights?.projection
    return projection?.let {
        "目前投入效益最高科目為 ${shortenSubjectName(it.subjectName)}，每次小幅提升會直接推動加權平均。"
    } ?: analysis.weaknesses.firstOrNull()?.let {
        "目前投入效益最高科目為 ${shortenSubjectName(it.subjectName)}。"
    } ?: "目前各科差距接近，ROI 最高的方向是維持弱科不下滑。"
}

@Composable
private fun StrengthWeaknessCard(analysis: GradeAnalysis) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("摘要", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        SubjectHighlightRow(title = "優勢科目", subjects = analysis.strengths, color = PositiveColor, emptyText = "尚無明顯高於平均的科目")
        SubjectHighlightRow(title = "待加強科目", subjects = analysis.weaknesses, color = NegativeColor, emptyText = "尚無明顯低於平均的科目")
    }
}

@Composable
private fun SubjectHighlightRow(title: String, subjects: List<SubjectScore>, color: Color, emptyText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (subjects.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                subjects.forEach { subject ->
                    Column(
                        modifier = Modifier
                            .width(150.dp)
                            .background(color.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = shortenSubjectName(subject.subjectName),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = diffSentence(subject.diffValue),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = color,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = subjectPercentLabel(subject.classRank, subject.classRankCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectsTab(
    analyses: List<SubjectAnalysis>,
    expandedSubjectKeys: Set<String>,
    onToggleSubject: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        analyses.forEach { analysis ->
            SubjectCard(
                analysis = analysis,
                expanded = cleanSubjectName(analysis.subject.subjectName) in expandedSubjectKeys,
                onToggle = { onToggleSubject(analysis.subject.subjectName) },
            )
        }
    }
}

@Composable
private fun InlineStatus(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OverviewSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(4) {
            SkeletonBlock(height = if (it == 1) 190.dp else 96.dp)
        }
    }
}

@Composable
private fun SkeletonBlock(height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f), RoundedCornerShape(16.dp)),
    )
}

@Composable
private fun EmptyPanel(message: String, onReload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(shape = RoundedCornerShape(14.dp), onClick = onReload) {
            Text("重新整理")
        }
    }
}

private fun formatRank(rank: Double?, count: Int?, showCount: Boolean): String {
    if (rank == null) return "--"
    val rankText = floor(rank).toInt().toString()
    return if (showCount && count != null && count > 0) "$rankText/$count" else rankText
}

private fun subjectPercentLabel(rank: Int?, count: Int?): String {
    if (rank == null || count == null || count <= 0) return "--"
    val percent = ((rank.toDouble() / count) * 100.0).toInt().coerceIn(1, 100)
    return "前 $percent%"
}

private fun signedValue(value: Double): String = "${if (value >= 0.0) "+" else ""}${"%.1f".format(value)}"

private fun Double?.formatCompactScore(): String = this?.let { "%.0f".format(it) } ?: "--"

private fun diffSentence(diff: Double): String = when {
    diff > 0.05 -> "高於平均 ${signedValue(diff)}"
    diff < -0.05 -> "低於平均 ${signedValue(diff)}"
    else -> "接近班級平均"
}

private fun trendGlyph(diff: Double?): String = when {
    diff == null -> "→"
    diff > 0.05 -> "↑"
    diff < -0.05 -> "↓"
    else -> "→"
}

private fun diffColor(diff: Double): Color = when {
    diff > 0.05 -> PositiveColor
    diff < -0.05 -> NegativeColor
    else -> NeutralColor
}
