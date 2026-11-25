package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.EgyptTime
import com.example.core.MoneyFormatter
import com.example.core.PhoneUtils
import com.example.data.local.AppSettingEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.BackupHistoryEntity
import com.example.data.local.CustomerAccountStatus
import com.example.data.local.CustomerEntity
import com.example.data.local.CustomerTransactionEntity
import com.example.data.local.CustomerTxTypes
import com.example.data.local.DailyInvoiceStatuses
import com.example.data.local.DailyWithdrawalInvoiceEntity
import com.example.data.local.DailyWithdrawalItemEntity
import com.example.data.local.HesabatyDatabase
import com.example.data.local.InstallmentScheduleEntity
import com.example.data.local.InstallmentStatuses
import com.example.data.local.InvoiceStatuses
import com.example.data.local.PaymentEntity
import com.example.data.local.PaymentMethods
import com.example.data.local.ProductEntity
import com.example.data.local.PurchaseInvoiceEntity
import com.example.data.local.PurchaseInvoiceItemEntity
import com.example.data.local.PurchaseTypes
import com.example.data.local.QuickSaleEntity
import com.example.data.local.SaleTypes
import com.example.data.local.SalesInvoiceEntity
import com.example.data.local.SalesInvoiceItemEntity
import com.example.data.local.SupplierEntity
import com.example.data.local.SupplierTransactionEntity
import com.example.data.local.SupplierTxTypes
import com.example.data.local.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

sealed class RepoResult<out T> {
    data class Success<T>(val data: T) : RepoResult<T>()
    data class Error(val message: String) : RepoResult<Nothing>()
}

data class SaleLineDraft(
    val description: String,
    val unitPricePiasters: Long,
    val quantityMilli: Long = 1000L,
    val productId: String? = null
) {
    val lineTotalPiasters: Long
        get() = MoneyFormatter.calculateLineTotalPiasters(unitPricePiasters, quantityMilli)
}

data class PurchaseLineDraft(
    val description: String,
    val unitPricePiasters: Long,
    val quantityMilli: Long = 1000L,
    val productId: String? = null
) {
    val lineTotalPiasters: Long
        get() = MoneyFormatter.calculateLineTotalPiasters(unitPricePiasters, quantityMilli)
}

data class InstallmentDraft(
    val dueDateMillis: Long,
    val amountPiasters: Long
)

data class CustomerSettlementSummary(
    val customerId: String,
    val closedOpenDailyCount: Int,
    val settledPreviousBalancePiasters: Long,
    val paidAmountPiasters: Long,
    val discountPiasters: Long,
    val finalBalancePiasters: Long
)

data class DashboardSummary(
    val totalIncomingPiasters: Long = 0L,
    val totalOutgoingPiasters: Long = 0L,
    val totalNetBalancePiasters: Long = 0L,
    val todayIncomingPiasters: Long = 0L,
    val totalCustomerReceivablesPiasters: Long = 0L,
    val totalSupplierPayablesPiasters: Long = 0L,
    val todaySalesPiasters: Long = 0L,
    val todayCashCollectionsPiasters: Long = 0L,
    val todayEWalletCollectionsPiasters: Long = 0L,
    val todaySupplierPaymentsPiasters: Long = 0L,
    val todayNetCashflowPiasters: Long = 0L,
    val customersCount: Int = 0,
    val suppliersCount: Int = 0,
    val overdueCustomersCount: Int = 0,
    val openDailyInvoicesCount: Int = 0
)

enum class AlertSeverity { INFO, WARNING, DANGER, SUCCESS }

data class AppAlert(
    val id: String,
    val title: String,
    val message: String,
    val severity: AlertSeverity,
    val targetRoute: String? = null
)

data class CreditLimitBreach(
    val customer: CustomerEntity,
    val effectiveLimitPiasters: Long,
    val excessPiasters: Long,
    val isCustomLimit: Boolean
)

enum class ReportType(val titleAr: String) {
    DAILY_SALES("تقرير المبيعات اليومية"),
    MONTHLY_SALES("تقرير المبيعات الشهرية"),
    PERIOD_SALES("تقرير المبيعات خلال فترة"),
    CASH_SALES("تقرير المبيعات النقدية"),
    EWALLET_SALES("تقرير المبيعات بالمحافظ الإلكترونية"),
    CREDIT_SALES("تقرير المبيعات الآجلة والمقسمة"),
    OVERDUE("تقرير المتأخرات"),
    CUSTOMER_COLLECTIONS("تقرير تحصيلات العملاء"),
    SUPPLIER_PAYMENTS("تقرير مدفوعات الموردين"),
    CUSTOMER_ACCOUNTS("تقرير حسابات العملاء"),
    SUPPLIER_ACCOUNTS("تقرير حسابات الموردين"),
    DAILY_WITHDRAWALS("تقرير السحوبات اليومية"),
    INVOICES_OPEN_CLOSED("تقرير الفواتير المفتوحة والمغلقة"),
    PURCHASES("تقرير المشتريات وفواتير الشراء"),
    QUICK_SALES("تقرير قسم المبيعات (بدون عميل)")
}

data class ReportOutput(
    val type: ReportType,
    val periodLabel: String,
    val summaryPairs: List<Pair<String, String>>,
    val columns: List<String>,
    val rows: List<List<String>>
)

class AccountingRepository(private val db: HesabatyDatabase) {

    private val customerDao = db.customerDao()
    private val supplierDao = db.supplierDao()
    private val salesInvoiceDao = db.salesInvoiceDao()
    private val paymentDao = db.paymentDao()
    private val transactionDao = db.transactionDao()
    private val dailyDao = db.dailyWithdrawalDao()
    private val installmentDao = db.installmentDao()
    private val systemDao = db.systemDao()
    private val productDao = db.productDao()
    private val purchaseInvoiceDao = db.purchaseInvoiceDao()
    private val quickSaleDao = db.quickSaleDao()

    // =========================================================================
    // Reactive Flows (Strictly Tenant-Isolated)
    // =========================================================================

    fun observeCustomers(tenantId: String): Flow<List<CustomerEntity>> = customerDao.observeCustomers(tenantId)
    fun observeCustomer(tenantId: String, customerId: String): Flow<CustomerEntity?> = customerDao.observeCustomer(tenantId, customerId)
    fun observeCustomerInvoices(tenantId: String, customerId: String) = salesInvoiceDao.observeCustomerInvoices(tenantId, customerId)
    fun observeCustomerPayments(tenantId: String, customerId: String) = paymentDao.observeCustomerPayments(tenantId, customerId)
    fun observeCustomerTransactions(tenantId: String, customerId: String) = transactionDao.observeCustomerTransactions(tenantId, customerId)
    fun observeCustomerInstallments(tenantId: String, customerId: String) = installmentDao.observeCustomerInstallments(tenantId, customerId)

    fun observeSuppliers(tenantId: String): Flow<List<SupplierEntity>> = supplierDao.observeSuppliers(tenantId)
    fun observeSupplier(tenantId: String, supplierId: String): Flow<SupplierEntity?> = supplierDao.observeSupplier(tenantId, supplierId)
    fun observeSupplierTransactions(tenantId: String, supplierId: String) = transactionDao.observeSupplierTransactions(tenantId, supplierId)
    fun observePurchaseInvoices(tenantId: String) = purchaseInvoiceDao.observePurchaseInvoices(tenantId)
    fun observeSupplierPurchaseInvoices(tenantId: String, supplierId: String) = purchaseInvoiceDao.observeSupplierPurchaseInvoices(tenantId, supplierId)
    fun observePurchaseInvoiceById(tenantId: String, purchaseId: String) = purchaseInvoiceDao.observePurchaseInvoiceById(tenantId, purchaseId)
    fun observePurchaseInvoiceItems(tenantId: String, purchaseId: String) = purchaseInvoiceDao.observePurchaseInvoiceItems(tenantId, purchaseId)

    fun observeInvoices(tenantId: String) = salesInvoiceDao.observeInvoices(tenantId)
    fun observeInvoiceById(tenantId: String, invoiceId: String) = salesInvoiceDao.observeInvoiceById(tenantId, invoiceId)
    fun observeInvoiceItems(tenantId: String, invoiceId: String) = salesInvoiceDao.observeInvoiceItems(tenantId, invoiceId)
    fun observeInvoiceInstallments(tenantId: String, invoiceId: String) = installmentDao.observeInvoiceInstallments(tenantId, invoiceId)

    fun observeDailyInvoices(tenantId: String) = dailyDao.observeAllDailyInvoices(tenantId)
    fun observeDailyInvoice(tenantId: String, dailyInvoiceId: String) = dailyDao.observeDailyInvoiceById(tenantId, dailyInvoiceId)
    fun observeDailyItems(tenantId: String, dailyInvoiceId: String) = dailyDao.observeDailyItems(tenantId, dailyInvoiceId)

    fun observePayments(tenantId: String) = paymentDao.observePayments(tenantId)
    fun observeQuickSales(tenantId: String): Flow<List<QuickSaleEntity>> = quickSaleDao.observeQuickSales(tenantId)
    fun observeProducts(tenantId: String): Flow<List<ProductEntity>> = productDao.observeProducts(tenantId)
    fun observeBackupHistory(tenantId: String) = systemDao.observeBackupHistory(tenantId)
    fun observeAppSettings(tenantId: String) = systemDao.observeAppSettings(tenantId)
    fun observeAuditLogs(tenantId: String) = systemDao.observeTenantAuditLogs(tenantId)

    fun observeDashboardSummary(tenantId: String): Flow<DashboardSummary> {
        val coreFlow = combine(
            customerDao.observeCustomers(tenantId),
            supplierDao.observeSuppliers(tenantId),
            salesInvoiceDao.observeInvoices(tenantId),
            paymentDao.observePayments(tenantId),
            transactionDao.observeAllSupplierTransactions(tenantId)
        ) { customers, suppliers, invoices, payments, supplierTxs ->
            val now = EgyptTime.nowMillis()
            val dayStart = EgyptTime.startOfDayMillis(now)
            val dayEnd = EgyptTime.endOfDayMillis(now)

            val totalCustomerReceivables = customers.sumOf { it.currentBalancePiasters.coerceAtLeast(0L) }
            val totalSupplierPayables = suppliers.sumOf { it.currentBalancePiasters.coerceAtLeast(0L) }

            val todaySales = invoices
                .filter { it.status != InvoiceStatuses.CANCELLED && it.createdAt in dayStart..dayEnd }
                .sumOf { it.totalPiasters }

            val todayPayments = payments.filter { it.createdAt in dayStart..dayEnd }
            val todayCash = todayPayments
                .filter { it.paymentMethod == PaymentMethods.CASH }
                .sumOf { it.amountPiasters }
            val todayEWallet = todayPayments
                .filter { it.paymentMethod == PaymentMethods.EWALLET }
                .sumOf { it.amountPiasters }
            val todayAllIn = todayPayments.sumOf { it.amountPiasters }

            val totalIncoming = payments.sumOf { it.amountPiasters }
            val totalOutgoing = supplierTxs
                .filter {
                    it.type == SupplierTxTypes.PAYMENT_PARTIAL || it.type == SupplierTxTypes.PAYMENT_FULL
                }
                .sumOf { it.amountPiasters }

            val todaySupplierOut = supplierTxs
                .filter {
                    it.createdAt in dayStart..dayEnd &&
                        (it.type == SupplierTxTypes.PAYMENT_PARTIAL || it.type == SupplierTxTypes.PAYMENT_FULL)
                }
                .sumOf { it.amountPiasters }

            val overdueCount = customers.count { it.accountStatus == CustomerAccountStatus.OVERDUE }

            DashboardSummary(
                totalIncomingPiasters = totalIncoming,
                totalOutgoingPiasters = totalOutgoing,
                totalNetBalancePiasters = totalIncoming - totalOutgoing,
                todayIncomingPiasters = todayAllIn,
                totalCustomerReceivablesPiasters = totalCustomerReceivables,
                totalSupplierPayablesPiasters = totalSupplierPayables,
                todaySalesPiasters = todaySales,
                todayCashCollectionsPiasters = todayCash,
                todayEWalletCollectionsPiasters = todayEWallet,
                todaySupplierPaymentsPiasters = todaySupplierOut,
                todayNetCashflowPiasters = todayAllIn - todaySupplierOut,
                customersCount = customers.size,
                suppliersCount = suppliers.size,
                overdueCustomersCount = overdueCount
            )
        }

        return combine(
            coreFlow,
            dailyDao.observeAllDailyInvoices(tenantId),
            quickSaleDao.observeQuickSales(tenantId)
        ) { summary, dailyInvoices, quickSales ->
            val now = EgyptTime.nowMillis()
            val dayStart = EgyptTime.startOfDayMillis(now)
            val dayEnd = EgyptTime.endOfDayMillis(now)

            val allQuickTotal = quickSales.sumOf { it.amountPiasters }
            val todayQuick = quickSales.filter { it.createdAt in dayStart..dayEnd }
            val todayQuickTotal = todayQuick.sumOf { it.amountPiasters }
            val todayQuickCash = todayQuick.filter { it.paymentMethod == PaymentMethods.CASH }.sumOf { it.amountPiasters }
            val todayQuickEWallet = todayQuick.filter { it.paymentMethod == PaymentMethods.EWALLET }.sumOf { it.amountPiasters }

            summary.copy(
                totalIncomingPiasters = summary.totalIncomingPiasters + allQuickTotal,
                totalNetBalancePiasters = summary.totalNetBalancePiasters + allQuickTotal,
                todayIncomingPiasters = summary.todayIncomingPiasters + todayQuickTotal,
                todaySalesPiasters = summary.todaySalesPiasters + todayQuickTotal,
                todayCashCollectionsPiasters = summary.todayCashCollectionsPiasters + todayQuickCash,
                todayEWalletCollectionsPiasters = summary.todayEWalletCollectionsPiasters + todayQuickEWallet,
                todayNetCashflowPiasters = summary.todayNetCashflowPiasters + todayQuickTotal,
                openDailyInvoicesCount = dailyInvoices.count { it.status == DailyInvoiceStatuses.OPEN }
            )
        }
    }

    fun observeCreditLimitBreaches(tenantId: String): Flow<List<CreditLimitBreach>> {
        return combine(
            customerDao.observeCustomers(tenantId),
            systemDao.observeAppSettings(tenantId)
        ) { customers, settings ->
            val defaultLimit = settings?.defaultCustomerCreditLimitPiasters ?: 0L
            customers.mapNotNull { c ->
                val isCustom = c.creditLimitPiasters > 0L
                val effectiveLimit = if (isCustom) c.creditLimitPiasters else defaultLimit
                if (effectiveLimit > 0L && c.currentBalancePiasters > effectiveLimit) {
                    CreditLimitBreach(
                        customer = c,
                        effectiveLimitPiasters = effectiveLimit,
                        excessPiasters = c.currentBalancePiasters - effectiveLimit,
                        isCustomLimit = isCustom
                    )
                } else null
            }.sortedByDescending { it.excessPiasters }
        }
    }

