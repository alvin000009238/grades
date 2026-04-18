package com.clhs.score.data

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.CopyOnWriteArrayList

class SchoolCookieJar : CookieJar {
    private val cookies = CopyOnWriteArrayList<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { newCookie ->
            this.cookies.removeAll { it.name == newCookie.name && it.domain == newCookie.domain && it.path == newCookie.path }
            if (newCookie.expiresAt > System.currentTimeMillis()) {
                this.cookies.add(newCookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        return cookies.filter { it.matches(url) }
    }

    fun snapshot(): Map<String, String> = cookies
        .filter { it.expiresAt > System.currentTimeMillis() }
        .associate { it.name to it.value }

    fun replace(cookieValues: Map<String, String>, domain: String = "shcloud2.k12ea.gov.tw") {
        clear()
        cookieValues.forEach { (name, value) ->
            if (name.isNotBlank()) {
                cookies.add(
                    Cookie.Builder()
                        .hostOnlyDomain(domain)
                        .path("/")
                        .name(name)
                        .value(value)
                        .build(),
                )
            }
        }
    }

    fun clear() {
        cookies.clear()
    }
}
