package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

// --- Domain Constants ---
object UserRoles {
    const val MASTER_ADMIN = "MASTER_ADMIN"
    const val SHOP_OWNER = "SHOP_OWNER"
    const val STAFF = "STAFF"
}

object AccountStatuses {
    const val TRIAL = "TRIAL"         // تجريبي
    const val ACTIVE = "ACTIVE"       // نشط
    const val EXPIRED = "EXPIRED"     // منتهي
    const val SUSPENDED = "SUSPENDED" // موقوف

    fun arabicLabel(status: String): String = when (status) {
        TRIAL -> "تجريبي"
        ACTIVE -> "نشط"
        EXPIRED -> "منتهي"
        SUSPENDED -> "موقوف"
        else -> status
    }
}

object PaymentMethods {
    const val CASH = "CASH"
    const val EWALLET = "EWALLET"
    const val BANK_TRANSFER = "BANK_TRANSFER"
    const val NONE = "NONE"

    fun arabicLabel(method: String): String = when (method) {
        CASH -> "نقدي"
        EWALLET -> "محفظة إلكترونية"
        BANK_TRANSFER -> "تحويل بنكي"
        else -> "آجل / بدون تحصيل"
    }
}

object SaleTypes {
    const val CASH = "CASH"
    const val CREDIT = "CREDIT"
    const val SCHEDULED = "SCHEDULED"

    fun arabicLabel(type: String): String = when (type) {
        CASH -> "بيع كاش"
        CREDIT -> "بيع آجل"
        SCHEDULED -> "بيع مقسم على تواريخ"
        else -> "فاتورة بيع"
    }
}

object InvoiceStatuses {
    const val PAID = "PAID"
    const val PARTIAL = "PARTIAL"
    const val UNPAID = "UNPAID"
    const val OVERDUE = "OVERDUE"
    const val CANCELLED = "CANCELLED"

    fun arabicLabel(status: String): String = when (status) {
        PAID -> "مدفوعة"
        PARTIAL -> "مدفوعة جزئيًا"
        UNPAID -> "غير مدفوعة"
        OVERDUE -> "متأخرة"
        CANCELLED -> "ملغاة"
        else -> status
    }
}

object InstallmentStatuses {
    const val UPCOMING = "UPCOMING"
    const val DUE = "DUE"
    const val PARTIAL = "PARTIAL"
    const val PAID = "PAID"
    const val OVERDUE = "OVERDUE"

    fun arabicLabel(status: String): String = when (status) {
        UPCOMING -> "لم تستحق"
        DUE -> "مستحقة"
        PARTIAL -> "مدفوعة جزئيًا"
        PAID -> "مدفوعة"
        OVERDUE -> "متأخرة"
        else -> status
    }
}

object DailyInvoiceStatuses {
    const val OPEN = "OPEN"
    const val CLOSED = "CLOSED"
}

object PurchaseTypes {
    const val CASH = "CASH"
    const val CREDIT = "CREDIT"

    fun arabicLabel(type: String): String = when (type) {
        CASH -> "شراء كاش"
        CREDIT -> "شراء آجل"
        else -> "فاتورة شراء"
    }
}

object SupplierTxTypes {
    const val OPENING_BALANCE = "OPENING_BALANCE"
    const val DEBT_ADD = "DEBT_ADD"
    const val PURCHASE_INVOICE = "PURCHASE_INVOICE"
    const val PAYMENT_PARTIAL = "PAYMENT_PARTIAL"
    const val PAYMENT_FULL = "PAYMENT_FULL"
    const val ADJUSTMENT = "ADJUSTMENT"

    fun arabicLabel(type: String): String = when (type) {
        OPENING_BALANCE -> "رصيد افتتاحي"
        DEBT_ADD -> "إضافة مديونية / مشتريات"
        PURCHASE_INVOICE -> "فاتورة شراء"
        PAYMENT_PARTIAL -> "دفعة جزئية لمورد"
        PAYMENT_FULL -> "دفعة كاملة لمورد"
        ADJUSTMENT -> "تسوية / تعديل مراجع"
        else -> type
    }
}

object CustomerTxTypes {
    const val OPENING_BALANCE = "OPENING_BALANCE"
    const val SALE = "SALE"
    const val PAYMENT = "PAYMENT"
    const val DAILY_WITHDRAWAL_CLOSE = "DAILY_WITHDRAWAL_CLOSE"
    const val ADJUSTMENT = "ADJUSTMENT"

