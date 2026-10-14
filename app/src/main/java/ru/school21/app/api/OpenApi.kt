package ru.school21.app.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.school21.app.AppConfig
import java.net.URLEncoder
import java.time.Instant

data class Participant(
    val login: String,
    val className: String?,
    val parallelName: String?,
    val level: Int,
    val expValue: Long,
    val expToNextLevel: Long,
    val campus: String?,
    val status: String?,
)

data class Workstation(val clusterName: String, val row: String, val number: Int)

data class Points(val peerReviewPoints: Int, val codeReviewPoints: Int, val coins: Int)

/** Коалиция (трайб) и место участника в ней. */
data class Coalition(val name: String, val rank: Int?)

data class CampusEvent(
    val id: Long,
    val type: String,
    val name: String,
    val location: String?,
    val start: Instant,
    val end: Instant,
)

/**
 * Официальный Open API Школы 21 (документация: platform.21-school.ru → «API»).
 * Авторизация — токен клиента s21-open-api по логину/паролю пользователя.
 */
class OpenApi(context: Context) {

    private val tokens = TokenProvider.get(context)

    suspend fun participant(login: String): Participant = get("/v1/participants/${path(login)}").let {
        val json = JSONObject(it)
        Participant(
            login = json.getString("login"),
            className = json.optStringOrNull("className"),
            parallelName = json.optStringOrNull("parallelName"),
            level = json.optInt("level"),
            expValue = json.optLong("expValue"),
            expToNextLevel = json.optLong("expToNextLevel"),
            campus = json.optJSONObject("campus")?.optStringOrNull("shortName"),
            status = json.optStringOrNull("status"),
        )
    }

    /**
     * null — пир сейчас не за компьютером в кампусе. Вопреки документации (404) API в этом
     * случае отвечает 200 с пустым телом — обрабатываем оба варианта.
     */
    suspend fun workstation(login: String): Workstation? = try {
        val body = get("/v1/participants/${path(login)}/workstation")
        if (body.isBlank()) {
            null
        } else {
            val json = JSONObject(body)
            Workstation(json.getString("clusterName"), json.optString("row"), json.optInt("number"))
        }
    } catch (e: HttpException) {
        if (e.code == 404) null else throw e
    }

    /** null — участник не состоит в коалиции (пустой ответ или 404). */
    suspend fun coalition(login: String): Coalition? = try {
        val body = get("/v1/participants/${path(login)}/coalition")
        if (body.isBlank()) {
            null
        } else {
            val json = JSONObject(body)
            Coalition(json.getString("name"), if (json.isNull("rank")) null else json.optInt("rank"))
        }
    } catch (e: HttpException) {
        if (e.code == 404) null else throw e
    }

    /** Средний логтайм за текущую неделю, часов. */
    suspend fun logtime(login: String): Double =
        get("/v1/participants/${path(login)}/logtime").trim().toDouble()

    suspend fun points(login: String): Points = JSONObject(get("/v1/participants/${path(login)}/points")).let {
        Points(it.optInt("peerReviewPoints"), it.optInt("codeReviewPoints"), it.optInt("coins"))
    }

    /** События кампуса пользователя в интервале [from, to]. */
    suspend fun events(from: Instant, to: Instant): List<CampusEvent> {
        val json = JSONObject(get("/v1/events?from=${query(from.toString())}&to=${query(to.toString())}&limit=100"))
        val array: JSONArray = json.optJSONArray("events") ?: return emptyList()
        return (0 until array.length()).map { i ->
            val e = array.getJSONObject(i)
            CampusEvent(
                id = e.getLong("id"),
                type = e.optString("type"),
                name = e.optString("name"),
                location = e.optStringOrNull("location"),
                start = Instant.parse(e.getString("startDateTime")),
                end = Instant.parse(e.getString("endDateTime")),
            )
        }
    }

    /** GET с одним повтором на 401 (токен мог истечь на сервере раньше срока). */
    private suspend fun get(pathAndQuery: String): String {
        repeat(2) { attempt ->
            val token = tokens.accessToken(CLIENT_ID)
            try {
                return withContext(Dispatchers.IO) {
                    Http.request(AppConfig.OPEN_API_URL + pathAndQuery, headers = mapOf("Authorization" to "Bearer $token"))
                }
            } catch (e: HttpException) {
                if (e.code != 401 || attempt == 1) throw e
                tokens.invalidate(CLIENT_ID)
            }
        }
        error("unreachable")
    }

    private fun path(segment: String) = URLEncoder.encode(segment.trim().lowercase(), "UTF-8").replace("+", "%20")
    private fun query(value: String) = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val CLIENT_ID = "s21-open-api"
    }
}

internal fun JSONObject.optStringOrNull(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
