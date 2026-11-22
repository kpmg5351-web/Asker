package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TenantEntity::class,
        UserEntity::class,
        CustomerEntity::class,
        SupplierEntity::class,
        SalesInvoiceEntity::class,
        SalesInvoiceItemEntity::class,
        PaymentEntity::class,
        SupplierTransactionEntity::class,
        CustomerTransactionEntity::class,
        DailyWithdrawalInvoiceEntity::class,
        DailyWithdrawalItemEntity::class,
        InstallmentScheduleEntity::class,
        BackupHistoryEntity::class,
        AuditLogEntity::class,
        AppSettingEntity::class,
        DeviceTrialRegistryEntity::class,
        ProductEntity::class,
        PurchaseInvoiceEntity::class,
        PurchaseInvoiceItemEntity::class,
        QuickSaleEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class HesabatyDatabase : RoomDatabase() {
    abstract fun tenantDao(): TenantDao
    abstract fun userDao(): UserDao
    abstract fun customerDao(): CustomerDao
    abstract fun supplierDao(): SupplierDao
    abstract fun salesInvoiceDao(): SalesInvoiceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun transactionDao(): TransactionDao
    abstract fun dailyWithdrawalDao(): DailyWithdrawalDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun systemDao(): SystemDao
    abstract fun deviceTrialDao(): DeviceTrialDao
    abstract fun productDao(): ProductDao
    abstract fun purchaseInvoiceDao(): PurchaseInvoiceDao
    abstract fun quickSaleDao(): QuickSaleDao

    companion object {
        private const val DB_NAME = "hesabaty_accounting_v1.db"

        /**
         * Non-destructive migration preserving 100% of existing accounting data
         * while adding device binding and 72-hour trial licensing support.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN accountStatus TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE users ADD COLUMN isTrialAccount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN boundDeviceFingerprint TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN boundDeviceLabel TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN deviceBoundAt INTEGER")
                db.execSQL("ALTER TABLE users ADD COLUMN trialStartedAt INTEGER")
                db.execSQL("ALTER TABLE users ADD COLUMN trialExpiresAt INTEGER")
                db.execSQL("ALTER TABLE users ADD COLUMN lastSeenWallClock INTEGER")
                db.execSQL("ALTER TABLE users ADD COLUMN lastSeenElapsedRealtime INTEGER")
                db.execSQL("ALTER TABLE users ADD COLUMN accumulatedTrialMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN clockTampered INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE users ADD COLUMN licenseSignature TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `device_trial_registry` (
                        `deviceFingerprint` TEXT NOT NULL,
                        `deviceLabel` TEXT NOT NULL,
                        `trialStartedAt` INTEGER NOT NULL,
                        `trialExpiresAt` INTEGER NOT NULL,
                        `lastSeenWallClock` INTEGER NOT NULL,
                        `lastSeenElapsedRealtime` INTEGER NOT NULL,
                        `accumulatedTrialMillis` INTEGER NOT NULL DEFAULT 0,
                        `clockTampered` INTEGER NOT NULL DEFAULT 0,
                        `isRevokedOrExpired` INTEGER NOT NULL DEFAULT 0,
                        `signatureHmac` TEXT NOT NULL,
                        PRIMARY KEY(`deviceFingerprint`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Non-destructive migration adding optional Products / Items catalog table.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `products` (
                        `id` TEXT NOT NULL,
                        `tenantId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL DEFAULT 'عام',
                        `skuOrBarcode` TEXT NOT NULL DEFAULT '',
                        `unitLabel` TEXT NOT NULL DEFAULT 'قطعة',
                        `salePricePiasters` INTEGER NOT NULL DEFAULT 0,
                        `purchasePricePiasters` INTEGER NOT NULL DEFAULT 0,
                        `stockQuantityMilli` INTEGER NOT NULL DEFAULT 0,
                        `trackStock` INTEGER NOT NULL DEFAULT 0,
                        `minStockAlertMilli` INTEGER NOT NULL DEFAULT 0,
                        `notes` TEXT NOT NULL DEFAULT '',
                        `isActive` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_tenantId` ON `products` (`tenantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_tenantId_name` ON `products` (`tenantId`, `name`)")
            }
        }

        /**
         * Non-destructive migration adding per-customer and shop-wide default debt limit alerts.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN creditLimitPiasters INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN defaultCustomerCreditLimitPiasters INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Non-destructive migration adding Purchases & Purchase Invoices tables.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `purchase_invoices` (
                        `id` TEXT NOT NULL,
                        `tenantId` TEXT NOT NULL,
                        `supplierId` TEXT NOT NULL,
                        `invoiceNumber` TEXT NOT NULL,
                        `purchaseType` TEXT NOT NULL,
                        `paymentMethod` TEXT NOT NULL,
                        `totalPiasters` INTEGER NOT NULL,
                        `paidPiasters` INTEGER NOT NULL,
                        `remainingPiasters` INTEGER NOT NULL,
                        `dueDateMillis` INTEGER,
                        `notes` TEXT NOT NULL DEFAULT '',
                        `status` TEXT NOT NULL,
                        `idempotencyKey` TEXT NOT NULL,
                        `createdByUserId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoices_tenantId` ON `purchase_invoices` (`tenantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoices_tenantId_supplierId` ON `purchase_invoices` (`tenantId`, `supplierId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoices_tenantId_createdAt` ON `purchase_invoices` (`tenantId`, `createdAt`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchase_invoices_tenantId_idempotencyKey` ON `purchase_invoices` (`tenantId`, `idempotencyKey`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `purchase_invoice_items` (
                        `id` TEXT NOT NULL,
                        `tenantId` TEXT NOT NULL,
                        `purchaseInvoiceId` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `unitPricePiasters` INTEGER NOT NULL,
                        `quantityMilli` INTEGER NOT NULL,
                        `lineTotalPiasters` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_items_tenantId` ON `purchase_invoice_items` (`tenantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_invoice_items_purchaseInvoiceId` ON `purchase_invoice_items` (`purchaseInvoiceId`)")
            }
        }

        /**
         * Non-destructive migration adding Quick Sales (customer-free price-only withdrawals).
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `quick_sales` (
                        `id` TEXT NOT NULL,
                        `tenantId` TEXT NOT NULL,
                        `amountPiasters` INTEGER NOT NULL,
                        `description` TEXT NOT NULL DEFAULT '',
                        `paymentMethod` TEXT NOT NULL DEFAULT 'CASH',
                        `idempotencyKey` TEXT NOT NULL,
                        `createdByUserId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quick_sales_tenantId` ON `quick_sales` (`tenantId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quick_sales_tenantId_createdAt` ON `quick_sales` (`tenantId`, `createdAt`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_quick_sales_tenantId_idempotencyKey` ON `quick_sales` (`tenantId`, `idempotencyKey`)")
            }
        }

        @Volatile
        private var INSTANCE: HesabatyDatabase? = null

        fun getInstance(context: Context): HesabatyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HesabatyDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun createInMemory(context: Context): HesabatyDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                HesabatyDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
