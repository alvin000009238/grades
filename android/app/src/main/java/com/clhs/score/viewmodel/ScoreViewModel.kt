package com.clhs.score.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.clhs.score.data.AuthenticatedSession
import com.clhs.score.data.CaptchaChallenge
import com.clhs.score.data.GradeAnalysis
import com.clhs.score.data.GradeReport
import com.clhs.score.data.GradeRepository
import com.clhs.score.data.GradeTrend
import com.clhs.score.data.LocalScoreInsightProvider
import com.clhs.score.data.SchoolException
import com.clhs.score.data.SchoolGradeClient
import com.clhs.score.data.ScoreInsightProvider
import com.clhs.score.data.ScoreInsightSet
import com.clhs.score.data.SessionStore
import com.clhs.score.data.YearTermOption
import com.clhs.score.data.buildGradeAnalysis
import com.clhs.score.data.buildGradeTrend
import com.clhs.score.data.cleanSubjectName
import com.clhs.score.data.previousExamOf
import com.clhs.score.data.previousExamsOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val captchaCode: String = "",
    val challenge: CaptchaChallenge? = null,
    val isCaptchaLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

data class GradesUiState(
    val isLoggedIn: Boolean = false,
    val studentNo: String = "",
    val isLoadingStructure: Boolean = false,
    val isLoadingGrades: Boolean = false,
    val isLoadingComparison: Boolean = false,
    val isLoadingTrend: Boolean = false,
    val structure: List<YearTermOption> = emptyList(),
    val selectedYearValue: String? = null,
    val selectedExamValue: String? = null,
    val report: GradeReport? = null,
    val comparisonReport: GradeReport? = null,
    val comparisonExamName: String? = null,
    val comparisonError: String? = null,
    val trendReports: List<GradeReport> = emptyList(),
    val trendError: String? = null,
    val trend: GradeTrend? = null,
    val insights: ScoreInsightSet? = null,
    val analysis: GradeAnalysis? = null,
    val expandedSubjectKeys: Set<String> = emptySet(),
    val errorMessage: String? = null,
)