    fun arabicLabel(type: String): String = when (type) {
        OPENING_BALANCE -> "رصيد افتتاحي"
        SALE -> "فاتورة بيع"
        PAYMENT -> "تحصيل دفعة"
        DAILY_WITHDRAWAL_CLOSE -> "إغلاق فاتورة سحب يومي"
        ADJUSTMENT -> "تسوية مراجعة"
        else -> type
    }
}

object CustomerAccountStatus {
    const val CLEAR = "CLEAR"       // خالص
    const val OWING = "OWING"       // عليه مديونية
    const val OVERDUE = "OVERDUE"   // متأخر في السداد

    fun arabicLabel(status: String): String = when (status) {
        CLEAR -> "خالص"
        OWING -> "عليه مديونية"
        OVERDUE -> "متأخر في السداد"
        else -> status
    }
}

// =========================================================================
// 15 Relational Room Tables
// =========================================================================

// 1. Tenants
@JsonClass(generateAdapter = true)
@Entity(tableName = "tenants")
data class TenantEntity(
    @PrimaryKey val id: String,
    val shopName: String,
    val ownerName: String,
    val address: String,
    val phone: String,
    val whatsappPhone: String = phone,
    val logoUri: String? = null,
    val currencyCode: String = "EGP",
    val invoiceFooter: String = "شكرًا لتعاملكم معنا",
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

// 2. Users (with Device Binding & 72-Hour Trial Licensing)
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["tenantId"])
    ]
)
data class UserEntity(
    @PrimaryKey val id: String,
    val tenantId: String?, // null for MASTER_ADMIN
    val username: String,
    val displayName: String,
    val role: String, // MASTER_ADMIN, SHOP_OWNER, STAFF
    val passwordSalt: String,
    val passwordHash: String,
    val isActive: Boolean = true,
    val mustChangePassword: Boolean = false,
    val createdAt: Long,
    val lastLoginAt: Long? = null,
    val accountStatus: String = AccountStatuses.ACTIVE, // TRIAL, ACTIVE, EXPIRED, SUSPENDED
    val isTrialAccount: Boolean = false,
    val boundDeviceFingerprint: String? = null,
    val boundDeviceLabel: String? = null,
    val deviceBoundAt: Long? = null,
    val trialStartedAt: Long? = null,
    val trialExpiresAt: Long? = null,
    val lastSeenWallClock: Long? = null,
    val lastSeenElapsedRealtime: Long? = null,
    val accumulatedTrialMillis: Long = 0L,
    val clockTampered: Boolean = false,
    val licenseSignature: String? = null
)

// 3. Customers
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "customers",
    indices = [Index(value = ["tenantId"]), Index(value = ["tenantId", "name"])]
)
data class CustomerEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val name: String,
    val phone: String,
    val whatsappPhone: String,
    val address: String,
    val notes: String,
    val openingBalancePiasters: Long = 0L,
    val totalSalesPiasters: Long = 0L,
    val totalPaidPiasters: Long = 0L,
    val currentBalancePiasters: Long = 0L,
    val creditLimitPiasters: Long = 0L, // 0 = no custom limit (falls back to shop default if set)
    val accountStatus: String = CustomerAccountStatus.CLEAR,
    val createdAt: Long,
    val updatedAt: Long
)

// 4. Suppliers
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "suppliers",
    indices = [Index(value = ["tenantId"]), Index(value = ["tenantId", "name"])]
)
data class SupplierEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val name: String,
    val phone: String,
    val whatsappPhone: String,
    val address: String,
    val notes: String,
    val openingBalancePiasters: Long = 0L,
    val totalDuePiasters: Long = 0L,
    val totalPaidPiasters: Long = 0L,
    val currentBalancePiasters: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long
)

// 5. SalesInvoices
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "sales_invoices",
    indices = [
        Index(value = ["tenantId"]),
        Index(value = ["tenantId", "customerId"]),
        Index(value = ["tenantId", "createdAt"]),
        Index(value = ["tenantId", "idempotencyKey"], unique = true)
    ]
)
data class SalesInvoiceEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val customerId: String,
    val invoiceNumber: String,
    val saleType: String, // CASH, CREDIT, SCHEDULED
    val paymentMethod: String, // CASH, EWALLET, BANK_TRANSFER, NONE
    val totalPiasters: Long,
    val paidPiasters: Long,
    val remainingPiasters: Long,
    val dueDateMillis: Long? = null,
    val notes: String = "",
    val status: String, // PAID, PARTIAL, UNPAID, OVERDUE, CANCELLED
    val sourceDailyInvoiceId: String? = null,
    val idempotencyKey: String,
    val createdByUserId: String,
    val createdAt: Long
)

