package ru.school21.app.web

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Прогресс загрузки и выбор файлов для input type=file.
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
}