class ScoreViewModel(
    private val repository: GradeRepository,
    private val insightProvider: ScoreInsightProvider = LocalScoreInsightProvider(),
) : ViewModel() {
    private var session: AuthenticatedSession? = null
    private var gradeRequestId = 0

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState

    private val _gradesState = MutableStateFlow(GradesUiState())
    val gradesState: StateFlow<GradesUiState> = _gradesState

    init {
        restoreSessionOrCaptcha()
    }

    fun updateUsername(value: String) {
        _loginState.update { it.copy(username = value, errorMessage = null) }
    }

    fun updatePassword(value: String) {
        _loginState.update { it.copy(password = value, errorMessage = null) }
    }

    fun updateCaptchaCode(value: String) {
        _loginState.update { it.copy(captchaCode = value, errorMessage = null) }
    }

    fun refreshCaptcha() {
        viewModelScope.launch {
            _loginState.update { it.copy(isCaptchaLoading = true, errorMessage = null, captchaCode = "") }
            runCatching { repository.refreshCaptcha() }
                .onSuccess { challenge ->
                    _loginState.update {
                        it.copy(
                            challenge = challenge,
                            isCaptchaLoading = false,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    _loginState.update {
                        it.copy(
                            isCaptchaLoading = false,
                            errorMessage = error.message ?: "取得驗證碼失敗",
                        )
                    }
                }
        }
    }

    fun login() {
        val current = _loginState.value
        val challenge = current.challenge ?: run {
            _loginState.update { it.copy(errorMessage = "請先取得驗證碼") }
            refreshCaptcha()
            return
        }
        viewModelScope.launch {
            _loginState.update { it.copy(isSubmitting = true, errorMessage = null) }
            runCatching {
                repository.login(
                    username = current.username,
                    password = current.password,
                    captchaCode = current.captchaCode,
                    challenge = challenge,
                )
            }.onSuccess { authenticatedSession ->
                session = authenticatedSession
                _loginState.update {
                    it.copy(
                        password = "",
                        captchaCode = "",
                        isSubmitting = false,
                        errorMessage = null,
                    )
                }
                _gradesState.update {
                    it.copy(
                        isLoggedIn = true,
                        studentNo = authenticatedSession.studentNo,
                        errorMessage = null,
                    )
                }
                loadStructure()
            }.onFailure { error ->
                _loginState.update {
                    it.copy(
                        captchaCode = "",
                        isSubmitting = false,
                        errorMessage = error.message ?: "登入失敗",
                    )
                }
                if ((error as? SchoolException)?.refreshCaptcha == true) {
                    refreshCaptcha()
                }
            }
        }
    }

    fun selectYear(value: String) {
        val year = _gradesState.value.structure.firstOrNull { it.value == value } ?: return
        val firstExam = year.exams.firstOrNull()
        _gradesState.update {
            it.copy(
                selectedYearValue = value,
                selectedExamValue = firstExam?.value,
                comparisonReport = null,
                comparisonExamName = null,
                comparisonError = null,
                isLoadingTrend = false,
                trendReports = emptyList(),
                trendError = null,
                trend = null,
                insights = null,
                expandedSubjectKeys = emptySet(),
                errorMessage = null,
            )
        }
        if (firstExam != null) {
            fetchGrades(value, firstExam.value)
        }
    }

    fun selectExam(value: String) {
        val yearValue = _gradesState.value.selectedYearValue ?: return
        _gradesState.update {
            it.copy(
                selectedExamValue = value,
                comparisonReport = null,
                comparisonExamName = null,
                comparisonError = null,
                isLoadingTrend = false,
                trendReports = emptyList(),
                trendError = null,
                trend = null,
                insights = null,
                expandedSubjectKeys = emptySet(),
                errorMessage = null,
            )
        }
        fetchGrades(yearValue, value)
    }

    fun toggleSubjectExpanded(subjectName: String) {
        val key = cleanSubjectName(subjectName)
        _gradesState.update { state ->
            val next = if (key in state.expandedSubjectKeys) {
                state.expandedSubjectKeys - key
            } else {
                state.expandedSubjectKeys + key
            }
            state.copy(expandedSubjectKeys = next)
        }
    }

    fun reloadStructure() {
        loadStructure()
    }

    fun logout() {
        gradeRequestId++
        repository.logout()
        session = null
        _gradesState.value = GradesUiState()
        _loginState.update {
            LoginUiState(username = it.username)
        }
        refreshCaptcha()
    }

    fun clearLoginError() {
        _loginState.update { it.copy(errorMessage = null) }
    }

    fun clearGradesError() {
        _gradesState.update { it.copy(errorMessage = null) }
    }

    private fun restoreSessionOrCaptcha() {
        val restored = repository.restoreSession()
        if (restored == null) {
            refreshCaptcha()
            return
        }
        session = restored
        _gradesState.update {
            it.copy(isLoggedIn = true, studentNo = restored.studentNo)
        }
        loadStructure()
    }

    private fun loadStructure() {
        val currentSession = session ?: return
        viewModelScope.launch {
            _gradesState.update { it.copy(isLoadingStructure = true, errorMessage = null) }
            runCatching { repository.loadStructure(currentSession) }
                .onSuccess { structure ->
                    val selectedYear = structure.firstOrNull()
                    val selectedExam = selectedYear?.exams?.firstOrNull()
                    _gradesState.update {
                        it.copy(
                            isLoadingStructure = false,
                            structure = structure,
                            selectedYearValue = selectedYear?.value,
                            selectedExamValue = selectedExam?.value,
                            errorMessage = null,
                        )
                    }
                    if (selectedYear != null && selectedExam != null) {
                        fetchGrades(selectedYear.value, selectedExam.value)
                    }
                }
                .onFailure { error ->
                    _gradesState.update {
                        it.copy(
                            isLoadingStructure = false,
                            errorMessage = error.message ?: "載入可查詢考試失敗",
                        )
                    }
                }
        }
    }

    private fun fetchGrades(yearValue: String, examValue: String) {
        val currentSession = session ?: return
        val requestId = ++gradeRequestId
        viewModelScope.launch {
            _gradesState.update {
                it.copy(
                    isLoadingGrades = true,
                    isLoadingComparison = false,
                    isLoadingTrend = false,
                    comparisonReport = null,
                    comparisonExamName = null,
                    comparisonError = null,
                    trendReports = emptyList(),
                    trendError = null,
                    trend = null,
                    insights = null,
                    errorMessage = null,
                )
            }
            runCatching { repository.fetchGrades(currentSession, yearValue, examValue) }
                .onSuccess { report ->
                    if (requestId != gradeRequestId) return@onSuccess
                    val analysis = buildGradeAnalysis(report)
                    _gradesState.update {
                        it.copy(
                            isLoadingGrades = false,
                            report = report,
                            comparisonReport = null,
                            comparisonExamName = null,
                            comparisonError = null,
                            trendReports = emptyList(),
                            trendError = null,
                            trend = null,
                            analysis = analysis,
                            insights = insightProvider.buildInsights(report, analysis),
                            errorMessage = null,
                        )
                    }
                    loadPreviousExamComparison(
                        requestId = requestId,
                        session = currentSession,
                        yearValue = yearValue,
                        examValue = examValue,
                        report = report,
                    )
                    loadGradeTrend(
                        requestId = requestId,
                        session = currentSession,
                        yearValue = yearValue,
                        examValue = examValue,
                        report = report,
                    )
                }
                .onFailure { error ->
                    if (requestId != gradeRequestId) return@onFailure
                    _gradesState.update {
                        it.copy(
                            isLoadingGrades = false,
                            isLoadingComparison = false,
                            isLoadingTrend = false,
                            errorMessage = error.message ?: "查詢成績失敗",
                        )
                    }
                }
        }
    }

    private fun loadPreviousExamComparison(
        requestId: Int,
        session: AuthenticatedSession,
        yearValue: String,
        examValue: String,
        report: GradeReport,
    ) {
        val year = _gradesState.value.structure.firstOrNull { it.value == yearValue }
        val previousExam = year?.previousExamOf(examValue)
        if (previousExam == null) {
            _gradesState.update {
                val analysis = buildGradeAnalysis(report)
                if (requestId != gradeRequestId) it else it.copy(
                    isLoadingComparison = false,
                    comparisonError = "尚無上一考可比較",
                    analysis = analysis,
                    insights = insightProvider.buildInsights(report, analysis, it.trend),
                )
            }
            return
        }
        viewModelScope.launch {
            _gradesState.update {
                if (requestId != gradeRequestId) it else it.copy(
                    isLoadingComparison = true,
                    comparisonError = null,
                )
            }
            runCatching {
                repository.fetchGrades(session, yearValue, previousExam.value)
            }.onSuccess { comparison ->
                if (requestId != gradeRequestId) return@onSuccess
                _gradesState.update {
                    val analysis = buildGradeAnalysis(
                        report = report,
                        comparisonReport = comparison,
                        previousExamName = previousExam.text,
                    )
                    it.copy(
                        isLoadingComparison = false,
                        comparisonReport = comparison,
                        comparisonExamName = previousExam.text,
                        comparisonError = null,
                        analysis = analysis,
                        insights = insightProvider.buildInsights(report, analysis, it.trend),
                    )
                }
            }.onFailure { error ->
                if (requestId != gradeRequestId) return@onFailure
                _gradesState.update {
                    val analysis = buildGradeAnalysis(report)
                    it.copy(
                        isLoadingComparison = false,
                        comparisonError = error.message ?: "上一考比較載入失敗",
                        analysis = analysis,
                        insights = insightProvider.buildInsights(report, analysis, it.trend),
                    )
                }
            }
        }
    }

    private fun loadGradeTrend(
        requestId: Int,
        session: AuthenticatedSession,
        yearValue: String,
        examValue: String,
        report: GradeReport,
    ) {
        val year = _gradesState.value.structure.firstOrNull { it.value == yearValue }
        val currentExamName = year?.exams
            ?.firstOrNull { it.value == examValue }
            ?.text
            ?: report.examSummary?.examName.orEmpty().ifBlank { "本次考試" }
        val previousExams = year?.previousExamsOf(examValue, limit = 2).orEmpty()
        if (previousExams.isEmpty()) {
            _gradesState.update {
                val analysis = it.analysis ?: buildGradeAnalysis(report)
                if (requestId != gradeRequestId) it else it.copy(
                    isLoadingTrend = false,
                    trendReports = emptyList(),
                    trend = null,
                    trendError = "尚無歷次趨勢可比較",
                    insights = insightProvider.buildInsights(report, analysis, null),
                )
            }
            return
        }

        viewModelScope.launch {
            _gradesState.update {
                if (requestId != gradeRequestId) it else it.copy(
                    isLoadingTrend = true,
                    trendError = null,
                )
            }
            runCatching {
                previousExams.map { exam ->
                    exam.text to repository.fetchGrades(session, yearValue, exam.value)
                }
            }.onSuccess { previousReports ->
                if (requestId != gradeRequestId) return@onSuccess
                val trend = buildGradeTrend(
                    currentExamName = currentExamName,
                    currentReport = report,
                    previousReports = previousReports,
                )
                _gradesState.update {
                    val analysis = it.analysis ?: buildGradeAnalysis(report)
                    it.copy(
                        isLoadingTrend = false,
                        trendReports = previousReports.map { pair -> pair.second },
                        trendError = null,
                        trend = trend,
                        insights = insightProvider.buildInsights(report, analysis, trend),
                    )
                }
            }.onFailure { error ->
                if (requestId != gradeRequestId) return@onFailure
                _gradesState.update {
                    val analysis = it.analysis ?: buildGradeAnalysis(report)
                    it.copy(
                        isLoadingTrend = false,
                        trendReports = emptyList(),
                        trend = null,
                        trendError = error.message ?: "歷次趨勢載入失敗",
                        insights = insightProvider.buildInsights(report, analysis, null),
                    )
                }
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val cookieJar = com.clhs.score.data.SchoolCookieJar()
                val client = SchoolGradeClient(cookieJar = cookieJar)
                val repository = GradeRepository(client, SessionStore(context))
                return ScoreViewModel(repository) as T
            }
        }
    }
}
