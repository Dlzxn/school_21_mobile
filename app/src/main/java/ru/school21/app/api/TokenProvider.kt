package ru.school21.app.api

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.school21.app.AppConfig
import ru.school21.app.auth.CredentialStore
import java.io.IOException

/** Автовход не настроен — фоновым функциям нечем авторизоваться. */
class NotConfiguredException : IOException("Auto-login is not configured")

/** Сохранённый пароль больше не подходит. */
class InvalidCredentialsException : IOException("Saved credentials were rejected")

/**
 * Access-токены Keycloak для фоновых запросов. Живут в памяти процесса; refresh-токен хранится
 * зашифрованным. Сначала просим offline_access — такой refresh-токен живёт неделями, и пароль
 * почти не нужен (меньше новых сессий на сервере). Если scope запрещён — обычный refresh.
 */
class TokenProvider private constructor(private val store: CredentialStore) {

    private class Token(val value: String, val expiresAt: Long)

    private val cache = mutableMapOf<String, Token>()
    private val mutex = Mutex()

    suspend fun accessToken(clientId: String): String = mutex.withLock {
        cache[clientId]?.takeIf { it.expiresAt > SystemClock.elapsedRealtime() + EXPIRY_MARGIN_MS }?.let {
            return@withLock it.value
        }
        withContext(Dispatchers.IO) {
            val refresh = store.getSecret(refreshKey(clientId))
            val response = refresh?.let { runCatching { refreshGrant(clientId, it) }.getOrNull() }
                ?: passwordGrant(clientId)
            remember(clientId, response)
        }
    }

    /** Сервер отверг токен (401) — забыть его, следующий вызов получит новый. */
    fun invalidate(clientId: String) {
        cache.remove(clientId)
    }

    private fun refreshGrant(clientId: String, refreshToken: String): JSONObject = tokenRequest(
        "client_id" to clientId,
        "grant_type" to "refresh_token",
        "refresh_token" to refreshToken,
    )

    private fun passwordGrant(clientId: String): JSONObject {
        val credentials = store.load() ?: throw NotConfiguredException()
        val base = arrayOf(
            "client_id" to clientId,
            "grant_type" to "password",
            "username" to credentials.login,
            "password" to credentials.password,
        )
        return try {
            try {
                tokenRequest(*base, "scope" to "openid offline_access")
            } catch (e: HttpException) {
                if (!e.body.contains("invalid_scope")) throw e
                tokenRequest(*base)
            }
        } catch (e: HttpException) {
            if (e.body.contains("invalid_grant")) throw InvalidCredentialsException()
            throw e
        }
    }

    private fun tokenRequest(vararg params: Pair<String, String>): JSONObject = JSONObject(
        Http.request(
            url = AppConfig.KEYCLOAK_TOKEN_URL,
            method = "POST",
            body = Http.form(*params),
            contentType = "application/x-www-form-urlencoded",
        )
    )

    private fun remember(clientId: String, response: JSONObject): String {
        val access = response.getString("access_token")
        val expiresIn = response.optLong("expires_in", 60)
        cache[clientId] = Token(access, SystemClock.elapsedRealtime() + expiresIn * 1000)
        response.optString("refresh_token").takeIf { it.isNotEmpty() }?.let {
            store.putSecret(refreshKey(clientId), it)
        }
        return access
    }

    private fun refreshKey(clientId: String) = "refresh_$clientId"

    companion object {
        private const val EXPIRY_MARGIN_MS = 30_000L

        @Volatile
        private var instance: TokenProvider? = null

        /** Один на процесс: UI и фоновый воркер делят кэш токенов. */
        fun get(context: Context): TokenProvider = instance ?: synchronized(this) {
            instance ?: TokenProvider(CredentialStore(context)).also { instance = it }
        }

        /** После смены или удаления логина/пароля. */
        fun reset() {
            instance?.cache?.clear()
            PlatformApi.cachedSchoolId = null
        }
    }
}
