package com.example.gamechat.ui

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.gamechat.R
import com.example.gamechat.data.EncounterUserAgentProvider
import com.example.gamechat.data.UserPreferences

class EngineNativeFragment : Fragment(R.layout.fragment_engine_native) {
    private var webView: WebView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val progress = view.findViewById<ProgressBar>(R.id.engineNativeProgress)
        val errorText = view.findViewById<TextView>(R.id.engineNativeError)
        val nativeWebView = view.findViewById<WebView>(R.id.engineNativeWebView)
        webView = nativeWebView

        val session = UserPreferences.getEncounterSession(requireContext())
        val gameId = UserPreferences.getEncounterGameId(requireContext()).trim()

        if (session.site.isBlank() || session.login.isBlank()) {
            showError(errorText, getString(R.string.engine_need_auth))
            return
        }
        if (gameId.isBlank()) {
            showError(errorText, getString(R.string.engine_need_game_id))
            return
        }

        configureWebView(nativeWebView, progress, errorText)

        val siteBaseUrl = session.site.trimEnd('/')
        val engineUrl = "$siteBaseUrl/GameEngines/Encounter/Play/$gameId"
        applyEncounterCookiesAndLoad(
            webView = nativeWebView,
            siteBaseUrl = siteBaseUrl,
            engineUrl = engineUrl,
            guid = session.guid,
            stoken = session.stoken,
            atoken = session.atoken
        )
    }

    override fun onDestroyView() {
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroyView()
    }

    private fun configureWebView(webView: WebView, progress: ProgressBar, errorText: TextView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = EncounterUserAgentProvider.get(requireContext())
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
                errorText.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    showError(
                        errorText,
                        getString(R.string.engine_native_load_error, error?.description ?: "")
                    )
                    progress.visibility = View.GONE
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                if (request?.isForMainFrame == true) {
                    showError(
                        errorText,
                        getString(R.string.engine_native_load_error_http, errorResponse?.statusCode ?: 0)
                    )
                    progress.visibility = View.GONE
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError?) {
                handler.cancel()
                showError(errorText, getString(R.string.engine_native_ssl_error))
                progress.visibility = View.GONE
            }
        }
    }

    private fun applyEncounterCookiesAndLoad(
        webView: WebView,
        siteBaseUrl: String,
        engineUrl: String,
        guid: String?,
        stoken: String?,
        atoken: String?
    ) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        val entries = buildList<Pair<String, String>> {
            addCookie(this, siteBaseUrl, "GUID", guid)
            addCookie(this, siteBaseUrl, "stoken", stoken)
            addCookie(this, siteBaseUrl, "atoken", atoken)
            addCookie(this, engineUrl, "GUID", guid)
            addCookie(this, engineUrl, "stoken", stoken)
            addCookie(this, engineUrl, "atoken", atoken)
        }

        if (entries.isEmpty()) {
            webView.loadUrl(engineUrl)
            return
        }

        fun applyAt(index: Int) {
            if (index >= entries.size) {
                cookieManager.flush()
                webView.loadUrl(engineUrl)
                return
            }
            val (url, cookie) = entries[index]
            cookieManager.setCookie(url, cookie) {
                applyAt(index + 1)
            }
        }
        applyAt(0)
    }

    private fun addCookie(target: MutableList<Pair<String, String>>, url: String, name: String, value: String?) {
        val safe = value?.trim().orEmpty()
        if (safe.isBlank()) return
        target.add(url to "$name=$safe; Path=/")
    }

    private fun showError(errorText: TextView, message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }
}
