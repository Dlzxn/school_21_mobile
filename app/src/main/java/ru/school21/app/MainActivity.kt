package ru.school21.app

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.school21.app.api.TokenProvider
import ru.school21.app.auth.AutoLogin
import ru.school21.app.auth.CredentialStore
import ru.school21.app.sync.Notifications
import ru.school21.app.sync.SyncWorker
import ru.school21.app.ui.AppMenu
import ru.school21.app.ui.AppTheme
import ru.school21.app.ui.AuthOverlays
import ru.school21.app.ui.AutoLoginSettingsScreen
import ru.school21.app.ui.MenuAction
import ru.school21.app.ui.MenuButton
import ru.school21.app.ui.PeerScreen
import ru.school21.app.ui.SettingsScreen
import ru.school21.app.ui.UpdateBanner
import ru.school21.app.ui.WebScreen
import ru.school21.app.update.UpdateChecker
import ru.school21.app.web.AppWebChromeClient
import ru.school21.app.web.AppWebViewClient
import ru.school21.app.web.DownloadHandler
import ru.school21.app.web.FileChooser
import ru.school21.app.web.FullscreenVideo
import ru.school21.app.web.Screen
import ru.school21.app.web.UrlRouter
import ru.school21.app.web.WebUiState
import ru.school21.app.web.WebViewFactory
import ru.school21.app.widget.Widgets

/**
 * Хост для WebView: жизненный цикл, сохранение состояния, deep links, нативные экраны поверх сайта.
 * Поворот экрана Activity не пересоздаёт (configChanges в манифесте), поэтому
 * saveState/restoreState нужны только для смены темы и восстановления после смерти процесса.
 */
class MainActivity : ComponentActivity() {

    private val uiState = WebUiState()
    private lateinit var webView: WebView
    private var webViewDestroyed = false
    private lateinit var credentialStore: CredentialStore
    private lateinit var autoLogin: AutoLogin
    private lateinit var settings: AppSettings
    private var menuButton by mutableStateOf(MenuButtonPosition.RIGHT)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Сайт и нативные экраны тёмные — светлые иконки в системных панелях.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val startedAt = SystemClock.uptimeMillis()
        splashScreen.setKeepOnScreenCondition {
            !uiState.firstPageFinished &&
                SystemClock.uptimeMillis() - startedAt < AppConfig.SPLASH_MAX_DURATION_MS
        }

        settings = AppSettings(this)
        menuButton = settings.menuButton
        Notifications.createChannels(this)
        SyncWorker.reschedule(this)
        // При открытии приложения сразу обновляем виджеты и проверяем уведомления, не дожидаясь расписания.
        if (savedInstanceState == null && SyncWorker.isNeeded(this)) SyncWorker.runNow(this)

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
        if (savedInstanceState == null) {
            handleShortcut(intent)
            checkForUpdates(manual = false)
        }

