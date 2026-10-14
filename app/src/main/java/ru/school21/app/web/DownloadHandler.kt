package ru.school21.app.web

import android.Manifest
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import ru.school21.app.R

/**
 * Скачивание файлов через системный DownloadManager.
 * Создавать в Activity.onCreate (регистрирует лаунчер разрешения для Android 8–9).
 *
 * Ограничение: DownloadManager качает заново по URL, передавая только куки и User-Agent.
 * blob:/data:-ссылки (файлы, собранные в JS) и загрузки с заголовком Authorization не работают.
 */
class DownloadHandler(private val activity: ComponentActivity) : DownloadListener {

    private var pendingDownload: (() -> Unit)? = null

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingDownload?.invoke() else toast(R.string.download_permission_denied)
        pendingDownload = null
    }

    override fun onDownloadStart(
        url: String,
        userAgent: String,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
    ) {
        if (!url.startsWith("https://", ignoreCase = true)) {
            toast(R.string.download_unsupported)
            return
        }
        val download = { enqueue(url, userAgent, contentDisposition, mimeType) }

        // До Android 10 для записи в общую папку Downloads нужно разрешение.
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingDownload = download
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            download()
        }
    }

    private fun enqueue(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(url.toUri()).apply {
            // Сессия сайта — в куках; без них защищённый файл не скачается.
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            addRequestHeader("User-Agent", userAgent)
            if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
            setTitle(fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        try {
            activity.getSystemService<DownloadManager>()!!.enqueue(request)
            Toast.makeText(activity, activity.getString(R.string.download_started, fileName), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            // IllegalStateException (нет внешнего хранилища), SecurityException и т.п.
            toast(R.string.download_failed)
        }
    }

    private fun toast(resId: Int) {
        Toast.makeText(activity, resId, Toast.LENGTH_SHORT).show()
    }
}
