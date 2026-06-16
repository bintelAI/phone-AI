package com.ai.phoneagent.net

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class CasdoorOAuthActivity : Activity() {
    data class OAuthResult(
        val success: Boolean,
        val code: String = "",
        val state: String = "",
        val message: String = "",
    )

    data class PendingSession(
        val redirectUri: String,
        val completion: (OAuthResult) -> Unit,
    )

    companion object {
        const val EXTRA_AUTH_URL = "extra_auth_url"
        var pendingSession: PendingSession? = null
    }

    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = pendingSession
        val authUrl = intent.getStringExtra(EXTRA_AUTH_URL).orEmpty()
        if (session == null || authUrl.isBlank()) {
            finishWith(OAuthResult(success = false, message = "Casdoor 登录会话已失效"))
            return
        }

        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    handleUrl(request.url, session)

                @Suppress("OVERRIDE_DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    handleUrl(Uri.parse(url), session)
            }
        setContentView(webView)
        webView.loadUrl(authUrl)
    }

    override fun onDestroy() {
        if (!completed) {
            finishWith(OAuthResult(success = false, message = "Casdoor 登录已取消"))
        }
        super.onDestroy()
    }

    private fun handleUrl(uri: Uri, session: PendingSession): Boolean {
        if (!isRedirectUri(uri, session.redirectUri)) return false

        val params = callbackParams(uri)
        val error = params["error"].orEmpty()
        val errorDescription = params["error_description"].orEmpty()
        if (error.isNotBlank()) {
            finishWith(
                OAuthResult(
                    success = false,
                    message = errorDescription.ifBlank { error },
                ),
            )
            return true
        }

        val code = params["code"].orEmpty()
        if (code.isBlank()) {
            finishWith(OAuthResult(success = false, message = "Casdoor 回调缺少 code"))
            return true
        }

        finishWith(
            OAuthResult(
                success = true,
                code = code,
                state = params["state"].orEmpty(),
            ),
        )
        return true
    }

    private fun isRedirectUri(uri: Uri, redirectUri: String): Boolean {
        val redirect = Uri.parse(redirectUri)
        if (uri.scheme == redirect.scheme && uri.host == redirect.host && callbackParams(uri).containsKey("code")) {
            return true
        }
        return uri.scheme == redirect.scheme &&
            uri.host == redirect.host &&
            uri.path.orEmpty() == redirect.path.orEmpty()
    }

    private fun callbackParams(uri: Uri): Map<String, String> {
        val params = linkedMapOf<String, String>()
        uri.queryParameterNames.forEach { name ->
            params[name] = uri.getQueryParameter(name).orEmpty()
        }

        val fragment = uri.fragment.orEmpty()
        val fragmentQuery = fragment.substringAfter('?', missingDelimiterValue = "")
        if (fragmentQuery.isNotBlank()) {
            Uri.parse("phone-ai://callback?$fragmentQuery").queryParameterNames.forEach { name ->
                if (name !in params) {
                    params[name] = Uri.parse("phone-ai://callback?$fragmentQuery").getQueryParameter(name).orEmpty()
                }
            }
        }
        return params
    }

    private fun finishWith(result: OAuthResult) {
        if (completed) return
        completed = true
        val completion = pendingSession?.completion
        pendingSession = null
        completion?.invoke(result)
        finish()
    }
}