        setContent {
            AppTheme {
                // На стартовой странице колбэк выключен — системный «Назад» закрывает приложение.
                // Обработчики ниже объявлены позже и потому перехватывают «Назад» раньше этого.
                BackHandler(enabled = uiState.canGoBack) { webView.goBack() }
                Box(Modifier.fillMaxSize()) {
                    WebScreen(
                        webView = webView,
                        state = uiState,
                        onRefresh = ::refresh,
                        onRetry = ::retry,
                    )
                    if (uiState.screen == Screen.NONE && !uiState.autoLoginInProgress) {
                        MenuButton(menuButton) { uiState.menuOpen = true }
                    }
                    AuthOverlays(state = uiState, onOpenSettings = { uiState.screen = Screen.AUTO_LOGIN })
                    val update = uiState.availableUpdate
                    if (update != null && uiState.authBanner == null && uiState.screen == Screen.NONE) {
                        UpdateBanner(
                            update = update,
                            onDownload = {
                                openExternally(update.apkUrl)
                                uiState.availableUpdate = null
                            },
                            onDismiss = { uiState.availableUpdate = null },
                        )
                    }
                    NativeScreens()
                    if (uiState.menuOpen) {
                        AppMenu(
                            onAction = { uiState.menuOpen = false; onMenuAction(it) },
                            onDismiss = { uiState.menuOpen = false },
                        )
                    }
                    uiState.fullscreenVideo?.let { FullscreenVideoLayer(it) }
                }
            }
        }
    }

    @Composable
    private fun NativeScreens() {
        if (uiState.screen == Screen.NONE) return
        BackHandler { uiState.screen = Screen.NONE }
        when (uiState.screen) {
            Screen.SETTINGS -> SettingsScreen(
                onOpenAutoLogin = { uiState.screen = Screen.AUTO_LOGIN },
                onCheckUpdates = { checkForUpdates(manual = true) },
                onOpenRepository = { openExternally("https://github.com/${AppConfig.GITHUB_REPO}") },
                onReportBug = { openExternally("https://github.com/${AppConfig.GITHUB_REPO}/issues") },
                onOpenAuthor = { openExternally("https://github.com/${AppConfig.AUTHOR_GITHUB}") },
                onMenuButtonChanged = { menuButton = it },
                onClose = { uiState.screen = Screen.NONE },
            )
            Screen.AUTO_LOGIN -> AutoLoginSettingsScreen(
                store = credentialStore,
                onSaved = ::onAutoLoginSaved,
                onDisabled = ::onAutoLoginDisabled,
                onClose = { uiState.screen = Screen.NONE },
            )
            Screen.PEER -> PeerScreen(
                onOpenProfile = { login ->
                    uiState.screen = Screen.NONE
                    webView.loadUrl("${AppConfig.PLATFORM_ORIGIN}/profile/$login")
                },
                onSetupAutoLogin = { uiState.screen = Screen.AUTO_LOGIN },
                onClose = { uiState.screen = Screen.NONE },
            )
            Screen.NONE -> Unit
        }
    }

    /** Видео на весь экран: прячем системные панели, «Назад» сворачивает видео. */
    @Composable
    private fun FullscreenVideoLayer(video: FullscreenVideo) {
        BackHandler { exitFullscreen(video) }
        DisposableEffect(video) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
        }
        key(video) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    FrameLayout(context).apply {
                        setBackgroundColor(Color.BLACK)
                        (video.view.parent as? ViewGroup)?.removeView(video.view)
                        addView(video.view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                    }
                },
            )
        }
    }

    private fun exitFullscreen(video: FullscreenVideo) {
        video.callback.onCustomViewHidden()
        uiState.fullscreenVideo = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
        deepLinkUrl(intent)?.let { url ->
            uiState.failedUrl = null
            uiState.screen = Screen.NONE
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

    private fun onMenuAction(action: MenuAction) {
        val url = webView.url ?: AppConfig.START_URL
        when (action) {
            MenuAction.RELOAD -> webView.reload()
            MenuAction.SHARE -> startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url),
                    null,
                )
            )
            MenuAction.OPEN_IN_BROWSER -> openExternally(url)
            MenuAction.PEER -> uiState.screen = Screen.PEER
            MenuAction.SETTINGS -> uiState.screen = Screen.SETTINGS
        }
    }

    /** Открыть ссылку в браузере. Само приложение исключаем — оно тоже умеет открывать platform.21-school.ru. */
    private fun openExternally(url: String) {
        val chooser = Intent.createChooser(Intent(Intent.ACTION_VIEW, url.toUri()), null)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this, MainActivity::class.java)))
        startActivity(chooser)
    }

    private fun checkForUpdates(manual: Boolean) {
        val now = System.currentTimeMillis()
        if (!manual && now - settings.lastUpdateCheck < UPDATE_CHECK_INTERVAL_MS) return
        lifecycleScope.launch {
            val update = runCatching { UpdateChecker.check() }
            settings.lastUpdateCheck = now
            update.onSuccess { info ->
                uiState.availableUpdate = info
                if (manual && info == null) toast(R.string.update_latest)
                if (manual && info != null) uiState.screen = Screen.NONE
            }
            update.onFailure { if (manual) toast(R.string.update_check_failed) }
        }
    }

    private fun onAutoLoginSaved() {
        toast(R.string.auto_login_saved)
        uiState.screen = Screen.NONE
        TokenProvider.reset()
        settings.widgetData = null
        SyncWorker.reschedule(this)
        if (Widgets.hasWidgets(this)) SyncWorker.runNow(this)
        // Если сейчас открыта форма входа — перезагружаем её, чтобы автовход сработал сразу.
        if (AppConfig.isAuthHost(webView.url?.toUri()?.host)) {
            autoLogin.reset()
            webView.reload()
        }
    }

    private fun onAutoLoginDisabled() {
        toast(R.string.auto_login_disabled)
        TokenProvider.reset()
        settings.widgetData = null
        lifecycleScope.launch { Widgets.updateAll(this@MainActivity) }
        SyncWorker.reschedule(this)
    }

    private fun handleShortcut(intent: Intent?) {
        uiState.screen = when (intent?.action) {
            ACTION_SETTINGS -> Screen.SETTINGS
            ACTION_PEER -> Screen.PEER
            ACTION_AUTO_LOGIN_SETTINGS -> Screen.AUTO_LOGIN
            else -> return
        }
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
        const val MATCH_PARENT = FrameLayout.LayoutParams.MATCH_PARENT
        const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

        // Ярлыки на иконке приложения (res/xml/shortcuts.xml).
        const val ACTION_SETTINGS = "ru.school21.app.action.SETTINGS"
        const val ACTION_PEER = "ru.school21.app.action.PEER"
        const val ACTION_AUTO_LOGIN_SETTINGS = "ru.school21.app.action.AUTO_LOGIN_SETTINGS"
    }
}
