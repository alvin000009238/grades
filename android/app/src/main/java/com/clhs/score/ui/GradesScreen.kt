package com.clhs.score.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clhs.score.data.GradeAnalysis
import com.clhs.score.data.GradeReport
import com.clhs.score.data.GradeTrend
import com.clhs.score.data.ScoreInsight
import com.clhs.score.data.ScoreInsightSet
import com.clhs.score.data.ScoreInsightTone
import com.clhs.score.data.SubjectAnalysis
import com.clhs.score.data.SubjectScore
import com.clhs.score.data.cleanSubjectName
import com.clhs.score.data.deltaText
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
    Overview("總覽", Icons.Outlined.Dashboard),
    Subjects("科目", Icons.AutoMirrored.Outlined.FormatListBulleted),
    Advanced("進階", Icons.Outlined.Analytics),
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
                                    text = "成績查詢",
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
                            TextButton(
                                enabled = !state.isLoadingStructure && !state.isLoadingGrades,
                                onClick = onReload,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                                ),
                            ) {
                                Text("重新整理")
                            }
                            TextButton(
                                onClick = onLogout,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Text("登出")
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
        containerColor = MaterialTheme.colorScheme.background,
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
                    selectedDestination == GradesDestination.Overview.ordinal -> OverviewTab(
                        report = report,
                        analysis = analysis,
                        isLoadingComparison = state.isLoadingComparison,
                        comparisonError = state.comparisonError,
                        isLoadingTrend = state.isLoadingTrend,
                        trendError = state.trendError,
                        trend = state.trend,
                        insights = state.insights,
                        onOpenSubjects = { selectedDestination = GradesDestination.Subjects.ordinal },
                        onOpenCharts = { selectedDestination = GradesDestination.Advanced.ordinal },
                        onOpenDistribution = { selectedDestination = GradesDestination.Advanced.ordinal },
                    )
                    selectedDestination == GradesDestination.Subjects.ordinal -> SubjectsTab(
                        analyses = analysis.subjects,
                        expandedSubjectKeys = state.expandedSubjectKeys,
                        onToggleSubject = onToggleSubject,
                    )
                    selectedDestination == GradesDestination.Advanced.ordinal -> AdvancedTab(
                        report = report,
                        analysis = analysis,
                    )
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
            "${student.className.ifBlank { "--" }} 座號 ${student.seatNo.ifBlank { "--" }}",
            "學號 ${student.studentNo.ifBlank { state.studentNo.ifBlank { "--" } }}",
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
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
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
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
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
    onOpenSubjects: () -> Unit,
    onOpenCharts: () -> Unit,
    onOpenDistribution: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MainSummaryCard(report, analysis)
        InsightCard(
            analysis = analysis,
            isLoadingComparison = isLoadingComparison,
            comparisonError = comparisonError,
            isLoadingTrend = isLoadingTrend,
            trendError = trendError,
            trend = trend,
            insights = insights,
        )
        QuickActionPanel(
            onOpenSubjects = onOpenSubjects,
            onOpenCharts = onOpenCharts,
            onOpenDistribution = onOpenDistribution,
        )
        StrengthWeaknessCard(analysis)
    }
}

@Composable
private fun QuickActionPanel(
    onOpenSubjects: () -> Unit,
    onOpenCharts: () -> Unit,
    onOpenDistribution: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "快速查看",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RoundedCornerShape(14.dp),
                onClick = onOpenSubjects,
            ) {
                Text("查看科目分析")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RoundedCornerShape(14.dp),
                onClick = onOpenCharts,
            ) {
                Text("查看圖表分析")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RoundedCornerShape(14.dp),
                onClick = onOpenDistribution,
            ) {
                Text("查看排名分布")
            }
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "sectionChevron")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    modifier = Modifier.rotate(rotation),
                    text = "⌄",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun CompactSubjectList(analyses: List<SubjectAnalysis>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        analyses.forEach { analysis ->
            val subject = analysis.subject
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = shortenSubjectName(subject.subjectName),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = diffSentence(subject.diffValue),
                        style = MaterialTheme.typography.bodySmall,
                        color = diffColor(subject.diffValue),
                    )
                }
                Text(
                    text = subject.scoreDisplay.ifBlank { "%.1f".format(subject.scoreValue) },
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = scoreColor(subject.scoreValue),
                )
            }
        }
    }
}

@Composable
private fun ChartInsightPreview(analysis: GradeAnalysis, onOpenCharts: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = subjectSectionSummary(analysis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            onClick = onOpenCharts,
        ) {
            Text("前往圖表分析")
        }
    }
}

@Composable
private fun AdvancedDataPreview(onOpenDistribution: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "進階頁會保留完整雷達圖、長條圖、班級五標分析與班級分數分佈。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            onClick = onOpenDistribution,
        ) {
            Text("查看排名分布")
        }
    }
}

private fun subjectSectionSummary(analysis: GradeAnalysis): String {
    val strengths = analysis.strengths.take(2).joinToString("、") { shortenSubjectName(it.subjectName) }
    val weaknesses = analysis.weaknesses.take(2).joinToString("、") { shortenSubjectName(it.subjectName) }
    return when {
        strengths.isNotBlank() && weaknesses.isNotBlank() -> "優勢科目：$strengths；待加強：$weaknesses"
        strengths.isNotBlank() -> "優勢科目：$strengths"
        weaknesses.isNotBlank() -> "待加強：$weaknesses"
        else -> "各科與班平均差距不大"
    }
}

@Composable
private fun AdvancedTab(report: GradeReport, analysis: GradeAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "進階",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        AnalysisSection(report = report, analysis = analysis)
    }
}

