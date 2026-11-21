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
        val driveOnly = inputData.getBoolean("driveOnly", false)
        val db = HesabatyDatabase.getInstance(applicationContext)
        val backupManager = BackupManager(applicationContext, db)

        if (driveOnly && !backupManager.isOnline()) {
            return Result.retry()
        }

        val result = backupManager.runScheduledBackup(
            tenantId = tenantId,
            driveOnly = driveOnly
        )
        return when (result) {
            is BackupOpResult.Success -> Result.success()
            is BackupOpResult.Error -> if (driveOnly) Result.retry() else Result.retry()
        }
    }
}
