package ru.school21.app.web

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

/**
 * Выбор файлов для `<input type="file">` через ActivityResult API. Если поле принимает картинки,
 * в выборе есть и «Камера»: снимок пишется во временный файл приложения через FileProvider.
 * Разрешение CAMERA не нужно — съёмку делает системное приложение камеры.
 * Создавать в Activity.onCreate: регистрация лаунчера допустима только до STARTED.
 */
class FileChooser(private val activity: ComponentActivity) {

    private var pendingCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPhoto: Pair<File, Uri>? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Колбэк обязан быть вызван всегда (null при отмене), иначе input перестанет открываться.
        pendingCallback?.onReceiveValue(parseResult(result.resultCode, result.data))
        pendingCallback = null
        pendingPhoto = null
    }

    fun show(callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
        pendingCallback?.onReceiveValue(null)
        pendingCallback = callback

        val mimeTypes = resolveMimeTypes(params.acceptTypes)
        val pickIntent = Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mimeTypes.singleOrNull() ?: "*/*")
        if (mimeTypes.size > 1) pickIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
        if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
            pickIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        val acceptsImages = mimeTypes.isEmpty() || mimeTypes.any { it.startsWith("image/") }
        val cameraIntent = if (acceptsImages) createCameraIntent() else null
        val intent = when {
            cameraIntent == null -> pickIntent
            // <input capture> — сразу камера, без выбора.
            params.isCaptureEnabled -> cameraIntent
            else -> Intent.createChooser(pickIntent, null)
                .putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
        }

        return try {
            launcher.launch(intent)
            true
        } catch (_: ActivityNotFoundException) {
            // false — WebView сам отменит выбор; колбэк в этом случае вызывать нельзя.
            pendingCallback = null
            pendingPhoto = null
            false
        }
    }

    private fun createCameraIntent(): Intent? {
        val dir = File(activity.cacheDir, "camera").apply { mkdirs() }
        // Старые снимки давно загружены на сайт — не копим их (свежие не трогаем: форма может быть не отправлена).
        val dayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        dir.listFiles()?.filter { it.lastModified() < dayAgo }?.forEach { it.delete() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        pendingPhoto = file to uri
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** accept может содержать и MIME-типы, и расширения (".pdf"); приводим всё к MIME. */
    private fun resolveMimeTypes(acceptTypes: Array<String>): List<String> {
        val types = acceptTypes
            .flatMap { it.split(',') }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .map { type ->
                if (type.startsWith('.')) {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.drop(1)) ?: "*/*"
                } else {
                    type
                }
            }
            .distinct()
        return if ("*/*" in types) emptyList() else types
    }

    private fun parseResult(resultCode: Int, data: Intent?): Array<Uri>? {
        if (resultCode != Activity.RESULT_OK) return null
        val clip = data?.clipData
        if (clip != null && clip.itemCount > 0) {
            return Array(clip.itemCount) { clip.getItemAt(it).uri }
        }
        data?.data?.let { return arrayOf(it) }
        // Камера не возвращает uri в data — снимок лежит в нашем файле.
        return pendingPhoto?.takeIf { (file, _) -> file.length() > 0 }?.let { (_, uri) -> arrayOf(uri) }
    }
}
