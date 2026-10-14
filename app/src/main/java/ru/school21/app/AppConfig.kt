package ru.school21.app

/**
 * Единственное место с настройками обёртки. При смене сайта поправьте также
 * host в intent-filter deep links в AndroidManifest.xml.
 */
object AppConfig {
    const val APP_NAME = "School21"

    const val START_URL = "https://platform.21-school.ru/"

    /** Домены, открываемые внутри WebView (вместе со всеми поддоменами). */
    val INTERNAL_DOMAINS = setOf("21-school.ru")

    /**
     * Хосты страницы входа (Keycloak). После возврата с них на сайт история WebView
     * очищается, чтобы «Назад» не уводил обратно на логин.
     */
    val AUTH_HOSTS = setOf("auth.21-school.ru")

    /** Keycloak: эндпоинт токенов и клиент сайта — для проверки логина/пароля при сохранении. */
    const val KEYCLOAK_TOKEN_URL =
        "https://auth.21-school.ru/auth/realms/EduPowerKeycloak/protocol/openid-connect/token"
    const val KEYCLOAK_CLIENT_ID = "school21"

    /** Суффикс User-Agent, по которому сайт может отличить приложение. */
    val USER_AGENT_SUFFIX = " ${APP_NAME}Android/${BuildConfig.VERSION_NAME}"

    /** Pull-to-refresh. Отключите, если на SPA он срабатывает при прокрутке вверх (см. README). */
    const val PULL_TO_REFRESH_ENABLED = true

    /** Максимум, сколько держим splash screen в ожидании первой загрузки страницы. */
    const val SPLASH_MAX_DURATION_MS = 3_000L

    fun isInternalHost(host: String?): Boolean =
        host != null && INTERNAL_DOMAINS.any { host.equals(it, ignoreCase = true) || host.endsWith(".$it", ignoreCase = true) }

    fun isAuthHost(host: String?): Boolean =
        host != null && AUTH_HOSTS.any { host.equals(it, ignoreCase = true) }
}
