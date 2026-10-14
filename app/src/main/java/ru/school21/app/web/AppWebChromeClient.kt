package ru.school21.app.web

import android.net.Uri
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Прогресс загрузки, выбор файлов для input type=file и полноэкранное видео.
 * Запросы разрешений сайта (камера, геолокация) не переопределены — WebView их отклоняет.
 */
class AppWebChromeClient(
    private val state: WebUiState,
    private val fileChooser: FileChooser,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        state.progress = newProgress
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = fileChooser.show(filePathCallback, fileChooserParams)

    /** Кнопка «на весь экран» у видео: WebView отдаёт отдельный View с плеером. */
    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (state.fullscreenVideo != null) {
            callback.onCustomViewHidden()
            return
        }
        state.fullscreenVideo = FullscreenVideo(view, callback)
    }

    override fun onHideCustomView() {
        state.fullscreenVideo = null
    }
}