@Composable
private fun CompactStudentCard(report: GradeReport) {
    val student = report.studentInfo
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = student.studentName.ifBlank { "--" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${student.className.ifBlank { "--" }}  座號 ${student.seatNo.ifBlank { "--" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "學號 ${student.studentNo.ifBlank { "--" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (student.updatedAt.isNotBlank()) {
                Text(
                    text = "更新時間 ${student.updatedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MainSummaryCard(report: GradeReport, analysis: GradeAnalysis) {
    val student = report.studentInfo
    val summary = report.examSummary
    val animatedAverage by animateFloatAsState(
        targetValue = analysis.weightedAverage.toFloat(),
        label = "weightedAverage",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = listOfNotNull(report.subjects.firstOrNull()?.yearTermDisplay, summary?.examName?.takeIf { it.isNotBlank() })
                    .joinToString(" ")
                    .ifBlank { "本次考試" },
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "%.1f".format(animatedAverage),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "加權平均",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelLarge,
            )
            RankSummaryLine(
                label = "班排",
                rank = formatRank(summary?.classRank, summary?.classCount, student.showClassRankCount),
                percentile = analysis.classPercentile?.percentLabel,
                visible = student.showClassRank,
            )
            RankSummaryLine(
                label = "類排",
                rank = formatRank(summary?.categoryRank, summary?.categoryRankCount, student.showCategoryRankCount),
                percentile = analysis.categoryPercentile?.percentLabel,
                visible = student.showCategoryRank,
            )
            Text(
                text = "科目數 ${report.subjects.size} ｜ 最高分 ${analysis.highestScore?.let { "%.0f".format(it) } ?: "--"}",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            )
        }
    }
}

@Composable
private fun RankSummaryLine(
    label: String,
    rank: String,
    percentile: String?,
    visible: Boolean,
) {
    Text(
        text = "$label ${if (visible) rank else "--"}（${percentile ?: "無百分位"}）",
        color = MaterialTheme.colorScheme.onPrimary,
        style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun RankPill(
    modifier: Modifier,
    label: String,
    rank: String,
    percentile: String?,
    visible: Boolean,
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.labelMedium)
        Text(
            if (visible) rank else "--",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
        )
        Text(percentile ?: "無百分位", color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SmallMetricPill(modifier: Modifier, label: String, value: String) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.bodySmall)
        Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
        )
    }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("重點解讀", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = analysis.summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            insights?.items
                ?.filterNot { it.title.startsWith("近 ") }
                ?.take(3)
                ?.forEach { insight ->
                    InsightRow(insight)
                }
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
private fun InsightRow(insight: ScoreInsight) {
    val color = when (insight.tone) {
        ScoreInsightTone.Positive -> PositiveColor
        ScoreInsightTone.Warning -> NegativeColor
        ScoreInsightTone.Neutral -> NeutralColor
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = insight.title,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = insight.body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun StrengthWeaknessCard(analysis: GradeAnalysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("強弱科摘要", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SubjectChipRow(title = "優勢科目", subjects = analysis.strengths, color = PositiveColor, emptyText = "尚無明顯高於平均的科目")
            SubjectChipRow(title = "待加強", subjects = analysis.weaknesses, color = NegativeColor, emptyText = "尚無明顯低於平均的科目")
        }
    }
}

@Composable
private fun SubjectChipRow(title: String, subjects: List<SubjectScore>, color: Color, emptyText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (subjects.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                subjects.forEach {
                    Box(
                        modifier = Modifier
                            .background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "${shortenSubjectName(it.subjectName)} ${signedValue(it.diffValue)}",
                            color = color,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectPreview(
    analyses: List<SubjectAnalysis>,
    expandedSubjectKeys: Set<String>,
    onToggleSubject: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("科目預覽", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
private fun SubjectCard(
    analysis: SubjectAnalysis,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val subject = analysis.subject
    val diffColor = diffColor(subject.diffValue)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = shortenSubjectName(subject.subjectName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = diffSentence(subject.diffValue),
                        color = diffColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "班排 ${formatRank(subject.classRank?.toDouble(), subject.classRankCount, true)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = subject.scoreDisplay.ifBlank { "%.2f".format(subject.scoreValue) },
                    color = scoreColor(subject.scoreValue),
                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                SubjectDetail(analysis)
            }
        }
    }
}

@Composable
private fun SubjectDetail(analysis: SubjectAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoRow("五標落點", analysis.standardDistance ?: "尚無五標資料")
        InfoRow("分佈摘要", analysis.distributionSummary ?: "尚無分佈資料")
        analysis.comparison?.let {
            InfoRow(
                label = "上一考比較",
                value = deltaText("分數", it.scoreDelta, "分"),
                valueColor = diffColor(it.scoreDelta),
            )
        } ?: InfoRow("上一考比較", "尚無上一考可比較")
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InlineStatus(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
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

private fun signedValue(value: Double): String = "${if (value >= 0.0) "+" else ""}${"%.1f".format(value)}"

private fun diffSentence(diff: Double): String = when {
    diff > 0.05 -> "高於平均 ${signedValue(diff)}"
    diff < -0.05 -> "低於平均 ${signedValue(diff)}"
    else -> "接近班級平均"
}

private fun diffColor(diff: Double): Color = when {
    diff > 0.05 -> PositiveColor
    diff < -0.05 -> NegativeColor
    else -> NeutralColor
}

private fun scoreColor(score: Double): Color = when {
    score >= 80.0 -> PositiveColor
    score >= 60.0 -> Color(0xFFF59E0B)
    else -> NegativeColor
}
