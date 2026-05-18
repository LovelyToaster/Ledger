package com.verdantgem.ledger.data.remote

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoManager @Inject constructor() {

    private val random = SecureRandom()

    fun encrypt(plainText: String, password: String): String {
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val key = deriveKey(password, salt)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)

        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, AES), GCMParameterSpec(TAG_SIZE, iv))

        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val result = ByteArray(SALT_SIZE + IV_SIZE + cipherText.size)
        System.arraycopy(salt, 0, result, 0, SALT_SIZE)
        System.arraycopy(iv, 0, result, IV_OFFSET, IV_SIZE)
        System.arraycopy(cipherText, 0, result, CIPHER_OFFSET, cipherText.size)

        return Base64.getEncoder().encodeToString(result)
    }

    fun decrypt(cipherText: String, password: String): String {
        val raw = Base64.getDecoder().decode(cipherText)
        if (raw.size < SALT_SIZE + IV_SIZE + MIN_CIPHER_SIZE) throw IllegalArgumentException("Invalid cipher text")

        val salt = raw.copyOfRange(0, SALT_SIZE)
        val iv = raw.copyOfRange(SALT_SIZE, SALT_SIZE + IV_SIZE)
        val encrypted = raw.copyOfRange(SALT_SIZE + IV_SIZE, raw.size)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, AES), GCMParameterSpec(TAG_SIZE, iv))

        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGO)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_SIZE)
        return factory.generateSecret(spec).encoded
    }

    companion object {
        private const val AES = "AES"
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 100_000
        private const val KEY_SIZE = 256
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12
        private const val TAG_SIZE = 128
        private const val IV_OFFSET = SALT_SIZE
        private const val CIPHER_OFFSET = SALT_SIZE + IV_SIZE
        private const val MIN_CIPHER_SIZE = 1
    }
}
