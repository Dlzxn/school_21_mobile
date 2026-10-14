package ru.school21.app.api

import android.util.Log
import ru.school21.app.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** HTTP-ошибка с кодом и телом ответа. */
class HttpException(val code: Int, val body: String) : IOException("HTTP $code")

/** Минимальный блокирующий HTTP-клиент на HttpURLConnection. Вызывать с Dispatchers.IO. */
internal object Http {

    fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        contentType: String? = null,
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "School21Android/${BuildConfig.VERSION_NAME}")
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                contentType?.let { connection.setRequestProperty("Content-Type", it) }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            // В логах метод, путь и код; для ошибок — начало тела (там сообщение сервера, не токены).
            if (BuildConfig.DEBUG) {
                val detail = if (code in 200..299) "" else " " + text.take(300)
                Log.d(TAG, "$method ${URL(url).path} → $code$detail")
            }
            if (code !in 200..299) throw HttpException(code, text)
            return text
        } finally {
            connection.disconnect()
        }
    }

    fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

    private const val TIMEOUT_MS = 15_000
    private const val TAG = "S21Http"
}
