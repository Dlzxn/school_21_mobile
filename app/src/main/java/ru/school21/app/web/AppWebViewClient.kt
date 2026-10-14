package ru.school21.app.web

import android.graphics.Bitmap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import ru.school21.app.AppConfig

/**
 * Навигация, ошибки загрузки и история.
 * onReceivedSslError намеренно не переопределён: по умолчанию WebView отменяет загрузку.
 */
class AppWebViewClient(
    private val state: WebUiState,
    private val router: UrlRouter,
    private val onRenderProcessGone: () -> Unit,
) : WebViewClient() {

    /** Была навигация через страницу входа — после возврата на сайт чистим историю. */
    private var passedThroughAuth = false

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        // Навигации внутри iframe не перехватываем, иначе встроенный контент уедет в браузер.
        if (!request.isForMainFrame) return false
        // Вызывается и для серверных редиректов, поэтому заход на auth ловим здесь.
        if (AppConfig.isAuthHost(request.url.host)) passedThroughAuth = true
        return router.route(view, request.url)
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        state.failedUrl = null
    }

    override fun onPageFinished(view: WebView, url: String?) {
        state.isRefreshing = false
        state.firstPageFinished = true

        val host = url?.toUri()?.host
        if (AppConfig.isAuthHost(host)) {
            passedThroughAuth = true
        } else if (passedThroughAuth) {
            // Иначе «Назад» вернёт на Keycloak, который сразу редиректит обратно, — петля.
            passedThroughAuth = false
            view.clearHistory()
        }
        state.canGoBack = view.canGoBack()
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        // Срабатывает и на history.pushState в SPA, в отличие от onPageFinished.
        state.canGoBack = view.canGoBack()
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        // Ошибки картинок/скриптов игнорируем — экран «Нет интернета» только для самой страницы.
        if (request.isForMainFrame) {
            state.failedUrl = request.url.toString()
            state.isRefreshing = false
            state.firstPageFinished = true
        }
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        // Процесс рендера упал или убит системой. Без true приложение упадёт вместе с ним.
        onRenderProcessGone()
        return true
    }
}
