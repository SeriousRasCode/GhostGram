package com.example.data.summary

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class GeminiSettings(val apiKey: String = "", val model: String = "gemini-3.8-flash", val language: String = "Auto")

/** Personal-device credentials, encrypted with Android Keystore and excluded from backups. */
class GeminiSettingsStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "gemini-settings.bin"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun load(): GeminiSettings = synchronized(LOCK) {
        runCatching {
            if (!file.baseFile.exists()) return@synchronized GeminiSettings()
            val bytes = file.readFully()
            require(bytes.size in 29..16_384)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            GeminiSettings(json.optString("key"), json.optString("model", "gemini-3.8-flash"), json.optString("language", "Auto"))
        }.getOrDefault(GeminiSettings())
    }

    fun save(settings: GeminiSettings) = synchronized(LOCK) {
        require(settings.apiKey.length <= 256 && settings.apiKey.none { it.isWhitespace() })
        require(GeminiSummaryProtocol.validModel(settings.model))
        require(settings.language in listOf("Auto", "Amharic", "English"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val json = JSONObject().put("key", settings.apiKey).put("model", settings.model).put("language", settings.language)
        val bytes = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
        GeminiMessageSummarizer.clearCache()
    }

    companion object { private const val ALIAS = "ghostgram-personal-gemini"; private val LOCK = Any() }
}
