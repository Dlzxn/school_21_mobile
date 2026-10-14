package ru.school21.app.web

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import ru.school21.app.AppConfig
import ru.school21.app.R
import java.net.URISyntaxException

/** Решает, где открыть ссылку: в WebView, в Custom Tabs или через системный интент. */
class UrlRouter(private val activity: Activity) {

    /** @return true, если ссылка обработана здесь и WebView не должен её загружать. */
    fun route(view: WebView, uri: Uri): Boolean {
        when (uri.scheme?.lowercase()) {
            "https" -> {
                if (AppConfig.isInternalHost(uri.host)) return false
                openInCustomTab(uri)
            }
            "http" -> {
                // Cleartext запрещён: свои ссылки повышаем до HTTPS, чужие отдаём браузеру.
                if (AppConfig.isInternalHost(uri.host)) {
                    view.loadUrl(uri.buildUpon().scheme("https").build().toString())
                } else {
                    openInCustomTab(uri)
                }
            }
            "intent" -> handleIntentScheme(view, uri.toString())
            "tel" -> startExternal(Intent(Intent.ACTION_DIAL, uri))
            "mailto", "sms", "smsto" -> startExternal(Intent(Intent.ACTION_SENDTO, uri))
            // Служебные схемы обрабатывает сам WebView.
            "about", "data", "blob", "javascript" -> return false
            // Прочие схемы (market:, tg:, geo: ...) — во внешние приложения.
            else -> startExternal(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        }
        return true
    }

    fun openInCustomTab(uri: Uri) {
        val toolbarColor = ContextCompat.getColor(activity, R.color.system_bar)
        val customTabs = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(
                CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build()
            )
            .setShowTitle(true)
            .build()
        try {
            customTabs.launchUrl(activity, uri)
        } catch (_: ActivityNotFoundException) {
            startExternal(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    /** intent://...#Intent;...;end — формат ссылок Chrome на приложения. */
    private fun handleIntentScheme(view: WebView, url: String) {
        val intent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (_: URISyntaxException) {
            return
        }
        // Страница не должна запускать произвольные (в т.ч. неэкспортированные) компоненты.
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null

        try {
            activity.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Приложения нет — пробуем запасной URL, затем Play Market.
        }

        val fallback = intent.getStringExtra("browser_fallback_url")?.toUri()
        if (fallback != null && fallback.scheme == "https") {
            if (AppConfig.isInternalHost(fallback.host)) view.loadUrl(fallback.toString()) else openInCustomTab(fallback)
            return
        }
        val pkg = intent.`package`
        if (pkg != null) {
            startExternal(Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri()))
        } else {
            showNoApp()
        }
    }

    private fun startExternal(intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showNoApp()
        }
    }

    private fun showNoApp() {
        Toast.makeText(activity, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
    }
}
