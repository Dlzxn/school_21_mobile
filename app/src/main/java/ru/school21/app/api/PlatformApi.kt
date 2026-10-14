package ru.school21.app.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.school21.app.AppConfig
import java.io.IOException
import java.time.Instant

data class PlatformNotification(
    val id: String,
    val message: String,
    val time: String,
    val wasRead: Boolean,
)

/** Пункт личного календаря: событие, на которое вы записаны, проверка и т.п. */
data class AgendaItem(
    val id: String,
    val label: String,
    val description: String?,
    val type: String?,
    val start: Instant,
    val end: Instant?,
)

/**
 * Внутренний API сайта (тот же GraphQL, что использует фронтенд платформы). Недокументирован.
 * Только запросы на чтение (query) — никаких мутаций.
 *
 * Особенности шлюза, найденные опытным путём:
 *  - авторизация — cookie tokenId с access-токеном клиента school21 и заголовки школы;
 *  - запрос маршрутизируется по ?operation=<имя>;
 *  - пропускаются только запросы, которые есть во фронтенде сайта (allowlist): изменённый текст
 *    запроса (другие поля, другое имя операции) получает 400. Поэтому тексты ниже — дословно с сайта.
 */
class PlatformApi(context: Context) {

    private val tokens = TokenProvider.get(context)

    suspend fun notifications(limit: Int = 20): List<PlatformNotification> {
        val variables = JSONObject().put("paging", JSONObject().put("offset", 0).put("limit", limit))
        val list = query("getUserNotifications", NOTIFICATIONS_QUERY, variables)
            .getJSONObject("s21Notification")
            .getJSONObject("getS21Notifications")
            .optJSONArray("notifications") ?: JSONArray()
        return (0 until list.length()).map { i ->
            val n = list.getJSONObject(i)
            PlatformNotification(
                id = n.getString("id"),
                message = n.optString("message"),
                time = n.optString("time"),
                wasRead = n.optBoolean("wasRead"),
            )
        }
    }

    /** Личный календарь в интервале [from, to]. */
    suspend fun agenda(from: Instant, to: Instant, limit: Int = 20): List<AgendaItem> {
        val variables = JSONObject().put("from", from.toString()).put("to", to.toString()).put("limit", limit)
        val list = query("getAgendaEvents", AGENDA_QUERY, variables)
            .getJSONObject("calendarEventS21")
            .optJSONArray("getMyAgendaEvents") ?: JSONArray()
        return (0 until list.length()).mapNotNull { i ->
            val e = list.getJSONObject(i)
            val start = e.optStringOrNull("start")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val context = e.optJSONObject("agendaItemContext")
            AgendaItem(
                id = context?.optStringOrNull("entityId") ?: "${e.optString("label")}@$start",
                label = e.optString("label"),
                description = e.optStringOrNull("description"),
                type = e.optStringOrNull("agendaEventType"),
                start = start,
                end = e.optStringOrNull("end")?.let { runCatching { Instant.parse(it) }.getOrNull() },
            )
        }
    }

    private suspend fun query(operation: String, text: String, variables: JSONObject): JSONObject {
        val body = JSONObject().put("operationName", operation).put("variables", variables).put("query", text)
        val response = JSONObject(post("/services/graphql?operation=$operation", body.toString()))
        response.optJSONArray("errors")?.let { throw IOException("GraphQL: ${it.optJSONObject(0)?.optString("message")}") }
        return response.getJSONObject("data")
    }

    private suspend fun post(path: String, json: String): String {
        repeat(2) { attempt ->
            val token = tokens.accessToken(CLIENT_ID)
            try {
                return withContext(Dispatchers.IO) {
                    val school = cachedSchoolId ?: fetchSchoolId(token).also { cachedSchoolId = it }
                    Http.request(
                        url = AppConfig.PLATFORM_ORIGIN + path,
                        method = "POST",
                        headers = mapOf(
                            "Cookie" to "tokenId=$token",
                            "schoolid" to school,
                            "userrole" to "STUDENT",
                            "X-EDU-ORG-UNIT-ID" to school,
                            "X-EDU-PRODUCT-ID" to EDU_PRODUCT_ID,
                        ),
                        body = json,
                        contentType = "application/json",
                    )
                }
            } catch (e: HttpException) {
                if (e.code != 401 || attempt == 1) throw e
                tokens.invalidate(CLIENT_ID)
            }
        }
        error("unreachable")
    }

    /** schoolId пользователя — тот же REST-вызов, что делает сайт при старте ({"data":[{"schoolId":…}]}). */
    private fun fetchSchoolId(token: String): String {
        val text = Http.request(
            url = AppConfig.PLATFORM_ORIGIN + "/services/rest/v1/users/getCurrentUserSchoolRoles",
            headers = mapOf("Cookie" to "tokenId=$token"),
        )
        return findSchoolId(if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text))
            ?: throw IOException("schoolId not found")
    }

    private fun findSchoolId(node: Any?): String? = when (node) {
        is JSONObject -> node.optStringOrNull("schoolId")
            ?: node.keys().asSequence().firstNotNullOfOrNull { findSchoolId(node.opt(it)) }
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { findSchoolId(node.opt(it)) }
        else -> null
    }

    companion object {
        private const val CLIENT_ID = "school21"

        /** Константа из фронтенда платформы (заголовок X-EDU-PRODUCT-ID). */
        private const val EDU_PRODUCT_ID = "96098f4b-5708-4c42-a62c-6893419169b3"

        /** schoolId не меняется — один запрос на процесс. Сбрасывается вместе с токенами. */
        @Volatile
        internal var cachedSchoolId: String? = null

        // Дословно из фронтенда платформы (см. комментарий к классу про allowlist).
        private const val NOTIFICATIONS_QUERY =
            "\n    query getUserNotifications(\$paging: PagingInput!) {\n  s21Notification {\n" +
                "    getS21Notifications(paging: \$paging) {\n      notifications {\n        id\n" +
                "        relatedObjectType\n        relatedObjectId\n        message\n        time\n" +
                "        wasRead\n        groupName\n      }\n      totalCount\n      groupNames\n    }\n  }\n}\n    "

        private const val AGENDA_QUERY =
            "query getAgendaEvents(\$from: DateTime!, \$to: DateTime!, \$limit: Int!) {\n  calendarEventS21 {\n" +
                "    getMyAgendaEvents(from: \$from, to: \$to, limit: \$limit) {\n      agendaItemContext {\n" +
                "        entityId\n        entityType\n        __typename\n      }\n      start\n      end\n" +
                "      label\n      description\n      agendaEventType\n      additionalInfo {\n        key\n" +
                "        value\n        __typename\n      }\n      __typename\n    }\n    __typename\n  }\n}\n"
    }
}
