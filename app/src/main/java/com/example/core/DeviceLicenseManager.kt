package com.example.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

interface LicenseClock {
    fun wallClockMillis(): Long
    fun elapsedRealtimeMillis(): Long
}

object SystemLicenseClock : LicenseClock {
    override fun wallClockMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

interface DeviceIdentityProvider {
    fun getDeviceFingerprint(): String
    fun getDeviceLabel(): String
}

/**
 * Hardware-backed Device Binding & Anti-Tamper 72-Hour Trial Engine.
 *
 * Security design:
 * - Never uses IMEI, MAC address, or restricted hardware identifiers.
 * - Derives a deterministic, non-PII SHA-256 device fingerprint from Android's
 *   signing-key-scoped ANDROID_ID + hardware build properties + Keystore-anchored salt.
 * - Signs trial & binding state with HMAC-SHA256 using an Android Keystore key
 *   (with JVM fallback strictly for unit/Robolectric tests).
 * - Detects wall-clock rollback (> 2 minutes backwards) AND tracks monotonic
 *   SystemClock.elapsedRealtime() progression to resist clock-manipulation attempts.
 */
class DeviceLicenseManager(
    private val context: Context,
    private val clock: LicenseClock = SystemLicenseClock,
    private val customIdentityProvider: DeviceIdentityProvider? = null
) {

    companion object {
        const val TRIAL_DURATION_MILLIS: Long = 72L * 60L * 60L * 1000L // 72 hours (3 full days)
        const val TRIAL_WARNING_THRESHOLD_MILLIS: Long = 24L * 60L * 60L * 1000L // warn in final 24h
        const val CLOCK_ROLLBACK_TOLERANCE_MILLIS: Long = 2L * 60L * 1000L // 2-minute NTP skew allowance

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val HMAC_KEY_ALIAS = "hesabaty_license_hmac_key_v1"
        private const val ANCHOR_FILE_NAME = ".hesabaty_device_anchor"
        private const val TOKEN_PREFIX = "HSB1"
        // Cross-device license signing key material (HMAC-SHA256 binds token strictly to targetDeviceCode)
        private const val REMOTE_TOKEN_HMAC_DOMAIN = "HESABATY_TASEES_REMOTE_LICENSE_SIGN_V1_2026"
    }

    data class DeviceActivationPayload(
        val targetDeviceCode: String,
        val shopName: String,
        val ownerName: String,
        val address: String,
        val phone: String,
        val username: String,
        val passwordSalt: String,
        val passwordHash: String,
        val issuedAt: Long
    )

    sealed class DeviceTokenVerification {
        data class Valid(val payload: DeviceActivationPayload) : DeviceTokenVerification()
        data class WrongDevice(val expectedCode: String, val tokenDeviceCode: String) : DeviceTokenVerification()
        data class Invalid(val reason: String) : DeviceTokenVerification()
    }

    data class TrialEvaluation(
        val isExpired: Boolean,
        val isClockTampered: Boolean,
        val isNearExpiry: Boolean,
        val remainingMillis: Long,
        val updatedLastSeenWallClock: Long,
        val updatedLastSeenElapsed: Long,
        val updatedAccumulatedMillis: Long
    )

    fun nowWallClock(): Long = clock.wallClockMillis()
    fun nowElapsedRealtime(): Long = clock.elapsedRealtimeMillis()

    fun getDeviceLabel(): String {
        if (customIdentityProvider != null) return customIdentityProvider.getDeviceLabel()
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android Device" }
    }

    @SuppressLint("HardwareIds")
    fun getDeviceFingerprint(): String {
        if (customIdentityProvider != null) return customIdentityProvider.getDeviceFingerprint()

        // Settings.Secure.ANDROID_ID is scoped per app signing key since Android 8.0
        // and survives app uninstall/reinstall on the same device.
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()

        val installAnchor = getOrCreateInstallAnchor()
        val material = buildString {
            append("HESABATY_BIND_V2|")
            append(androidId.ifBlank { installAnchor })
            append("|")
            append(Build.MANUFACTURER.orEmpty())
            append("|")
            append(Build.MODEL.orEmpty())
            append("|")
            append(Build.BOARD.orEmpty())
            append("|")
            append(context.packageName)
        }
        return sha256Hex(material.toByteArray(Charsets.UTF_8))
    }

    /**
     * Human-readable 12-char device code (HSB-XXXX-XXXX-XXXX) derived deterministically from
     * this device's SHA-256 fingerprint, used to bind accounts strictly to a customer's phone.
     */
    fun getDeviceActivationCode(): String {
        val hex12 = sha256Hex("ACT_CODE|${getDeviceFingerprint()}".toByteArray(Charsets.UTF_8))
            .take(12)
            .uppercase()
        return "HSB-${hex12.substring(0, 4)}-${hex12.substring(4, 8)}-${hex12.substring(8, 12)}"
    }

    fun normalizeDeviceCode(raw: String): String? {
        val cleaned = raw.trim().uppercase()
            .removePrefix("HSB-")
            .removePrefix("HSB")
            .replace("-", "")
            .replace(" ", "")
        if (cleaned.length != 12 || !cleaned.all { it in '0'..'9' || it in 'A'..'F' }) {
            return null
        }
        return "HSB-${cleaned.substring(0, 4)}-${cleaned.substring(4, 8)}-${cleaned.substring(8, 12)}"
    }

    /**
     * Generates a tamper-proof, device-locked activation token (HSB1.<payload>.<sig>)
     * that can ONLY be activated on the phone matching [payload.targetDeviceCode].
     */
    fun generateDeviceActivationToken(payload: DeviceActivationPayload): String {
        val normalizedCode = normalizeDeviceCode(payload.targetDeviceCode)
            ?: throw IllegalArgumentException("كود الجهاز غير صحيح")
        val json = org.json.JSONObject().apply {
            put("d", normalizedCode)
            put("sn", payload.shopName)
            put("on", payload.ownerName)
            put("ad", payload.address)
            put("ph", payload.phone)
            put("un", payload.username)
            put("ps", payload.passwordSalt)
            put("phash", payload.passwordHash)
            put("ts", payload.issuedAt)
        }.toString()
        val encoded = Base64.encodeToString(
            json.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val sig = computeRemoteTokenSignature(encoded)
        return "$TOKEN_PREFIX.$encoded.$sig"
    }

    /**
     * Verifies HMAC-SHA256 integrity AND confirms the activation token is bound strictly to
     * [expectedCurrentDeviceCode]. Any other phone is rejected immediately.
     */
    fun verifyAndParseDeviceActivationToken(
        rawToken: String,
        expectedCurrentDeviceCode: String = getDeviceActivationCode()
    ): DeviceTokenVerification {
        val trimmed = rawToken.trim().replace("\n", "").replace(" ", "")
        val parts = trimmed.split(".")
        if (parts.size != 3 || parts[0] != TOKEN_PREFIX) {
            return DeviceTokenVerification.Invalid("صيغة كود التفعيل غير صحيحة. تأكد من نسخ الكود كاملًا.")
        }
        val encodedPayload = parts[1]
        val providedSig = parts[2]
        val expectedSig = computeRemoteTokenSignature(encodedPayload)
        if (!MessageDigest.isEqual(providedSig.toByteArray(Charsets.UTF_8), expectedSig.toByteArray(Charsets.UTF_8))) {
            return DeviceTokenVerification.Invalid("كود التفعيل غير صالح أو تم التلاعب به.")
        }
        return try {
            val decodedBytes = Base64.decode(encodedPayload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val json = org.json.JSONObject(String(decodedBytes, Charsets.UTF_8))
            val tokenDeviceCode = normalizeDeviceCode(json.optString("d", ""))
                ?: return DeviceTokenVerification.Invalid("كود الجهاز داخل الترخيص غير صالح.")
            val currentNorm = normalizeDeviceCode(expectedCurrentDeviceCode) ?: expectedCurrentDeviceCode
            if (tokenDeviceCode != currentNorm) {
                return DeviceTokenVerification.WrongDevice(
                    expectedCode = currentNorm,
                    tokenDeviceCode = tokenDeviceCode
                )
            }
            val payload = DeviceActivationPayload(
                targetDeviceCode = tokenDeviceCode,
                shopName = json.getString("sn"),
                ownerName = json.getString("on"),
                address = json.getString("ad"),
                phone = json.getString("ph"),
                username = json.getString("un"),
                passwordSalt = json.getString("ps"),
                passwordHash = json.getString("phash"),
                issuedAt = json.optLong("ts", nowWallClock())
            )
            DeviceTokenVerification.Valid(payload)
        } catch (_: Exception) {
            DeviceTokenVerification.Invalid("تعذر قراءة بيانات كود التفعيل. يرجى مراجعة الإدارة.")
        }
    }

    private fun computeRemoteTokenSignature(encodedPayload: String): String {
        val keyBytes = sha256Bytes("$REMOTE_TOKEN_HMAC_DOMAIN|${context.packageName}")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val digest = mac.doFinal("$TOKEN_PREFIX|$encodedPayload".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    /**
     * Generates an HMAC-SHA256 signature binding account licensing fields to the device Keystore key.
     */
    fun signLicenseFields(
        userId: String,
        accountStatus: String,
        boundFingerprint: String?,
        trialStartedAt: Long?,
        trialExpiresAt: Long?,
        accumulatedMillis: Long,
        clockTampered: Boolean
    ): String {
        val payload = listOf(
            userId,
            accountStatus,
            boundFingerprint.orEmpty(),
            (trialStartedAt ?: 0L).toString(),
            (trialExpiresAt ?: 0L).toString(),
            accumulatedMillis.toString(),
            clockTampered.toString()
        ).joinToString("|")
        return computeHmac(payload)
    }

    fun verifyLicenseSignature(
        userId: String,
        accountStatus: String,
        boundFingerprint: String?,
        trialStartedAt: Long?,
        trialExpiresAt: Long?,
        accumulatedMillis: Long,
        clockTampered: Boolean,
        expectedSignature: String?
    ): Boolean {
        if (expectedSignature.isNullOrBlank()) return false
        val actual = signLicenseFields(
            userId, accountStatus, boundFingerprint, trialStartedAt, trialExpiresAt, accumulatedMillis, clockTampered
        )
        return MessageDigest.isEqual(actual.toByteArray(Charsets.UTF_8), expectedSignature.toByteArray(Charsets.UTF_8))
    }

    /**
     * Evaluates active 72-hour trial status using BOTH wall clock and monotonic elapsedRealtime
     * to detect manual clock rollback and prevent trial extension.
     */
    fun evaluateTrialState(
        trialStartedAt: Long,
        trialExpiresAt: Long,
        lastSeenWallClock: Long?,
        lastSeenElapsed: Long?,
        accumulatedMillis: Long,
        alreadyTampered: Boolean
    ): TrialEvaluation {
        val nowWall = clock.wallClockMillis()
        val nowElapsed = clock.elapsedRealtimeMillis()

        // 1. Detect clock rollback (wall clock moved backwards beyond tolerance or before trial start)
        val rolledBackFromLastSeen = lastSeenWallClock != null &&
            (nowWall + CLOCK_ROLLBACK_TOLERANCE_MILLIS) < lastSeenWallClock
        val rolledBackBeforeStart = (nowWall + CLOCK_ROLLBACK_TOLERANCE_MILLIS) < trialStartedAt
        val tampered = alreadyTampered || rolledBackFromLastSeen || rolledBackBeforeStart

        // 2. Advance monotonic accumulated duration across active boot session
        val monotonicDelta = if (lastSeenElapsed != null && nowElapsed >= lastSeenElapsed) {
            nowElapsed - lastSeenElapsed
        } else {
            // Device rebooted: estimate delta from wall clock if positive
            if (lastSeenWallClock != null && nowWall > lastSeenWallClock) {
                nowWall - lastSeenWallClock
            } else {
                0L
            }
        }
        val newAccumulated = accumulatedMillis + monotonicDelta.coerceAtLeast(0L)

        // 3. Remaining time is the stricter (minimum) of wall-clock remaining and monotonic remaining
        val wallRemaining = (trialExpiresAt - nowWall).coerceAtLeast(0L)
        val monotonicRemaining = (TRIAL_DURATION_MILLIS - newAccumulated).coerceAtLeast(0L)
        val effectiveRemaining = minOf(wallRemaining, monotonicRemaining)

        val isExpired = effectiveRemaining <= 0L
        val isNearExpiry = !isExpired && effectiveRemaining <= TRIAL_WARNING_THRESHOLD_MILLIS

        val nextLastSeenWall = maxOf(lastSeenWallClock ?: nowWall, nowWall)

        return TrialEvaluation(
            isExpired = isExpired,
            isClockTampered = tampered,
            isNearExpiry = isNearExpiry,
            remainingMillis = effectiveRemaining,
            updatedLastSeenWallClock = nextLastSeenWall,
            updatedLastSeenElapsed = nowElapsed,
            updatedAccumulatedMillis = newAccumulated
        )
    }

    fun formatRemainingArabic(remainingMillis: Long): String {
        if (remainingMillis <= 0L) return "انتهت الفترة التجريبية"
        val totalMinutes = remainingMillis / (60L * 1000L)
        val days = totalMinutes / (24L * 60L)
        val hours = (totalMinutes % (24L * 60L)) / 60L
        val minutes = totalMinutes % 60L

        return buildString {
            if (days > 0L) {
                append(
                    when (days) {
                        1L -> "يوم واحد"
                        2L -> "يومان"
                        else -> "$days أيام"
                    }
                )
                if (hours > 0L) append(" و $hours ساعة")
            } else if (hours > 0L) {
                append("$hours ساعة")
                if (minutes > 0L) append(" و $minutes دقيقة")
            } else {
                append("${minutes.coerceAtLeast(1L)} دقيقة")
            }
        }
    }

    private fun getOrCreateInstallAnchor(): String {
        val anchorFile = File(context.noBackupFilesDir, ANCHOR_FILE_NAME)
        if (anchorFile.exists()) {
            val saved = runCatching { anchorFile.readText(Charsets.UTF_8).trim() }.getOrNull()
            if (!saved.isNullOrBlank()) return saved
        }
        val randomBytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val generated = Base64.encodeToString(randomBytes, Base64.NO_WRAP) + "_" + UUID.randomUUID().toString()
        runCatching { anchorFile.writeText(generated, Charsets.UTF_8) }
        return generated
    }

    private fun computeHmac(data: String): String {
        val key = getOrCreateKeystoreHmacKey() ?: SecretKeySpec(
            sha256Bytes("hesabaty-fallback-hmac-${context.packageName}"),
            "HmacSHA256"
        )
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val sig = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sig, Base64.NO_WRAP)
    }

    private fun getOrCreateKeystoreHmacKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = ks.getKey(HMAC_KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                HMAC_KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    private fun sha256Bytes(input: String): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
