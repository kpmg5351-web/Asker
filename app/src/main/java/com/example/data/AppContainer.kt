package com.example.data

import android.content.Context
import com.example.core.DeviceLicenseManager
import com.example.data.backup.BackupManager
import com.example.data.local.HesabatyDatabase
import com.example.data.local.SessionPreferences
import com.example.data.repository.AccountingRepository
import com.example.data.repository.AuthRepository

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: HesabatyDatabase = HesabatyDatabase.getInstance(context)
    val sessionPreferences: SessionPreferences = SessionPreferences(context)
    val deviceLicenseManager: DeviceLicenseManager = DeviceLicenseManager(context)
    val authRepository: AuthRepository = AuthRepository(database, deviceLicenseManager)
    val accountingRepository: AccountingRepository = AccountingRepository(database)
    val backupManager: BackupManager = BackupManager(context, database)
}
