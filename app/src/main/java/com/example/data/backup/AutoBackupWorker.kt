package com.example.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.HesabatyDatabase

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val tenantId = inputData.getString("tenantId") ?: return Result.failure()
        val db = HesabatyDatabase.getInstance(applicationContext)
        val backupManager = BackupManager(applicationContext, db)

        val localResult = backupManager.createLocalBackup(
            tenantId = tenantId,
            actorUserId = "system_auto_backup",
            actorUsername = "النسخ التلقائي"
        )
        return when (localResult) {
            is BackupOpResult.Success -> Result.success()
            is BackupOpResult.Error -> Result.retry()
        }
    }
}