// 6. SalesInvoiceItems
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "sales_invoice_items",
    indices = [Index(value = ["tenantId"]), Index(value = ["invoiceId"])]
)
data class SalesInvoiceItemEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val invoiceId: String,
    val description: String,
    val unitPricePiasters: Long,
    val quantityMilli: Long, // 1000 = 1.000
    val lineTotalPiasters: Long,
    val sortOrder: Int = 0
)

// 7. Payments (Customer Collections)
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "payments",
    indices = [
        Index(value = ["tenantId"]),
        Index(value = ["tenantId", "customerId"]),
        Index(value = ["tenantId", "invoiceId"]),
        Index(value = ["tenantId", "idempotencyKey"], unique = true)
    ]
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val customerId: String,
    val invoiceId: String? = null,
    val installmentId: String? = null,
    val dailyInvoiceId: String? = null,
    val amountPiasters: Long,
    val paymentMethod: String, // CASH, EWALLET, BANK_TRANSFER
    val excessRecordedAsCredit: Boolean = false,
    val notes: String = "",
    val idempotencyKey: String,
    val createdByUserId: String,
    val createdAt: Long
)

// 8. SupplierTransactions
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "supplier_transactions",
    indices = [
        Index(value = ["tenantId"]),
        Index(value = ["tenantId", "supplierId"]),
        Index(value = ["tenantId", "idempotencyKey"], unique = true)
    ]
)
data class SupplierTransactionEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val supplierId: String,
    val type: String, // OPENING_BALANCE, DEBT_ADD, PAYMENT_PARTIAL, PAYMENT_FULL, ADJUSTMENT
    val amountPiasters: Long, // positive magnitude
    val signedDeltaPiasters: Long, // + increases owed to supplier, - reduces owed to supplier
    val runningBalancePiasters: Long,
    val paymentMethod: String,
    val notes: String = "",
    val idempotencyKey: String,
    val createdByUserId: String,
    val createdAt: Long
)

// 9. CustomerTransactions (Chronological Statement Ledger)
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "customer_transactions",
    indices = [
        Index(value = ["tenantId"]),
        Index(value = ["tenantId", "customerId"]),
        Index(value = ["tenantId", "createdAt"])
    ]
)
data class CustomerTransactionEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val customerId: String,
    val type: String, // OPENING_BALANCE, SALE, PAYMENT, DAILY_WITHDRAWAL_CLOSE, ADJUSTMENT
    val amountPiasters: Long, // positive magnitude
    val signedDeltaPiasters: Long, // + increases customer debt, - decreases customer debt
    val runningBalancePiasters: Long,
    val paymentMethod: String,
    val referenceId: String? = null, // invoiceId, paymentId, or dailyInvoiceId
    val description: String = "",
    val createdByUserId: String,
    val createdAt: Long
)

// 10. DailyWithdrawalInvoices
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "daily_withdrawal_invoices",
    indices = [
        Index(value = ["tenantId"]),
        Index(value = ["tenantId", "customerId"]),
        Index(value = ["tenantId", "status"])
    ]
)
data class DailyWithdrawalInvoiceEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val customerId: String,
    val invoiceNumber: String,
    val invoiceDateEpochDay: Long, // YYYYMMDD in Africa/Cairo
    val status: String, // OPEN, CLOSED
    val withdrawalsCount: Int = 0,
    val totalQuantityMilli: Long = 0L,
    val totalPiasters: Long = 0L,
    val paidPiasters: Long = 0L,
    val remainingPiasters: Long = 0L,
    val isPostedToLedger: Boolean = false,
    val postedTotalPiasters: Long = 0L, // Tracks exact amount already posted to prevent double-counting on reopen
    val linkedSalesInvoiceId: String? = null,
    val reopenCount: Int = 0,
    val lastReopenReason: String? = null,
    val createdByUserId: String,
    val createdAt: Long,
    val closedAt: Long? = null
)

// 11. DailyWithdrawalItems
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "daily_withdrawal_items",
    indices = [Index(value = ["tenantId"]), Index(value = ["dailyInvoiceId"])]
)
data class DailyWithdrawalItemEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val dailyInvoiceId: String,
    val description: String,
    val unitPricePiasters: Long,
    val quantityMilli: Long,
    val lineTotalPiasters: Long,
    val notes: String = "",
    val recordedAtMillis: Long, // exact timestamp down to second
    val createdByUserId: String
)

