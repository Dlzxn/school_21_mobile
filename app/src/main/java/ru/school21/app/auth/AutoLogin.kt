package ru.school21.app.auth

import android.net.Uri
import android.os.SystemClock
import android.webkit.WebView
import org.json.JSONObject
import ru.school21.app.AppConfig
import ru.school21.app.web.AuthBanner
import ru.school21.app.web.WebUiState

/**
 * Автовход: когда сайт выкидывает на форму Keycloak, заполняет логин/пароль и отправляет форму.
 * Работает через evaluateJavascript, без addJavascriptInterface.
 *
 * Защита от петли: если сразу после нашей отправки снова показана форма (неверный пароль,
 * капча и т.п.), повторно не пробуем — показываем баннер, дальше пользователь входит сам.
 */
class AutoLogin(
    private val store: CredentialStore,
    private val state: WebUiState,
) {
    private var lastAttemptAt = 0L
    private var lastAttemptUrl: String? = null

    fun onPageFinished(view: WebView, url: String?) {
        val uri = url?.let(Uri::parse) ?: return
        if (!AppConfig.isAuthHost(uri.host)) {
            // Ушли со страницы входа — вход удался (или пользователь ушёл сам).
            lastAttemptAt = 0L
            state.autoLoginInProgress = false
            state.authBanner = null
            return
        }
        if (!isLoginPage(uri)) return

        val credentials = store.load()
        if (credentials == null) {
            if (!state.autoLoginOfferDismissed) state.authBanner = AuthBanner.OFFER
            return
        }

        val now = SystemClock.elapsedRealtime()
        // WebView иногда сообщает onPageFinished дважды для одной страницы — это не повторный показ формы.
        if (url == lastAttemptUrl && now - lastAttemptAt < DUPLICATE_EVENT_MS) return
        if (lastAttemptAt != 0L && now - lastAttemptAt < RETRY_GUARD_MS) {
            lastAttemptAt = 0L
            state.autoLoginInProgress = false
            state.authBanner = AuthBanner.FAILED
            return
        }
        lastAttemptAt = now
        lastAttemptUrl = url
        state.authBanner = null
        state.autoLoginInProgress = true
        view.evaluateJavascript(fillScript(credentials), null)
    }

    /** Пользователь сам поменял настройки — начинаем с чистого листа. */
    fun reset() {
        lastAttemptAt = 0L
    }

    /** Форма логина: первый показ (.../openid-connect/auth) и повторный после ошибки (.../login-actions/authenticate). */
    private fun isLoginPage(uri: Uri): Boolean {
        val path = uri.path.orEmpty()
        return path.endsWith("/protocol/openid-connect/auth") || path.endsWith("/login-actions/authenticate")
    }

    /**
     * Форма рендерится React'ом уже после onPageFinished, поэтому ждём полей до ~8 с.
     * Значения ставим через нативный setter + событие input (иначе React их затрёт),
     * отправляем обычным form.submit() — форма у Keycloak простая POST-форма.
     * Проверка hostname внутри скрипта: если страница успела смениться, данные никуда не уйдут.
     */
    private fun fillScript(credentials: Credentials): String {
        val host = JSONObject.quote(AppConfig.AUTH_HOSTS.first())
        val login = JSONObject.quote(credentials.login)
        val password = JSONObject.quote(credentials.password)
        return """
            (function (host, login, password) {
              if (location.hostname !== host || window.__s21AutoLogin) return;
              window.__s21AutoLogin = true;
              var tries = 0;
              var timer = setInterval(function () {
                var u = document.querySelector('input[name="username"]');
                var p = document.querySelector('input[name="password"]');
                if (!u || !p || !p.form) { if (++tries > 40) clearInterval(timer); return; }
                clearInterval(timer);
                var setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                [[u, login], [p, password]].forEach(function (pair) {
                  setValue.call(pair[0], pair[1]);
                  pair[0].dispatchEvent(new Event('input', { bubbles: true }));
                });
                setTimeout(function () { HTMLFormElement.prototype.submit.call(p.form); }, 300);
              }, 200);
            })($host, $login, $password);
        """.trimIndent()
    }

    private companion object {
        /** Форма снова показана в течение этого времени после отправки — считаем, что вход не удался. */
        const val RETRY_GUARD_MS = 60_000L
        const val DUPLICATE_EVENT_MS = 3_000L
    }
}
