package com.clhs.score

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clhs.score.ui.ScoreApp
import com.clhs.score.ui.theme.ScoreTheme
import com.clhs.score.viewmodel.ScoreViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScoreTheme {
                val viewModel: ScoreViewModel = viewModel(factory = ScoreViewModel.factory(applicationContext))
                val loginState by viewModel.loginState.collectAsState()
                val gradesState by viewModel.gradesState.collectAsState()
                ScoreApp(
                    loginState = loginState,
                    gradesState = gradesState,
                    onUsernameChange = viewModel::updateUsername,
                    onPasswordChange = viewModel::updatePassword,
                    onCaptchaChange = viewModel::updateCaptchaCode,
                    onRefreshCaptcha = viewModel::refreshCaptcha,
                    onLogin = viewModel::login,
                    onSelectYear = viewModel::selectYear,
                    onSelectExam = viewModel::selectExam,
                    onReload = viewModel::reloadStructure,
                    onLogout = viewModel::logout,
                    onToggleSubject = viewModel::toggleSubjectExpanded,
                    onDismissLoginError = viewModel::clearLoginError,
                    onDismissGradesError = viewModel::clearGradesError,
                )
            }
        }
    }
}