// 12. InstallmentSchedules
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "installment_schedules",
    indices = [
        Index(value = ["tenantId"]),
        Index(value = ["tenantId", "invoiceId"]),
        Index(value = ["tenantId", "customerId"]),
        Index(value = ["tenantId", "dueDateMillis"])
    ]
)
data class InstallmentScheduleEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val invoiceId: String,
    val customerId: String,
    val installmentIndex: Int,
    val dueDateMillis: Long,
    val amountPiasters: Long,
    val paidPiasters: Long = 0L,
    val remainingPiasters: Long,
    val status: String // UPCOMING, DUE, PARTIAL, PAID, OVERDUE
)

// 13. BackupHistory
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "backup_history",
    indices = [Index(value = ["tenantId"])]
)
data class BackupHistoryEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val destination: String, // LOCAL, GOOGLE_DRIVE
    val status: String, // SUCCESS, FAILED
    val fileName: String,
    val driveFileId: String? = null,
    val sizeBytes: Long = 0L,
    val message: String = "",
    val createdByUserId: String,
    val createdAt: Long
)

// 14. AuditLogs
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "audit_logs",
    indices = [Index(value = ["tenantId"]), Index(value = ["createdAt"])]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val tenantId: String?, // null for master-admin global events
    val actorUserId: String,
    val actorUsername: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val details: String,
    val createdAt: Long
)

// 15. AppSettings
@JsonClass(generateAdapter = true)
@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val tenantId: String,
    val darkMode: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val autoBackupEnabled: Boolean = false,
    val autoBackupHour: Int = 23,
    val autoBackupMinute: Int = 0,
    val googleAccountEmail: String? = null,
    val whatsappCountryCode: String = "20",
    val invoiceSignatureName: String = "",
    val defaultCurrencyLabel: String = "ج.م",
    val defaultCustomerCreditLimitPiasters: Long = 0L, // 0 = disabled unless set per customer
    val updatedAt: Long = System.currentTimeMillis()
)

// 16. DeviceTrialRegistry (Tamper-Signed Per-Device 72-Hour Trial Anchor)
@JsonClass(generateAdapter = true)
@Entity(tableName = "device_trial_registry")
data class DeviceTrialRegistryEntity(
    @PrimaryKey val deviceFingerprint: String,
    val deviceLabel: String,
    val trialStartedAt: Long,
    val trialExpiresAt: Long,
    val lastSeenWallClock: Long,
    val lastSeenElapsedRealtime: Long,
    val accumulatedTrialMillis: Long = 0L,
    val clockTampered: Boolean = false,
    val isRevokedOrExpired: Boolean = false,
    val signatureHmac: String
)

// 17. Products (Optional Shop Items & Inventory Catalog)
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "products",
    indices = [
        Index(value = ["tenantId"]),
        Index(value = ["tenantId", "name"])
    ]
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val name: String,
    val category: String = "عام",
    val skuOrBarcode: String = "",
    val unitLabel: String = "قطعة",
    val salePricePiasters: Long = 0L,
    val purchasePricePiasters: Long = 0L,
    val stockQuantityMilli: Long = 0L, // 1000 = 1.000
    val trackStock: Boolean = false,
    val minStockAlertMilli: Long = 0L,
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

// 18. PurchaseInvoices
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "purchase_invoices",
    indices = [
        Index(value = ["tenantId"]),
        Index(value = ["tenantId", "supplierId"]),
        Index(value = ["tenantId", "createdAt"]),
        Index(value = ["tenantId", "idempotencyKey"], unique = true)
    ]
)
data class PurchaseInvoiceEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val supplierId: String,
    val invoiceNumber: String,
    val purchaseType: String, // CASH, CREDIT
    val paymentMethod: String, // CASH, EWALLET, BANK_TRANSFER, NONE
    val totalPiasters: Long,
    val paidPiasters: Long,
    val remainingPiasters: Long,
    val dueDateMillis: Long? = null,
    val notes: String = "",
    val status: String, // PAID, PARTIAL, UNPAID
    val idempotencyKey: String,
    val createdByUserId: String,
    val createdAt: Long
)

// 19. PurchaseInvoiceItems
@JsonClass(generateAdapter = true)
@Entity(
    tableName = "purchase_invoice_items",
    indices = [Index(value = ["tenantId"]), Index(value = ["purchaseInvoiceId"])]
)
data class PurchaseInvoiceItemEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val purchaseInvoiceId: String,
    val description: String,
    val unitPricePiasters: Long,
    val quantityMilli: Long, // 1000 = 1.000
    val lineTotalPiasters: Long,
    val sortOrder: Int = 0
)
