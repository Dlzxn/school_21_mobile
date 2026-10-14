package ru.school21.app.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.school21.app.AppConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Проверка логина и пароля в Keycloak до сохранения — чтобы не сохранить опечатку. */
object CredentialChecker {

    enum class Result { OK, WRONG_CREDENTIALS, NETWORK_ERROR }

    suspend fun check(credentials: Credentials): Result = withContext(Dispatchers.IO) {
        val body = listOf(
            "client_id" to AppConfig.KEYCLOAK_CLIENT_ID,
            "grant_type" to "password",
            "username" to credentials.login,
            "password" to credentials.password,
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

        val connection = URL(AppConfig.KEYCLOAK_TOKEN_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            // Ответ с токенами не нужен и не сохраняется: важен только статус.
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Result.OK
                HttpURLConnection.HTTP_BAD_REQUEST, HttpURLConnection.HTTP_UNAUTHORIZED -> Result.WRONG_CREDENTIALS
                else -> Result.NETWORK_ERROR
            }
        } catch (_: IOException) {
            Result.NETWORK_ERROR
        } finally {
            connection.disconnect()
        }
    }

    private const val TIMEOUT_MS = 15_000
}