    fun observeAlerts(tenantId: String): Flow<List<AppAlert>> {
        val baseAlerts = combine(
            customerDao.observeCustomers(tenantId),
            installmentDao.observeAllInstallments(tenantId),
            dailyDao.observeAllDailyInvoices(tenantId),
            systemDao.observeBackupHistory(tenantId),
            productDao.observeProducts(tenantId)
        ) { customers, installments, dailyInvoices, backups, products ->
            val customerMap = customers.associateBy { it.id }
            val alerts = mutableListOf<AppAlert>()
            val now = EgyptTime.nowMillis()
            val dayStart = EgyptTime.startOfDayMillis(now)
            val dayEnd = EgyptTime.endOfDayMillis(now)

            // 1. Overdue installments
            installments.filter { it.remainingPiasters > 0L && it.dueDateMillis < dayStart }
                .take(5)
                .forEach { inst ->
                    val cname = customerMap[inst.customerId]?.name ?: "عميل"
                    alerts.add(
                        AppAlert(
                            id = "overdue_${inst.id}",
                            title = "تأخر عميل عن السداد",
                            message = "$cname — متبقي ${MoneyFormatter.formatEgp(inst.remainingPiasters)} (كان مستحقًا ${EgyptTime.formatDate(inst.dueDateMillis)})",
                            severity = AlertSeverity.DANGER,
                            targetRoute = "customer_detail/${inst.customerId}"
                        )
                    )
                }

            // 2. Due today installments
            installments.filter { it.remainingPiasters > 0L && it.dueDateMillis in dayStart..dayEnd }
                .take(5)
                .forEach { inst ->
                    val cname = customerMap[inst.customerId]?.name ?: "عميل"
                    alerts.add(
                        AppAlert(
                            id = "due_${inst.id}",
                            title = "موعد استحقاق دفعة اليوم",
                            message = "$cname — دفعة مستحقة بقيمة ${MoneyFormatter.formatEgp(inst.remainingPiasters)}",
                            severity = AlertSeverity.WARNING,
                            targetRoute = "customer_detail/${inst.customerId}"
                        )
                    )
                }

            // 3. Open daily withdrawal invoices
            dailyInvoices.filter { it.status == DailyInvoiceStatuses.OPEN }
                .forEach { daily ->
                    val cname = customerMap[daily.customerId]?.name ?: "عميل"
                    alerts.add(
                        AppAlert(
                            id = "daily_${daily.id}",
                            title = "فاتورة سحب يومي مفتوحة",
                            message = "$cname (${daily.invoiceNumber}) — الإجمالي ${MoneyFormatter.formatEgp(daily.totalPiasters)}",
                            severity = AlertSeverity.INFO,
                            targetRoute = "daily_detail/${daily.id}"
                        )
                    )
                }

            // 3b. Low stock alerts (for products with stock tracking enabled)
            products
                .filter { it.trackStock && it.minStockAlertMilli > 0L && it.stockQuantityMilli <= it.minStockAlertMilli }
                .take(4)
                .forEach { prod ->
                    alerts.add(
                        AppAlert(
                            id = "lowstock_${prod.id}",
                            title = "تنبيه انخفاض مخزون صنف",
                            message = "${prod.name} — المتبقي ${MoneyFormatter.formatQuantity(prod.stockQuantityMilli)} ${prod.unitLabel}",
                            severity = AlertSeverity.WARNING,
                            targetRoute = "products"
                        )
                    )
                }

            // 4. Last backup status
            backups.firstOrNull()?.let { latest ->
                if (latest.status == "FAILED") {
                    alerts.add(
                        AppAlert(
                            id = "backup_${latest.id}",
                            title = "فشل آخر نسخة احتياطية",
                            message = latest.message.ifBlank { "تعذر إتمام النسخ الاحتياطي" },
                            severity = AlertSeverity.DANGER,
                            targetRoute = "backup"
                        )
                    )
                } else {
                    alerts.add(
                        AppAlert(
                            id = "backup_${latest.id}",
                            title = "تم النسخ الاحتياطي بنجاح",
                            message = "${latest.fileName} — ${EgyptTime.formatDateTime(latest.createdAt)}",
                            severity = AlertSeverity.SUCCESS,
                            targetRoute = "backup"
                        )
                    )
                }
            }
            alerts
        }

        return combine(observeCreditLimitBreaches(tenantId), baseAlerts) { breaches, otherAlerts ->
            val limitAlerts = breaches.map { breach ->
                AppAlert(
                    id = "credit_limit_${breach.customer.id}",
                    title = "تجاوز حد المديونية المسموح",
                    message = "${breach.customer.name} — الرصيد ${MoneyFormatter.formatEgp(breach.customer.currentBalancePiasters)} (الحد ${MoneyFormatter.formatEgp(breach.effectiveLimitPiasters)} • زيادة ${MoneyFormatter.formatEgp(breach.excessPiasters)})",
                    severity = AlertSeverity.DANGER,
                    targetRoute = "customer_detail/${breach.customer.id}"
                )
            }
            limitAlerts + otherAlerts
        }
    }

    // =========================================================================
    // Suppliers
    // =========================================================================

