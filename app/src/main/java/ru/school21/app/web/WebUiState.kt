package ru.school21.app.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Состояние WebView, которое наблюдает Compose UI. Пишется из WebViewClient / WebChromeClient. */
class WebUiState {
    /** Прогресс загрузки 0..100. */
    var progress by mutableIntStateOf(0)

    var canGoBack by mutableStateOf(false)

    /** Идёт обновление, запущенное pull-to-refresh. */
    var isRefreshing by mutableStateOf(false)

    /** URL, который не удалось загрузить; не null — показываем экран «Нет интернета». */
    var failedUrl by mutableStateOf<String?>(null)

    /** Первая страница загрузилась (или упала с ошибкой) — можно убирать splash. */
    var firstPageFinished by mutableStateOf(false)
}
