package com.clhs.score.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val LOGIN_URL = "https://shcloud2.k12ea.gov.tw/CLHSTYC/Auth/Auth/CloudLogin"
private const val SCHOOL_DOMAIN = "shcloud2.k12ea.gov.tw"

@Composable
fun WebViewLoginScreen(
    isProcessingLogin: Boolean,
    errorMessage: String?,
    onLoginSuccess: (studentNo: String, cookieString: String) -> Unit,
    onBack: () -> Unit,
    onDismissError: () -> Unit,
) {
    var isPageLoading by remember { mutableStateOf(true) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        WebViewContent(
            onWebViewCreated = { webViewRef = it },
            onPageStarted = { isPageLoading = true },
            onPageFinished = { isPageLoading = false },
            onProgressChanged = { pageProgress = it / 100f },
            onLoginSuccess = onLoginSuccess,
        )

        AnimatedVisibility(
            visible = isPageLoading || isProcessingLogin,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            LinearProgressIndicator(
                progress = { if (isProcessingLogin) 1f else pageProgress },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Floating Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
            )
        }

        // Floating Refresh Button
        IconButton(
            onClick = { webViewRef?.reload() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "重新載入",
            )
        }

        AnimatedVisibility(
            visible = isProcessingLogin,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.38f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(20.dp),
                        )
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Text(
                        text = "正在建立連線…",
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        errorMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewContent(
    onWebViewCreated: (WebView) -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onProgressChanged: (Int) -> Unit,
    onLoginSuccess: (studentNo: String, cookieString: String) -> Unit,
) {
    var loginHandled by remember { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            android.webkit.WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    textZoom = 100
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    saveFormData = true
                }

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                val jsInterface = LoginJsInterface { studentNo ->
                    if (loginHandled) return@LoginJsInterface
                    loginHandled = true
                    val cookieString = CookieManager.getInstance()
                        .getCookie("https://$SCHOOL_DOMAIN") ?: ""
                    post { onLoginSuccess(studentNo, cookieString) }
                }
                addJavascriptInterface(jsInterface, "AndroidLogin")

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onPageStarted()
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onPageFinished()
                        if (url?.contains("Auth/Auth/CloudLogin") == true) {
                            loginHandled = false
                            view?.evaluateJavascript(LOGIN_HOOK_JS, null)
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgressChanged(newProgress)
                    }
                }

                onWebViewCreated(this)
                loadUrl(LOGIN_URL)
            }
        },
    )
}

private class LoginJsInterface(
    private val onSuccess: (studentNo: String) -> Unit,
) {
    @JavascriptInterface
    fun onLoginSuccess(loginId: String) {
        onSuccess(loginId)
    }
}

private val LOGIN_HOOK_JS = """
(function() {
    if (window.__loginHooked) return;
    window.__loginHooked = true;

    var origOpen = XMLHttpRequest.prototype.open;
    var origSend = XMLHttpRequest.prototype.send;

    XMLHttpRequest.prototype.open = function(method, url) {
        this.__url = url;
        this.__method = method;
        return origOpen.apply(this, arguments);
    };

    XMLHttpRequest.prototype.send = function(body) {
        var self = this;
        if (self.__url && self.__url.indexOf('DoCloudLoginCheck') >= 0) {
            var loginId = '';
            try {
                var loginField = document.querySelector('input[name="LoginId"]');
                if (loginField) loginId = loginField.value || '';
            } catch(e) {}

            if (!loginId && body) {
                try {
                    var params = new URLSearchParams(body);
                    loginId = params.get('LoginId') || '';
                } catch(e) {}
            }

            self.addEventListener('load', function() {
                try {
                    var resp = JSON.parse(self.responseText);
                    if (resp && resp.Result && resp.Result.IsLoginSuccess === true) {
                        if (window.AndroidLogin) {
                            window.AndroidLogin.onLoginSuccess(loginId);
                        }
                    }
                } catch(e) {}
            });
        }
        return origSend.apply(this, arguments);
    };
})();
""".trimIndent()