    suspend fun addSupplier(
        tenantId: String,
        actor: UserEntity,
        name: String,
        phone: String,
        whatsappPhone: String,
        address: String,
        notes: String,
        openingBalancePiasters: Long = 0L
    ): RepoResult<SupplierEntity> {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return RepoResult.Error("اسم المورد إجباري")
        if (openingBalancePiasters < 0L) return RepoResult.Error("الرصيد الافتتاحي لا يمكن أن يكون سالبًا")

        val cleanPhone = PhoneUtils.normalizePhoneDigits(phone)
        val cleanWa = PhoneUtils.normalizePhoneDigits(whatsappPhone).ifBlank { cleanPhone }
        val now = EgyptTime.nowMillis()
        val supplierId = UUID.randomUUID().toString()

        val supplier = SupplierEntity(
            id = supplierId,
            tenantId = tenantId,
            name = cleanName,
            phone = cleanPhone,
            whatsappPhone = cleanWa,
            address = address.trim(),
            notes = notes.trim(),
            openingBalancePiasters = openingBalancePiasters,
            totalDuePiasters = openingBalancePiasters,
            totalPaidPiasters = 0L,
            currentBalancePiasters = openingBalancePiasters,
            createdAt = now,
            updatedAt = now
        )

        db.withTransaction {
            supplierDao.upsertSupplier(supplier)
            if (openingBalancePiasters > 0L) {
                transactionDao.upsertSupplierTransaction(
                    SupplierTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        tenantId = tenantId,
                        supplierId = supplierId,
                        type = SupplierTxTypes.OPENING_BALANCE,
                        amountPiasters = openingBalancePiasters,
                        signedDeltaPiasters = openingBalancePiasters,
                        runningBalancePiasters = openingBalancePiasters,
                        paymentMethod = PaymentMethods.NONE,
                        notes = "رصيد افتتاحي للمورد",
                        idempotencyKey = "opening_$supplierId",
                        createdByUserId = actor.id,
                        createdAt = now
                    )
                )
            }
            recordAudit(tenantId, actor, "ADD_SUPPLIER", "SUPPLIER", supplierId, "إضافة مورد: $cleanName")
        }
        return RepoResult.Success(supplier)
    }

    suspend fun recordSupplierTransaction(
        tenantId: String,
        actor: UserEntity,
        supplierId: String,
        type: String,
        amountPiasters: Long,
        paymentMethod: String,
        notes: String,
        occurredAtMillis: Long = EgyptTime.nowMillis(),
        idempotencyKey: String = UUID.randomUUID().toString(),
        isNegativeAdjustment: Boolean = false
    ): RepoResult<SupplierTransactionEntity> {
        if (amountPiasters <= 0L) return RepoResult.Error("المبلغ يجب أن يكون أكبر من صفر")

        return db.withTransaction {
            val existingDup = transactionDao.getSupplierTxByIdempotencyKey(tenantId, idempotencyKey)
            if (existingDup != null) {
                return@withTransaction RepoResult.Success(existingDup)
            }
            val supplier = supplierDao.getSupplierById(tenantId, supplierId)
                ?: return@withTransaction RepoResult.Error("المورد غير موجود")

            val signedDelta = when (type) {
                SupplierTxTypes.DEBT_ADD -> amountPiasters
                SupplierTxTypes.PAYMENT_PARTIAL, SupplierTxTypes.PAYMENT_FULL -> -amountPiasters
                SupplierTxTypes.ADJUSTMENT -> if (isNegativeAdjustment) -amountPiasters else amountPiasters
                else -> return@withTransaction RepoResult.Error("نوع حركة غير صالح")
            }

            if (type == SupplierTxTypes.PAYMENT_FULL && supplier.currentBalancePiasters > 0L && amountPiasters > supplier.currentBalancePiasters) {
                return@withTransaction RepoResult.Error("المبلغ المدفوع يتجاوز الرصيد المستحق للمورد")
            }

            val tx = SupplierTransactionEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                supplierId = supplierId,
                type = type,
                amountPiasters = amountPiasters,
                signedDeltaPiasters = signedDelta,
                runningBalancePiasters = supplier.currentBalancePiasters + signedDelta,
                paymentMethod = if (type == SupplierTxTypes.DEBT_ADD) PaymentMethods.NONE else paymentMethod,
                notes = notes.trim(),
                idempotencyKey = idempotencyKey,
                createdByUserId = actor.id,
                createdAt = occurredAtMillis
            )
            transactionDao.upsertSupplierTransaction(tx)
            recalculateSupplierLedger(tenantId, supplierId)
            recordAudit(
                tenantId,
                actor,
                "SUPPLIER_TX_$type",
                "SUPPLIER",
                supplierId,
                "${SupplierTxTypes.arabicLabel(type)} بقيمة ${MoneyFormatter.formatEgp(amountPiasters)} للمورد ${supplier.name}"
            )
            RepoResult.Success(tx)
        }
    }

    private suspend fun recalculateSupplierLedger(tenantId: String, supplierId: String) {
        val supplier = supplierDao.getSupplierById(tenantId, supplierId) ?: return
        val txs = transactionDao.getSupplierTransactionsAsc(tenantId, supplierId)
        var running = 0L
        var totalDue = 0L
        var totalPaid = 0L
        val updatedTxs = txs.map { tx ->
            running += tx.signedDeltaPiasters
            if (tx.signedDeltaPiasters > 0L) {
                totalDue += tx.signedDeltaPiasters
            } else {
                totalPaid += -tx.signedDeltaPiasters
            }
            if (tx.runningBalancePiasters != running) tx.copy(runningBalancePiasters = running) else tx
        }
        transactionDao.upsertSupplierTransactions(updatedTxs)
        supplierDao.upsertSupplier(
            supplier.copy(
                totalDuePiasters = totalDue,
                totalPaidPiasters = totalPaid,
                currentBalancePiasters = running,
                updatedAt = EgyptTime.nowMillis()
            )
        )
    }

    // =========================================================================
    // Purchase Invoices (Suppliers, Inventory Restocking & Supplier Ledger)
    // =========================================================================

    suspend fun createPurchaseInvoice(
        tenantId: String,
        actor: UserEntity,
        supplierId: String,
        lines: List<PurchaseLineDraft>,
        purchaseType: String,
        paymentMethod: String,
        paidNowPiasters: Long,
        dueDateMillis: Long?,
        notes: String,
        occurredAtMillis: Long = EgyptTime.nowMillis(),
        idempotencyKey: String = UUID.randomUUID().toString()
    ): RepoResult<PurchaseInvoiceEntity> {
        if (lines.isEmpty()) return RepoResult.Error("يرجى إدخال صنف أو بند واحد على الأقل")
        val totalPiasters = lines.sumOf { it.lineTotalPiasters }
        if (totalPiasters <= 0L) return RepoResult.Error("إجمالي فاتورة الشراء يجب أن يكون أكبر من صفر")
        if (paidNowPiasters < 0L) return RepoResult.Error("المبلغ المدفوع لا يمكن أن يكون سالبًا")
        if (paidNowPiasters > totalPiasters) {
            return RepoResult.Error("المبلغ المدفوع لا يمكن أن يتجاوز إجمالي فاتورة الشراء")
        }

        val remainingPiasters = totalPiasters - paidNowPiasters

        return db.withTransaction {
            val dup = purchaseInvoiceDao.getByIdempotencyKey(tenantId, idempotencyKey)
            if (dup != null) return@withTransaction RepoResult.Success(dup)

            val supplier = supplierDao.getSupplierById(tenantId, supplierId)
                ?: return@withTransaction RepoResult.Error("المورد غير موجود")

            val seq = purchaseInvoiceDao.countPurchaseInvoices(tenantId) + 1
            val invoiceNumber = "PUR-${seq.toString().padStart(5, '0')}"
            val purchaseId = UUID.randomUUID().toString()

            val status = when {
                remainingPiasters == 0L -> InvoiceStatuses.PAID
                paidNowPiasters > 0L -> InvoiceStatuses.PARTIAL
                else -> InvoiceStatuses.UNPAID
            }

            val invoice = PurchaseInvoiceEntity(
                id = purchaseId,
                tenantId = tenantId,
                supplierId = supplierId,
                invoiceNumber = invoiceNumber,
                purchaseType = purchaseType,
                paymentMethod = if (paidNowPiasters > 0L) paymentMethod else PaymentMethods.NONE,
                totalPiasters = totalPiasters,
                paidPiasters = paidNowPiasters,
                remainingPiasters = remainingPiasters,
                dueDateMillis = dueDateMillis,
                notes = notes.trim(),
                status = status,
                idempotencyKey = idempotencyKey,
                createdByUserId = actor.id,
                createdAt = occurredAtMillis
            )
            purchaseInvoiceDao.upsertPurchaseInvoice(invoice)

            val itemEntities = lines.mapIndexed { index, draft ->
                PurchaseInvoiceItemEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = tenantId,
                    purchaseInvoiceId = purchaseId,
                    description = draft.description.trim().ifBlank { "مشتريات بضاعة" },
                    unitPricePiasters = draft.unitPricePiasters,
                    quantityMilli = draft.quantityMilli,
                    lineTotalPiasters = draft.lineTotalPiasters,
                    sortOrder = index
                )
            }
            purchaseInvoiceDao.upsertPurchaseInvoiceItems(itemEntities)

            // Automatically restock tracked catalog products & backfill purchase price if empty
            for (draft in lines) {
                val pid = draft.productId ?: continue
                val prod = productDao.getProductById(tenantId, pid) ?: continue
                if (prod.trackStock || prod.purchasePricePiasters <= 0L) {
                    val updatedStock = if (prod.trackStock) prod.stockQuantityMilli + draft.quantityMilli else prod.stockQuantityMilli
                    val updatedBuyPrice = if (prod.purchasePricePiasters <= 0L) draft.unitPricePiasters else prod.purchasePricePiasters
                    productDao.upsertProduct(
                        prod.copy(
                            stockQuantityMilli = updatedStock,
                            purchasePricePiasters = updatedBuyPrice,
                            updatedAt = occurredAtMillis
                        )
                    )
                }
            }

            // Post purchase invoice debt to supplier ledger once
            transactionDao.upsertSupplierTransaction(
                SupplierTransactionEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = tenantId,
                    supplierId = supplierId,
                    type = SupplierTxTypes.PURCHASE_INVOICE,
                    amountPiasters = totalPiasters,
                    signedDeltaPiasters = totalPiasters,
                    runningBalancePiasters = 0L, // recalculated below
                    paymentMethod = invoice.paymentMethod,
                    notes = "فاتورة شراء $invoiceNumber (${PurchaseTypes.arabicLabel(purchaseType)})",
                    idempotencyKey = "pur_debt_$idempotencyKey",
                    createdByUserId = actor.id,
                    createdAt = occurredAtMillis
                )
            )

            // Post down-payment / cash payment to supplier ledger if paid now
            if (paidNowPiasters > 0L) {
                transactionDao.upsertSupplierTransaction(
                    SupplierTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        tenantId = tenantId,
                        supplierId = supplierId,
                        type = if (remainingPiasters == 0L) SupplierTxTypes.PAYMENT_FULL else SupplierTxTypes.PAYMENT_PARTIAL,
                        amountPiasters = paidNowPiasters,
                        signedDeltaPiasters = -paidNowPiasters,
                        runningBalancePiasters = 0L, // recalculated below
                        paymentMethod = paymentMethod,
                        notes = if (remainingPiasters == 0L) {
                            "سداد كامل فاتورة شراء $invoiceNumber"
                        } else {
                            "دفعة مقدمة لفاتورة شراء $invoiceNumber"
                        },
                        idempotencyKey = "pur_pay_$idempotencyKey",
                        createdByUserId = actor.id,
                        createdAt = occurredAtMillis + 1L
                    )
                )
            }

            recalculateSupplierLedger(tenantId, supplierId)
            recordAudit(
                tenantId = tenantId,
                actor = actor,
                action = "CREATE_PURCHASE",
                entityType = "PURCHASE_INVOICE",
                entityId = purchaseId,
                details = "تسجيل فاتورة شراء $invoiceNumber من المورد ${supplier.name} بإجمالي ${MoneyFormatter.formatEgp(totalPiasters)}"
            )
            RepoResult.Success(invoice)
        }
    }

    // =========================================================================
    // Customers
    // =========================================================================

    suspend fun addCustomer(
        tenantId: String,
        actor: UserEntity,
        name: String,
        phone: String,
        whatsappPhone: String,
        address: String,
        notes: String,
        openingBalancePiasters: Long = 0L,
        creditLimitPiasters: Long = 0L
    ): RepoResult<CustomerEntity> {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return RepoResult.Error("اسم العميل إجباري")
        if (openingBalancePiasters < 0L) return RepoResult.Error("الرصيد الافتتاحي لا يمكن أن يكون سالبًا")
        if (creditLimitPiasters < 0L) return RepoResult.Error("حد المديونية لا يمكن أن يكون سالبًا")

        val cleanPhone = PhoneUtils.normalizePhoneDigits(phone)
        val cleanWa = PhoneUtils.normalizePhoneDigits(whatsappPhone).ifBlank { cleanPhone }
        val now = EgyptTime.nowMillis()
        val customerId = UUID.randomUUID().toString()

        val initialStatus = if (openingBalancePiasters > 0L) CustomerAccountStatus.OWING else CustomerAccountStatus.CLEAR
        val customer = CustomerEntity(
            id = customerId,
            tenantId = tenantId,
            name = cleanName,
            phone = cleanPhone,
            whatsappPhone = cleanWa,
            address = address.trim(),
            notes = notes.trim(),
            openingBalancePiasters = openingBalancePiasters,
            totalSalesPiasters = openingBalancePiasters,
            totalPaidPiasters = 0L,
            currentBalancePiasters = openingBalancePiasters,
            creditLimitPiasters = creditLimitPiasters,
            accountStatus = initialStatus,
            createdAt = now,
            updatedAt = now
        )

        db.withTransaction {
            customerDao.upsertCustomer(customer)
            if (openingBalancePiasters > 0L) {
                transactionDao.upsertCustomerTransaction(
                    CustomerTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        tenantId = tenantId,
                        customerId = customerId,
                        type = CustomerTxTypes.OPENING_BALANCE,
                        amountPiasters = openingBalancePiasters,
                        signedDeltaPiasters = openingBalancePiasters,
                        runningBalancePiasters = openingBalancePiasters,
                        paymentMethod = PaymentMethods.NONE,
                        description = "رصيد افتتاحي للعميل",
                        createdByUserId = actor.id,
                        createdAt = now
                    )
                )
            }
            recordAudit(tenantId, actor, "ADD_CUSTOMER", "CUSTOMER", customerId, "إضافة عميل: $cleanName")
        }
        return RepoResult.Success(customer)
    }

    suspend fun mergeCustomers(
        tenantId: String,
        actor: UserEntity,
        sourceCustomerId: String,
        targetCustomerId: String
    ): RepoResult<CustomerEntity> {
        if (sourceCustomerId == targetCustomerId) {
            return RepoResult.Error("يرجى اختيار عميل مختلف للدمج معه")
        }
        return db.withTransaction {
            val source = customerDao.getCustomerById(tenantId, sourceCustomerId)
                ?: return@withTransaction RepoResult.Error("العميل المصدر غير موجود")
            val target = customerDao.getCustomerById(tenantId, targetCustomerId)
                ?: return@withTransaction RepoResult.Error("العميل المستهدف غير موجود")

            mergeCustomersLocked(tenantId, actor, source, target)
            val updated = customerDao.getCustomerById(tenantId, target.id) ?: target
            RepoResult.Success(updated)
        }
    }

    suspend fun mergeDuplicateSameNameCustomers(
        tenantId: String,
        actor: UserEntity
    ): RepoResult<Int> {
        return db.withTransaction {
            val groups = customerDao.getCustomers(tenantId)
                .groupBy { it.name.trim().lowercase() }
                .values
                .filter { it.size > 1 }

            var mergedCount = 0
            groups.forEach { group ->
                // Prefer keeping the customer that has a phone number, then oldest createdAt
                val sorted = group.sortedWith(
                    compareByDescending<CustomerEntity> { it.phone.isNotBlank() }
                        .thenBy { it.createdAt }
                )
                val target = sorted.first()
                sorted.drop(1).forEach { source ->
                    val currentTarget = customerDao.getCustomerById(tenantId, target.id) ?: target
                    mergeCustomersLocked(tenantId, actor, source, currentTarget)
                    mergedCount++
                }
            }
            RepoResult.Success(mergedCount)
        }
    }

    private suspend fun mergeCustomersLocked(
        tenantId: String,
        actor: UserEntity,
        source: CustomerEntity,
        target: CustomerEntity
    ) {
        salesInvoiceDao.reassignInvoicesCustomer(tenantId, source.id, target.id)
        paymentDao.reassignPaymentsCustomer(tenantId, source.id, target.id)
        installmentDao.reassignInstallmentsCustomer(tenantId, source.id, target.id)
        dailyDao.reassignDailyInvoicesCustomer(tenantId, source.id, target.id)
        transactionDao.reassignCustomerTransactions(tenantId, source.id, target.id)

        // Also connect any duplicate OPEN daily invoices now owned by target into one open invoice
        val openForTarget = dailyDao.getCustomerDailyInvoicesByStatus(tenantId, target.id, DailyInvoiceStatuses.OPEN)
        if (openForTarget.size > 1) {
            val primary = openForTarget.first()
            var extraPaid = 0L
            openForTarget.drop(1).forEach { dup ->
                val items = dailyDao.getDailyItems(tenantId, dup.id)
                if (items.isNotEmpty()) {
                    dailyDao.upsertDailyItems(items.map { it.copy(dailyInvoiceId = primary.id) })
                }
                val dupPayments = paymentDao.getPaymentsForDailyOrLinkedInvoice(tenantId, dup.id, dup.linkedSalesInvoiceId)
                if (dupPayments.isNotEmpty()) {
                    paymentDao.upsertPayments(dupPayments.map { it.copy(dailyInvoiceId = primary.id) })
                }
                extraPaid += dup.paidPiasters
                dailyDao.deleteDailyInvoice(tenantId, dup.id)
            }
            if (extraPaid > 0L) {
                val curPrimary = dailyDao.getDailyInvoiceById(tenantId, primary.id) ?: primary
                dailyDao.upsertDailyInvoice(curPrimary.copy(paidPiasters = curPrimary.paidPiasters + extraPaid))
            }
            recalculateDailyInvoiceTotals(tenantId, primary.id)
        }

        val mergedNotes = listOf(target.notes.trim(), source.notes.trim())
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" | ")

        customerDao.upsertCustomer(
            target.copy(
                phone = target.phone.ifBlank { source.phone },
                whatsappPhone = target.whatsappPhone.ifBlank { source.whatsappPhone },
                address = target.address.ifBlank { source.address },
                notes = mergedNotes,
                openingBalancePiasters = target.openingBalancePiasters + source.openingBalancePiasters,
                creditLimitPiasters = if (target.creditLimitPiasters > 0L) target.creditLimitPiasters else source.creditLimitPiasters,
                updatedAt = EgyptTime.nowMillis()
            )
        )
        customerDao.deleteCustomer(tenantId, source.id)
        recalculateCustomerLedger(tenantId, target.id)
        recordAudit(
            tenantId = tenantId,
            actor = actor,
            action = "MERGE_CUSTOMERS",
            entityType = "CUSTOMER",
            entityId = target.id,
            details = "دمج العميل \"${source.name}\" في حساب \"${target.name}\""
        )
    }

    suspend fun deleteCustomer(
        tenantId: String,
        actor: UserEntity,
        customerId: String
    ): RepoResult<Unit> {
        return db.withTransaction {
            val customer = customerDao.getCustomerById(tenantId, customerId)
                ?: return@withTransaction RepoResult.Error("العميل غير موجود")

            dailyDao.deleteCustomerDailyItems(tenantId, customerId)
            dailyDao.deleteCustomerDailyInvoices(tenantId, customerId)
            installmentDao.deleteCustomerInstallments(tenantId, customerId)
            salesInvoiceDao.deleteCustomerInvoiceItems(tenantId, customerId)
            salesInvoiceDao.deleteCustomerInvoices(tenantId, customerId)
            paymentDao.deleteCustomerPayments(tenantId, customerId)
            transactionDao.deleteAllCustomerTransactions(tenantId, customerId)
            customerDao.deleteCustomer(tenantId, customerId)

            recordAudit(
                tenantId = tenantId,
                actor = actor,
                action = "DELETE_CUSTOMER",
                entityType = "CUSTOMER",
                entityId = customerId,
                details = "حذف العميل \"${customer.name}\" وكل معاملاته"
            )
            RepoResult.Success(Unit)
        }
    }

    suspend fun settleCustomerAccount(
        tenantId: String,
        actor: UserEntity,
        customerId: String,
        paidAmountPiasters: Long,
        paymentMethod: String,
        closeOpenDailyInvoices: Boolean = true,
        notes: String = "",
        idempotencyKey: String = UUID.randomUUID().toString()
    ): RepoResult<CustomerSettlementSummary> {
        return db.withTransaction {
            val existingPay = paymentDao.getByIdempotencyKey(tenantId, idempotencyKey)
            if (existingPay != null) {
                val cur = customerDao.getCustomerById(tenantId, customerId)
                return@withTransaction RepoResult.Success(
                    CustomerSettlementSummary(
                        customerId = customerId,
                        closedOpenDailyCount = 0,
                        settledPreviousBalancePiasters = existingPay.amountPiasters,
                        paidAmountPiasters = existingPay.amountPiasters,
                        discountPiasters = 0L,
                        finalBalancePiasters = cur?.currentBalancePiasters ?: 0L
                    )
                )
            }

            if (customerDao.getCustomerById(tenantId, customerId) == null) {
                return@withTransaction RepoResult.Error("العميل غير موجود")
            }

            var closedDailyCount = 0
            if (closeOpenDailyInvoices) {
                val openDailies = dailyDao.getCustomerDailyInvoicesByStatus(tenantId, customerId, DailyInvoiceStatuses.OPEN)
                openDailies.forEach { daily ->
                    val items = dailyDao.getDailyItems(tenantId, daily.id)
                    if (items.isEmpty()) {
                        if (daily.paidPiasters == 0L && !daily.isPostedToLedger) {
                            dailyDao.deleteDailyInvoice(tenantId, daily.id)
                        }
                    } else {
                        closeDailyInvoiceLocked(tenantId, actor, daily, items)
                        closedDailyCount++
                    }
                }
                if (closedDailyCount > 0) {
                    recalculateCustomerLedger(tenantId, customerId)
                }
            }

            val customer = customerDao.getCustomerById(tenantId, customerId)!!
            val netBalance = customer.currentBalancePiasters
            if (netBalance == 0L) {
                if (closedDailyCount == 0) {
                    return@withTransaction RepoResult.Error("حساب العميل خالص بالفعل (الرصيد 0)")
                }
                return@withTransaction RepoResult.Success(
                    CustomerSettlementSummary(
                        customerId = customerId,
                        closedOpenDailyCount = closedDailyCount,
                        settledPreviousBalancePiasters = 0L,
                        paidAmountPiasters = 0L,
                        discountPiasters = 0L,
                        finalBalancePiasters = 0L
                    )
                )
            }

            val now = EgyptTime.nowMillis()
            val cleanNotes = notes.trim()
            var recordedPaid = 0L
            var recordedDiscount = 0L

            if (netBalance > 0L) {
                if (paidAmountPiasters < 0L) {
                    return@withTransaction RepoResult.Error("المبلغ المدفوع لا يمكن أن يكون سالبًا")
                }
                if (paidAmountPiasters > netBalance) {
                    return@withTransaction RepoResult.Error(
                        "المبلغ المدفوع (${MoneyFormatter.formatEgp(paidAmountPiasters)}) يتجاوز إجمالي المستحق (${MoneyFormatter.formatEgp(netBalance)})"
                    )
                }
                recordedPaid = paidAmountPiasters
                recordedDiscount = netBalance - paidAmountPiasters

                if (recordedPaid > 0L) {
                    val safeMethod = paymentMethod.ifBlank { PaymentMethods.CASH }
                    val paymentId = UUID.randomUUID().toString()
                    paymentDao.upsertPayment(
                        PaymentEntity(
                            id = paymentId,
                            tenantId = tenantId,
                            customerId = customerId,
                            invoiceId = null,
                            installmentId = null,
                            amountPiasters = recordedPaid,
                            paymentMethod = safeMethod,
                            excessRecordedAsCredit = false,
                            notes = buildString {
                                append("تصفية حساب العميل")
                                if (cleanNotes.isNotEmpty()) append(" — $cleanNotes")
                            },
                            idempotencyKey = idempotencyKey,
                            createdByUserId = actor.id,
                            createdAt = now
                        )
                    )
                    transactionDao.upsertCustomerTransaction(
                        CustomerTransactionEntity(
                            id = UUID.randomUUID().toString(),
                            tenantId = tenantId,
                            customerId = customerId,
                            type = CustomerTxTypes.PAYMENT,
                            amountPiasters = recordedPaid,
                            signedDeltaPiasters = -recordedPaid,
                            runningBalancePiasters = 0L,
                            paymentMethod = safeMethod,
                            referenceId = paymentId,
                            description = buildString {
                                append("تحصيل تصفية حساب (${PaymentMethods.arabicLabel(safeMethod)})")
                                if (cleanNotes.isNotEmpty()) append(" — $cleanNotes")
                            },
                            createdByUserId = actor.id,
                            createdAt = now
                        )
                    )
                }

                if (recordedDiscount > 0L) {
                    transactionDao.upsertCustomerTransaction(
                        CustomerTransactionEntity(
                            id = UUID.randomUUID().toString(),
                            tenantId = tenantId,
                            customerId = customerId,
                            type = CustomerTxTypes.ADJUSTMENT,
                            amountPiasters = recordedDiscount,
                            signedDeltaPiasters = -recordedDiscount,
                            runningBalancePiasters = 0L,
                            paymentMethod = PaymentMethods.NONE,
                            referenceId = customerId,
                            description = buildString {
                                append("خصم / تسوية تصفية حساب")
                                if (cleanNotes.isNotEmpty()) append(" — $cleanNotes")
                            },
                            createdByUserId = actor.id,
                            createdAt = now + 1L
                        )
                    )
                }
            } else {
                // Customer holds credit balance (< 0); settle credit back to zero
                val creditToZero = -netBalance
                transactionDao.upsertCustomerTransaction(
                    CustomerTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        tenantId = tenantId,
                        customerId = customerId,
                        type = CustomerTxTypes.ADJUSTMENT,
                        amountPiasters = creditToZero,
                        signedDeltaPiasters = creditToZero,
                        runningBalancePiasters = 0L,
                        paymentMethod = PaymentMethods.NONE,
                        referenceId = customerId,
                        description = buildString {
                            append("تصفية رصيد دائن للعميل")
                            if (cleanNotes.isNotEmpty()) append(" — $cleanNotes")
                        },
                        createdByUserId = actor.id,
                        createdAt = now
                    )
                )
            }

            // Clear all open customer invoices and installments so no overdue flags remain
            val openInvoices = salesInvoiceDao.getCustomerInvoices(tenantId, customerId)
                .filter { it.remainingPiasters > 0L || it.status != InvoiceStatuses.PAID }
            if (openInvoices.isNotEmpty()) {
                salesInvoiceDao.upsertInvoices(
                    openInvoices.map { inv ->
                        inv.copy(
                            remainingPiasters = 0L,
                            status = InvoiceStatuses.PAID
                        )
                    }
                )
            }

            val openInstallments = installmentDao.getCustomerInstallments(tenantId, customerId)
                .filter { it.remainingPiasters > 0L || it.status != InstallmentStatuses.PAID }
            if (openInstallments.isNotEmpty()) {
                installmentDao.upsertInstallments(
                    openInstallments.map { inst ->
                        inst.copy(
                            remainingPiasters = 0L,
                            status = InstallmentStatuses.PAID
                        )
                    }
                )
            }

            val closedDailiesWithRem = dailyDao.getCustomerDailyInvoicesByStatus(tenantId, customerId, DailyInvoiceStatuses.CLOSED)
                .filter { it.remainingPiasters > 0L }
            if (closedDailiesWithRem.isNotEmpty()) {
                dailyDao.upsertDailyInvoices(
                    closedDailiesWithRem.map { d ->
                        d.copy(remainingPiasters = 0L)
                    }
                )
            }

            recalculateCustomerLedger(tenantId, customerId)
            val updated = customerDao.getCustomerById(tenantId, customerId) ?: customer
            recordAudit(
                tenantId = tenantId,
                actor = actor,
                action = "SETTLE_CUSTOMER",
                entityType = "CUSTOMER",
                entityId = customerId,
                details = buildString {
                    append("تصفية حساب العميل \"${customer.name}\"")
                    if (recordedPaid > 0L) append(" • تحصيل ${MoneyFormatter.formatEgp(recordedPaid)}")
                    if (recordedDiscount > 0L) append(" • خصم تصفية ${MoneyFormatter.formatEgp(recordedDiscount)}")
                    if (closedDailyCount > 0) append(" • إغلاق $closedDailyCount فاتورة يومية")
                }
            )
            RepoResult.Success(
                CustomerSettlementSummary(
                    customerId = customerId,
                    closedOpenDailyCount = closedDailyCount,
                    settledPreviousBalancePiasters = netBalance,
                    paidAmountPiasters = recordedPaid,
                    discountPiasters = recordedDiscount,
                    finalBalancePiasters = updated.currentBalancePiasters
                )
            )
        }
    }

    suspend fun updateCustomerCreditLimit(
        tenantId: String,
        actor: UserEntity,
        customerId: String,
        creditLimitPiasters: Long
    ): RepoResult<CustomerEntity> {
        if (creditLimitPiasters < 0L) return RepoResult.Error("حد المديونية لا يمكن أن يكون سالبًا")
        return db.withTransaction {
            val existing = customerDao.getCustomerById(tenantId, customerId)
                ?: return@withTransaction RepoResult.Error("العميل غير موجود")
            val updated = existing.copy(
                creditLimitPiasters = creditLimitPiasters,
                updatedAt = EgyptTime.nowMillis()
            )
            customerDao.upsertCustomer(updated)
            val detail = if (creditLimitPiasters > 0L) {
                "تحديد حد المديونية للعميل ${updated.name}: ${MoneyFormatter.formatEgp(creditLimitPiasters)}"
            } else {
                "إلغاء الحد الخاص للمديونية للعميل ${updated.name}"
            }
            recordAudit(tenantId, actor, "UPDATE_CREDIT_LIMIT", "CUSTOMER", customerId, detail)
            RepoResult.Success(updated)
        }
    }

    // =========================================================================
    // Sales Invoices (Cash, Credit, Scheduled Installments)
    // =========================================================================

    suspend fun createSalesInvoice(
        tenantId: String,
        actor: UserEntity,
        customerId: String,
        lines: List<SaleLineDraft>,
        saleType: String,
        paymentMethod: String,
        paidNowPiasters: Long,
        dueDateMillis: Long?,
        installments: List<InstallmentDraft>,
        notes: String,
        occurredAtMillis: Long = EgyptTime.nowMillis(),
        idempotencyKey: String = UUID.randomUUID().toString()
    ): RepoResult<SalesInvoiceEntity> {
        if (lines.isEmpty()) return RepoResult.Error("يرجى إدخال مبلغ أو بند واحد على الأقل")
        val totalPiasters = lines.sumOf { it.lineTotalPiasters }
        if (totalPiasters <= 0L) return RepoResult.Error("إجمالي الفاتورة يجب أن يكون أكبر من صفر")
        if (paidNowPiasters < 0L) return RepoResult.Error("المبلغ المدفوع لا يمكن أن يكون سالبًا")
        if (paidNowPiasters > totalPiasters) {
            return RepoResult.Error("المبلغ المدفوع لا يمكن أن يتجاوز إجمالي الفاتورة")
        }

        val remainingPiasters = totalPiasters - paidNowPiasters

        if (saleType == SaleTypes.SCHEDULED) {
            if (installments.isEmpty()) return RepoResult.Error("يرجى إضافة دفعة مجدولة واحدة على الأقل")
            val sumInst = installments.sumOf { it.amountPiasters }
            if (sumInst != remainingPiasters) {
                return RepoResult.Error(
                    "مجموع الدفعات المجدولة (${MoneyFormatter.formatEgp(sumInst)}) يجب أن يساوي المتبقي (${MoneyFormatter.formatEgp(remainingPiasters)})"
                )
            }
            if (installments.any { it.amountPiasters <= 0L }) {
                return RepoResult.Error("قيمة كل دفعة مجدولة يجب أن تكون أكبر من صفر")
            }
        }

        return db.withTransaction {
            val dup = salesInvoiceDao.getByIdempotencyKey(tenantId, idempotencyKey)
            if (dup != null) return@withTransaction RepoResult.Success(dup)

            val customer = customerDao.getCustomerById(tenantId, customerId)
                ?: return@withTransaction RepoResult.Error("العميل غير موجود")

            val seq = salesInvoiceDao.countInvoices(tenantId) + 1
            val invoiceNumber = "INV-${seq.toString().padStart(5, '0')}"
            val invoiceId = UUID.randomUUID().toString()

            val dayStart = EgyptTime.startOfDayMillis(EgyptTime.nowMillis())
            val status = when {
                remainingPiasters == 0L -> InvoiceStatuses.PAID
                dueDateMillis != null && dueDateMillis < dayStart -> InvoiceStatuses.OVERDUE
                paidNowPiasters > 0L -> InvoiceStatuses.PARTIAL
                else -> InvoiceStatuses.UNPAID
            }

            val invoice = SalesInvoiceEntity(
                id = invoiceId,
                tenantId = tenantId,
                customerId = customerId,
                invoiceNumber = invoiceNumber,
                saleType = saleType,
                paymentMethod = if (paidNowPiasters > 0L) paymentMethod else PaymentMethods.NONE,
                totalPiasters = totalPiasters,
                paidPiasters = paidNowPiasters,
                remainingPiasters = remainingPiasters,
                dueDateMillis = dueDateMillis,
                notes = notes.trim(),
                status = status,
                idempotencyKey = idempotencyKey,
                createdByUserId = actor.id,
                createdAt = occurredAtMillis
            )
            salesInvoiceDao.upsertInvoice(invoice)

            val itemEntities = lines.mapIndexed { index, draft ->
                SalesInvoiceItemEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = tenantId,
                    invoiceId = invoiceId,
                    description = draft.description.trim().ifBlank { "بضاعة / مبيعات" },
                    unitPricePiasters = draft.unitPricePiasters,
                    quantityMilli = draft.quantityMilli,
                    lineTotalPiasters = draft.lineTotalPiasters,
                    sortOrder = index
                )
            }
            salesInvoiceDao.upsertInvoiceItems(itemEntities)

            // Deduct tracked inventory automatically for any catalog product selected in this sale
            for (draft in lines) {
                val pid = draft.productId ?: continue
                val prod = productDao.getProductById(tenantId, pid)
                if (prod != null && prod.trackStock) {
                    val updatedStock = (prod.stockQuantityMilli - draft.quantityMilli).coerceAtLeast(0L)
                    productDao.upsertProduct(
                        prod.copy(
                            stockQuantityMilli = updatedStock,
                            updatedAt = occurredAtMillis
                        )
                    )
                }
            }

            // Post sale debit once to customer ledger
            transactionDao.upsertCustomerTransaction(
                CustomerTransactionEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = tenantId,
                    customerId = customerId,
                    type = CustomerTxTypes.SALE,
                    amountPiasters = totalPiasters,
                    signedDeltaPiasters = totalPiasters,
                    runningBalancePiasters = 0L, // recalculated below
                    paymentMethod = invoice.paymentMethod,
                    referenceId = invoiceId,
                    description = "فاتورة $invoiceNumber (${SaleTypes.arabicLabel(saleType)})",
                    createdByUserId = actor.id,
                    createdAt = occurredAtMillis
                )
            )

            // Post down-payment / cash payment once to payments + customer ledger
            if (paidNowPiasters > 0L) {
                val paymentId = UUID.randomUUID().toString()
                paymentDao.upsertPayment(
                    PaymentEntity(
                        id = paymentId,
                        tenantId = tenantId,
                        customerId = customerId,
                        invoiceId = invoiceId,
                        amountPiasters = paidNowPiasters,
                        paymentMethod = paymentMethod,
                        notes = if (remainingPiasters == 0L) "سداد كامل فاتورة $invoiceNumber" else "مقدم فاتورة $invoiceNumber",
                        idempotencyKey = "pay_init_$idempotencyKey",
                        createdByUserId = actor.id,
                        createdAt = occurredAtMillis + 1L
                    )
                )
                transactionDao.upsertCustomerTransaction(
                    CustomerTransactionEntity(
                        id = UUID.randomUUID().toString(),
                        tenantId = tenantId,
                        customerId = customerId,
                        type = CustomerTxTypes.PAYMENT,
                        amountPiasters = paidNowPiasters,
                        signedDeltaPiasters = -paidNowPiasters,
                        runningBalancePiasters = 0L,
                        paymentMethod = paymentMethod,
                        referenceId = paymentId,
                        description = "تحصيل فاتورة $invoiceNumber (${PaymentMethods.arabicLabel(paymentMethod)})",
                        createdByUserId = actor.id,
                        createdAt = occurredAtMillis + 1L
                    )
                )
            }

            // Create Installment Schedules if SCHEDULED
            if (saleType == SaleTypes.SCHEDULED) {
                val dayEnd = EgyptTime.endOfDayMillis(EgyptTime.nowMillis())
                val scheduleEntities = installments.sortedBy { it.dueDateMillis }.mapIndexed { idx, draft ->
                    val instStatus = when {
                        draft.dueDateMillis < dayStart -> InstallmentStatuses.OVERDUE
                        draft.dueDateMillis in dayStart..dayEnd -> InstallmentStatuses.DUE
                        else -> InstallmentStatuses.UPCOMING
                    }
                    InstallmentScheduleEntity(
                        id = UUID.randomUUID().toString(),
                        tenantId = tenantId,
                        invoiceId = invoiceId,
                        customerId = customerId,
                        installmentIndex = idx + 1,
                        dueDateMillis = draft.dueDateMillis,
                        amountPiasters = draft.amountPiasters,
                        paidPiasters = 0L,
                        remainingPiasters = draft.amountPiasters,
                        status = instStatus
                    )
                }
                installmentDao.upsertInstallments(scheduleEntities)
            }

            recalculateCustomerLedger(tenantId, customerId)
            recordAudit(
                tenantId,
                actor,
                "CREATE_SALE",
                "SALES_INVOICE",
                invoiceId,
                "تسجيل فاتورة $invoiceNumber للعميل ${customer.name} بإجمالي ${MoneyFormatter.formatEgp(totalPiasters)}"
            )
            RepoResult.Success(invoice)
        }
    }

    // =========================================================================
    // Customer Collections (Payments)
    // =========================================================================

    suspend fun recordCustomerPayment(
        tenantId: String,
        actor: UserEntity,
        customerId: String,
        amountPiasters: Long,
        paymentMethod: String,
        invoiceId: String? = null,
        installmentId: String? = null,
        allowExcessAsCredit: Boolean = false,
        notes: String = "",
        occurredAtMillis: Long = EgyptTime.nowMillis(),
        idempotencyKey: String = UUID.randomUUID().toString()
    ): RepoResult<PaymentEntity> {
        if (amountPiasters <= 0L) return RepoResult.Error("المبلغ المستلم يجب أن يكون أكبر من صفر")

        return db.withTransaction {
            val dup = paymentDao.getByIdempotencyKey(tenantId, idempotencyKey)
            if (dup != null) return@withTransaction RepoResult.Success(dup)

            val customer = customerDao.getCustomerById(tenantId, customerId)
                ?: return@withTransaction RepoResult.Error("العميل غير موجود")

            var appliedToInvoice = amountPiasters
            var excessRecorded = false
            var targetInvoice: SalesInvoiceEntity? = null

            if (invoiceId != null) {
                val inv = salesInvoiceDao.getInvoiceById(tenantId, invoiceId)
                    ?: return@withTransaction RepoResult.Error("الفاتورة المحددة غير موجودة")
                if (inv.customerId != customerId) {
                    return@withTransaction RepoResult.Error("الفاتورة لا تنتمي لهذا العميل")
                }
                if (amountPiasters > inv.remainingPiasters && !allowExcessAsCredit) {
                    return@withTransaction RepoResult.Error(
                        "المبلغ المدخل يتجاوز المتبقي على الفاتورة (${MoneyFormatter.formatEgp(inv.remainingPiasters)}). فعّل خيار تسجيل الفرق كرصيد دائن للعميل إذا رغبت."
                    )
                }
                if (amountPiasters > inv.remainingPiasters) {
                    appliedToInvoice = inv.remainingPiasters
                    excessRecorded = true
                }
                targetInvoice = inv
            }

            val paymentId = UUID.randomUUID().toString()
            val payment = PaymentEntity(
                id = paymentId,
                tenantId = tenantId,
                customerId = customerId,
                invoiceId = invoiceId,
                installmentId = installmentId,
                amountPiasters = amountPiasters,
                paymentMethod = paymentMethod,
                excessRecordedAsCredit = excessRecorded,
                notes = notes.trim(),
                idempotencyKey = idempotencyKey,
                createdByUserId = actor.id,
                createdAt = occurredAtMillis
            )
            paymentDao.upsertPayment(payment)

            // Update linked invoice & installments
            if (targetInvoice != null && appliedToInvoice > 0L) {
                val newPaid = targetInvoice.paidPiasters + appliedToInvoice
                val newRem = (targetInvoice.totalPiasters - newPaid).coerceAtLeast(0L)
                val newStatus = if (newRem == 0L) InvoiceStatuses.PAID else InvoiceStatuses.PARTIAL
                salesInvoiceDao.upsertInvoice(
                    targetInvoice.copy(
                        paidPiasters = newPaid,
                        remainingPiasters = newRem,
                        status = newStatus
                    )
                )

                // Allocate payment across invoice installments (targeted first, then earliest due)
                val invoiceInstallments = installmentDao.getInvoiceInstallments(tenantId, targetInvoice.id)
                if (invoiceInstallments.isNotEmpty()) {
                    var leftToAllocate = appliedToInvoice
                    val ordered = if (installmentId != null) {
                        invoiceInstallments.sortedByDescending { it.id == installmentId }
                    } else {
                        invoiceInstallments.sortedBy { it.dueDateMillis }
                    }
                    val updatedInsts = ordered.map { inst ->
                        if (leftToAllocate <= 0L || inst.remainingPiasters <= 0L) {
                            inst
                        } else {
                            val payHere = minOf(leftToAllocate, inst.remainingPiasters)
                            leftToAllocate -= payHere
                            val instPaid = inst.paidPiasters + payHere
                            val instRem = inst.amountPiasters - instPaid
                            inst.copy(
                                paidPiasters = instPaid,
                                remainingPiasters = instRem,
                                status = computeInstallmentStatus(inst.dueDateMillis, instPaid, instRem)
                            )
                        }
                    }
                    installmentDao.upsertInstallments(updatedInsts)
                }
            }

            val desc = buildString {
                append("تحصيل دفعة (${PaymentMethods.arabicLabel(paymentMethod)})")
                if (targetInvoice != null) append(" — فاتورة ${targetInvoice.invoiceNumber}")
                else append(" — دفعة على الحساب")
                if (excessRecorded) append(" (مع رصيد دائن)")
            }

            transactionDao.upsertCustomerTransaction(
                CustomerTransactionEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = tenantId,
                    customerId = customerId,
                    type = CustomerTxTypes.PAYMENT,
                    amountPiasters = amountPiasters,
                    signedDeltaPiasters = -amountPiasters,
                    runningBalancePiasters = 0L,
                    paymentMethod = paymentMethod,
                    referenceId = paymentId,
                    description = desc,
                    createdByUserId = actor.id,
                    createdAt = occurredAtMillis
                )
            )

            recalculateCustomerLedger(tenantId, customerId)
            recordAudit(
                tenantId,
                actor,
                "RECORD_PAYMENT",
                "PAYMENT",
                paymentId,
                "تحصيل ${MoneyFormatter.formatEgp(amountPiasters)} من العميل ${customer.name}"
            )
            RepoResult.Success(payment)
        }
    }

    // =========================================================================
    // Daily Withdrawal Invoices (السحب اليومي)
    // =========================================================================

    suspend fun createDailyWithdrawalInvoice(
        tenantId: String,
        actor: UserEntity,
        customerId: String
    ): RepoResult<DailyWithdrawalInvoiceEntity> {
        return db.withTransaction {
            val customer = customerDao.getCustomerById(tenantId, customerId)
                ?: return@withTransaction RepoResult.Error("العميل غير موجود")

            // Keep daily withdrawals connected: reuse existing OPEN daily invoice for this customer
            val existingOpen = dailyDao.getCustomerDailyInvoicesByStatus(
                tenantId = tenantId,
                customerId = customerId,
                status = DailyInvoiceStatuses.OPEN
            ).firstOrNull()
            if (existingOpen != null) {
                return@withTransaction RepoResult.Success(existingOpen)
            }

            val now = EgyptTime.nowMillis()
            val seq = dailyDao.countDailyInvoices(tenantId) + 1
            val number = "DW-${seq.toString().padStart(5, '0')}"
            val invoice = DailyWithdrawalInvoiceEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                customerId = customerId,
                invoiceNumber = number,
                invoiceDateEpochDay = EgyptTime.todayEpochDay(now),
                status = DailyInvoiceStatuses.OPEN,
                createdByUserId = actor.id,
                createdAt = now
            )
            dailyDao.upsertDailyInvoice(invoice)
            recordAudit(
                tenantId,
                actor,
                "OPEN_DAILY_INVOICE",
                "DAILY_WITHDRAWAL",
                invoice.id,
                "فتح فاتورة سحب يومي $number للعميل ${customer.name}"
            )
            RepoResult.Success(invoice)
        }
    }

    suspend fun addDailyWithdrawalItem(
        tenantId: String,
        actor: UserEntity,
        dailyInvoiceId: String,
        description: String,
        unitPricePiasters: Long,
        quantityMilli: Long,
        notes: String = "",
        recordedAtMillis: Long = EgyptTime.nowMillis(),
        productId: String? = null
    ): RepoResult<DailyWithdrawalItemEntity> {
        if (unitPricePiasters <= 0L) return RepoResult.Error("السعر يجب أن يكون أكبر من صفر")
        if (quantityMilli <= 0L) return RepoResult.Error("العدد يجب أن يكون أكبر من صفر")

        return db.withTransaction {
            val daily = dailyDao.getDailyInvoiceById(tenantId, dailyInvoiceId)
                ?: return@withTransaction RepoResult.Error("الفاتورة اليومية غير موجودة")
            if (daily.status != DailyInvoiceStatuses.OPEN) {
                return@withTransaction RepoResult.Error("الفاتورة مغلقة. أعد فتحها بصلاحية المدير لإضافة سحوبات.")
            }

            val lineTotal = MoneyFormatter.calculateLineTotalPiasters(unitPricePiasters, quantityMilli)
            val item = DailyWithdrawalItemEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                dailyInvoiceId = dailyInvoiceId,
                description = description.trim().ifBlank { "بضاعة" },
                unitPricePiasters = unitPricePiasters,
                quantityMilli = quantityMilli,
                lineTotalPiasters = lineTotal,
                notes = notes.trim(),
                recordedAtMillis = recordedAtMillis,
                createdByUserId = actor.id
            )
            dailyDao.upsertDailyItem(item)
            if (productId != null) {
                val prod = productDao.getProductById(tenantId, productId)
                if (prod != null && prod.trackStock) {
                    productDao.upsertProduct(
                        prod.copy(
                            stockQuantityMilli = (prod.stockQuantityMilli - quantityMilli).coerceAtLeast(0L),
                            updatedAt = recordedAtMillis
                        )
                    )
                }
            }
            recalculateDailyInvoiceTotals(tenantId, dailyInvoiceId)
            recordAudit(
                tenantId,
                actor,
                "ADD_DAILY_WITHDRAWAL",
                "DAILY_WITHDRAWAL",
                dailyInvoiceId,
                "إضافة سحبة (${item.description}) بقيمة ${MoneyFormatter.formatEgp(lineTotal)} في ${EgyptTime.formatTimeHms12(recordedAtMillis)}"
            )
            RepoResult.Success(item)
        }
    }

    suspend fun editDailyWithdrawalItem(
        tenantId: String,
        actor: UserEntity,
        itemId: String,
        dailyInvoiceId: String,
        newDescription: String,
        newUnitPricePiasters: Long,
        newQuantityMilli: Long,
        newNotes: String,
        reason: String
    ): RepoResult<DailyWithdrawalItemEntity> {
        if (newUnitPricePiasters <= 0L || newQuantityMilli <= 0L) {
            return RepoResult.Error("السعر والعدد يجب أن يكونا أكبر من صفر")
        }
        return db.withTransaction {
            val daily = dailyDao.getDailyInvoiceById(tenantId, dailyInvoiceId)
                ?: return@withTransaction RepoResult.Error("الفاتورة غير موجودة")
            if (daily.status != DailyInvoiceStatuses.OPEN) {
                return@withTransaction RepoResult.Error("لا يمكن التعديل على فاتورة مغلقة")
            }
            val existing = dailyDao.getDailyItems(tenantId, dailyInvoiceId).firstOrNull { it.id == itemId }
                ?: return@withTransaction RepoResult.Error("السحبة غير موجودة")

            val newTotal = MoneyFormatter.calculateLineTotalPiasters(newUnitPricePiasters, newQuantityMilli)
            val updated = existing.copy(
                description = newDescription.trim().ifBlank { "بضاعة" },
                unitPricePiasters = newUnitPricePiasters,
                quantityMilli = newQuantityMilli,
                lineTotalPiasters = newTotal,
                notes = newNotes.trim()
            )
            dailyDao.upsertDailyItem(updated)
            recalculateDailyInvoiceTotals(tenantId, dailyInvoiceId)
            recordAudit(
                tenantId,
                actor,
                "EDIT_DAILY_WITHDRAWAL_ITEM",
                "DAILY_WITHDRAWAL",
                dailyInvoiceId,
                "تعديل سحبة من ${MoneyFormatter.formatEgp(existing.lineTotalPiasters)} إلى ${MoneyFormatter.formatEgp(newTotal)} — السبب: ${reason.ifBlank { "تصحيح" }}"
            )
            RepoResult.Success(updated)
        }
    }

    suspend fun recordDailyInvoicePayment(
        tenantId: String,
        actor: UserEntity,
        dailyInvoiceId: String,
        amountPiasters: Long,
        paymentMethod: String,
        notes: String = "",
        idempotencyKey: String = UUID.randomUUID().toString()
    ): RepoResult<DailyWithdrawalInvoiceEntity> {
        if (amountPiasters <= 0L) return RepoResult.Error("المبلغ المدفوع يجب أن يكون أكبر من صفر")

        return db.withTransaction {
            val existingPay = paymentDao.getByIdempotencyKey(tenantId, idempotencyKey)
            val daily = dailyDao.getDailyInvoiceById(tenantId, dailyInvoiceId)
                ?: return@withTransaction RepoResult.Error("الفاتورة اليومية غير موجودة")
            if (existingPay != null) return@withTransaction RepoResult.Success(daily)

            val now = EgyptTime.nowMillis()
            val paymentId = UUID.randomUUID().toString()
            paymentDao.upsertPayment(
                PaymentEntity(
                    id = paymentId,
                    tenantId = tenantId,
                    customerId = daily.customerId,
                    invoiceId = daily.linkedSalesInvoiceId,
                    dailyInvoiceId = dailyInvoiceId,
                    amountPiasters = amountPiasters,
                    paymentMethod = paymentMethod,
                    notes = notes.trim().ifBlank { "دفعة على فاتورة سحب يومي ${daily.invoiceNumber}" },
                    idempotencyKey = idempotencyKey,
                    createdByUserId = actor.id,
                    createdAt = now
                )
            )
            transactionDao.upsertCustomerTransaction(
                CustomerTransactionEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = tenantId,
                    customerId = daily.customerId,
                    type = CustomerTxTypes.PAYMENT,
                    amountPiasters = amountPiasters,
                    signedDeltaPiasters = -amountPiasters,
                    runningBalancePiasters = 0L,
                    paymentMethod = paymentMethod,
                    referenceId = paymentId,
                    description = "دفعة فاتورة سحب يومي ${daily.invoiceNumber} (${PaymentMethods.arabicLabel(paymentMethod)})",
                    createdByUserId = actor.id,
                    createdAt = now
                )
            )

            val newPaid = daily.paidPiasters + amountPiasters
            val newRem = (daily.totalPiasters - newPaid).coerceAtLeast(0L)
            val updated = daily.copy(paidPiasters = newPaid, remainingPiasters = newRem)
            dailyDao.upsertDailyInvoice(updated)

            // Also update linked sales invoice if already closed
            daily.linkedSalesInvoiceId?.let { linkedId ->
                salesInvoiceDao.getInvoiceById(tenantId, linkedId)?.let { sinv ->
                    val sPaid = sinv.paidPiasters + amountPiasters
                    val sRem = (sinv.totalPiasters - sPaid).coerceAtLeast(0L)
                    salesInvoiceDao.upsertInvoice(
                        sinv.copy(
                            paidPiasters = sPaid,
                            remainingPiasters = sRem,
                            status = if (sRem == 0L) InvoiceStatuses.PAID else InvoiceStatuses.PARTIAL
                        )
                    )
                }
            }

            recalculateCustomerLedger(tenantId, daily.customerId)
            recordAudit(
                tenantId,
                actor,
                "DAILY_INVOICE_PAYMENT",
                "DAILY_WITHDRAWAL",
                dailyInvoiceId,
                "تحصيل ${MoneyFormatter.formatEgp(amountPiasters)} على فاتورة سحب يومي ${daily.invoiceNumber}"
            )
            RepoResult.Success(updated)
        }
    }

    /**
     * Closes a daily withdrawal invoice atomically and posts ONLY the unposted delta to the customer ledger.
     * Prevents duplicate closing and guarantees a daily invoice is NEVER double-counted!
     */
    suspend fun closeDailyWithdrawalInvoice(
        tenantId: String,
        actor: UserEntity,
        dailyInvoiceId: String
    ): RepoResult<DailyWithdrawalInvoiceEntity> {
        return db.withTransaction {
            val daily = dailyDao.getDailyInvoiceById(tenantId, dailyInvoiceId)
                ?: return@withTransaction RepoResult.Error("الفاتورة اليومية غير موجودة")
            if (daily.status == DailyInvoiceStatuses.CLOSED) {
                return@withTransaction RepoResult.Error("هذه الفاتورة مغلقة بالفعل ولا يمكن تكرار إغلاقها")
            }

            val items = dailyDao.getDailyItems(tenantId, dailyInvoiceId)
            if (items.isEmpty()) {
                return@withTransaction RepoResult.Error("لا يمكن إغلاق فاتورة سحب يومي فارغة")
            }

            val closed = closeDailyInvoiceLocked(tenantId, actor, daily, items)
            recalculateCustomerLedger(tenantId, daily.customerId)
            recordAudit(
                tenantId,
                actor,
                "CLOSE_DAILY_INVOICE",
                "DAILY_WITHDRAWAL",
                dailyInvoiceId,
                "إغلاق فاتورة سحب يومي ${daily.invoiceNumber} بإجمالي ${MoneyFormatter.formatEgp(closed.totalPiasters)}"
            )
            RepoResult.Success(closed)
        }
    }

    private suspend fun closeDailyInvoiceLocked(
        tenantId: String,
        actor: UserEntity,
        daily: DailyWithdrawalInvoiceEntity,
        items: List<DailyWithdrawalItemEntity>
    ): DailyWithdrawalInvoiceEntity {
        val total = items.sumOf { it.lineTotalPiasters }
        val totalQty = items.sumOf { it.quantityMilli }
        val now = EgyptTime.nowMillis()

        val unpostedDelta = total - daily.postedTotalPiasters
        var linkedSalesId = daily.linkedSalesInvoiceId

        if (unpostedDelta != 0L) {
            transactionDao.upsertCustomerTransaction(
                CustomerTransactionEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = tenantId,
                    customerId = daily.customerId,
                    type = CustomerTxTypes.DAILY_WITHDRAWAL_CLOSE,
                    amountPiasters = kotlin.math.abs(unpostedDelta),
                    signedDeltaPiasters = unpostedDelta,
                    runningBalancePiasters = 0L,
                    paymentMethod = PaymentMethods.NONE,
                    referenceId = daily.id,
                    description = if (!daily.isPostedToLedger) {
                        "إغلاق فاتورة سحب يومي ${daily.invoiceNumber} (${items.size} سحبات)"
                    } else {
                        "تعديل إغلاق فاتورة سحب يومي ${daily.invoiceNumber} بعد إعادة الفتح"
                    },
                    createdByUserId = actor.id,
                    createdAt = now
                )
            )
        }

        val rem = (total - daily.paidPiasters).coerceAtLeast(0L)
        val invStatus = when {
            rem == 0L -> InvoiceStatuses.PAID
            daily.paidPiasters > 0L -> InvoiceStatuses.PARTIAL
            else -> InvoiceStatuses.UNPAID
        }
        if (linkedSalesId == null) {
            linkedSalesId = UUID.randomUUID().toString()
            salesInvoiceDao.upsertInvoice(
                SalesInvoiceEntity(
                    id = linkedSalesId,
                    tenantId = tenantId,
                    customerId = daily.customerId,
                    invoiceNumber = daily.invoiceNumber,
                    saleType = if (rem == 0L) SaleTypes.CASH else SaleTypes.CREDIT,
                    paymentMethod = if (daily.paidPiasters > 0L) PaymentMethods.CASH else PaymentMethods.NONE,
                    totalPiasters = total,
                    paidPiasters = daily.paidPiasters,
                    remainingPiasters = rem,
                    notes = "فاتورة سحب يومي (${items.size} سحبات)",
                    status = invStatus,
                    sourceDailyInvoiceId = daily.id,
                    idempotencyKey = "daily_close_${daily.id}",
                    createdByUserId = actor.id,
                    createdAt = now
                )
            )
        } else {
            salesInvoiceDao.getInvoiceById(tenantId, linkedSalesId)?.let { existingSales ->
                salesInvoiceDao.upsertInvoice(
                    existingSales.copy(
                        totalPiasters = total,
                        paidPiasters = daily.paidPiasters,
                        remainingPiasters = rem,
                        status = invStatus
                    )
                )
            }
        }

        val closed = daily.copy(
            status = DailyInvoiceStatuses.CLOSED,
            withdrawalsCount = items.size,
            totalQuantityMilli = totalQty,
            totalPiasters = total,
            remainingPiasters = rem,
            isPostedToLedger = true,
            postedTotalPiasters = total,
            linkedSalesInvoiceId = linkedSalesId,
            closedAt = now
        )
        dailyDao.upsertDailyInvoice(closed)
        return closed
    }

    suspend fun reopenDailyWithdrawalInvoice(
        tenantId: String,
        actor: UserEntity,
        dailyInvoiceId: String,
        reason: String
    ): RepoResult<DailyWithdrawalInvoiceEntity> {
        val cleanReason = reason.trim()
        if (cleanReason.length < 3) {
            return RepoResult.Error("يرجى كتابة سبب واضح لإعادة فتح الفاتورة")
        }
        return db.withTransaction {
            val daily = dailyDao.getDailyInvoiceById(tenantId, dailyInvoiceId)
                ?: return@withTransaction RepoResult.Error("الفاتورة غير موجودة")
            if (daily.status == DailyInvoiceStatuses.OPEN) {
                return@withTransaction RepoResult.Error("الفاتورة مفتوحة بالفعل")
            }
            val reopened = daily.copy(
                status = DailyInvoiceStatuses.OPEN,
                reopenCount = daily.reopenCount + 1,
                lastReopenReason = cleanReason
            )
            dailyDao.upsertDailyInvoice(reopened)
            recordAudit(
                tenantId,
                actor,
                "REOPEN_DAILY_INVOICE",
                "DAILY_WITHDRAWAL",
                dailyInvoiceId,
                "إعادة فتح فاتورة سحب يومي ${daily.invoiceNumber} — السبب: $cleanReason"
            )
            RepoResult.Success(reopened)
        }
    }

    suspend fun deleteDailyWithdrawalItem(
        tenantId: String,
        actor: UserEntity,
        dailyInvoiceId: String,
        itemId: String
    ): RepoResult<Unit> {
        return db.withTransaction {
            val daily = dailyDao.getDailyInvoiceById(tenantId, dailyInvoiceId)
                ?: return@withTransaction RepoResult.Error("الفاتورة اليومية غير موجودة")
            if (daily.status != DailyInvoiceStatuses.OPEN) {
                return@withTransaction RepoResult.Error("لا يمكن حذف سحبة من فاتورة مغلقة. أعد فتحها أولاً.")
            }
            val item = dailyDao.getDailyItemById(tenantId, itemId)
            if (item == null || item.dailyInvoiceId != dailyInvoiceId) {
                return@withTransaction RepoResult.Error("السحبة غير موجودة")
            }
            dailyDao.deleteDailyItemById(tenantId, itemId)
            recalculateDailyInvoiceTotals(tenantId, dailyInvoiceId)
            recordAudit(
                tenantId,
                actor,
                "DELETE_DAILY_WITHDRAWAL_ITEM",
                "DAILY_WITHDRAWAL",
                dailyInvoiceId,
                "حذف سحبة (${item.description}) بقيمة ${MoneyFormatter.formatEgp(item.lineTotalPiasters)} من ${daily.invoiceNumber}"
            )
            RepoResult.Success(Unit)
        }
    }

    suspend fun deleteDailyWithdrawalInvoice(
        tenantId: String,
        actor: UserEntity,
        dailyInvoiceId: String
    ): RepoResult<Unit> {
        return db.withTransaction {
            val daily = dailyDao.getDailyInvoiceById(tenantId, dailyInvoiceId)
                ?: return@withTransaction RepoResult.Error("الفاتورة اليومية غير موجودة")

            val linkedPayments = paymentDao.getPaymentsForDailyOrLinkedInvoice(
                tenantId = tenantId,
                dailyInvoiceId = dailyInvoiceId,
                linkedSalesId = daily.linkedSalesInvoiceId
            )
            linkedPayments.forEach { p ->
                transactionDao.deleteCustomerTransactionsByReference(tenantId, daily.customerId, p.id)
                paymentDao.deletePaymentById(tenantId, p.id)
            }
            transactionDao.deleteCustomerTransactionsByReference(tenantId, daily.customerId, dailyInvoiceId)
            daily.linkedSalesInvoiceId?.let { linkedId ->
                transactionDao.deleteCustomerTransactionsByReference(tenantId, daily.customerId, linkedId)
                salesInvoiceDao.deleteInvoice(tenantId, linkedId)
            }
            dailyDao.deleteDailyItemsForInvoice(tenantId, dailyInvoiceId)
            dailyDao.deleteDailyInvoice(tenantId, dailyInvoiceId)
            recalculateCustomerLedger(tenantId, daily.customerId)

            recordAudit(
                tenantId,
                actor,
                "DELETE_DAILY_INVOICE",
                "DAILY_WITHDRAWAL",
                dailyInvoiceId,
                "حذف فاتورة السحب اليومي ${daily.invoiceNumber} بقيمة ${MoneyFormatter.formatEgp(daily.totalPiasters)}"
            )
            RepoResult.Success(Unit)
        }
    }

    suspend fun mergeDuplicateOpenDailyInvoices(
        tenantId: String,
        actor: UserEntity
    ): RepoResult<Int> {
        return db.withTransaction {
            val openInvoices = dailyDao.getAllDailyInvoices(tenantId)
                .filter { it.status == DailyInvoiceStatuses.OPEN }
                .sortedBy { it.createdAt }
                .groupBy { it.customerId }

            var mergedCount = 0
            openInvoices.forEach { (_, list) ->
                if (list.size > 1) {
                    val primary = list.first()
                    var extraPaid = 0L
                    list.drop(1).forEach { dup ->
                        val items = dailyDao.getDailyItems(tenantId, dup.id)
                        if (items.isNotEmpty()) {
                            dailyDao.upsertDailyItems(items.map { it.copy(dailyInvoiceId = primary.id) })
                        }
                        val dupPayments = paymentDao.getPaymentsForDailyOrLinkedInvoice(
                            tenantId = tenantId,
                            dailyInvoiceId = dup.id,
                            linkedSalesId = dup.linkedSalesInvoiceId
                        )
                        if (dupPayments.isNotEmpty()) {
                            paymentDao.upsertPayments(dupPayments.map { it.copy(dailyInvoiceId = primary.id) })
                        }
                        extraPaid += dup.paidPiasters
                        dailyDao.deleteDailyInvoice(tenantId, dup.id)
                        mergedCount++
                    }
                    if (extraPaid > 0L) {
                        val currentPrimary = dailyDao.getDailyInvoiceById(tenantId, primary.id) ?: primary
                        dailyDao.upsertDailyInvoice(
                            currentPrimary.copy(paidPiasters = currentPrimary.paidPiasters + extraPaid)
                        )
                    }
                    recalculateDailyInvoiceTotals(tenantId, primary.id)
                }
            }
            if (mergedCount > 0) {
                recordAudit(
                    tenantId,
                    actor,
                    "MERGE_DAILY_INVOICES",
                    "DAILY_WITHDRAWAL",
                    tenantId,
                    "دمج $mergedCount فاتورة سحب يومي مكررة في فواتير متصلة"
                )
            }
            RepoResult.Success(mergedCount)
        }
    }

    private suspend fun recalculateDailyInvoiceTotals(tenantId: String, dailyInvoiceId: String) {
        val daily = dailyDao.getDailyInvoiceById(tenantId, dailyInvoiceId) ?: return
        val items = dailyDao.getDailyItems(tenantId, dailyInvoiceId)
        val total = items.sumOf { it.lineTotalPiasters }
        val totalQty = items.sumOf { it.quantityMilli }
        val rem = (total - daily.paidPiasters).coerceAtLeast(0L)
        dailyDao.upsertDailyInvoice(
            daily.copy(
                withdrawalsCount = items.size,
                totalQuantityMilli = totalQty,
                totalPiasters = total,
                remainingPiasters = rem
            )
        )
    }

    // =========================================================================
    // Deterministic Customer Ledger & Status Recalculation
    // =========================================================================

    private suspend fun recalculateCustomerLedger(tenantId: String, customerId: String) {
        val customer = customerDao.getCustomerById(tenantId, customerId) ?: return
        val txs = transactionDao.getCustomerTransactionsAsc(tenantId, customerId)
        var running = 0L
        var totalSales = 0L
        var totalPaid = 0L
        val updatedTxs = txs.map { tx ->
            running += tx.signedDeltaPiasters
            if (tx.signedDeltaPiasters > 0L) {
                totalSales += tx.signedDeltaPiasters
            } else {
                totalPaid += -tx.signedDeltaPiasters
            }
            if (tx.runningBalancePiasters != running) tx.copy(runningBalancePiasters = running) else tx
        }
        transactionDao.upsertCustomerTransactions(updatedTxs)

        // Refresh installment statuses against today's date
        val now = EgyptTime.nowMillis()
        val dayStart = EgyptTime.startOfDayMillis(now)
        val installments = installmentDao.getCustomerInstallments(tenantId, customerId)
        val refreshedInsts = installments.map { inst ->
            val st = computeInstallmentStatus(inst.dueDateMillis, inst.paidPiasters, inst.remainingPiasters, now)
            if (st != inst.status) inst.copy(status = st) else inst
        }
        if (refreshedInsts.isNotEmpty()) installmentDao.upsertInstallments(refreshedInsts)

        val invoices = salesInvoiceDao.getCustomerInvoices(tenantId, customerId)
        val hasOverdueInvoice = invoices.any {
            it.remainingPiasters > 0L && it.dueDateMillis != null && it.dueDateMillis < dayStart
        }
        val hasOverdueInstallment = refreshedInsts.any { it.status == InstallmentStatuses.OVERDUE }

        val accountStatus = when {
            running <= 0L -> CustomerAccountStatus.CLEAR
            hasOverdueInvoice || hasOverdueInstallment -> CustomerAccountStatus.OVERDUE
            else -> CustomerAccountStatus.OWING
        }

        customerDao.upsertCustomer(
            customer.copy(
                totalSalesPiasters = totalSales,
                totalPaidPiasters = totalPaid,
                currentBalancePiasters = running,
                accountStatus = accountStatus,
                updatedAt = now
            )
        )
    }

    private fun computeInstallmentStatus(
        dueDateMillis: Long,
        paidPiasters: Long,
        remainingPiasters: Long,
        nowMillis: Long = EgyptTime.nowMillis()
    ): String {
        if (remainingPiasters <= 0L) return InstallmentStatuses.PAID
        val dayStart = EgyptTime.startOfDayMillis(nowMillis)
        val dayEnd = EgyptTime.endOfDayMillis(nowMillis)
        return when {
            dueDateMillis < dayStart -> InstallmentStatuses.OVERDUE
            paidPiasters > 0L -> InstallmentStatuses.PARTIAL
            dueDateMillis in dayStart..dayEnd -> InstallmentStatuses.DUE
            else -> InstallmentStatuses.UPCOMING
        }
    }

    // =========================================================================
    // Settings
    // =========================================================================

    suspend fun saveAppSettings(tenantId: String, settings: AppSettingEntity) {
        systemDao.upsertAppSettings(settings.copy(tenantId = tenantId, updatedAt = EgyptTime.nowMillis()))
    }

    // =========================================================================
    // Reports Engine (All 13 Required Reports)
    // =========================================================================

    suspend fun generateReport(
        tenantId: String,
        type: ReportType,
        fromMillis: Long,
        toMillis: Long,
        query: String = ""
    ): ReportOutput {
        val start = EgyptTime.startOfDayMillis(fromMillis)
        val end = EgyptTime.endOfDayMillis(toMillis)
        val q = query.trim().lowercase()

        val customers = customerDao.getCustomers(tenantId)
        val customerMap = customers.associateBy { it.id }
        val suppliers = supplierDao.getSuppliers(tenantId)
        val supplierMap = suppliers.associateBy { it.id }
        val periodLabel = "من ${EgyptTime.formatDate(start)} إلى ${EgyptTime.formatDate(end)}"

        return when (type) {
            ReportType.DAILY_SALES,
            ReportType.MONTHLY_SALES,
            ReportType.PERIOD_SALES,
            ReportType.CASH_SALES,
            ReportType.EWALLET_SALES,
            ReportType.CREDIT_SALES,
            ReportType.INVOICES_OPEN_CLOSED -> {
                val rangeStart = when (type) {
                    ReportType.DAILY_SALES -> EgyptTime.startOfDayMillis(EgyptTime.nowMillis())
                    ReportType.MONTHLY_SALES -> EgyptTime.startOfMonthMillis(EgyptTime.nowMillis())
                    else -> start
                }
                val rangeEnd = when (type) {
                    ReportType.DAILY_SALES, ReportType.MONTHLY_SALES -> EgyptTime.endOfDayMillis(EgyptTime.nowMillis())
                    else -> end
                }
                val allInvs = salesInvoiceDao.getInvoices(tenantId).filter { inv ->
                    val inRange = inv.createdAt in rangeStart..rangeEnd
                    val cname = customerMap[inv.customerId]?.name?.lowercase().orEmpty()
                    val matchesQ = q.isEmpty() || cname.contains(q) || inv.invoiceNumber.lowercase().contains(q)
                    val matchesType = when (type) {
                        ReportType.CASH_SALES -> inv.saleType == SaleTypes.CASH && inv.paymentMethod == PaymentMethods.CASH
                        ReportType.EWALLET_SALES -> inv.paymentMethod == PaymentMethods.EWALLET
                        ReportType.CREDIT_SALES -> inv.saleType == SaleTypes.CREDIT || inv.saleType == SaleTypes.SCHEDULED
                        else -> true
                    }
                    inRange && matchesQ && matchesType
                }
                val sumTotal = allInvs.sumOf { it.totalPiasters }
                val sumPaid = allInvs.sumOf { it.paidPiasters }
                val sumRem = allInvs.sumOf { it.remainingPiasters }

                val summary = mutableListOf(
                    "عدد الفواتير" to "${allInvs.size}",
                    "إجمالي المبيعات" to MoneyFormatter.formatEgp(sumTotal),
                    "إجمالي المدفوع" to MoneyFormatter.formatEgp(sumPaid),
                    "إجمالي المتبقي" to MoneyFormatter.formatEgp(sumRem)
                )
                if (type == ReportType.INVOICES_OPEN_CLOSED) {
                    val closedCount = allInvs.count { it.remainingPiasters == 0L }
                    val openCount = allInvs.size - closedCount
                    summary.add("فواتير مسددة / مفتوحة" to "$closedCount مسددة — $openCount مفتوحة")
                }

                ReportOutput(
                    type = type,
                    periodLabel = "من ${EgyptTime.formatDate(rangeStart)} إلى ${EgyptTime.formatDate(rangeEnd)}",
                    summaryPairs = summary,
                    columns = listOf("الفاتورة", "العميل", "التاريخ", "الإجمالي", "المتبقي"),
                    rows = allInvs.map { inv ->
                        listOf(
                            inv.invoiceNumber,
                            customerMap[inv.customerId]?.name ?: "—",
                            EgyptTime.formatDate(inv.createdAt),
                            MoneyFormatter.formatEgp(inv.totalPiasters, ""),
                            MoneyFormatter.formatEgp(inv.remainingPiasters, "")
                        )
                    }
                )
            }

            ReportType.OVERDUE -> {
                val nowStart = EgyptTime.startOfDayMillis(EgyptTime.nowMillis())
                val overdueInsts = installmentDao.getAllInstallments(tenantId).filter {
                    it.remainingPiasters > 0L && it.dueDateMillis < nowStart &&
                        (q.isEmpty() || (customerMap[it.customerId]?.name?.lowercase()?.contains(q) == true))
                }
                val overdueInvs = salesInvoiceDao.getInvoices(tenantId).filter {
                    it.remainingPiasters > 0L && it.dueDateMillis != null && it.dueDateMillis < nowStart &&
                        it.saleType == SaleTypes.CREDIT &&
                        (q.isEmpty() || (customerMap[it.customerId]?.name?.lowercase()?.contains(q) == true))
                }
                val rows = mutableListOf<List<String>>()
                var totalOverdue = 0L
                overdueInsts.forEach { inst ->
                    totalOverdue += inst.remainingPiasters
                    rows.add(
                        listOf(
                            customerMap[inst.customerId]?.name ?: "—",
                            "قسط #${inst.installmentIndex}",
                            EgyptTime.formatDate(inst.dueDateMillis),
                            MoneyFormatter.formatEgp(inst.remainingPiasters, "")
                        )
                    )
                }
                overdueInvs.forEach { inv ->
                    totalOverdue += inv.remainingPiasters
                    rows.add(
                        listOf(
                            customerMap[inv.customerId]?.name ?: "—",
                            "فاتورة ${inv.invoiceNumber}",
                            inv.dueDateMillis?.let { EgyptTime.formatDate(it) } ?: "—",
                            MoneyFormatter.formatEgp(inv.remainingPiasters, "")
                        )
                    )
                }
                ReportOutput(
                    type = type,
                    periodLabel = "حتى ${EgyptTime.formatDate(EgyptTime.nowMillis())}",
                    summaryPairs = listOf(
                        "عدد المستحقات المتأخرة" to "${rows.size}",
                        "إجمالي المتأخرات" to MoneyFormatter.formatEgp(totalOverdue)
                    ),
                    columns = listOf("العميل", "المرجع", "تاريخ الاستحقاق", "المتبقي"),
                    rows = rows
                )
            }

            ReportType.CUSTOMER_COLLECTIONS -> {
                val pays = paymentDao.getPayments(tenantId).filter { p ->
                    p.createdAt in start..end &&
                        (q.isEmpty() || (customerMap[p.customerId]?.name?.lowercase()?.contains(q) == true))
                }
                val cashSum = pays.filter { it.paymentMethod == PaymentMethods.CASH }.sumOf { it.amountPiasters }
                val walletSum = pays.filter { it.paymentMethod == PaymentMethods.EWALLET }.sumOf { it.amountPiasters }
                ReportOutput(
                    type = type,
                    periodLabel = periodLabel,
                    summaryPairs = listOf(
                        "إجمالي التحصيلات" to MoneyFormatter.formatEgp(pays.sumOf { it.amountPiasters }),
                        "تحصيلات نقدية" to MoneyFormatter.formatEgp(cashSum),
                        "تحصيلات محافظ إلكترونية" to MoneyFormatter.formatEgp(walletSum)
                    ),
                    columns = listOf("العميل", "التاريخ", "الطريقة", "المبلغ"),
                    rows = pays.map { p ->
                        listOf(
                            customerMap[p.customerId]?.name ?: "—",
                            EgyptTime.formatDateTime(p.createdAt),
                            PaymentMethods.arabicLabel(p.paymentMethod),
                            MoneyFormatter.formatEgp(p.amountPiasters, "")
                        )
                    }
                )
            }

            ReportType.SUPPLIER_PAYMENTS -> {
                val txs = transactionDao.getAllSupplierTransactions(tenantId).filter { tx ->
                    tx.createdAt in start..end &&
                        (tx.type == SupplierTxTypes.PAYMENT_PARTIAL || tx.type == SupplierTxTypes.PAYMENT_FULL) &&
                        (q.isEmpty() || (supplierMap[tx.supplierId]?.name?.lowercase()?.contains(q) == true))
                }
                ReportOutput(
                    type = type,
                    periodLabel = periodLabel,
                    summaryPairs = listOf(
                        "عدد المدفوعات" to "${txs.size}",
                        "إجمالي مدفوعات الموردين" to MoneyFormatter.formatEgp(txs.sumOf { it.amountPiasters })
                    ),
                    columns = listOf("المورد", "التاريخ", "الطريقة", "المبلغ"),
                    rows = txs.map { tx ->
                        listOf(
                            supplierMap[tx.supplierId]?.name ?: "—",
                            EgyptTime.formatDateTime(tx.createdAt),
                            PaymentMethods.arabicLabel(tx.paymentMethod),
                            MoneyFormatter.formatEgp(tx.amountPiasters, "")
                        )
                    }
                )
            }

            ReportType.CUSTOMER_ACCOUNTS -> {
                val filtered = customers.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.phone.contains(q) }
                ReportOutput(
                    type = type,
                    periodLabel = "كشف أرصدة العملاء",
                    summaryPairs = listOf(
                        "عدد العملاء" to "${filtered.size}",
                        "إجمالي المبيعات" to MoneyFormatter.formatEgp(filtered.sumOf { it.totalSalesPiasters }),
                        "إجمالي التحصيلات" to MoneyFormatter.formatEgp(filtered.sumOf { it.totalPaidPiasters }),
                        "إجمالي المستحقات" to MoneyFormatter.formatEgp(filtered.sumOf { it.currentBalancePiasters.coerceAtLeast(0L) })
                    ),
                    columns = listOf("العميل", "المبيعات", "المدفوع", "الرصيد", "الحالة"),
                    rows = filtered.map { c ->
                        listOf(
                            c.name,
                            MoneyFormatter.formatEgp(c.totalSalesPiasters, ""),
                            MoneyFormatter.formatEgp(c.totalPaidPiasters, ""),
                            MoneyFormatter.formatEgp(c.currentBalancePiasters, ""),
                            CustomerAccountStatus.arabicLabel(c.accountStatus)
                        )
                    }
                )
            }

            ReportType.SUPPLIER_ACCOUNTS -> {
                val filtered = suppliers.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.phone.contains(q) }
                ReportOutput(
                    type = type,
                    periodLabel = "كشف أرصدة الموردين",
                    summaryPairs = listOf(
                        "عدد الموردين" to "${filtered.size}",
                        "إجمالي المستحقات" to MoneyFormatter.formatEgp(filtered.sumOf { it.totalDuePiasters }),
                        "إجمالي المدفوع" to MoneyFormatter.formatEgp(filtered.sumOf { it.totalPaidPiasters }),
                        "الرصيد المتبقي" to MoneyFormatter.formatEgp(filtered.sumOf { it.currentBalancePiasters.coerceAtLeast(0L) })
                    ),
                    columns = listOf("المورد", "المستحق", "المدفوع", "الرصيد"),
                    rows = filtered.map { s ->
                        listOf(
                            s.name,
                            MoneyFormatter.formatEgp(s.totalDuePiasters, ""),
                            MoneyFormatter.formatEgp(s.totalPaidPiasters, ""),
                            MoneyFormatter.formatEgp(s.currentBalancePiasters, "")
                        )
                    }
                )
            }

            ReportType.DAILY_WITHDRAWALS -> {
                val dailies = dailyDao.getAllDailyInvoices(tenantId).filter { d ->
                    d.createdAt in start..end &&
                        (q.isEmpty() || (customerMap[d.customerId]?.name?.lowercase()?.contains(q) == true))
                }
                ReportOutput(
                    type = type,
                    periodLabel = periodLabel,
                    summaryPairs = listOf(
                        "عدد الفواتير اليومية" to "${dailies.size}",
                        "فواتير مفتوحة" to "${dailies.count { it.status == DailyInvoiceStatuses.OPEN }}",
                        "إجمالي السحوبات" to MoneyFormatter.formatEgp(dailies.sumOf { it.totalPiasters }),
                        "المتبقي" to MoneyFormatter.formatEgp(dailies.sumOf { it.remainingPiasters })
                    ),
                    columns = listOf("الفاتورة", "العميل", "السحبات", "الإجمالي", "الحالة"),
                    rows = dailies.map { d ->
                        listOf(
                            d.invoiceNumber,
                            customerMap[d.customerId]?.name ?: "—",
                            "${d.withdrawalsCount}",
                            MoneyFormatter.formatEgp(d.totalPiasters, ""),
                            if (d.status == DailyInvoiceStatuses.OPEN) "مفتوحة" else "مغلقة"
                        )
                    }
                )
            }

            ReportType.PURCHASES -> {
                val purchases = purchaseInvoiceDao.getPurchaseInvoices(tenantId).filter { inv ->
                    val inRange = inv.createdAt in start..end
                    val sname = supplierMap[inv.supplierId]?.name?.lowercase().orEmpty()
                    val matchesQ = q.isEmpty() || sname.contains(q) || inv.invoiceNumber.lowercase().contains(q)
                    inRange && matchesQ
                }
                val purchaseIds = purchases.map { it.id }.toSet()
                val itemsByPurchase = purchaseInvoiceDao.getAllPurchaseInvoiceItems(tenantId)
                    .filter { it.purchaseInvoiceId in purchaseIds }
                    .groupBy { it.purchaseInvoiceId }
                val sumTotal = purchases.sumOf { it.totalPiasters }
                val sumPaid = purchases.sumOf { it.paidPiasters }
                val sumRem = purchases.sumOf { it.remainingPiasters }
                val totalQty = purchaseIds.sumOf { pid -> itemsByPurchase[pid]?.sumOf { it.quantityMilli } ?: 0L }

                ReportOutput(
                    type = type,
                    periodLabel = periodLabel,
                    summaryPairs = listOf(
                        "عدد فواتير الشراء" to "${purchases.size}",
                        "إجمالي العدد / الكمية" to MoneyFormatter.formatQuantity(totalQty),
                        "إجمالي المشتريات" to MoneyFormatter.formatEgp(sumTotal),
                        "المدفوع للموردين" to MoneyFormatter.formatEgp(sumPaid),
                        "المتبقي للموردين" to MoneyFormatter.formatEgp(sumRem)
                    ),
                    columns = listOf("الفاتورة", "المورد", "الكمية", "الإجمالي", "المتبقي"),
                    rows = purchases.map { inv ->
                        val invQty = itemsByPurchase[inv.id]?.sumOf { it.quantityMilli } ?: 0L
                        listOf(
                            inv.invoiceNumber,
                            supplierMap[inv.supplierId]?.name ?: "—",
                            MoneyFormatter.formatQuantity(invQty),
                            MoneyFormatter.formatEgp(inv.totalPiasters, ""),
                            MoneyFormatter.formatEgp(inv.remainingPiasters, "")
                        )
                    }
                )
            }

            ReportType.QUICK_SALES -> {
                val filtered = quickSaleDao.getQuickSales(tenantId).filter { qs ->
                    qs.createdAt in start..end &&
                        (q.isEmpty() || qs.description.lowercase().contains(q) || MoneyFormatter.formatEgp(qs.amountPiasters, "").contains(q))
                }
                ReportOutput(
                    type = type,
                    periodLabel = periodLabel,
                    summaryPairs = listOf(
                        "عدد السحوبات" to "${filtered.size}",
                        "إجمالي المبيعات السريعة" to MoneyFormatter.formatEgp(filtered.sumOf { it.amountPiasters }),
                        "نقدي" to MoneyFormatter.formatEgp(filtered.filter { it.paymentMethod == PaymentMethods.CASH }.sumOf { it.amountPiasters }),
                        "محفظة إلكترونية" to MoneyFormatter.formatEgp(filtered.filter { it.paymentMethod == PaymentMethods.EWALLET }.sumOf { it.amountPiasters })
                    ),
                    columns = listOf("التاريخ والوقت", "البيان", "الكمية / الوحدة", "الطريقة", "المبلغ"),
                    rows = filtered.map { qs ->
                        val qtyText = if (qs.quantityMilli != 1_000L || qs.unitLabel.isNotBlank()) {
                            val qStr = MoneyFormatter.formatQuantity(qs.quantityMilli)
                            if (qs.unitLabel.isNotBlank()) "$qStr ${qs.unitLabel}" else qStr
                        } else {
                            "—"
                        }
                        listOf(
                            EgyptTime.formatDateTime(qs.createdAt),
                            qs.description.ifBlank { "بدون بيان" },
                            qtyText,
                            PaymentMethods.arabicLabel(qs.paymentMethod),
                            MoneyFormatter.formatEgp(qs.amountPiasters, "")
                        )
                    }
                )
            }
        }
    }

    // =========================================================================
    // Products / Items Catalog (Optional per Shop)
    // =========================================================================

    suspend fun addProduct(
        tenantId: String,
        actor: UserEntity,
        name: String,
        category: String,
        skuOrBarcode: String,
        unitLabel: String,
        salePricePiasters: Long,
        purchasePricePiasters: Long,
        stockQuantityMilli: Long,
        trackStock: Boolean,
        minStockAlertMilli: Long,
        notes: String,
        halfWholesalePricePiasters: Long = 0L,
        wholesalePricePiasters: Long = 0L
    ): RepoResult<ProductEntity> {
        val cleanName = name.trim()
        if (cleanName.length < 2) return RepoResult.Error("يرجى إدخال اسم الصنف (حرفين على الأقل)")
        if (salePricePiasters < 0L || halfWholesalePricePiasters < 0L || wholesalePricePiasters < 0L || purchasePricePiasters < 0L) {
            return RepoResult.Error("السعر لا يمكن أن يكون سالبًا")
        }
        if (trackStock && stockQuantityMilli < 0L) {
            return RepoResult.Error("كمية المخزون لا يمكن أن تكون سالبة")
        }

        val now = EgyptTime.nowMillis()
        val product = ProductEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            name = cleanName,
            category = category.trim().ifBlank { "عام" },
            skuOrBarcode = skuOrBarcode.trim(),
            unitLabel = unitLabel.trim().ifBlank { "قطعة" },
            salePricePiasters = salePricePiasters,
            halfWholesalePricePiasters = halfWholesalePricePiasters,
            wholesalePricePiasters = wholesalePricePiasters,
            purchasePricePiasters = purchasePricePiasters,
            stockQuantityMilli = if (trackStock) stockQuantityMilli else 0L,
            trackStock = trackStock,
            minStockAlertMilli = if (trackStock) minStockAlertMilli.coerceAtLeast(0L) else 0L,
            notes = notes.trim(),
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        return db.withTransaction {
            productDao.upsertProduct(product)
            recordAudit(
                tenantId = tenantId,
                actor = actor,
                action = "ADD_PRODUCT",
                entityType = "PRODUCT",
                entityId = product.id,
                details = "إضافة صنف: ${product.name} — قطاعي ${MoneyFormatter.formatEgp(product.salePricePiasters)}"
            )
            RepoResult.Success(product)
        }
    }

    suspend fun updateProduct(
        tenantId: String,
        actor: UserEntity,
        productId: String,
        name: String,
        category: String,
        skuOrBarcode: String,
        unitLabel: String,
        salePricePiasters: Long,
        purchasePricePiasters: Long,
        stockQuantityMilli: Long,
        trackStock: Boolean,
        minStockAlertMilli: Long,
        notes: String,
        halfWholesalePricePiasters: Long = 0L,
        wholesalePricePiasters: Long = 0L
    ): RepoResult<ProductEntity> {
        val cleanName = name.trim()
        if (cleanName.length < 2) return RepoResult.Error("يرجى إدخال اسم الصنف (حرفين على الأقل)")
        if (salePricePiasters < 0L || halfWholesalePricePiasters < 0L || wholesalePricePiasters < 0L || purchasePricePiasters < 0L) {
            return RepoResult.Error("السعر لا يمكن أن يكون سالبًا")
        }
        return db.withTransaction {
            val existing = productDao.getProductById(tenantId, productId)
                ?: return@withTransaction RepoResult.Error("الصنف غير موجود")
            val updated = existing.copy(
                name = cleanName,
                category = category.trim().ifBlank { "عام" },
                skuOrBarcode = skuOrBarcode.trim(),
                unitLabel = unitLabel.trim().ifBlank { "قطعة" },
                salePricePiasters = salePricePiasters,
                halfWholesalePricePiasters = halfWholesalePricePiasters,
                wholesalePricePiasters = wholesalePricePiasters,
                purchasePricePiasters = purchasePricePiasters,
                stockQuantityMilli = if (trackStock) stockQuantityMilli.coerceAtLeast(0L) else 0L,
                trackStock = trackStock,
                minStockAlertMilli = if (trackStock) minStockAlertMilli.coerceAtLeast(0L) else 0L,
                notes = notes.trim(),
                updatedAt = EgyptTime.nowMillis()
            )
            productDao.upsertProduct(updated)
            recordAudit(
                tenantId = tenantId,
                actor = actor,
                action = "UPDATE_PRODUCT",
                entityType = "PRODUCT",
                entityId = updated.id,
                details = "تعديل صنف: ${updated.name} — قطاعي ${MoneyFormatter.formatEgp(updated.salePricePiasters)}"
            )
            RepoResult.Success(updated)
        }
    }

    suspend fun adjustProductStock(
        tenantId: String,
        actor: UserEntity,
        productId: String,
        deltaMilli: Long,
        reason: String
    ): RepoResult<ProductEntity> {
        if (deltaMilli == 0L) return RepoResult.Error("يرجى إدخال كمية أكبر من صفر")
        return db.withTransaction {
            val existing = productDao.getProductById(tenantId, productId)
                ?: return@withTransaction RepoResult.Error("الصنف غير موجود")
            val newQty = (existing.stockQuantityMilli + deltaMilli).coerceAtLeast(0L)
            val updated = existing.copy(
                trackStock = true,
                stockQuantityMilli = newQty,
                updatedAt = EgyptTime.nowMillis()
            )
            productDao.upsertProduct(updated)
            val signLabel = if (deltaMilli > 0L) "+${MoneyFormatter.formatQuantity(deltaMilli)}" else "-${MoneyFormatter.formatQuantity(-deltaMilli)}"
            recordAudit(
                tenantId = tenantId,
                actor = actor,
                action = "ADJUST_PRODUCT_STOCK",
                entityType = "PRODUCT",
                entityId = updated.id,
                details = "تعديل مخزون (${updated.name}): $signLabel ${updated.unitLabel} — الرصيد الجديد ${MoneyFormatter.formatQuantity(newQty)} ${reason.trim().ifBlank { "" }}"
            )
            RepoResult.Success(updated)
        }
    }

    suspend fun deleteProduct(
        tenantId: String,
        actor: UserEntity,
        productId: String
    ): RepoResult<Unit> {
        return db.withTransaction {
            val existing = productDao.getProductById(tenantId, productId)
                ?: return@withTransaction RepoResult.Error("الصنف غير موجود")
            productDao.deleteProduct(tenantId, productId)
            recordAudit(
                tenantId = tenantId,
                actor = actor,
                action = "DELETE_PRODUCT",
                entityType = "PRODUCT",
                entityId = productId,
                details = "حذف صنف: ${existing.name}"
            )
            RepoResult.Success(Unit)
        }
    }

    // =========================================================================
    // Quick Sales (مبيعات وسحوبات سريعة بدون عميل - بالسعر فقط)
    // =========================================================================

    suspend fun addQuickSale(
        tenantId: String,
        actor: UserEntity,
        amountPiasters: Long,
        description: String = "",
        paymentMethod: String = PaymentMethods.CASH,
        quantityMilli: Long = 1_000L,
        unitLabel: String = "",
        occurredAtMillis: Long = EgyptTime.nowMillis(),
        idempotencyKey: String = UUID.randomUUID().toString()
    ): RepoResult<QuickSaleEntity> {
        if (amountPiasters <= 0L) {
            return RepoResult.Error("يرجى إدخال السعر / المبلغ أكبر من صفر")
        }
        if (quantityMilli <= 0L) {
            return RepoResult.Error("يرجى إدخال العدد / الكمية أكبر من صفر")
        }
        val cleanDesc = description.trim()
        val cleanUnit = unitLabel.trim()
        val computedTotalPiasters = if (quantityMilli == 1_000L) {
            amountPiasters
        } else {
            MoneyFormatter.calculateLineTotalPiasters(amountPiasters, quantityMilli)
        }
        if (computedTotalPiasters <= 0L) {
            return RepoResult.Error("إجمالي السحبة يجب أن يكون أكبر من صفر")
        }
        val safeMethod = when (paymentMethod) {
            PaymentMethods.CASH, PaymentMethods.EWALLET, PaymentMethods.BANK_TRANSFER -> paymentMethod
            else -> PaymentMethods.CASH
        }

        return db.withTransaction {
            val dup = quickSaleDao.getByIdempotencyKey(tenantId, idempotencyKey)
            if (dup != null) return@withTransaction RepoResult.Success(dup)

            val sale = QuickSaleEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                amountPiasters = computedTotalPiasters,
                unitPricePiasters = amountPiasters,
                quantityMilli = quantityMilli,
                unitLabel = cleanUnit,
                description = cleanDesc,
                paymentMethod = safeMethod,
                idempotencyKey = idempotencyKey,
                createdByUserId = actor.id,
                createdAt = occurredAtMillis
            )
            quickSaleDao.upsertQuickSale(sale)
            val descLabel = cleanDesc.ifBlank { "بدون بيان" }
            val qtyAudit = if (quantityMilli != 1_000L || cleanUnit.isNotBlank()) {
                " — الكمية: ${MoneyFormatter.formatQuantity(quantityMilli)}${if (cleanUnit.isNotBlank()) " $cleanUnit" else ""}"
            } else ""
            recordAudit(
                tenantId = tenantId,
                actor = actor,
                action = "ADD_QUICK_SALE",
                entityType = "QUICK_SALE",
                entityId = sale.id,
                details = "سحبة مبيعات سريعة بقيمة ${MoneyFormatter.formatEgp(computedTotalPiasters)} ($descLabel)$qtyAudit"
            )
            RepoResult.Success(sale)
        }
    }

    suspend fun deleteQuickSale(
        tenantId: String,
        actor: UserEntity,
        quickSaleId: String
    ): RepoResult<Unit> {
        return db.withTransaction {
            val existing = quickSaleDao.getQuickSaleById(tenantId, quickSaleId)
                ?: return@withTransaction RepoResult.Error("السجل غير موجود")
            quickSaleDao.deleteQuickSale(tenantId, quickSaleId)
            recordAudit(
                tenantId = tenantId,
                actor = actor,
                action = "DELETE_QUICK_SALE",
                entityType = "QUICK_SALE",
                entityId = quickSaleId,
                details = "حذف سحبة مبيعات بقيمة ${MoneyFormatter.formatEgp(existing.amountPiasters)}"
            )
            RepoResult.Success(Unit)
        }
    }

    private suspend fun recordAudit(
        tenantId: String,
        actor: UserEntity,
        action: String,
        entityType: String,
        entityId: String,
        details: String
    ) {
        systemDao.insertAuditLog(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                actorUserId = actor.id,
                actorUsername = actor.username,
                action = action,
                entityType = entityType,
                entityId = entityId,
                details = details,
                createdAt = EgyptTime.nowMillis()
            )
        )
    }
}
