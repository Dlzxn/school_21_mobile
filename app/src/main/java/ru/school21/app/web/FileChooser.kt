package ru.school21.app.web

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Выбор файлов для `<input type="file">` через ActivityResult API.
 * Создавать в Activity.onCreate: регистрация лаунчера допустима только до STARTED.
 */
class FileChooser(activity: ComponentActivity) {

    private var pendingCallback: ValueCallback<Array<Uri>>? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Колбэк обязан быть вызван всегда (null при отмене), иначе input перестанет открываться.
        pendingCallback?.onReceiveValue(parseResult(result.resultCode, result.data))
        pendingCallback = null
    }

    fun show(callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
        pendingCallback?.onReceiveValue(null)
        pendingCallback = callback

        val mimeTypes = resolveMimeTypes(params.acceptTypes)
        val intent = Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mimeTypes.singleOrNull() ?: "*/*")
        if (mimeTypes.size > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
        if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        return try {
            launcher.launch(intent)
            true
        } catch (_: ActivityNotFoundException) {
            // false — WebView сам отменит выбор; колбэк в этом случае вызывать нельзя.
            pendingCallback = null
            false
        }
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
        if (resultCode != Activity.RESULT_OK || data == null) return null
        val clip = data.clipData
        if (clip != null && clip.itemCount > 0) {
            return Array(clip.itemCount) { clip.getItemAt(it).uri }
        }
        return data.data?.let { arrayOf(it) }
    }
}
