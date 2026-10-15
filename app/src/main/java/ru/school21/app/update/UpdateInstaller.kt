package ru.school21.app.update

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ru.school21.app.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Обновление в один тап: скачать APK из релиза GitHub в кэш и открыть системный установщик.
 * Android всё равно покажет подтверждение и проверит, что APK подписан тем же ключом.
 */
object UpdateInstaller {

    private const val DIR = "updates"
    private fun allowed(url: URL) =
        url.protocol == "https" && (url.host == "github.com" || url.host.endsWith(".githubusercontent.com"))

    /** Скачивает APK; [onProgress] получает долю 0..1 (или -1, если размер неизвестен). */
    suspend fun download(activity: Activity, update: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(activity.cacheDir, DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "School21-${update.version}.apk")

            var url = URL(update.apkUrl)
            // Редиректы GitHub ведут на другой хост — проходим их вручную, проверяя, куда идём.
            repeat(5) {
                if (!allowed(url)) throw IOException("bad host ${url.host}")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "School21Android/${BuildConfig.VERSION_NAME}")
                }
                try {
                    val code = connection.responseCode
                    if (code in 300..399) {
                        url = URL(url, connection.getHeaderField("Location") ?: throw IOException("no location"))
                        return@repeat
                    }
                    if (code !in 200..299) throw IOException("HTTP $code")
                    val total = connection.contentLengthLong
                    connection.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                onProgress(if (total > 0) done.toFloat() / total else -1f)
                            }
                        }
                    }
                    return@withContext target
                } finally {
                    connection.disconnect()
                }
            }
            throw IOException("too many redirects")
        }

    /** Можно ли ставить APK; если нет — пользователя нужно отправить в системные настройки. */
    fun canInstall(activity: Activity): Boolean = activity.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(activity: Activity) {
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${activity.packageName}".toUri())
        )
    }

    fun install(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
    }
}
