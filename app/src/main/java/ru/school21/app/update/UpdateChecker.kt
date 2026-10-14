package ru.school21.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.school21.app.AppConfig
import ru.school21.app.BuildConfig
import ru.school21.app.api.Http

data class UpdateInfo(val version: String, val apkUrl: String, val pageUrl: String)

/** Проверка новой версии по последнему релизу на GitHub. */
object UpdateChecker {

    /** @return информация о более новой версии или null, если стоит последняя. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val release = JSONObject(Http.request("https://api.github.com/repos/${AppConfig.GITHUB_REPO}/releases/latest"))
        val version = release.getString("tag_name").removePrefix("v")
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
        val assets = release.optJSONArray("assets")
        val apkUrl = (0 until (assets?.length() ?: 0))
            .map { assets!!.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?.getString("browser_download_url")
        val pageUrl = release.getString("html_url")
        UpdateInfo(version, apkUrl ?: pageUrl, pageUrl)
    }

    /** Сравнение версий вида 1.2.3 по числам. */
    internal fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }
}
