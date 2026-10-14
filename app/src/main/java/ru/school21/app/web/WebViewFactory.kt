package ru.school21.app.web

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import ru.school21.app.AppConfig
import ru.school21.app.BuildConfig

/** Создание и настройка WebView. Здесь собраны все security-настройки. */
object WebViewFactory {

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        webViewClient: WebViewClient,
        webChromeClient: WebChromeClient,
        downloadListener: DownloadListener,
    ): WebView {
        // chrome://inspect в debug-сборках.
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        return WebView(context).apply webView@{
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // Сайту не нужен доступ к файлам устройства. allowFileAccessFromFileURLs и
                // allowUniversalAccessFromFileURLs по умолчанию false и не трогаются.
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                safeBrowsingEnabled = true
                // target=_blank / window.open грузятся в этом же WebView через shouldOverrideUrlLoading.
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                userAgentString = userAgentString + AppConfig.USER_AGENT_SUFFIX
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                // platform. и auth.21-school.ru — один сайт (same-site), сторонние куки не нужны.
                setAcceptThirdPartyCookies(this@webView, false)
            }

            this.webViewClient = webViewClient
            this.webChromeClient = webChromeClient
            setDownloadListener(downloadListener)
        }
    }
}
