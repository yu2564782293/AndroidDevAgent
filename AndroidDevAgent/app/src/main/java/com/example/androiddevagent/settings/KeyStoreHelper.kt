package com.example.androiddevagent.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyStoreHelper @Inject constructor() {

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())

        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val payload = ByteBuffer.allocate(1 + iv.size + cipherText.size)
            .put(iv.size.toByte())
            .put(iv)
            .put(cipherText)
            .array()

        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return ""

        val payload = Base64.decode(encryptedText, Base64.NO_WRAP)
        require(payload.isNotEmpty()) { "Encrypted payload is empty." }

        val ivSize = payload[0].toInt()
        require(ivSize > 0 && payload.size > 1 + ivSize) { "Encrypted payload is invalid." }

        val iv = payload.copyOfRange(1, 1 + ivSize)
        val cipherText = payload.copyOfRange(1 + ivSize, payload.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply {
            load(null)
        }

        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "android_dev_agent_llm_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
