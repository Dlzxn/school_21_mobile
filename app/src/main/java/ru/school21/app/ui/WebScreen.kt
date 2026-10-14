package ru.school21.app.ui

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import ru.school21.app.AppConfig
import ru.school21.app.web.WebUiState

/**
 * Edge-to-edge: окно рисуется под системными панелями, корневой Box закрашивает их зону
 * цветом system_bar, а контент получает отступы safeDrawing (панели, вырез, клавиатура).
 */
@Composable
fun WebScreen(
    webView: WebView,
    state: WebUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
) {
    val indicatorColor = MaterialTheme.colorScheme.primary.toArgb()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SwipeRefreshLayout(context).apply {
                    // WebView живёт дольше композиции (им владеет Activity) — отцепляем от старого родителя.
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    addView(webView)
                    isEnabled = AppConfig.PULL_TO_REFRESH_ENABLED
                    setColorSchemeColors(indicatorColor)
                    // Жест «потянуть» доступен, только когда страница прокручена к самому верху.
                    setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
                    setOnRefreshListener(onRefresh)
                    // Insets уже учтены отступами Compose; не даём WebView применить их второй раз.
                    ViewCompat.setOnApplyWindowInsetsListener(this) { _, _ -> WindowInsetsCompat.CONSUMED }
                }
            },
            update = { it.isRefreshing = state.isRefreshing },
        )

        if (state.progress < 100 && state.failedUrl == null) {
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }

        if (state.failedUrl != null) {
            OfflineScreen(onRetry = onRetry)
        }
    }
}
