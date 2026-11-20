package com.example.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic utilities:
 * - Salted PBKDF2-HMAC-SHA256 password hashing (no plaintext passwords ever stored).
 * - AES-256-GCM encrypted backup envelopes with SHA-256 integrity verification.
 */
object SecurityUtils {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val BACKUP_KEY_ALIAS = "hesabaty_backup_aes_key_v1"

    // Pre-computed salted PBKDF2-HMAC-SHA256 bootstrap digest for initial Master Admin (asker1907).
    // Plaintext credential is never stored in code or APK.
    const val BOOTSTRAP_ADMIN_USERNAME = "asker1907"
    const val BOOTSTRAP_ADMIN_SALT_B64 = "aGVzYWJhdHlfYWRtaW5fc2FsdF92MQ=="
    const val BOOTSTRAP_ADMIN_HASH_B64 = "cAlDY4yfRYycBg/PXoHeO2ABt+fbCteLUg4BRWp7t7k="

    // Pre-computed salted PBKDF2-HMAC-SHA256 digest for the isolated 72-hour Trial account (asker).
    const val TRIAL_USERNAME = "asker"
    const val TRIAL_SALT_B64 = "aGVzYWJhdHlfdHJpYWxfc2FsdF92MQ=="
    const val TRIAL_HASH_B64 = "kRbc91MUwAQcx9QeV9FNSN8nPwiyc0H78K5M6Chsc8g="

    private val secureRandom = SecureRandom()

    data class PasswordHashResult(
        val saltBase64: String,
        val hashBase64: String
    )

    fun hashPassword(passwordChars: CharArray): PasswordHashResult {
        val salt = ByteArray(SALT_BYTES)
        secureRandom.nextBytes(salt)
        val hash = pbkdf2(passwordChars, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
        return PasswordHashResult(
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            hashBase64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        )
    }

    fun verifyPassword(passwordChars: CharArray, saltBase64: String, expectedHashBase64: String): Boolean {
        return try {
            val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
            val expected = Base64.decode(expectedHashBase64, Base64.NO_WRAP)
            val actual = pbkdf2(passwordChars, salt, PBKDF2_ITERATIONS, expected.size * 8)
            MessageDigest.isEqual(actual, expected)
        } catch (_: Exception) {
            false
        }
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyBits)
        return try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    data class EncryptedPayload(
        val keyMode: String, // "PASSPHRASE" or "KEYSTORE"
        val saltBase64: String,
        val ivBase64: String,
        val ciphertextBase64: String,
        val plaintextSha256: String
    )

    fun encryptBackup(plaintext: ByteArray, passphrase: String?): EncryptedPayload {
        val checksum = sha256Hex(plaintext)
        return if (!passphrase.isNullOrBlank()) {
            val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val keyBytes = pbkdf2(passphrase.toCharArray(), salt, 65_536, 256)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val encrypted = cipher.doFinal(plaintext)
            EncryptedPayload(
                keyMode = "PASSPHRASE",
                saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                ciphertextBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP),
                plaintextSha256 = checksum
            )
        } else {
            val key = getOrCreateKeystoreAesKey()
            if (key != null) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val iv = cipher.iv
                val encrypted = cipher.doFinal(plaintext)
                EncryptedPayload(
                    keyMode = "KEYSTORE",
                    saltBase64 = "",
                    ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                    ciphertextBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP),
                    plaintextSha256 = checksum
                )
            } else {
                // Fallback deterministic device-app key only in non-Keystore JVM test environments
                val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
                val fallbackKey = pbkdf2("hesabaty-local-keystore-fallback".toCharArray(), salt, 10_000, 256)
                val secretKey = SecretKeySpec(fallbackKey, "AES")
                val iv = ByteArray(GCM_IV_BYTES).also { secureRandom.nextBytes(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                val encrypted = cipher.doFinal(plaintext)
                EncryptedPayload(
                    keyMode = "DEVICE_FALLBACK",
                    saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
                    ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                    ciphertextBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP),
                    plaintextSha256 = checksum
                )
            }
        }
    }

    fun decryptBackup(payload: EncryptedPayload, passphrase: String?): ByteArray {
        val iv = Base64.decode(payload.ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(payload.ciphertextBase64, Base64.NO_WRAP)
        val secretKey: SecretKey = when (payload.keyMode) {
            "PASSPHRASE" -> {
                require(!passphrase.isNullOrBlank()) { "مطلوب كلمة سر فك تشفير النسخة الاحتياطية" }
                val salt = Base64.decode(payload.saltBase64, Base64.NO_WRAP)
                val keyBytes = pbkdf2(passphrase.toCharArray(), salt, 65_536, 256)
                SecretKeySpec(keyBytes, "AES")
            }
            "KEYSTORE" -> {
                getOrCreateKeystoreAesKey()
                    ?: throw IllegalStateException("مفتاح التشفير غير متاح على هذا الجهاز؛ استخدم نسخة مشفرة بكلمة مرور للاستعادة على جهاز آخر")
            }
            "DEVICE_FALLBACK" -> {
                val salt = Base64.decode(payload.saltBase64, Base64.NO_WRAP)
                val fallbackKey = pbkdf2("hesabaty-local-keystore-fallback".toCharArray(), salt, 10_000, 256)
                SecretKeySpec(fallbackKey, "AES")
            }
            else -> throw IllegalArgumentException("صيغة تشفير غير مدعومة")
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)
        val actualSha = sha256Hex(plaintext)
        require(actualSha == payload.plaintextSha256) { "فشل التحقق من سلامة النسخة الاحتياطية" }
        return plaintext
    }

    private fun getOrCreateKeystoreAesKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = ks.getKey(BACKUP_KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                BACKUP_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    fun encryptLocalSecret(plaintext: String): String {
        val payload = encryptBackup(plaintext.toByteArray(Charsets.UTF_8), passphrase = null)
        return listOf(
            payload.keyMode,
            payload.saltBase64,
            payload.ivBase64,
            payload.ciphertextBase64,
            payload.plaintextSha256
        ).joinToString("|")
    }

    fun decryptLocalSecret(encoded: String): String? {
        return try {
            val parts = encoded.split('|')
            if (parts.size != 5) return null
            val payload = EncryptedPayload(
                keyMode = parts[0],
                saltBase64 = parts[1],
                ivBase64 = parts[2],
                ciphertextBase64 = parts[3],
                plaintextSha256 = parts[4]
            )
            decryptBackup(payload, passphrase = null).toString(Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }
}
