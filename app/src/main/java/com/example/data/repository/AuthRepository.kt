package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.DeviceLicenseManager
import com.example.core.PhoneUtils
import com.example.core.SecurityUtils
import com.example.data.local.AccountStatuses
import com.example.data.local.AppSettingEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.DeviceTrialRegistryEntity
import com.example.data.local.HesabatyDatabase
import com.example.data.local.TenantEntity
import com.example.data.local.UserEntity
import com.example.data.local.UserRoles
import kotlinx.coroutines.flow.Flow
import java.util.UUID

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
    data class TrialExpired(
        val isClockTampered: Boolean,
        val trialStartedAt: Long?,
        val trialExpiresAt: Long?
    ) : AuthResult<Nothing>()
}

data class TrialStatusInfo(
    val isTrial: Boolean,
    val startedAt: Long?,
    val expiresAt: Long?,
    val remainingMillis: Long,
    val isNearExpiry: Boolean,
    val isExpired: Boolean,
    val isClockTampered: Boolean
)

data class AuthenticatedSession(
    val user: UserEntity,
    val tenant: TenantEntity?,
    val trialInfo: TrialStatusInfo? = null
)

class AuthRepository(
    private val db: HesabatyDatabase,
    private val licenseManager: DeviceLicenseManager
) {

    companion object {
        const val TRIAL_TENANT_ID = "tenant_trial_sandbox"
        const val TRIAL_USER_ID = "trial-user-asker"
        const val DEVICE_MISMATCH_MESSAGE = "هذا الحساب مرتبط بجهاز آخر ولا يمكن استخدامه على هذا الجهاز."
        const val TRIAL_EXPIRED_MESSAGE =
            "انتهت الفترة التجريبية لبرنامج حساباتي. يسعدنا استمرارك معنا، برجاء التواصل مع الدعم الفني لتفعيل البرنامج."
    }

    private val userDao = db.userDao()
    private val tenantDao = db.tenantDao()
    private val systemDao = db.systemDao()
    private val deviceTrialDao = db.deviceTrialDao()

    /**
     * Securely seeds the initial Master Admin account on first run using pre-hashed
     * PBKDF2-HMAC-SHA256 salt + digest (no plaintext password in code or APK).
     */
    suspend fun ensureMasterAdminSeeded() {
        val existing = userDao.getMasterAdmin()
        if (existing == null) {
            val now = licenseManager.nowWallClock()
            val admin = UserEntity(
                id = "master-admin-root",
                tenantId = null,
                username = SecurityUtils.BOOTSTRAP_ADMIN_USERNAME,
                displayName = "المدير الرئيسي للنظام",
                role = UserRoles.MASTER_ADMIN,
                passwordSalt = SecurityUtils.BOOTSTRAP_ADMIN_SALT_B64,
                passwordHash = SecurityUtils.BOOTSTRAP_ADMIN_HASH_B64,
                isActive = true,
                mustChangePassword = false,
                createdAt = now,
                accountStatus = AccountStatuses.ACTIVE,
                isTrialAccount = false
            )
            userDao.upsertUser(admin)
        }
    }

    /**
     * Ensures the isolated 72-hour trial account ("asker") and its sandbox tenant exist.
     * IMPORTANT: Does NOT start the 72-hour timer during seeding — the countdown starts
     * strictly upon first actual activation/login on the device.
     */
    suspend fun ensureTrialAccountSeeded(): Pair<TenantEntity, UserEntity> {
        val now = licenseManager.nowWallClock()
        val existingTenant = tenantDao.getTenantById(TRIAL_TENANT_ID)
        val tenant = existingTenant ?: TenantEntity(
            id = TRIAL_TENANT_ID,
            shopName = "حساب تجريبي — حساباتي",
            ownerName = "مستخدم تجريبي",
            address = "بيئة تجربة معزولة (3 أيام)",
            phone = PhoneUtils.SUPPORT_PHONE_LOCAL,
            whatsappPhone = PhoneUtils.SUPPORT_PHONE_LOCAL,
            invoiceFooter = "نسخة تجريبية من نظام حساباتي — تأسيس لصناعة البرمجيات",
            isActive = true,
            createdAt = now,
            updatedAt = now
        ).also {
            tenantDao.upsertTenant(it)
            systemDao.upsertAppSettings(
                AppSettingEntity(
                    tenantId = TRIAL_TENANT_ID,
                    darkMode = false,
                    invoiceSignatureName = "الحساب التجريبي",
                    updatedAt = now
                )
            )
        }

        val existingUser = userDao.getById(TRIAL_USER_ID) ?: userDao.getByUsername(SecurityUtils.TRIAL_USERNAME)
        val user = existingUser ?: UserEntity(
            id = TRIAL_USER_ID,
            tenantId = TRIAL_TENANT_ID,
            username = SecurityUtils.TRIAL_USERNAME,
            displayName = "الحساب التجريبي (72 ساعة)",
            role = UserRoles.SHOP_OWNER,
            passwordSalt = SecurityUtils.TRIAL_SALT_B64,
            passwordHash = SecurityUtils.TRIAL_HASH_B64,
            isActive = true,
            mustChangePassword = false,
            createdAt = now,
            accountStatus = AccountStatuses.TRIAL,
            isTrialAccount = true,
            trialStartedAt = null,
            trialExpiresAt = null
        ).also {
            userDao.upsertUser(it)
        }
        return tenant to user
    }

    /**
     * One-tap activation & login for the isolated 72-hour trial account ("asker").
     * Does not require contacting support; starts the 72-hour clock on first actual activation.
     */
    suspend fun activateAndLoginTrial(): AuthResult<AuthenticatedSession> {
        ensureMasterAdminSeeded()
        val (tenant, user) = ensureTrialAccountSeeded()
        return evaluateAndProceedTrialLogin(user, tenant, isRestore = false)
    }

    suspend fun login(username: String, password: String): AuthResult<AuthenticatedSession> {
        ensureMasterAdminSeeded()
        ensureTrialAccountSeeded()

        val cleanUser = username.trim()
        if (cleanUser.isEmpty() || password.isEmpty()) {
            return AuthResult.Error("يرجى إدخال اسم المستخدم وكلمة المرور")
        }
        val user = userDao.getByUsername(cleanUser)
            ?: return AuthResult.Error("اسم المستخدم أو كلمة المرور غير صحيحة")

        val valid = SecurityUtils.verifyPassword(password.toCharArray(), user.passwordSalt, user.passwordHash)
        if (!valid) {
            return AuthResult.Error("اسم المستخدم أو كلمة المرور غير صحيحة")
        }

        // Trial account path (isolated 72-hour sandbox with device binding & anti-tamper clock)
        if (user.isTrialAccount) {
            val tenant = user.tenantId?.let { tenantDao.getTenantById(it) }
                ?: ensureTrialAccountSeeded().first
            return evaluateAndProceedTrialLogin(user, tenant, isRestore = false)
        }

        // Regular & Master Admin accounts
        if (user.accountStatus == AccountStatuses.SUSPENDED || !user.isActive) {
            return AuthResult.Error("تم إيقاف هذا الحساب. يرجى مراجعة الإدارة أو الدعم الفني.")
        }
        if (user.accountStatus == AccountStatuses.EXPIRED) {
            return AuthResult.Error("انتهت صلاحية هذا الحساب. يرجى التواصل مع الدعم الفني للتجديد.")
        }

        val tenant = user.tenantId?.let { tid ->
            val t = tenantDao.getTenantById(tid)
            if (t == null || !t.isActive) {
                return AuthResult.Error("حساب المحل معطّل حاليًا. يرجى التواصل مع الإدارة.")
            }
            t
        }

        val now = licenseManager.nowWallClock()

        // Device Binding enforcement for all non-admin accounts (SHOP_OWNER / STAFF)
        var userToSave = user.copy(lastLoginAt = now)
        var didAutoBind = false
        if (user.role != UserRoles.MASTER_ADMIN) {
            val currentFp = licenseManager.getDeviceFingerprint()
            val currentLabel = licenseManager.getDeviceLabel()

            if (!user.boundDeviceFingerprint.isNullOrBlank()) {
                if (user.boundDeviceFingerprint != currentFp) {
                    return AuthResult.Error(DEVICE_MISMATCH_MESSAGE)
                }
            } else {
                // First login after creation (unbound) or after Admin unbound device for transfer
                val sig = licenseManager.signLicenseFields(
                    userId = user.id,
                    accountStatus = user.accountStatus,
                    boundFingerprint = currentFp,
                    trialStartedAt = user.trialStartedAt,
                    trialExpiresAt = user.trialExpiresAt,
                    accumulatedMillis = user.accumulatedTrialMillis,
                    clockTampered = user.clockTampered
                )
                userToSave = userToSave.copy(
                    boundDeviceFingerprint = currentFp,
                    boundDeviceLabel = currentLabel,
                    deviceBoundAt = now,
                    licenseSignature = sig
                )
                didAutoBind = true
            }
        }

        db.withTransaction {
            userDao.upsertUser(userToSave)
            systemDao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = user.tenantId,
                    actorUserId = user.id,
                    actorUsername = user.username,
                    action = if (didAutoBind) "LOGIN_AND_BIND_DEVICE" else "LOGIN",
                    entityType = "USER",
                    entityId = user.id,
                    details = if (didAutoBind) {
                        "تسجيل دخول وربط الحساب تلقائيًا بالجهاز: ${userToSave.boundDeviceLabel}"
                    } else {
                        "تسجيل دخول ناجح (${user.role})"
                    },
                    createdAt = now
                )
            )
        }
        return AuthResult.Success(AuthenticatedSession(userToSave, tenant, trialInfo = null))
    }

    /**
     * Re-validates an active/restored session against device binding, suspension,
     * 72-hour trial expiration, and clock tampering.
     */
    suspend fun validateOrRestoreSession(userId: String): AuthResult<AuthenticatedSession> {
        ensureMasterAdminSeeded()
        val user = userDao.getById(userId)
            ?: return AuthResult.Error("انتهت الجلسة، يرجى تسجيل الدخول مرة أخرى")

        if (user.isTrialAccount) {
            val tenant = user.tenantId?.let { tenantDao.getTenantById(it) }
                ?: return AuthResult.Error("بيانات الحساب التجريبي غير مكتملة")
            return evaluateAndProceedTrialLogin(user, tenant, isRestore = true)
        }

        if (user.accountStatus == AccountStatuses.SUSPENDED || !user.isActive) {
            return AuthResult.Error("تم إيقاف هذا الحساب. يرجى التواصل مع الإدارة.")
        }
        if (user.accountStatus == AccountStatuses.EXPIRED) {
            return AuthResult.Error("انتهت صلاحية هذا الحساب.")
        }

        if (user.role != UserRoles.MASTER_ADMIN) {
            val currentFp = licenseManager.getDeviceFingerprint()
            if (!user.boundDeviceFingerprint.isNullOrBlank() && user.boundDeviceFingerprint != currentFp) {
                return AuthResult.Error(DEVICE_MISMATCH_MESSAGE)
            }
        }

        val tenant = user.tenantId?.let { tid ->
            val t = tenantDao.getTenantById(tid)
            if (t == null || !t.isActive) return AuthResult.Error("حساب المحل معطّل حاليًا.")
            t
        }
        return AuthResult.Success(AuthenticatedSession(user, tenant, trialInfo = null))
    }

    /**
     * Backward-compatible convenience wrapper used by older callers/tests.
     */
    suspend fun restoreSession(userId: String): AuthenticatedSession? {
        return when (val res = validateOrRestoreSession(userId)) {
            is AuthResult.Success -> res.data
            else -> null
        }
    }

    private suspend fun evaluateAndProceedTrialLogin(
        user: UserEntity,
        tenant: TenantEntity,
        isRestore: Boolean
    ): AuthResult<AuthenticatedSession> {
        if (user.accountStatus == AccountStatuses.SUSPENDED || !user.isActive || !tenant.isActive) {
            return AuthResult.Error("تم إيقاف الحساب التجريبي من قِبل الإدارة. يرجى التواصل مع الدعم الفني.")
        }

        val currentFp = licenseManager.getDeviceFingerprint()
        val currentLabel = licenseManager.getDeviceLabel()
        val nowWall = licenseManager.nowWallClock()
        val nowElapsed = licenseManager.nowElapsedRealtime()

        // Enforce single-device binding on the trial account too
        if (!user.boundDeviceFingerprint.isNullOrBlank() && user.boundDeviceFingerprint != currentFp) {
            return AuthResult.Error(DEVICE_MISMATCH_MESSAGE)
        }

        val registry = deviceTrialDao.getByDeviceFingerprint(currentFp)

        // Detect tampering on existing stored signatures
        var detectedTamper = user.clockTampered || (registry?.clockTampered == true)
        if (user.trialStartedAt != null && user.licenseSignature != null) {
            val userSigValid = licenseManager.verifyLicenseSignature(
                userId = user.id,
                accountStatus = user.accountStatus,
                boundFingerprint = user.boundDeviceFingerprint,
                trialStartedAt = user.trialStartedAt,
                trialExpiresAt = user.trialExpiresAt,
                accumulatedMillis = user.accumulatedTrialMillis,
                clockTampered = user.clockTampered,
                expectedSignature = user.licenseSignature
            )
            if (!userSigValid) {
                detectedTamper = true
            }
        }
        if (registry != null) {
            val regSigValid = licenseManager.verifyLicenseSignature(
                userId = "DEVICE_TRIAL_REGISTRY",
                accountStatus = if (registry.isRevokedOrExpired) AccountStatuses.EXPIRED else AccountStatuses.TRIAL,
                boundFingerprint = registry.deviceFingerprint,
                trialStartedAt = registry.trialStartedAt,
                trialExpiresAt = registry.trialExpiresAt,
                accumulatedMillis = registry.accumulatedTrialMillis,
                clockTampered = registry.clockTampered,
                expectedSignature = registry.signatureHmac
            )
            if (!regSigValid) {
                detectedTamper = true
            }
        }

        val isFirstActivation = user.trialStartedAt == null && registry == null

        // Determine authoritative start & expiry timestamps (earliest wins if both exist)
        val startedAt = when {
            user.trialStartedAt != null && registry != null -> minOf(user.trialStartedAt, registry.trialStartedAt)
            user.trialStartedAt != null -> user.trialStartedAt
            registry != null -> registry.trialStartedAt
            else -> nowWall
        }
        val expiresAt = startedAt + DeviceLicenseManager.TRIAL_DURATION_MILLIS

        val lastSeenWall = listOfNotNull(user.lastSeenWallClock, registry?.lastSeenWallClock).maxOrNull()
        val lastSeenElapsed = user.lastSeenElapsedRealtime ?: registry?.lastSeenElapsedRealtime
        val accumulatedBefore = maxOf(user.accumulatedTrialMillis, registry?.accumulatedTrialMillis ?: 0L)

        val eval = licenseManager.evaluateTrialState(
            trialStartedAt = startedAt,
            trialExpiresAt = expiresAt,
            lastSeenWallClock = if (isFirstActivation) nowWall else lastSeenWall,
            lastSeenElapsed = if (isFirstActivation) nowElapsed else lastSeenElapsed,
            accumulatedMillis = accumulatedBefore,
            alreadyTampered = detectedTamper
        )

        val isBlocked = eval.isExpired ||
            eval.isClockTampered ||
            user.accountStatus == AccountStatuses.EXPIRED ||
            (registry?.isRevokedOrExpired == true)

        val nextStatus = if (isBlocked) AccountStatuses.EXPIRED else AccountStatuses.TRIAL

        val newUserSig = licenseManager.signLicenseFields(
            userId = user.id,
            accountStatus = nextStatus,
            boundFingerprint = currentFp,
            trialStartedAt = startedAt,
            trialExpiresAt = expiresAt,
            accumulatedMillis = eval.updatedAccumulatedMillis,
            clockTampered = eval.isClockTampered
        )

        val updatedUser = user.copy(
            accountStatus = nextStatus,
            boundDeviceFingerprint = currentFp,
            boundDeviceLabel = currentLabel,
            deviceBoundAt = user.deviceBoundAt ?: nowWall,
            trialStartedAt = startedAt,
            trialExpiresAt = expiresAt,
            lastSeenWallClock = eval.updatedLastSeenWallClock,
            lastSeenElapsedRealtime = eval.updatedLastSeenElapsed,
            accumulatedTrialMillis = eval.updatedAccumulatedMillis,
            clockTampered = eval.isClockTampered,
            licenseSignature = newUserSig,
            lastLoginAt = if (isRestore) user.lastLoginAt else nowWall
        )

        val newRegSig = licenseManager.signLicenseFields(
            userId = "DEVICE_TRIAL_REGISTRY",
            accountStatus = nextStatus,
            boundFingerprint = currentFp,
            trialStartedAt = startedAt,
            trialExpiresAt = expiresAt,
            accumulatedMillis = eval.updatedAccumulatedMillis,
            clockTampered = eval.isClockTampered
        )

        val updatedRegistry = DeviceTrialRegistryEntity(
            deviceFingerprint = currentFp,
            deviceLabel = currentLabel,
            trialStartedAt = startedAt,
            trialExpiresAt = expiresAt,
            lastSeenWallClock = eval.updatedLastSeenWallClock,
            lastSeenElapsedRealtime = eval.updatedLastSeenElapsed,
            accumulatedTrialMillis = eval.updatedAccumulatedMillis,
            clockTampered = eval.isClockTampered,
            isRevokedOrExpired = isBlocked,
            signatureHmac = newRegSig
        )

        db.withTransaction {
            userDao.upsertUser(updatedUser)
            deviceTrialDao.upsertRecord(updatedRegistry)
            if (!isRestore || isBlocked) {
                val action = when {
                    eval.isClockTampered -> "TRIAL_CLOCK_TAMPER_BLOCKED"
                    isBlocked -> "TRIAL_EXPIRED_BLOCKED"
                    isFirstActivation -> "TRIAL_FIRST_ACTIVATION"
                    else -> "TRIAL_LOGIN"
                }
                val details = when {
                    eval.isClockTampered -> "تم رصد تلاعب في وقت الجهاز وإيقاف الحساب التجريبي"
                    isBlocked -> "انتهت مدة الحساب التجريبي (72 ساعة)"
                    isFirstActivation -> "بدء الفترة التجريبية (72 ساعة) على الجهاز: $currentLabel"
                    else -> "دخول إلى الحساب التجريبي — المتبقي: ${licenseManager.formatRemainingArabic(eval.remainingMillis)}"
                }
                systemDao.insertAuditLog(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        tenantId = tenant.id,
                        actorUserId = updatedUser.id,
                        actorUsername = updatedUser.username,
                        action = action,
                        entityType = "TRIAL_LICENSE",
                        entityId = updatedUser.id,
                        details = details,
                        createdAt = nowWall
                    )
                )
            }
        }

        if (isBlocked) {
            return AuthResult.TrialExpired(
                isClockTampered = eval.isClockTampered,
                trialStartedAt = startedAt,
                trialExpiresAt = expiresAt
            )
        }

        val trialInfo = TrialStatusInfo(
            isTrial = true,
            startedAt = startedAt,
            expiresAt = expiresAt,
            remainingMillis = eval.remainingMillis,
            isNearExpiry = eval.isNearExpiry,
            isExpired = false,
            isClockTampered = false
        )
        return AuthResult.Success(AuthenticatedSession(updatedUser, tenant, trialInfo = trialInfo))
    }

    /**
     * Master Admin creates a brand-new isolated Shop Tenant + Shop Owner account.
     * Validates all 7 required fields strictly and optionally binds immediately to current device.
     */
    suspend fun createShopAccount(
        actor: UserEntity,
        shopName: String,
        ownerName: String,
        address: String,
        phone: String,
        username: String,
        password: String,
        confirmPassword: String,
        bindToCurrentDevice: Boolean = true
    ): AuthResult<Pair<TenantEntity, UserEntity>> {
        if (actor.role != UserRoles.MASTER_ADMIN) {
            return AuthResult.Error("هذه الصلاحية مخصصة للمدير الرئيسي فقط")
        }
        val sName = shopName.trim()
        val oName = ownerName.trim()
        val addr = address.trim()
        val cleanPhone = PhoneUtils.normalizePhoneDigits(phone)
        val cleanUser = username.trim()

        if (sName.isEmpty()) return AuthResult.Error("اسم المحل مطلوب")
        if (oName.isEmpty()) return AuthResult.Error("اسم صاحب المحل مطلوب")
        if (addr.isEmpty()) return AuthResult.Error("العنوان مطلوب")
        if (!PhoneUtils.isValidPhone(cleanPhone)) return AuthResult.Error("رقم الهاتف غير صحيح (مثال: 01012345678)")
        if (cleanUser.length < 3) return AuthResult.Error("اسم المستخدم يجب ألا يقل عن 3 أحرف")
        if (password.length < 4) return AuthResult.Error("كلمة المرور يجب ألا تقل عن 4 أحرف")
        if (password != confirmPassword) return AuthResult.Error("كلمة المرور وتأكيدها غير متطابقين")

        if (userDao.getByUsername(cleanUser) != null) {
            return AuthResult.Error("اسم المستخدم مستخدم بالفعل، اختر اسمًا آخر")
        }

        val now = licenseManager.nowWallClock()
        val tenantId = "tenant_${UUID.randomUUID()}"
        val tenant = TenantEntity(
            id = tenantId,
            shopName = sName,
            ownerName = oName,
            address = addr,
            phone = cleanPhone,
            whatsappPhone = cleanPhone,
            isActive = true,
            createdAt = now,
            updatedAt = now
        )

        val hashed = SecurityUtils.hashPassword(password.toCharArray())
        val userId = UUID.randomUUID().toString()
        val boundFp = if (bindToCurrentDevice) licenseManager.getDeviceFingerprint() else null
        val boundLabel = if (bindToCurrentDevice) licenseManager.getDeviceLabel() else null
        val boundAt = if (bindToCurrentDevice) now else null
        val signature = licenseManager.signLicenseFields(
            userId = userId,
            accountStatus = AccountStatuses.ACTIVE,
            boundFingerprint = boundFp,
            trialStartedAt = null,
            trialExpiresAt = null,
            accumulatedMillis = 0L,
            clockTampered = false
        )

        val shopOwner = UserEntity(
            id = userId,
            tenantId = tenantId,
            username = cleanUser,
            displayName = oName,
            role = UserRoles.SHOP_OWNER,
            passwordSalt = hashed.saltBase64,
            passwordHash = hashed.hashBase64,
            isActive = true,
            createdAt = now,
            accountStatus = AccountStatuses.ACTIVE,
            isTrialAccount = false,
            boundDeviceFingerprint = boundFp,
            boundDeviceLabel = boundLabel,
            deviceBoundAt = boundAt,
            licenseSignature = signature
        )

        val defaultSettings = AppSettingEntity(
            tenantId = tenantId,
            darkMode = false,
            invoiceSignatureName = oName,
            updatedAt = now
        )

        db.withTransaction {
            tenantDao.upsertTenant(tenant)
            userDao.upsertUser(shopOwner)
            systemDao.upsertAppSettings(defaultSettings)
            systemDao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = tenantId,
                    actorUserId = actor.id,
                    actorUsername = actor.username,
                    action = "CREATE_SHOP_ACCOUNT",
                    entityType = "TENANT",
                    entityId = tenantId,
                    details = buildString {
                        append("إنشاء حساب محل جديد: $sName (المستخدم: $cleanUser)")
                        if (bindToCurrentDevice) append(" — مرتبط بالجهاز: $boundLabel")
                    },
                    createdAt = now
                )
            )
        }
        return AuthResult.Success(tenant to shopOwner)
    }

    // =========================================================================
    // Master Admin — Device Binding & License Lifecycle Control
    // =========================================================================

    suspend fun bindUserToCurrentDevice(actor: UserEntity, targetUserId: String): AuthResult<UserEntity> {
        if (actor.role != UserRoles.MASTER_ADMIN) {
            return AuthResult.Error("هذه الصلاحية مخصصة للمدير الرئيسي فقط")
        }
        val target = userDao.getById(targetUserId) ?: return AuthResult.Error("الحساب غير موجود")
        val now = licenseManager.nowWallClock()
        val fp = licenseManager.getDeviceFingerprint()
        val label = licenseManager.getDeviceLabel()
        val sig = licenseManager.signLicenseFields(
            userId = target.id,
            accountStatus = target.accountStatus,
            boundFingerprint = fp,
            trialStartedAt = target.trialStartedAt,
            trialExpiresAt = target.trialExpiresAt,
            accumulatedMillis = target.accumulatedTrialMillis,
            clockTampered = target.clockTampered
        )
        val updated = target.copy(
            boundDeviceFingerprint = fp,
            boundDeviceLabel = label,
            deviceBoundAt = now,
            licenseSignature = sig
        )
        db.withTransaction {
            userDao.upsertUser(updated)
            systemDao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = target.tenantId,
                    actorUserId = actor.id,
                    actorUsername = actor.username,
                    action = "ADMIN_BIND_DEVICE",
                    entityType = "USER",
                    entityId = target.id,
                    details = "ربط الحساب (${target.username}) بالجهاز: $label",
                    createdAt = now
                )
            )
        }
        return AuthResult.Success(updated)
    }

    suspend fun unbindUserDevice(actor: UserEntity, targetUserId: String): AuthResult<UserEntity> {
        if (actor.role != UserRoles.MASTER_ADMIN) {
            return AuthResult.Error("هذه الصلاحية مخصصة للمدير الرئيسي فقط")
        }
        val target = userDao.getById(targetUserId) ?: return AuthResult.Error("الحساب غير موجود")
        val now = licenseManager.nowWallClock()
        val oldLabel = target.boundDeviceLabel ?: "جهاز غير معروف"
        val sig = licenseManager.signLicenseFields(
            userId = target.id,
            accountStatus = target.accountStatus,
            boundFingerprint = null,
            trialStartedAt = target.trialStartedAt,
            trialExpiresAt = target.trialExpiresAt,
            accumulatedMillis = target.accumulatedTrialMillis,
            clockTampered = target.clockTampered
        )
        val updated = target.copy(
            boundDeviceFingerprint = null,
            boundDeviceLabel = null,
            deviceBoundAt = null,
            licenseSignature = sig
        )
        db.withTransaction {
            userDao.upsertUser(updated)
            systemDao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = target.tenantId,
                    actorUserId = actor.id,
                    actorUsername = actor.username,
                    action = "ADMIN_UNBIND_DEVICE",
                    entityType = "USER",
                    entityId = target.id,
                    details = "فك ارتباط الحساب (${target.username}) عن الجهاز: $oldLabel (جاهز للربط بجهاز جديد)",
                    createdAt = now
                )
            )
        }
        return AuthResult.Success(updated)
    }

    suspend fun setUserAccountStatus(
        actor: UserEntity,
        targetUserId: String,
        newStatus: String
    ): AuthResult<UserEntity> {
        if (actor.role != UserRoles.MASTER_ADMIN) {
            return AuthResult.Error("هذه الصلاحية مخصصة للمدير الرئيسي فقط")
        }
        val target = userDao.getById(targetUserId) ?: return AuthResult.Error("الحساب غير موجود")
        val now = licenseManager.nowWallClock()
        val nextActive = newStatus == AccountStatuses.ACTIVE || newStatus == AccountStatuses.TRIAL
        val sig = licenseManager.signLicenseFields(
            userId = target.id,
            accountStatus = newStatus,
            boundFingerprint = target.boundDeviceFingerprint,
            trialStartedAt = target.trialStartedAt,
            trialExpiresAt = target.trialExpiresAt,
            accumulatedMillis = target.accumulatedTrialMillis,
            clockTampered = if (newStatus == AccountStatuses.ACTIVE) false else target.clockTampered
        )
        val updated = target.copy(
            accountStatus = newStatus,
            isActive = nextActive,
            clockTampered = if (newStatus == AccountStatuses.ACTIVE) false else target.clockTampered,
            licenseSignature = sig
        )
        db.withTransaction {
            userDao.upsertUser(updated)
            target.tenantId?.let { tid ->
                tenantDao.getTenantById(tid)?.let { tenant ->
                    if (!target.isTrialAccount && tenant.isActive != nextActive) {
                        tenantDao.upsertTenant(tenant.copy(isActive = nextActive, updatedAt = now))
                    }
                }
            }
            systemDao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = target.tenantId,
                    actorUserId = actor.id,
                    actorUsername = actor.username,
                    action = "ADMIN_SET_ACCOUNT_STATUS",
                    entityType = "USER",
                    entityId = target.id,
                    details = "تغيير حالة الحساب (${target.username}) إلى: ${AccountStatuses.arabicLabel(newStatus)}",
                    createdAt = now
                )
            )
        }
        return AuthResult.Success(updated)
    }

    /**
     * Master Admin resets/reactivates a trial account (clears expiration & clock tamper lock
     * and removes the prior device trial registry anchor so a fresh 72h period starts on next activation).
     */
    suspend fun reactivateOrResetTrial(actor: UserEntity, targetUserId: String): AuthResult<UserEntity> {
        if (actor.role != UserRoles.MASTER_ADMIN) {
            return AuthResult.Error("هذه الصلاحية مخصصة للمدير الرئيسي فقط")
        }
        val target = userDao.getById(targetUserId) ?: return AuthResult.Error("الحساب غير موجود")
        val now = licenseManager.nowWallClock()
        val oldFp = target.boundDeviceFingerprint
        val currentFp = licenseManager.getDeviceFingerprint()

        val sig = licenseManager.signLicenseFields(
            userId = target.id,
            accountStatus = AccountStatuses.TRIAL,
            boundFingerprint = null,
            trialStartedAt = null,
            trialExpiresAt = null,
            accumulatedMillis = 0L,
            clockTampered = false
        )
        val updated = target.copy(
            accountStatus = AccountStatuses.TRIAL,
            isActive = true,
            boundDeviceFingerprint = null,
            boundDeviceLabel = null,
            deviceBoundAt = null,
            trialStartedAt = null,
            trialExpiresAt = null,
            lastSeenWallClock = null,
            lastSeenElapsedRealtime = null,
            accumulatedTrialMillis = 0L,
            clockTampered = false,
            licenseSignature = sig
        )
        db.withTransaction {
            userDao.upsertUser(updated)
            oldFp?.let { deviceTrialDao.deleteByDeviceFingerprint(it) }
            deviceTrialDao.deleteByDeviceFingerprint(currentFp)
            target.tenantId?.let { tid ->
                tenantDao.getTenantById(tid)?.let { t ->
                    tenantDao.upsertTenant(t.copy(isActive = true, updatedAt = now))
                }
            }
            systemDao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = target.tenantId,
                    actorUserId = actor.id,
                    actorUsername = actor.username,
                    action = "ADMIN_RESET_TRIAL",
                    entityType = "TRIAL_LICENSE",
                    entityId = target.id,
                    details = "إعادة تفعيل الحساب التجريبي (${target.username}) لمدة 72 ساعة جديدة وفك قفل الجهاز",
                    createdAt = now
                )
            )
        }
        return AuthResult.Success(updated)
    }

    suspend fun updateTenantInfo(
        actor: UserEntity,
        tenant: TenantEntity,
        shopName: String,
        ownerName: String,
        address: String,
        phone: String,
        whatsappPhone: String,
        invoiceFooter: String = tenant.invoiceFooter,
        logoUri: String? = tenant.logoUri
    ): AuthResult<TenantEntity> {
        val sName = shopName.trim()
        val oName = ownerName.trim()
        if (sName.isEmpty()) return AuthResult.Error("اسم المحل مطلوب")
        val cleanPhone = PhoneUtils.normalizePhoneDigits(phone)
        if (cleanPhone.isNotBlank() && !PhoneUtils.isValidPhone(cleanPhone)) {
            return AuthResult.Error("رقم الهاتف غير صحيح")
        }
        val cleanWa = PhoneUtils.normalizePhoneDigits(whatsappPhone).ifBlank { cleanPhone }
        val now = licenseManager.nowWallClock()
        val updated = tenant.copy(
            shopName = sName,
            ownerName = oName.ifBlank { tenant.ownerName },
            address = address.trim(),
            phone = cleanPhone,
            whatsappPhone = cleanWa,
            invoiceFooter = invoiceFooter.trim(),
            logoUri = logoUri,
            updatedAt = now
        )
        db.withTransaction {
            tenantDao.upsertTenant(updated)
            systemDao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = updated.id,
                    actorUserId = actor.id,
                    actorUsername = actor.username,
                    action = "UPDATE_TENANT",
                    entityType = "TENANT",
                    entityId = updated.id,
                    details = "تحديث بيانات المحل: $sName",
                    createdAt = now
                )
            )
        }
        return AuthResult.Success(updated)
    }

    suspend fun setTenantActive(actor: UserEntity, tenantId: String, active: Boolean): AuthResult<Unit> {
        if (actor.role != UserRoles.MASTER_ADMIN) {
            return AuthResult.Error("غير مصرح")
        }
        val tenant = tenantDao.getTenantById(tenantId) ?: return AuthResult.Error("المحل غير موجود")
        val now = licenseManager.nowWallClock()
        db.withTransaction {
            tenantDao.upsertTenant(tenant.copy(isActive = active, updatedAt = now))
            val tenantUsers = userDao.getUsersForTenant(tenantId)
            tenantUsers.forEach { u ->
                val newStatus = when {
                    !active -> AccountStatuses.SUSPENDED
                    u.isTrialAccount -> AccountStatuses.TRIAL
                    else -> AccountStatuses.ACTIVE
                }
                val sig = licenseManager.signLicenseFields(
                    userId = u.id,
                    accountStatus = newStatus,
                    boundFingerprint = u.boundDeviceFingerprint,
                    trialStartedAt = u.trialStartedAt,
                    trialExpiresAt = u.trialExpiresAt,
                    accumulatedMillis = u.accumulatedTrialMillis,
                    clockTampered = u.clockTampered
                )
                userDao.upsertUser(u.copy(isActive = active, accountStatus = newStatus, licenseSignature = sig))
            }
            systemDao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = tenantId,
                    actorUserId = actor.id,
                    actorUsername = actor.username,
                    action = if (active) "ENABLE_TENANT" else "DISABLE_TENANT",
                    entityType = "TENANT",
                    entityId = tenantId,
                    details = if (active) "تفعيل حساب المحل: ${tenant.shopName}" else "تعطيل حساب المحل: ${tenant.shopName}",
                    createdAt = now
                )
            )
        }
        return AuthResult.Success(Unit)
    }

    suspend fun resetUserPasswordByAdmin(
        actor: UserEntity,
        targetUserId: String,
        newPassword: String,
        confirmPassword: String
    ): AuthResult<Unit> {
        if (actor.role != UserRoles.MASTER_ADMIN && actor.role != UserRoles.SHOP_OWNER) {
            return AuthResult.Error("غير مصرح بإعادة تعيين كلمة المرور")
        }
        if (newPassword.length < 4) return AuthResult.Error("كلمة المرور يجب ألا تقل عن 4 أحرف")
        if (newPassword != confirmPassword) return AuthResult.Error("كلمة المرور وتأكيدها غير متطابقين")

        val target = userDao.getById(targetUserId) ?: return AuthResult.Error("المستخدم غير موجود")
        if (actor.role == UserRoles.SHOP_OWNER && target.tenantId != actor.tenantId) {
            return AuthResult.Error("غير مصرح بالوصول لهذا الحساب")
        }

        val hashed = SecurityUtils.hashPassword(newPassword.toCharArray())
        val now = licenseManager.nowWallClock()
        db.withTransaction {
            userDao.upsertUser(
                target.copy(
                    passwordSalt = hashed.saltBase64,
                    passwordHash = hashed.hashBase64
                )
            )
            systemDao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = target.tenantId,
                    actorUserId = actor.id,
                    actorUsername = actor.username,
                    action = "RESET_PASSWORD",
                    entityType = "USER",
                    entityId = target.id,
                    details = "إعادة تعيين كلمة مرور الحساب: ${target.username}",
                    createdAt = now
                )
            )
        }
        return AuthResult.Success(Unit)
    }

    suspend fun changeOwnPassword(
        userId: String,
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): AuthResult<Unit> {
        val user = userDao.getById(userId) ?: return AuthResult.Error("الحساب غير موجود")
        if (!SecurityUtils.verifyPassword(currentPassword.toCharArray(), user.passwordSalt, user.passwordHash)) {
            return AuthResult.Error("كلمة المرور الحالية غير صحيحة")
        }
        if (newPassword.length < 4) return AuthResult.Error("كلمة المرور الجديدة يجب ألا تقل عن 4 أحرف")
        if (newPassword != confirmPassword) return AuthResult.Error("كلمة المرور الجديدة وتأكيدها غير متطابقين")

        val hashed = SecurityUtils.hashPassword(newPassword.toCharArray())
        val now = licenseManager.nowWallClock()
        db.withTransaction {
            userDao.upsertUser(
                user.copy(
                    passwordSalt = hashed.saltBase64,
                    passwordHash = hashed.hashBase64,
                    mustChangePassword = false
                )
            )
            systemDao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = user.tenantId,
                    actorUserId = user.id,
                    actorUsername = user.username,
                    action = "CHANGE_PASSWORD",
                    entityType = "USER",
                    entityId = user.id,
                    details = "تغيير كلمة المرور الشخصية بنجاح",
                    createdAt = now
                )
            )
        }
        return AuthResult.Success(Unit)
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun createStaffUser(
        actor: UserEntity,
        displayName: String,
        username: String,
        password: String
    ): AuthResult<UserEntity> {
        return AuthResult.Error("البرنامج يعمل بحساب واحد فقط على هذا الهاتف ولا يدعم إضافة مستخدمين آخرين")
    }

    suspend fun toggleStaffActive(actor: UserEntity, targetUserId: String, active: Boolean): AuthResult<Unit> {
        val target = userDao.getById(targetUserId) ?: return AuthResult.Error("المستخدم غير موجود")
        if (actor.role == UserRoles.SHOP_OWNER && (target.tenantId != actor.tenantId || target.id == actor.id)) {
            return AuthResult.Error("لا يمكن تعديل هذا المستخدم")
        }
        val now = licenseManager.nowWallClock()
        val newStatus = if (active) AccountStatuses.ACTIVE else AccountStatuses.SUSPENDED
        db.withTransaction {
            userDao.upsertUser(target.copy(isActive = active, accountStatus = newStatus))
            systemDao.insertAuditLog(
                AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = target.tenantId,
                    actorUserId = actor.id,
                    actorUsername = actor.username,
                    action = if (active) "ENABLE_USER" else "DISABLE_USER",
                    entityType = "USER",
                    entityId = target.id,
                    details = if (active) "تفعيل المستخدم ${target.username}" else "تعطيل المستخدم ${target.username}",
                    createdAt = now
                )
            )
        }
        return AuthResult.Success(Unit)
    }

    // =========================================================================
    // Customer-Phone Account Creation & Device-Locked Remote Activation
    // =========================================================================

    /**
     * Allows Master Admin to create a brand-new Shop Account directly from the customer's phone
     * (without leaving an Admin session open). Always strictly binds the account to THIS phone's
     * hardware fingerprint so it can never be opened on any other device.
     */
    suspend fun createShopAccountDirectlyOnCustomerPhone(
        adminUsername: String,
        adminPassword: String,
        shopName: String,
        ownerName: String,
        address: String,
        phone: String,
        username: String,
        password: String,
        confirmPassword: String
    ): AuthResult<AuthenticatedSession> {
        ensureMasterAdminSeeded()
        val cleanAdmin = adminUsername.trim()
        if (cleanAdmin.isEmpty() || adminPassword.isEmpty()) {
            return AuthResult.Error("يرجى إدخال بيانات المدير الرئيسي للتحقق قبل إنشاء الحساب")
        }
        val admin = userDao.getByUsername(cleanAdmin)
            ?: return AuthResult.Error("بيانات المدير الرئيسي غير صحيحة")
        if (admin.role != UserRoles.MASTER_ADMIN) {
            return AuthResult.Error("إنشاء الحسابات مخصص للمدير الرئيسي فقط")
        }
        val adminValid = SecurityUtils.verifyPassword(
            adminPassword.toCharArray(),
            admin.passwordSalt,
            admin.passwordHash
        )
        if (!adminValid) {
            return AuthResult.Error("كلمة مرور المدير الرئيسي غير صحيحة")
        }

        return when (
            val created = createShopAccount(
                actor = admin,
                shopName = shopName,
                ownerName = ownerName,
                address = address,
                phone = phone,
                username = username,
                password = password,
                confirmPassword = confirmPassword,
                bindToCurrentDevice = true
            )
        ) {
            is AuthResult.Error -> created
            is AuthResult.TrialExpired -> created
            is AuthResult.Success -> {
                val (tenant, owner) = created.data
                val now = licenseManager.nowWallClock()
                val loggedInOwner = owner.copy(lastLoginAt = now)
                userDao.upsertUser(loggedInOwner)
                AuthResult.Success(AuthenticatedSession(loggedInOwner, tenant, trialInfo = null))
            }
        }
    }

    /**
     * Master Admin generates a cryptographically signed, device-locked activation token (HSB1...)
     * for a customer's remote phone using that phone's unique Device Code (HSB-XXXX-XXXX-XXXX).
     * The generated token will ONLY activate on that exact customer's phone.
     */
    suspend fun generateRemoteDeviceActivationToken(
        actor: UserEntity,
        targetDeviceCode: String,
        shopName: String,
        ownerName: String,
        address: String,
        phone: String,
        username: String,
        password: String,
        confirmPassword: String
    ): AuthResult<String> {
        if (actor.role != UserRoles.MASTER_ADMIN) {
            return AuthResult.Error("هذه الصلاحية مخصصة للمدير الرئيسي فقط")
        }
        val normalizedCode = licenseManager.normalizeDeviceCode(targetDeviceCode)
            ?: return AuthResult.Error("كود جهاز العميل غير صحيح (الصيغة الصحيحة: HSB-XXXX-XXXX-XXXX)")

        val sName = shopName.trim()
        val oName = ownerName.trim()
        val addr = address.trim()
        val cleanPhone = PhoneUtils.normalizePhoneDigits(phone)
        val cleanUser = username.trim()

        if (sName.isEmpty()) return AuthResult.Error("اسم المحل مطلوب")
        if (oName.isEmpty()) return AuthResult.Error("اسم صاحب المحل مطلوب")
        if (addr.isEmpty()) return AuthResult.Error("العنوان مطلوب")
        if (!PhoneUtils.isValidPhone(cleanPhone)) return AuthResult.Error("رقم الهاتف غير صحيح (مثال: 01012345678)")
        if (cleanUser.length < 3) return AuthResult.Error("اسم المستخدم يجب ألا يقل عن 3 أحرف")
        if (password.length < 4) return AuthResult.Error("كلمة المرور يجب ألا تقل عن 4 أحرف")
        if (password != confirmPassword) return AuthResult.Error("كلمة المرور وتأكيدها غير متطابقين")

        val hashed = SecurityUtils.hashPassword(password.toCharArray())
        val now = licenseManager.nowWallClock()
        val token = licenseManager.generateDeviceActivationToken(
            DeviceLicenseManager.DeviceActivationPayload(
                targetDeviceCode = normalizedCode,
                shopName = sName,
                ownerName = oName,
                address = addr,
                phone = cleanPhone,
                username = cleanUser,
                passwordSalt = hashed.saltBase64,
                passwordHash = hashed.hashBase64,
                issuedAt = now
            )
        )
        systemDao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                tenantId = null,
                actorUserId = actor.id,
                actorUsername = actor.username,
                action = "GENERATE_REMOTE_DEVICE_TOKEN",
                entityType = "DEVICE_LICENSE",
                entityId = normalizedCode,
                details = "توليد كود تفعيل مربوط بجهاز العميل ($normalizedCode) لمحل: $sName (المستخدم: $cleanUser)",
                createdAt = now
            )
        )
        return AuthResult.Success(token)
    }

    /**
     * Activates a device-locked token (HSB1...) on the customer's phone.
     * Verifies HMAC-SHA256 signature AND confirms the token belongs strictly to THIS phone.
     * Creates the isolated shop tenant & permanently locks the account to this device's fingerprint.
     */
    suspend fun activateShopAccountWithDeviceToken(rawToken: String): AuthResult<AuthenticatedSession> {
        ensureMasterAdminSeeded()
        val currentCode = licenseManager.getDeviceActivationCode()
        return when (val check = licenseManager.verifyAndParseDeviceActivationToken(rawToken, currentCode)) {
            is DeviceLicenseManager.DeviceTokenVerification.Invalid -> {
                AuthResult.Error(check.reason)
            }
            is DeviceLicenseManager.DeviceTokenVerification.WrongDevice -> {
                AuthResult.Error("كود التفعيل هذا مرتبط بجهاز آخر ولا يمكن استخدامه على هذا الجهاز.")
            }
            is DeviceLicenseManager.DeviceTokenVerification.Valid -> {
                val payload = check.payload
                val now = licenseManager.nowWallClock()
                val currentFp = licenseManager.getDeviceFingerprint()
                val currentLabel = licenseManager.getDeviceLabel()

                val existingUser = userDao.getByUsername(payload.username)
                if (existingUser != null) {
                    if (existingUser.boundDeviceFingerprint == currentFp && existingUser.tenantId != null) {
                        val existingTenant = tenantDao.getTenantById(existingUser.tenantId)
                        if (existingTenant != null) {
                            return AuthResult.Success(AuthenticatedSession(existingUser, existingTenant, trialInfo = null))
                        }
                    }
                    return AuthResult.Error("اسم المستخدم (${payload.username}) مسجل بالفعل على هذا الجهاز")
                }

                val tenantId = "tenant_${UUID.randomUUID()}"
                val userId = UUID.randomUUID().toString()
                val tenant = TenantEntity(
                    id = tenantId,
                    shopName = payload.shopName,
                    ownerName = payload.ownerName,
                    address = payload.address,
                    phone = payload.phone,
                    whatsappPhone = payload.phone,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now
                )
                val sig = licenseManager.signLicenseFields(
                    userId = userId,
                    accountStatus = AccountStatuses.ACTIVE,
                    boundFingerprint = currentFp,
                    trialStartedAt = null,
                    trialExpiresAt = null,
                    accumulatedMillis = 0L,
                    clockTampered = false
                )
                val shopOwner = UserEntity(
                    id = userId,
                    tenantId = tenantId,
                    username = payload.username,
                    displayName = payload.ownerName,
                    role = UserRoles.SHOP_OWNER,
                    passwordSalt = payload.passwordSalt,
                    passwordHash = payload.passwordHash,
                    isActive = true,
                    createdAt = now,
                    lastLoginAt = now,
                    accountStatus = AccountStatuses.ACTIVE,
                    isTrialAccount = false,
                    boundDeviceFingerprint = currentFp,
                    boundDeviceLabel = currentLabel,
                    deviceBoundAt = now,
                    licenseSignature = sig
                )
                val defaultSettings = AppSettingEntity(
                    tenantId = tenantId,
                    darkMode = false,
                    invoiceSignatureName = payload.ownerName,
                    updatedAt = now
                )
                db.withTransaction {
                    tenantDao.upsertTenant(tenant)
                    userDao.upsertUser(shopOwner)
                    systemDao.upsertAppSettings(defaultSettings)
                    systemDao.insertAuditLog(
                        AuditLogEntity(
                            id = UUID.randomUUID().toString(),
                            tenantId = tenantId,
                            actorUserId = userId,
                            actorUsername = shopOwner.username,
                            action = "ACTIVATE_DEVICE_LOCKED_ACCOUNT",
                            entityType = "TENANT",
                            entityId = tenantId,
                            details = "تفعيل حساب المحل (${payload.shopName}) وربطه حصريًا بالجهاز: $currentLabel ($currentCode)",
                            createdAt = now
                        )
                    )
                }
                AuthResult.Success(AuthenticatedSession(shopOwner, tenant, trialInfo = null))
            }
        }
    }

    fun observeAllTenants(): Flow<List<TenantEntity>> = tenantDao.observeAllTenants()
    fun observeTenant(tenantId: String): Flow<TenantEntity?> = tenantDao.observeTenant(tenantId)
    fun observeAllShopUsers(): Flow<List<UserEntity>> = userDao.observeAllShopUsers()
    fun observeTenantUsers(tenantId: String): Flow<List<UserEntity>> = userDao.observeUsersForTenant(tenantId)
    fun observeAllAuditLogs(): Flow<List<AuditLogEntity>> = systemDao.observeAllAuditLogs()
    fun observeTenantAuditLogs(tenantId: String): Flow<List<AuditLogEntity>> = systemDao.observeTenantAuditLogs(tenantId)
    fun observeAllBackupHistory() = systemDao.observeAllBackupHistory()
}
