package com.clhs.score.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class SessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveSession(session: AuthenticatedSession) {
        val cookiesJson = buildJsonObject {
            session.cookies.forEach { (name, value) -> put(name, value) }
        }.toString()
        prefs.edit()
            .putString(KEY_STUDENT_NO, session.studentNo)
            .putString(KEY_API_TOKEN, session.apiToken)
            .putString(KEY_COOKIES, cookiesJson)
            .apply()
    }

    fun loadSession(): AuthenticatedSession? {
        val studentNo = prefs.getString(KEY_STUDENT_NO, null)?.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.getString(KEY_API_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val rawCookies = prefs.getString(KEY_COOKIES, "{}").orEmpty()
        val cookies = runCatching {
            SchoolJson.parseToJsonElement(rawCookies).jsonObject.toStringMap()
        }.getOrElse { emptyMap() }
        if (cookies.isEmpty()) return null
        return AuthenticatedSession(studentNo = studentNo, apiToken = token, cookies = cookies)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun JsonObject.toStringMap(): Map<String, String> = entries.associate { (key, value) ->
        key to value.asPrimitiveOrNull()?.contentOrNull.orEmpty()
    }.filterValues { it.isNotBlank() }

    private companion object {
        const val PREFS_NAME = "score_session"
        const val KEY_STUDENT_NO = "student_no"
        const val KEY_API_TOKEN = "api_token"
        const val KEY_COOKIES = "cookies"
    }
}
