package com.example.data.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.core.EgyptTime
import com.example.core.SecurityUtils
import com.example.data.local.AuditLogEntity
import com.example.data.local.BackupHistoryEntity
import com.example.data.local.HesabatyDatabase
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

sealed class BackupOpResult<out T> {
    data class Success<T>(val data: T) : BackupOpResult<T>()
    data class Error(val message: String) : BackupOpResult<Nothing>()
}

class BackupManager(
    private val context: Context,
    private val db: HesabatyDatabase
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val payloadAdapter = moshi.adapter(TenantBackupPayload::class.java)
    private val envelopeAdapter = moshi.adapter(EncryptedBackupEnvelope::class.java)
    val driveApi = GoogleDriveApi(moshi)
    private val prefs by lazy {
        context.getSharedPreferences("hesabaty_auto_drive_prefs", Context.MODE_PRIVATE)
    }

    fun isAutoDriveBackupEnabled(tenantId: String): Boolean =
        prefs.getBoolean("auto_drive_enabled_$tenantId", false)

    fun setAutoDriveBackupEnabled(tenantId: String, enabled: Boolean) {
        prefs.edit().putBoolean("auto_drive_enabled_$tenantId", enabled).apply()
    }

    fun isDriveAuthorized(tenantId: String): Boolean =
        prefs.getBoolean("drive_authorized_$tenantId", false)

    fun markDriveAuthorized(tenantId: String, authorized: Boolean) {
        prefs.edit().putBoolean("drive_authorized_$tenantId", authorized).apply()
    }

    fun backupsDirectory(): File {
        return File(context.filesDir, "backups").apply { mkdirs() }
    }

    fun listLocalBackupFiles(tenantId: String): List<File> {
        val prefix = "hesabaty_${tenantId.take(16)}"
        return backupsDirectory()
            .listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".hbak") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Collects all tenant tables atomically, serializes to JSON, and encrypts with AES-256-GCM.
     */
    suspend fun buildEncryptedBackupBytes(
        tenantId: String,
        passphrase: String? = null
    ): Pair<String, ByteArray> = withContext(Dispatchers.IO) {
        val now = EgyptTime.nowMillis()
        val payload = db.withTransaction {
            val tenant = db.tenantDao().getTenantById(tenantId)
                ?: throw IllegalStateException("بيانات المحل غير موجودة")
            TenantBackupPayload(
                schemaVersion = 1,
                exportedAtMillis = now,
                tenant = tenant,
                users = db.userDao().getUsersForTenant(tenantId),
                customers = db.customerDao().getCustomers(tenantId),
                suppliers = db.supplierDao().getSuppliers(tenantId),
                salesInvoices = db.salesInvoiceDao().getInvoices(tenantId),
                salesInvoiceItems = db.salesInvoiceDao().getAllInvoiceItems(tenantId),
                payments = db.paymentDao().getPayments(tenantId),
                supplierTransactions = db.transactionDao().getAllSupplierTransactions(tenantId),
                customerTransactions = db.transactionDao().getAllCustomerTransactions(tenantId),
                dailyWithdrawalInvoices = db.dailyWithdrawalDao().getAllDailyInvoices(tenantId),
                dailyWithdrawalItems = db.dailyWithdrawalDao().getAllDailyItems(tenantId),
                installmentSchedules = db.installmentDao().getAllInstallments(tenantId),
                appSettings = db.systemDao().getAppSettings(tenantId),
                auditLogs = db.systemDao().getTenantAuditLogs(tenantId),
                products = db.productDao().getProducts(tenantId),
                purchaseInvoices = db.purchaseInvoiceDao().getPurchaseInvoices(tenantId),
                purchaseInvoiceItems = db.purchaseInvoiceDao().getAllPurchaseInvoiceItems(tenantId)
            )
        }

        val jsonBytes = payloadAdapter.toJson(payload).toByteArray(Charsets.UTF_8)
        val encrypted = SecurityUtils.encryptBackup(jsonBytes, passphrase)
        val envelope = EncryptedBackupEnvelope(
            formatVersion = 1,
            tenantId = tenantId,
            shopName = payload.tenant.shopName,
            keyMode = encrypted.keyMode,
            saltBase64 = encrypted.saltBase64,
            ivBase64 = encrypted.ivBase64,
            ciphertextBase64 = encrypted.ciphertextBase64,
            plaintextSha256 = encrypted.plaintextSha256,
            createdAtMillis = now
        )
        val envelopeBytes = envelopeAdapter.toJson(envelope).toByteArray(Charsets.UTF_8)
        val stamp = EgyptTime.formatDate(now).replace("/", "") + "_" + EgyptTime.formatFileStampTime(now)
        val fileName = "hesabaty_${tenantId.take(16)}_$stamp.hbak"
        fileName to envelopeBytes
    }

    suspend fun createLocalBackup(
        tenantId: String,
        actorUserId: String,
        actorUsername: String,
        passphrase: String? = null,
        exportUri: Uri? = null
    ): BackupOpResult<BackupHistoryEntity> = withContext(Dispatchers.IO) {
        val now = EgyptTime.nowMillis()
        try {
            val (fileName, bytes) = buildEncryptedBackupBytes(tenantId, passphrase)
            val localFile = File(backupsDirectory(), fileName)
            localFile.writeBytes(bytes)

            if (exportUri != null) {
                context.contentResolver.openOutputStream(exportUri)?.use { out ->
                    out.write(bytes)
                }
            }

            val record = BackupHistoryEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                destination = "LOCAL",
                status = "SUCCESS",
                fileName = fileName,
                sizeBytes = bytes.size.toLong(),
                message = "تم إنشاء نسخة محلية مشفرة (AES-256-GCM)",
                createdByUserId = actorUserId,
                createdAt = now
            )
            db.withTransaction {
                db.systemDao().upsertBackupRecord(record)
                db.systemDao().insertAuditLog(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        tenantId = tenantId,
                        actorUserId = actorUserId,
                        actorUsername = actorUsername,
                        action = "LOCAL_BACKUP",
                        entityType = "BACKUP",
                        entityId = record.id,
                        details = "نسخ احتياطي محلي مشفر: $fileName",
                        createdAt = now
                    )
                )
            }
            BackupOpResult.Success(record)
        } catch (e: Exception) {
            val fail = BackupHistoryEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                destination = "LOCAL",
                status = "FAILED",
                fileName = "",
                message = e.message ?: "فشل إنشاء النسخة المحلية",
                createdByUserId = actorUserId,
                createdAt = now
            )
            db.systemDao().upsertBackupRecord(fail)
            BackupOpResult.Error(fail.message)
        }
    }

    suspend fun uploadToGoogleDrive(
        tenantId: String,
        actorUserId: String,
        actorUsername: String,
        accessToken: String,
        passphrase: String? = null
    ): BackupOpResult<BackupHistoryEntity> = withContext(Dispatchers.IO) {
        val now = EgyptTime.nowMillis()
        if (!isOnline()) {
            val fail = BackupHistoryEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                destination = "GOOGLE_DRIVE",
                status = "FAILED",
                fileName = "",
                message = "لا يوجد اتصال بالإنترنت حاليًا. جميع بياناتك محفوظة محليًا بأمان.",
                createdByUserId = actorUserId,
                createdAt = now
            )
            db.systemDao().upsertBackupRecord(fail)
            return@withContext BackupOpResult.Error(fail.message)
        }

        try {
            val (fileName, bytes) = buildEncryptedBackupBytes(tenantId, passphrase)
            // Always persist a local encrypted copy too
            File(backupsDirectory(), fileName).writeBytes(bytes)

            val driveItem = driveApi.uploadToAppDataFolder(accessToken, fileName, bytes)
            val record = BackupHistoryEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                destination = "GOOGLE_DRIVE",
                status = "SUCCESS",
                fileName = fileName,
                driveFileId = driveItem.id,
                sizeBytes = bytes.size.toLong(),
                message = "تم رفع النسخة المشفرة إلى Google Drive بنجاح",
                createdByUserId = actorUserId,
                createdAt = now
            )
            db.withTransaction {
                db.systemDao().upsertBackupRecord(record)
                db.systemDao().insertAuditLog(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        tenantId = tenantId,
                        actorUserId = actorUserId,
                        actorUsername = actorUsername,
                        action = "GOOGLE_DRIVE_BACKUP",
                        entityType = "BACKUP",
                        entityId = record.id,
                        details = "رفع نسخة مشفرة إلى Google Drive: $fileName",
                        createdAt = now
                    )
                )
            }
            BackupOpResult.Success(record)
        } catch (e: Exception) {
            val fail = BackupHistoryEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                destination = "GOOGLE_DRIVE",
                status = "FAILED",
                fileName = "",
                message = e.message ?: "فشل الرفع إلى Google Drive",
                createdByUserId = actorUserId,
                createdAt = now
            )
            db.systemDao().upsertBackupRecord(fail)
            BackupOpResult.Error(fail.message)
        }
    }

    /**
     * Atomically restores an encrypted backup for the matching tenant using deterministic UUID upserts
     * so transactions are NEVER duplicated on repeated restores.
     */
    suspend fun restoreFromEncryptedBytes(
        encryptedBytes: ByteArray,
        expectedTenantId: String?,
        actorUserId: String,
        actorUsername: String,
        passphrase: String? = null
    ): BackupOpResult<TenantBackupPayload> = withContext(Dispatchers.IO) {
        try {
            val envelopeJson = encryptedBytes.toString(Charsets.UTF_8)
            val envelope = envelopeAdapter.fromJson(envelopeJson)
                ?: return@withContext BackupOpResult.Error("ملف النسخة الاحتياطية غير صالح")

            if (expectedTenantId != null && envelope.tenantId != expectedTenantId) {
                return@withContext BackupOpResult.Error("هذه النسخة الاحتياطية تخص محلًا آخر ولا يمكن استعادتها داخل هذا المحل")
            }

            val decryptedBytes = SecurityUtils.decryptBackup(
                SecurityUtils.EncryptedPayload(
                    keyMode = envelope.keyMode,
                    saltBase64 = envelope.saltBase64,
                    ivBase64 = envelope.ivBase64,
                    ciphertextBase64 = envelope.ciphertextBase64,
                    plaintextSha256 = envelope.plaintextSha256
                ),
                passphrase
            )
            val payload = payloadAdapter.fromJson(decryptedBytes.toString(Charsets.UTF_8))
                ?: return@withContext BackupOpResult.Error("تعذر قراءة محتوى النسخة الاحتياطية")

            val now = EgyptTime.nowMillis()
            db.withTransaction {
                db.tenantDao().upsertTenant(payload.tenant)
                db.userDao().upsertUsers(payload.users)
                db.customerDao().upsertCustomers(payload.customers)
                db.supplierDao().upsertSuppliers(payload.suppliers)
                db.salesInvoiceDao().upsertInvoices(payload.salesInvoices)
                db.salesInvoiceDao().upsertInvoiceItems(payload.salesInvoiceItems)
                db.paymentDao().upsertPayments(payload.payments)
                db.transactionDao().upsertSupplierTransactions(payload.supplierTransactions)
                db.transactionDao().upsertCustomerTransactions(payload.customerTransactions)
                db.dailyWithdrawalDao().upsertDailyInvoices(payload.dailyWithdrawalInvoices)
                db.dailyWithdrawalDao().upsertDailyItems(payload.dailyWithdrawalItems)
                db.installmentDao().upsertInstallments(payload.installmentSchedules)
                if (payload.products.isNotEmpty()) {
                    db.productDao().upsertProducts(payload.products)
                }
                if (payload.purchaseInvoices.isNotEmpty()) {
                    db.purchaseInvoiceDao().upsertPurchaseInvoices(payload.purchaseInvoices)
                }
                if (payload.purchaseInvoiceItems.isNotEmpty()) {
                    db.purchaseInvoiceDao().upsertPurchaseInvoiceItems(payload.purchaseInvoiceItems)
                }
                payload.appSettings?.let { db.systemDao().upsertAppSettings(it) }
                db.systemDao().upsertAuditLogs(payload.auditLogs)
                db.systemDao().insertAuditLog(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        tenantId = payload.tenant.id,
                        actorUserId = actorUserId,
                        actorUsername = actorUsername,
                        action = "RESTORE_BACKUP",
                        entityType = "BACKUP",
                        entityId = payload.tenant.id,
                        details = "استعادة نسخة احتياطية بتاريخ ${EgyptTime.formatDateTime(payload.exportedAtMillis)} (${payload.customers.size} عميل، ${payload.suppliers.size} مورد، ${payload.salesInvoices.size} فاتورة)",
                        createdAt = now
                    )
                )
            }
            BackupOpResult.Success(payload)
        } catch (e: Exception) {
            BackupOpResult.Error(e.message ?: "فشل فك تشفير أو استعادة النسخة الاحتياطية")
        }
    }

    fun scheduleDailyAutoBackup(tenantId: String, enabled: Boolean, hour: Int, minute: Int) {
        val workName = "hesabaty_daily_backup_$tenantId"
        val retryWorkName = "hesabaty_drive_retry_$tenantId"
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(workName)
            wm.cancelUniqueWork(retryWorkName)
            return
        }
        val now = Calendar.getInstance(EgyptTime.timeZone)
        val target = Calendar.getInstance(EgyptTime.timeZone).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        val delayMs = (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("tenantId" to tenantId, "driveOnly" to false))
            .build()
        wm.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scheduleDriveRetryWhenOnline(tenantId: String) {
        val retryWorkName = "hesabaty_drive_retry_$tenantId"
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf("tenantId" to tenantId, "driveOnly" to true))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(retryWorkName, ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun authorizeDriveSilently(): Pair<String?, String?> = suspendCancellableCoroutine { cont ->
        try {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.appdata")))
                .build()
            Identity.getAuthorizationClient(context)
                .authorize(authRequest)
                .addOnSuccessListener { result ->
                    if (!cont.isActive) return@addOnSuccessListener
                    val token = result.accessToken
                    when {
                        result.hasResolution() -> {
                            cont.resume(null to "يتطلب Google Drive إعادة تأكيد الصلاحية من شاشة النسخ الاحتياطي.")
                        }
                        !token.isNullOrBlank() -> cont.resume(token to null)
                        else -> cont.resume(null to "تعذر الحصول على رمز صلاحية Google Drive.")
                    }
                }
                .addOnFailureListener { e ->
                    if (!cont.isActive) return@addOnFailureListener
                    cont.resume(
                        null to "يتطلب الرفع التلقائي إلى Google Drive ربط الحساب وتفعيل OAuth Android Client. (${e.localizedMessage ?: "خطأ غير معروف"})"
                    )
                }
        } catch (e: Exception) {
            if (cont.isActive) {
                cont.resume(null to (e.localizedMessage ?: "تعذر بدء مصادقة Google Drive"))
            }
        }
    }

    /**
     * Executes scheduled daily backup:
     * 1. Always creates an encrypted local backup first (unless [driveOnly] is true).
     * 2. If Auto-Drive backup is enabled for [tenantId], uploads to Google Drive appDataFolder
     *    when online, or schedules a connected one-time retry when offline.
     */
    suspend fun runScheduledBackup(
        tenantId: String,
        driveOnly: Boolean = false
    ): BackupOpResult<BackupHistoryEntity> {
        val localResult = if (!driveOnly) {
            createLocalBackup(
                tenantId = tenantId,
                actorUserId = "system_auto_backup",
                actorUsername = "النسخ التلقائي"
            )
        } else {
            null
        }

        if (!isAutoDriveBackupEnabled(tenantId)) {
            return localResult ?: BackupOpResult.Error("النسخ التلقائي على Google Drive غير مفعّل")
        }

        if (!isOnline()) {
            scheduleDriveRetryWhenOnline(tenantId)
            return localResult ?: BackupOpResult.Error("بانتظار توفر اتصال بالإنترنت للرفع إلى Google Drive")
        }

        val (token, authError) = authorizeDriveSilently()
        if (token.isNullOrBlank()) {
            markDriveAuthorized(tenantId, false)
            val fail = BackupHistoryEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                destination = "GOOGLE_DRIVE",
                status = "FAILED",
                fileName = "",
                message = authError ?: "تعذر الرفع التلقائي إلى Google Drive",
                createdByUserId = "system_auto_backup",
                createdAt = EgyptTime.nowMillis()
            )
            db.systemDao().upsertBackupRecord(fail)
            return localResult ?: BackupOpResult.Error(fail.message)
        }

        markDriveAuthorized(tenantId, true)
        val driveRes = uploadToGoogleDrive(
            tenantId = tenantId,
            actorUserId = "system_auto_backup",
            actorUsername = "النسخ التلقائي",
            accessToken = token
        )
        return when {
            driveRes is BackupOpResult.Success -> driveRes
            localResult is BackupOpResult.Success -> localResult
            else -> driveRes
        }
    }
}
