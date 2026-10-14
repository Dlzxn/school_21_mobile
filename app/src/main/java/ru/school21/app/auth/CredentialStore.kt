package ru.school21.app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Credentials(val login: String, val password: String)

/**
 * Логин и пароль для автовхода. Шифруются AES-256-GCM ключом из Android Keystore:
 * ключ не извлекается из устройства, без него зашифрованные данные бесполезны.
 * Бэкап приложения отключён в манифесте, так что данные не уходят в облако.
 */
class CredentialStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasCredentials(): Boolean = prefs.contains(KEY_LOGIN) && prefs.contains(KEY_PASSWORD)

    fun load(): Credentials? {
        val login = prefs.getString(KEY_LOGIN, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return try {
            Credentials(decrypt(login), decrypt(password))
        } catch (_: Exception) {
            // Ключ Keystore потерян (сброс блокировки экрана, восстановление и т.п.) — данные не прочитать.
            clear()
            null
        }
    }

    fun save(credentials: Credentials) {
        prefs.edit {
            putString(KEY_LOGIN, encrypt(credentials.login))
            putString(KEY_PASSWORD, encrypt(credentials.password))
        }
    }

    fun clear() {
        prefs.edit { remove(KEY_LOGIN); remove(KEY_PASSWORD) }
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        // Формат: IV (12 байт) + шифротекст с тегом GCM.
        val bytes = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyStore().getKey(KEY_ALIAS, null) as SecretKey,
            GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_BYTES),
        )
        return String(cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val PREFS_NAME = "auto_login"
        const val KEY_LOGIN = "login"
        const val KEY_PASSWORD = "password"
        const val KEY_ALIAS = "auto_login_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}
