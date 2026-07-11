package edu.hust.medicalaichatbot.utils

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

class CryptoManager(context: Context) {
    private val aead: Aead

    init {
        // Initialize Tink's configuration
        AeadConfig.register()
        
        // Setup AndroidKeysetManager to automatically manage key storage inside Android KeyStore
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
            .withKeyTemplate(com.google.crypto.tink.aead.AesGcmKeyManager.aes256GcmTemplate())
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle

        aead = keysetHandle.getPrimitive(Aead::class.java)
    }

    fun encrypt(plainText: String): String {
        val cipherText = aead.encrypt(plainText.toByteArray(Charsets.UTF_8), null)
        return Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    fun decrypt(encryptedText: String): String {
        return try {
            val cipherText = Base64.decode(encryptedText, Base64.NO_WRAP)
            val decryptedBytes = aead.decrypt(cipherText, null)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    companion object {
        private const val KEYSET_NAME = "tink_keyset"
        private const val PREF_FILE_NAME = "tink_pref"
        private const val MASTER_KEY_URI = "android-keystore://tink_master_key"
    }
}
