package ru.school21.app

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ru.school21.app.auth.AutoLogin
import ru.school21.app.auth.CredentialStore
import ru.school21.app.ui.AppTheme
import ru.school21.app.ui.AuthOverlays
import ru.school21.app.ui.AutoLoginSettingsScreen
import ru.school21.app.ui.WebScreen
import ru.school21.app.web.AppWebChromeClient
import ru.school21.app.web.AppWebViewClient
import ru.school21.app.web.DownloadHandler
import ru.school21.app.web.FileChooser
import ru.school21.app.web.UrlRouter
import ru.school21.app.web.WebUiState
import ru.school21.app.web.WebViewFactory

/**
 * Хост для WebView: жизненный цикл, сохранение состояния, deep links.
 * Поворот экрана Activity не пересоздаёт (configChanges в манифесте), поэтому
 * saveState/restoreState нужны только для смены темы и восстановления после смерти процесса.
 */
class MainActivity : ComponentActivity() {

    private val uiState = WebUiState()
    private lateinit var webView: WebView
    private var webViewDestroyed = false
    private lateinit var credentialStore: CredentialStore
    private lateinit var autoLogin: AutoLogin

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startedAt = SystemClock.uptimeMillis()
        splashScreen.setKeepOnScreenCondition {
            !uiState.firstPageFinished &&
                SystemClock.uptimeMillis() - startedAt < AppConfig.SPLASH_MAX_DURATION_MS
        }

        // Лаунчеры ActivityResult регистрируются здесь, до STARTED.
        val fileChooser = FileChooser(this)
        val downloadHandler = DownloadHandler(this)
        credentialStore = CredentialStore(this)
        autoLogin = AutoLogin(credentialStore, uiState)
        webView = WebViewFactory.create(
            context = this,
            webViewClient = AppWebViewClient(uiState, UrlRouter(this), autoLogin, ::onRenderProcessGone),
            webChromeClient = AppWebChromeClient(uiState, fileChooser),
            downloadListener = downloadHandler,
        )

        // restoreState восстанавливает историю и перезагружает текущую страницу;
        // JS-состояние страницы (несохранённые формы) при этом теряется.
        val restored = savedInstanceState?.getBundle(KEY_WEBVIEW_STATE)
            ?.let { webView.restoreState(it) != null } == true
        if (restored) {
            uiState.canGoBack = webView.canGoBack()
        } else {
            webView.loadUrl(deepLinkUrl(intent) ?: AppConfig.START_URL)
        }
        if (savedInstanceState == null) handleShortcut(intent)

        setContent {
            AppTheme {
                // На стартовой странице колбэк выключен — системный «Назад» закрывает приложение.
                BackHandler(enabled = uiState.canGoBack) { webView.goBack() }
                Box {
                    WebScreen(
                        webView = webView,
                        state = uiState,
                        onRefresh = ::refresh,
                        onRetry = ::retry,
                    )
                    AuthOverlays(state = uiState, onOpenSettings = { uiState.showAutoLoginSettings = true })
                    if (uiState.showAutoLoginSettings) {
                        // Объявлен позже — перехватывает «Назад» раньше WebView.
                        BackHandler { uiState.showAutoLoginSettings = false }
                        AutoLoginSettingsScreen(
                            store = credentialStore,
                            onSaved = ::onAutoLoginSaved,
                            onDisabled = { toast(R.string.auto_login_disabled) },
                            onClose = { uiState.showAutoLoginSettings = false },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
        deepLinkUrl(intent)?.let { url ->
            uiState.failedUrl = null
            webView.loadUrl(url)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!webViewDestroyed) webView.onResume()
    }

    override fun onPause() {
        if (!webViewDestroyed) webView.onPause()
        // Куки пишутся на диск с задержкой; принудительно сохраняем, чтобы логин пережил убийство процесса.
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!webViewDestroyed) {
            outState.putBundle(KEY_WEBVIEW_STATE, Bundle().also { webView.saveState(it) })
        }
    }

    override fun onDestroy() {
        destroyWebView()
        super.onDestroy()
    }

    private fun refresh() {
        uiState.isRefreshing = true
        if (uiState.failedUrl != null) retry() else webView.reload()
    }

    private fun retry() {
        val url = uiState.failedUrl ?: webView.url ?: AppConfig.START_URL
        uiState.failedUrl = null
        webView.loadUrl(url)
    }

    private fun onAutoLoginSaved() {
        toast(R.string.auto_login_saved)
        uiState.showAutoLoginSettings = false
        // Если сейчас открыта форма входа — перезагружаем её, чтобы автовход сработал сразу.
        if (AppConfig.isAuthHost(webView.url?.toUri()?.host)) {
            autoLogin.reset()
            webView.reload()
        }
    }

    private fun handleShortcut(intent: Intent?) {
        if (intent?.action == ACTION_AUTO_LOGIN_SETTINGS) uiState.showAutoLoginSettings = true
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    /** Упавший WebView нельзя использовать дальше: уничтожаем и пересоздаём Activity. */
    private fun onRenderProcessGone() {
        destroyWebView()
        recreate()
    }

    private fun destroyWebView() {
        if (webViewDestroyed || !::webView.isInitialized) return
        webViewDestroyed = true
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    /** URL из deep link, если он ведёт на разрешённый домен. */
    private fun deepLinkUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        return uri.toString().takeIf { uri.scheme == "https" && AppConfig.isInternalHost(uri.host) }
    }

    private companion object {
        const val KEY_WEBVIEW_STATE = "webview_state"

        /** Ярлык «Автовход» на иконке приложения (res/xml/shortcuts.xml). */
        const val ACTION_AUTO_LOGIN_SETTINGS = "ru.school21.app.action.AUTO_LOGIN_SETTINGS"
    }
}
