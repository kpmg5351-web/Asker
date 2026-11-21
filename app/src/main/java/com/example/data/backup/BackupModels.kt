package com.example.data.backup

import com.example.data.local.AppSettingEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerTransactionEntity
import com.example.data.local.DailyWithdrawalInvoiceEntity
import com.example.data.local.DailyWithdrawalItemEntity
import com.example.data.local.InstallmentScheduleEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.ProductEntity
import com.example.data.local.PurchaseInvoiceEntity
import com.example.data.local.PurchaseInvoiceItemEntity
import com.example.data.local.SalesInvoiceEntity
import com.example.data.local.SalesInvoiceItemEntity
import com.example.data.local.SupplierEntity
import com.example.data.local.SupplierTransactionEntity
import com.example.data.local.TenantEntity
import com.example.data.local.UserEntity
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TenantBackupPayload(
    val schemaVersion: Int = 1,
    val exportedAtMillis: Long,
    val tenant: TenantEntity,
    val users: List<UserEntity>,
    val customers: List<CustomerEntity>,
    val suppliers: List<SupplierEntity>,
    val salesInvoices: List<SalesInvoiceEntity>,
    val salesInvoiceItems: List<SalesInvoiceItemEntity>,
    val payments: List<PaymentEntity>,
    val supplierTransactions: List<SupplierTransactionEntity>,
    val customerTransactions: List<CustomerTransactionEntity>,
    val dailyWithdrawalInvoices: List<DailyWithdrawalInvoiceEntity>,
    val dailyWithdrawalItems: List<DailyWithdrawalItemEntity>,
    val installmentSchedules: List<InstallmentScheduleEntity>,
    val appSettings: AppSettingEntity?,
    val auditLogs: List<AuditLogEntity>,
    val products: List<ProductEntity> = emptyList(),
    val purchaseInvoices: List<PurchaseInvoiceEntity> = emptyList(),
    val purchaseInvoiceItems: List<PurchaseInvoiceItemEntity> = emptyList()
)

@JsonClass(generateAdapter = true)
data class EncryptedBackupEnvelope(
    val formatVersion: Int = 1,
    val appId: String = "hesabaty",
    val tenantId: String,
    val shopName: String,
    val algorithm: String = "AES-256-GCM",
    val keyMode: String,
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String,
    val plaintextSha256: String,
    val createdAtMillis: Long
)

@JsonClass(generateAdapter = true)
data class DriveFileItem(
    val id: String,
    val name: String,
    val modifiedTime: String? = null,
    val size: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveFileListResponse(
    val files: List<DriveFileItem> = emptyList()
)
