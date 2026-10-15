package ru.school21.app.friends

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import ru.school21.app.api.OpenApi
import ru.school21.app.api.Workstation

/** «Мои друзья»: список логинов и где они сейчас в кампусе. Только чтение Open API. */
object Friends {

    const val MAX = 30

    /** Сколько запросов к API одновременно — чтобы не упереться в лимит. */
    private const val PARALLEL = 3

    fun normalize(input: String): String = input.trim().substringBefore('@').lowercase()

    fun placeLabel(ws: Workstation): String = "${ws.clusterName} · ${ws.row.uppercase()}${ws.number}"

    /**
     * Место каждого друга. В ответе только те, для кого запрос удался:
     * значение null — друг не в кампусе.
     */
    suspend fun places(api: OpenApi, logins: List<String>): Map<String, Workstation?> = coroutineScope {
        val limit = Semaphore(PARALLEL)
        logins.map { login ->
            async { limit.withPermit { runCatching { login to api.workstation(login) }.getOrNull() } }
        }.awaitAll().filterNotNull().toMap()
    }

    /** Кто пришёл в кампус с прошлой проверки (был вне кампуса, теперь за компьютером). */
    fun arrivals(before: Map<String, String>, now: Map<String, Workstation?>): List<Pair<String, Workstation>> =
        now.mapNotNull { (login, ws) ->
            if (ws != null && login in before && before[login].isNullOrEmpty()) login to ws else null
        }
}
