package com.example.data.backup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.R
import com.example.core.EgyptTime
import com.example.core.SecurityUtils
import com.example.data.local.AuditLogEntity
import com.example.data.local.BackupHistoryEntity
import com.example.data.local.HesabatyDatabase
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

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
        context.getSharedPreferences("hesabaty_auto_backup_prefs", Context.MODE_PRIVATE)
    }

    fun getAutoBackupFolderUri(tenantId: String): String? =
        prefs.getString("auto_folder_uri_$tenantId", null)

    fun getAutoBackupFolderLabel(tenantId: String): String? =
        prefs.getString("auto_folder_label_$tenantId", null)

    fun setAutoBackupFolder(tenantId: String, uriString: String?, label: String?) {
        prefs.edit().apply {
            if (uriString.isNullOrBlank()) {
                remove("auto_folder_uri_$tenantId")
                remove("auto_folder_label_$tenantId")
            } else {
                putString("auto_folder_uri_$tenantId", uriString)
                putString("auto_folder_label_$tenantId", label ?: "مجلد مخصص")
            }
        }.apply()
    }

    fun isDailyDriveReminderEnabled(tenantId: String): Boolean =
        prefs.getBoolean("daily_drive_reminder_$tenantId", true)

    fun setDailyDriveReminderEnabled(tenantId: String, enabled: Boolean) {
        prefs.edit().putBoolean("daily_drive_reminder_$tenantId", enabled).apply()
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

            val savedDownloads = exportCopyToDownloads(fileName, bytes)
            val customFolderUri = getAutoBackupFolderUri(tenantId)
            val savedCustomFolder = if (!customFolderUri.isNullOrBlank()) {
                exportCopyToTreeFolder(customFolderUri, fileName, bytes)
            } else false

            val locationSummary = buildList {
                add("الهاتف")
                if (savedDownloads) add("التنزيلات (HesabatyBackups)")
                if (savedCustomFolder) add("المجلد المخصص")
            }.joinToString(" + ")

            val record = BackupHistoryEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                destination = "LOCAL",
                status = "SUCCESS",
                fileName = fileName,
                sizeBytes = bytes.size.toLong(),
                message = "نسخة مشفرة محفوظة في: $locationSummary",
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
                        details = "نسخ احتياطي مشفر ($locationSummary): $fileName",
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
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(workName)
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
            .setInputData(workDataOf("tenantId" to tenantId))
            .build()
        wm.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Builds an encrypted backup file on disk for instant sharing to the Google Drive app,
     * WhatsApp, Telegram, or Android's Save Picker without any Google Cloud Console setup.
     */
    suspend fun createBackupFileForShare(
        tenantId: String,
        actorUserId: String,
        actorUsername: String,
        passphrase: String? = null
    ): BackupOpResult<File> = withContext(Dispatchers.IO) {
        try {
            val (fileName, bytes) = buildEncryptedBackupBytes(tenantId, passphrase)
            val localFile = File(backupsDirectory(), fileName)
            localFile.writeBytes(bytes)
            exportCopyToDownloads(fileName, bytes)
            getAutoBackupFolderUri(tenantId)?.let { uriStr ->
                exportCopyToTreeFolder(uriStr, fileName, bytes)
            }
            val now = EgyptTime.nowMillis()
            val record = BackupHistoryEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                destination = "GOOGLE_DRIVE",
                status = "SUCCESS",
                fileName = fileName,
                sizeBytes = bytes.size.toLong(),
                message = "تم تجهيز النسخة المشفرة للحفظ على Google Drive / المشاركة",
                createdByUserId = actorUserId,
                createdAt = now
            )
            db.systemDao().upsertBackupRecord(record)
            BackupOpResult.Success(localFile)
        } catch (e: Exception) {
            BackupOpResult.Error(e.message ?: "تعذر تجهيز ملف النسخة الاحتياطية")
        }
    }

    fun isGoogleDriveAppInstalled(): Boolean {
        val drivePkg = "com.google.android.apps.docs"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(drivePkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(drivePkg, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getShareableUri(backupFile: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return try {
            FileProvider.getUriForFile(context, authority, backupFile)
        } catch (_: IllegalArgumentException) {
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cachedCopy = File(exportsDir, backupFile.name)
            backupFile.copyTo(cachedCopy, overwrite = true)
            FileProvider.getUriForFile(context, authority, cachedCopy)
        }
    }

    fun createSaveToDriveIntent(backupFile: File): Intent? {
        if (!isGoogleDriveAppInstalled()) return null
        val contentUri = getShareableUri(backupFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, backupFile.name)
            setPackage("com.google.android.apps.docs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) intent else null
    }

    fun createShareBackupIntent(backupFile: File): Intent {
        val contentUri = getShareableUri(backupFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, backupFile.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "حفظ أو إرسال النسخة الاحتياطية").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun runScheduledBackup(tenantId: String): BackupOpResult<BackupHistoryEntity> {
        val result = createLocalBackup(
            tenantId = tenantId,
            actorUserId = "system_auto_backup",
            actorUsername = "النسخ التلقائي"
        )
        if (result is BackupOpResult.Success && isDailyDriveReminderEnabled(tenantId)) {
            postDailyBackupNotification(result.data.fileName)
        }
        return result
    }

    private fun exportCopyToDownloads(fileName: String, bytes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/HesabatyBackups")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(itemUri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            true
        }.getOrDefault(false)
    }

    private fun exportCopyToTreeFolder(treeUriString: String, fileName: String, bytes: ByteArray): Boolean {
        return runCatching {
            val treeUri = Uri.parse(treeUriString)
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            val docUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                "application/octet-stream",
                fileName
            ) ?: return false
            context.contentResolver.openOutputStream(docUri)?.use { it.write(bytes) }
            true
        }.getOrDefault(false)
    }

    private fun postDailyBackupNotification(fileName: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }
            val channelId = "hesabaty_auto_backup_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (manager != null && manager.getNotificationChannel(channelId) == null) {
                    val channel = NotificationChannel(
                        channelId,
                        "النسخ الاحتياطي اليومي",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "إشعارات إتمام النسخة الاحتياطية اليومية والتذكير بالحفظ على Google Drive"
                    }
                    manager.createNotificationChannel(channel)
                }
            }
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                4001,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("تم حفظ النسخة الاحتياطية اليومية")
                .setContentText("حُفظت في الهاتف والتنزيلات ($fileName) — اضغط لرفع نسخة إلى Google Drive")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "تم حفظ نسختك المشفرة تلقائيًا في الهاتف ومجلد Downloads/HesabatyBackups ($fileName). اضغط هنا إذا أردت رفعها إلى Google Drive بضغطة واحدة."
                    )
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(4001, notification)
        }
    }
}
