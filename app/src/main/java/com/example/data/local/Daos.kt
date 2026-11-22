package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TenantDao {
    @Query("SELECT * FROM tenants ORDER BY createdAt DESC")
    fun observeAllTenants(): Flow<List<TenantEntity>>

    @Query("SELECT * FROM tenants ORDER BY createdAt DESC")
    suspend fun getAllTenants(): List<TenantEntity>

    @Query("SELECT * FROM tenants WHERE id = :tenantId LIMIT 1")
    fun observeTenant(tenantId: String): Flow<TenantEntity?>

    @Query("SELECT * FROM tenants WHERE id = :tenantId LIMIT 1")
    suspend fun getTenantById(tenantId: String): TenantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTenant(tenant: TenantEntity)

    @Update
    suspend fun updateTenant(tenant: TenantEntity)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getByUsername(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE role = 'MASTER_ADMIN' LIMIT 1")
    suspend fun getMasterAdmin(): UserEntity?

    @Query("SELECT * FROM users WHERE isTrialAccount = 1 LIMIT 1")
    suspend fun getTrialUser(): UserEntity?

    @Query("SELECT * FROM users WHERE tenantId = :tenantId ORDER BY createdAt ASC")
    fun observeUsersForTenant(tenantId: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE tenantId = :tenantId ORDER BY createdAt ASC")
    suspend fun getUsersForTenant(tenantId: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE role != 'MASTER_ADMIN' ORDER BY createdAt DESC")
    fun observeAllShopUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUsers(users: List<UserEntity>)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE tenantId = :tenantId ORDER BY name COLLATE NOCASE ASC")
    fun observeCustomers(tenantId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE tenantId = :tenantId ORDER BY name COLLATE NOCASE ASC")
    suspend fun getCustomers(tenantId: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE tenantId = :tenantId AND id = :customerId LIMIT 1")
    fun observeCustomer(tenantId: String, customerId: String): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE tenantId = :tenantId AND id = :customerId LIMIT 1")
    suspend fun getCustomerById(tenantId: String, customerId: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomer(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomers(customers: List<CustomerEntity>)
}

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers WHERE tenantId = :tenantId ORDER BY name COLLATE NOCASE ASC")
    fun observeSuppliers(tenantId: String): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE tenantId = :tenantId ORDER BY name COLLATE NOCASE ASC")
    suspend fun getSuppliers(tenantId: String): List<SupplierEntity>

    @Query("SELECT * FROM suppliers WHERE tenantId = :tenantId AND id = :supplierId LIMIT 1")
    fun observeSupplier(tenantId: String, supplierId: String): Flow<SupplierEntity?>

    @Query("SELECT * FROM suppliers WHERE tenantId = :tenantId AND id = :supplierId LIMIT 1")
    suspend fun getSupplierById(tenantId: String, supplierId: String): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSupplier(supplier: SupplierEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSuppliers(suppliers: List<SupplierEntity>)
}

@Dao
interface SalesInvoiceDao {
    @Query("SELECT * FROM sales_invoices WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    fun observeInvoices(tenantId: String): Flow<List<SalesInvoiceEntity>>

    @Query("SELECT * FROM sales_invoices WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    suspend fun getInvoices(tenantId: String): List<SalesInvoiceEntity>

    @Query("SELECT * FROM sales_invoices WHERE tenantId = :tenantId AND customerId = :customerId ORDER BY createdAt DESC")
    fun observeCustomerInvoices(tenantId: String, customerId: String): Flow<List<SalesInvoiceEntity>>

    @Query("SELECT * FROM sales_invoices WHERE tenantId = :tenantId AND customerId = :customerId ORDER BY createdAt ASC")
    suspend fun getCustomerInvoices(tenantId: String, customerId: String): List<SalesInvoiceEntity>

    @Query("SELECT * FROM sales_invoices WHERE tenantId = :tenantId AND id = :invoiceId LIMIT 1")
    fun observeInvoiceById(tenantId: String, invoiceId: String): Flow<SalesInvoiceEntity?>

    @Query("SELECT * FROM sales_invoices WHERE tenantId = :tenantId AND id = :invoiceId LIMIT 1")
    suspend fun getInvoiceById(tenantId: String, invoiceId: String): SalesInvoiceEntity?

    @Query("SELECT * FROM sales_invoices WHERE tenantId = :tenantId AND idempotencyKey = :key LIMIT 1")
    suspend fun getByIdempotencyKey(tenantId: String, key: String): SalesInvoiceEntity?

    @Query("SELECT COUNT(*) FROM sales_invoices WHERE tenantId = :tenantId")
    suspend fun countInvoices(tenantId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoice(invoice: SalesInvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoices(invoices: List<SalesInvoiceEntity>)

    @Query("SELECT * FROM sales_invoice_items WHERE tenantId = :tenantId AND invoiceId = :invoiceId ORDER BY sortOrder ASC")
    fun observeInvoiceItems(tenantId: String, invoiceId: String): Flow<List<SalesInvoiceItemEntity>>

    @Query("SELECT * FROM sales_invoice_items WHERE tenantId = :tenantId AND invoiceId = :invoiceId ORDER BY sortOrder ASC")
    suspend fun getInvoiceItems(tenantId: String, invoiceId: String): List<SalesInvoiceItemEntity>

    @Query("SELECT * FROM sales_invoice_items WHERE tenantId = :tenantId")
    suspend fun getAllInvoiceItems(tenantId: String): List<SalesInvoiceItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoiceItems(items: List<SalesInvoiceItemEntity>)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    fun observePayments(tenantId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    suspend fun getPayments(tenantId: String): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE tenantId = :tenantId AND customerId = :customerId ORDER BY createdAt DESC")
    fun observeCustomerPayments(tenantId: String, customerId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE tenantId = :tenantId AND customerId = :customerId ORDER BY createdAt ASC")
    suspend fun getCustomerPayments(tenantId: String, customerId: String): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE tenantId = :tenantId AND idempotencyKey = :key LIMIT 1")
    suspend fun getByIdempotencyKey(tenantId: String, key: String): PaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayment(payment: PaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPayments(payments: List<PaymentEntity>)
}

@Dao
interface TransactionDao {
    // Supplier Transactions
    @Query("SELECT * FROM supplier_transactions WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    fun observeAllSupplierTransactions(tenantId: String): Flow<List<SupplierTransactionEntity>>

    @Query("SELECT * FROM supplier_transactions WHERE tenantId = :tenantId ORDER BY createdAt ASC")
    suspend fun getAllSupplierTransactions(tenantId: String): List<SupplierTransactionEntity>

    @Query("SELECT * FROM supplier_transactions WHERE tenantId = :tenantId AND supplierId = :supplierId ORDER BY createdAt DESC")
    fun observeSupplierTransactions(tenantId: String, supplierId: String): Flow<List<SupplierTransactionEntity>>

    @Query("SELECT * FROM supplier_transactions WHERE tenantId = :tenantId AND supplierId = :supplierId ORDER BY createdAt ASC")
    suspend fun getSupplierTransactionsAsc(tenantId: String, supplierId: String): List<SupplierTransactionEntity>

    @Query("SELECT * FROM supplier_transactions WHERE tenantId = :tenantId AND idempotencyKey = :key LIMIT 1")
    suspend fun getSupplierTxByIdempotencyKey(tenantId: String, key: String): SupplierTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSupplierTransaction(tx: SupplierTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSupplierTransactions(txs: List<SupplierTransactionEntity>)

    // Customer Transactions
    @Query("SELECT * FROM customer_transactions WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    fun observeAllCustomerTransactions(tenantId: String): Flow<List<CustomerTransactionEntity>>

    @Query("SELECT * FROM customer_transactions WHERE tenantId = :tenantId ORDER BY createdAt ASC")
    suspend fun getAllCustomerTransactions(tenantId: String): List<CustomerTransactionEntity>

    @Query("SELECT * FROM customer_transactions WHERE tenantId = :tenantId AND customerId = :customerId ORDER BY createdAt DESC")
    fun observeCustomerTransactions(tenantId: String, customerId: String): Flow<List<CustomerTransactionEntity>>

    @Query("SELECT * FROM customer_transactions WHERE tenantId = :tenantId AND customerId = :customerId ORDER BY createdAt ASC")
    suspend fun getCustomerTransactionsAsc(tenantId: String, customerId: String): List<CustomerTransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomerTransaction(tx: CustomerTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomerTransactions(txs: List<CustomerTransactionEntity>)
}

@Dao
interface DailyWithdrawalDao {
    @Query("SELECT * FROM daily_withdrawal_invoices WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    fun observeAllDailyInvoices(tenantId: String): Flow<List<DailyWithdrawalInvoiceEntity>>

    @Query("SELECT * FROM daily_withdrawal_invoices WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    suspend fun getAllDailyInvoices(tenantId: String): List<DailyWithdrawalInvoiceEntity>

    @Query("SELECT * FROM daily_withdrawal_invoices WHERE tenantId = :tenantId AND customerId = :customerId ORDER BY createdAt DESC")
    fun observeCustomerDailyInvoices(tenantId: String, customerId: String): Flow<List<DailyWithdrawalInvoiceEntity>>

    @Query("SELECT * FROM daily_withdrawal_invoices WHERE tenantId = :tenantId AND id = :dailyInvoiceId LIMIT 1")
    fun observeDailyInvoiceById(tenantId: String, dailyInvoiceId: String): Flow<DailyWithdrawalInvoiceEntity?>

    @Query("SELECT * FROM daily_withdrawal_invoices WHERE tenantId = :tenantId AND id = :dailyInvoiceId LIMIT 1")
    suspend fun getDailyInvoiceById(tenantId: String, dailyInvoiceId: String): DailyWithdrawalInvoiceEntity?

    @Query("SELECT COUNT(*) FROM daily_withdrawal_invoices WHERE tenantId = :tenantId")
    suspend fun countDailyInvoices(tenantId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyInvoice(invoice: DailyWithdrawalInvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyInvoices(invoices: List<DailyWithdrawalInvoiceEntity>)

    @Query("SELECT * FROM daily_withdrawal_items WHERE tenantId = :tenantId AND dailyInvoiceId = :dailyInvoiceId ORDER BY recordedAtMillis ASC")
    fun observeDailyItems(tenantId: String, dailyInvoiceId: String): Flow<List<DailyWithdrawalItemEntity>>

    @Query("SELECT * FROM daily_withdrawal_items WHERE tenantId = :tenantId AND dailyInvoiceId = :dailyInvoiceId ORDER BY recordedAtMillis ASC")
    suspend fun getDailyItems(tenantId: String, dailyInvoiceId: String): List<DailyWithdrawalItemEntity>

    @Query("SELECT * FROM daily_withdrawal_items WHERE tenantId = :tenantId ORDER BY recordedAtMillis ASC")
    suspend fun getAllDailyItems(tenantId: String): List<DailyWithdrawalItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyItem(item: DailyWithdrawalItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyItems(items: List<DailyWithdrawalItemEntity>)
}

@Dao
interface InstallmentDao {
    @Query("SELECT * FROM installment_schedules WHERE tenantId = :tenantId ORDER BY dueDateMillis ASC")
    fun observeAllInstallments(tenantId: String): Flow<List<InstallmentScheduleEntity>>

    @Query("SELECT * FROM installment_schedules WHERE tenantId = :tenantId ORDER BY dueDateMillis ASC")
    suspend fun getAllInstallments(tenantId: String): List<InstallmentScheduleEntity>

    @Query("SELECT * FROM installment_schedules WHERE tenantId = :tenantId AND invoiceId = :invoiceId ORDER BY installmentIndex ASC")
    fun observeInvoiceInstallments(tenantId: String, invoiceId: String): Flow<List<InstallmentScheduleEntity>>

    @Query("SELECT * FROM installment_schedules WHERE tenantId = :tenantId AND invoiceId = :invoiceId ORDER BY installmentIndex ASC")
    suspend fun getInvoiceInstallments(tenantId: String, invoiceId: String): List<InstallmentScheduleEntity>

    @Query("SELECT * FROM installment_schedules WHERE tenantId = :tenantId AND customerId = :customerId ORDER BY dueDateMillis ASC")
    fun observeCustomerInstallments(tenantId: String, customerId: String): Flow<List<InstallmentScheduleEntity>>

    @Query("SELECT * FROM installment_schedules WHERE tenantId = :tenantId AND customerId = :customerId ORDER BY dueDateMillis ASC")
    suspend fun getCustomerInstallments(tenantId: String, customerId: String): List<InstallmentScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstallment(installment: InstallmentScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstallments(installments: List<InstallmentScheduleEntity>)
}

@Dao
interface SystemDao {
    // Backup History
    @Query("SELECT * FROM backup_history WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    fun observeBackupHistory(tenantId: String): Flow<List<BackupHistoryEntity>>

    @Query("SELECT * FROM backup_history ORDER BY createdAt DESC")
    fun observeAllBackupHistory(): Flow<List<BackupHistoryEntity>>

    @Query("SELECT * FROM backup_history WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    suspend fun getBackupHistory(tenantId: String): List<BackupHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBackupRecord(record: BackupHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBackupRecords(records: List<BackupHistoryEntity>)

    // Audit Logs
    @Query("SELECT * FROM audit_logs WHERE tenantId = :tenantId ORDER BY createdAt DESC LIMIT 250")
    fun observeTenantAuditLogs(tenantId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC LIMIT 250")
    fun observeAllAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    suspend fun getTenantAuditLogs(tenantId: String): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAuditLogs(logs: List<AuditLogEntity>)

    // App Settings
    @Query("SELECT * FROM app_settings WHERE tenantId = :tenantId LIMIT 1")
    fun observeAppSettings(tenantId: String): Flow<AppSettingEntity?>

    @Query("SELECT * FROM app_settings WHERE tenantId = :tenantId LIMIT 1")
    suspend fun getAppSettings(tenantId: String): AppSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppSettings(settings: AppSettingEntity)
}

@Dao
interface DeviceTrialDao {
    @Query("SELECT * FROM device_trial_registry WHERE deviceFingerprint = :fingerprint LIMIT 1")
    suspend fun getByDeviceFingerprint(fingerprint: String): DeviceTrialRegistryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecord(record: DeviceTrialRegistryEntity)

    @Query("DELETE FROM device_trial_registry WHERE deviceFingerprint = :fingerprint")
    suspend fun deleteByDeviceFingerprint(fingerprint: String)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE tenantId = :tenantId AND isActive = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeProducts(tenantId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE tenantId = :tenantId ORDER BY name COLLATE NOCASE ASC")
    suspend fun getProducts(tenantId: String): List<ProductEntity>

    @Query("SELECT * FROM products WHERE tenantId = :tenantId AND id = :productId LIMIT 1")
    suspend fun getProductById(tenantId: String, productId: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(products: List<ProductEntity>)

    @Query("DELETE FROM products WHERE tenantId = :tenantId AND id = :productId")
    suspend fun deleteProduct(tenantId: String, productId: String)
}

@Dao
interface PurchaseInvoiceDao {
    @Query("SELECT * FROM purchase_invoices WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    fun observePurchaseInvoices(tenantId: String): Flow<List<PurchaseInvoiceEntity>>

    @Query("SELECT * FROM purchase_invoices WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    suspend fun getPurchaseInvoices(tenantId: String): List<PurchaseInvoiceEntity>

    @Query("SELECT * FROM purchase_invoices WHERE tenantId = :tenantId AND supplierId = :supplierId ORDER BY createdAt DESC")
    fun observeSupplierPurchaseInvoices(tenantId: String, supplierId: String): Flow<List<PurchaseInvoiceEntity>>

    @Query("SELECT * FROM purchase_invoices WHERE tenantId = :tenantId AND id = :purchaseId LIMIT 1")
    fun observePurchaseInvoiceById(tenantId: String, purchaseId: String): Flow<PurchaseInvoiceEntity?>

    @Query("SELECT * FROM purchase_invoices WHERE tenantId = :tenantId AND id = :purchaseId LIMIT 1")
    suspend fun getPurchaseInvoiceById(tenantId: String, purchaseId: String): PurchaseInvoiceEntity?

    @Query("SELECT * FROM purchase_invoices WHERE tenantId = :tenantId AND idempotencyKey = :key LIMIT 1")
    suspend fun getByIdempotencyKey(tenantId: String, key: String): PurchaseInvoiceEntity?

    @Query("SELECT COUNT(*) FROM purchase_invoices WHERE tenantId = :tenantId")
    suspend fun countPurchaseInvoices(tenantId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPurchaseInvoice(invoice: PurchaseInvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPurchaseInvoices(invoices: List<PurchaseInvoiceEntity>)

    @Query("SELECT * FROM purchase_invoice_items WHERE tenantId = :tenantId AND purchaseInvoiceId = :purchaseId ORDER BY sortOrder ASC")
    fun observePurchaseInvoiceItems(tenantId: String, purchaseId: String): Flow<List<PurchaseInvoiceItemEntity>>

    @Query("SELECT * FROM purchase_invoice_items WHERE tenantId = :tenantId AND purchaseInvoiceId = :purchaseId ORDER BY sortOrder ASC")
    suspend fun getPurchaseInvoiceItems(tenantId: String, purchaseId: String): List<PurchaseInvoiceItemEntity>

    @Query("SELECT * FROM purchase_invoice_items WHERE tenantId = :tenantId")
    suspend fun getAllPurchaseInvoiceItems(tenantId: String): List<PurchaseInvoiceItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPurchaseInvoiceItems(items: List<PurchaseInvoiceItemEntity>)
}

@Dao
interface QuickSaleDao {
    @Query("SELECT * FROM quick_sales WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    fun observeQuickSales(tenantId: String): Flow<List<QuickSaleEntity>>

    @Query("SELECT * FROM quick_sales WHERE tenantId = :tenantId ORDER BY createdAt DESC")
    suspend fun getQuickSales(tenantId: String): List<QuickSaleEntity>

    @Query("SELECT * FROM quick_sales WHERE tenantId = :tenantId AND id = :quickSaleId LIMIT 1")
    suspend fun getQuickSaleById(tenantId: String, quickSaleId: String): QuickSaleEntity?

    @Query("SELECT * FROM quick_sales WHERE tenantId = :tenantId AND idempotencyKey = :key LIMIT 1")
    suspend fun getByIdempotencyKey(tenantId: String, key: String): QuickSaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuickSale(sale: QuickSaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuickSales(sales: List<QuickSaleEntity>)

    @Query("DELETE FROM quick_sales WHERE tenantId = :tenantId AND id = :quickSaleId")
    suspend fun deleteQuickSale(tenantId: String, quickSaleId: String)
}
