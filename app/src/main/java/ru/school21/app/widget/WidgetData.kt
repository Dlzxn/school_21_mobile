package ru.school21.app.widget

import org.json.JSONArray
import org.json.JSONObject
import ru.school21.app.api.Coalition
import ru.school21.app.api.Points
import ru.school21.app.api.Workstation
import java.util.Locale

/** Часы логтайма с одним знаком после запятой в локали устройства. */
fun formatHours(hours: Double): String = String.format(Locale.getDefault(), "%.1f", hours)

/** [mine] — пункт личного календаря (вы записаны), иначе событие кампуса. */
data class WidgetEvent(val name: String, val startMillis: Long, val location: String?, val mine: Boolean = false)

/** Снимок данных пользователя для виджетов; хранится в AppSettings как JSON. */
data class WidgetData(
    val login: String,
    val subtitle: String?,
    val level: Int,
    val exp: Long,
    val expToNextLevel: Long,
    val points: Points,
    val logtime: Double?,
    val workstation: Workstation?,
    val coalition: Coalition?,
    val events: List<WidgetEvent>,
    val updatedAt: Long,
) {
    fun toJson(): String = JSONObject()
        .put("login", login)
        .put("subtitle", subtitle ?: JSONObject.NULL)
        .put("level", level)
        .put("exp", exp)
        .put("expToNext", expToNextLevel)
        .put("prp", points.peerReviewPoints)
        .put("crp", points.codeReviewPoints)
        .put("coins", points.coins)
        .put("logtime", logtime ?: JSONObject.NULL)
        .put("cluster", workstation?.clusterName ?: JSONObject.NULL)
        .put("row", workstation?.row ?: JSONObject.NULL)
        .put("seat", workstation?.number ?: JSONObject.NULL)
        .put("coalition", coalition?.name ?: JSONObject.NULL)
        .put("coalitionRank", coalition?.rank ?: JSONObject.NULL)
        .put(
            "events",
            JSONArray(events.map {
                JSONObject().put("name", it.name).put("start", it.startMillis)
                    .put("location", it.location ?: JSONObject.NULL).put("mine", it.mine)
            }),
        )
        .put("updatedAt", updatedAt)
        .toString()

    companion object {
        fun fromJson(text: String?): WidgetData? = text?.let {
            runCatching {
                val json = JSONObject(it)
                val events = json.optJSONArray("events") ?: JSONArray()
                WidgetData(
                    login = json.getString("login"),
                    subtitle = if (json.isNull("subtitle")) null else json.optString("subtitle"),
                    level = json.getInt("level"),
                    exp = json.getLong("exp"),
                    expToNextLevel = json.optLong("expToNext"),
                    points = Points(json.getInt("prp"), json.getInt("crp"), json.getInt("coins")),
                    logtime = if (json.isNull("logtime")) null else json.getDouble("logtime"),
                    workstation = if (json.isNull("cluster")) null else Workstation(
                        json.getString("cluster"), json.optString("row"), json.optInt("seat"),
                    ),
                    coalition = if (json.isNull("coalition")) null else Coalition(
                        json.getString("coalition"),
                        if (json.isNull("coalitionRank")) null else json.optInt("coalitionRank"),
                    ),
                    events = (0 until events.length()).map { i ->
                        val e = events.getJSONObject(i)
                        WidgetEvent(
                            name = e.getString("name"),
                            startMillis = e.getLong("start"),
                            location = if (e.isNull("location")) null else e.getString("location"),
                            mine = e.optBoolean("mine"),
                        )
                    },
                    updatedAt = json.getLong("updatedAt"),
                )
            }.getOrNull()
        }
    }
}
