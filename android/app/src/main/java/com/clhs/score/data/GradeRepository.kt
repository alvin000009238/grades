package com.clhs.score.data

class GradeRepository(
    private val client: SchoolGradeClient,
    private val sessionStore: SessionStore,
) {
    fun restoreSession(): AuthenticatedSession? {
        val session = sessionStore.loadSession() ?: return null
        client.restoreSession(session)
        return session
    }

    suspend fun refreshCaptcha(): CaptchaChallenge = client.prepareLoginCaptcha()

    suspend fun login(
        username: String,
        password: String,
        captchaCode: String,
        challenge: CaptchaChallenge,
    ): AuthenticatedSession {
        val session = client.login(username, password, captchaCode, challenge)
        sessionStore.saveSession(session)
        return session
    }

    suspend fun loadStructure(session: AuthenticatedSession): List<YearTermOption> =
        client.loadStructure(session)

    suspend fun fetchGrades(
        session: AuthenticatedSession,
        yearValue: String,
        examValue: String,
    ): GradeReport = client.fetchGrades(session, yearValue, examValue)

    fun logout() {
        sessionStore.clear()
        client.clearSession()
    }
}
