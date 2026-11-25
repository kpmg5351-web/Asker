package com.example

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import com.example.core.ContactPickResult
import com.example.core.ContactPickerHelper
import com.example.core.DeviceLicenseManager
import com.example.core.EgyptTime
import com.example.core.MoneyFormatter
import com.example.core.PdfInvoiceGenerator
import com.example.core.PhoneUtils
import com.example.core.WhatsAppHelper
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupOpResult
import com.example.data.local.AppSettingEntity
import com.example.data.local.CustomerAccountStatus
import com.example.data.local.DailyInvoiceStatuses
import com.example.data.local.HesabatyDatabase
import com.example.data.local.InstallmentStatuses
import com.example.data.local.InvoiceStatuses
import com.example.data.local.PaymentMethods
import com.example.data.local.PurchaseTypes
import com.example.data.local.SaleTypes
import com.example.data.local.SupplierTxTypes
import com.example.data.local.UserEntity
import com.example.data.repository.AccountingRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthResult
import com.example.data.repository.InstallmentDraft
import com.example.data.repository.PurchaseLineDraft
import com.example.data.repository.ReportType
import com.example.data.repository.RepoResult
import com.example.data.repository.SaleLineDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive Robolectric verification suite covering all 18 mandatory business,
 * accounting, security, backup/restore, multi-tenant isolation, and UI/theme scenarios.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HesabatyAccountingRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: HesabatyDatabase
    private lateinit var authRepo: AuthRepository
    private lateinit var accRepo: AccountingRepository
    private lateinit var backupManager: BackupManager

    private lateinit var adminUser: UserEntity
    private lateinit var tenantAId: String
    private lateinit var ownerA: UserEntity

    @Before
    fun setUp() = runTest {
        runCatching {
            val cacheField = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            cacheField.isAccessible = true
            (cacheField.get(null) as? MutableMap<*, *>)?.clear()
        }
        context = ApplicationProvider.getApplicationContext()
        db = HesabatyDatabase.createInMemory(context)
        authRepo = AuthRepository(db, DeviceLicenseManager(context))
        accRepo = AccountingRepository(db)
        backupManager = BackupManager(context, db)

        // Verify secure bootstrap Master Admin login (PBKDF2-HMAC-SHA256 verification)
        val loginRes = authRepo.login("asker1907", "422004")
        assertTrue("Master Admin login should succeed", loginRes is AuthResult.Success)
        adminUser = (loginRes as AuthResult.Success).data.user

        // Create Shop Tenant A via Master Admin with all 7 required fields
        val shopRes = authRepo.createShopAccount(
            actor = adminUser,
            shopName = "معرض النور للتجارة",
            ownerName = "أحمد محمود",
            address = "القاهرة - مدينة نصر",
            phone = "01012345678",
            username = "noor_shop",
            password = "pass1234",
            confirmPassword = "pass1234"
        )
        assertTrue("Shop creation should succeed", shopRes is AuthResult.Success)
        val (tenantA, userA) = (shopRes as AuthResult.Success).data
        tenantAId = tenantA.id
        ownerA = userA
    }

    @After
    fun tearDown() {
        db.close()
    }

    // 1. إضافة مورد وتسجيل دفعة له والتأكد من الرصيد
    @Test
    fun test01_addSupplierAndRecordPaymentUpdatesBalance() = runTest {
        val addRes = accRepo.addSupplier(
            tenantId = tenantAId,
            actor = ownerA,
            name = "شركة الأمل للتوريدات",
            phone = "01099887766",
            whatsappPhone = "01099887766",
            address = "العاشر من رمضان",
            notes = "مورد جملة",
            openingBalancePiasters = 500_000L // 5,000 EGP
        )
        assertTrue(addRes is RepoResult.Success)
        val supplierId = (addRes as RepoResult.Success).data.id

        // Add debt of 2,000 EGP -> balance 7,000 EGP
        val debtRes = accRepo.recordSupplierTransaction(
            tenantId = tenantAId,
            actor = ownerA,
            supplierId = supplierId,
            type = SupplierTxTypes.DEBT_ADD,
            amountPiasters = 200_000L,
            paymentMethod = PaymentMethods.NONE,
            notes = "بضاعة جديدة"
        )
        assertTrue(debtRes is RepoResult.Success)

        // Record partial payment of 2,500 EGP -> balance 4,500 EGP
        val payRes = accRepo.recordSupplierTransaction(
            tenantId = tenantAId,
            actor = ownerA,
            supplierId = supplierId,
            type = SupplierTxTypes.PAYMENT_PARTIAL,
            amountPiasters = 250_000L,
            paymentMethod = PaymentMethods.BANK_TRANSFER,
            notes = "دفعة تحويل بنكي"
        )
        assertTrue(payRes is RepoResult.Success)

        val updated = accRepo.observeSupplier(tenantAId, supplierId).first()!!
        assertEquals(700_000L, updated.totalDuePiasters)
        assertEquals(250_000L, updated.totalPaidPiasters)
        assertEquals(450_000L, updated.currentBalancePiasters)
    }

    // 2 & 3. إضافة عميل وتسجيل عملية بيع + حساب السعر × العدد تلقائيًا (مع الكسور)
    @Test
    fun test02_03_addCustomerAndAutoCalculateSaleLineTotal() = runTest {
        val custRes = accRepo.addCustomer(
            tenantId = tenantAId,
            actor = ownerA,
            name = "محمد علي",
            phone = "01122334455",
            whatsappPhone = "01122334455",
            address = "المعادي",
            notes = "",
            openingBalancePiasters = 0L
        )
        val customerId = (custRes as RepoResult.Success).data.id

        // 200 EGP * 2 = 400 EGP, plus 150.50 EGP * 1.5 = 225.75 EGP => Total 625.75 EGP (62575 piasters)
        val price1 = MoneyFormatter.parseEgpToPiasters("200")!!
        val qty1 = MoneyFormatter.parseQuantityToMilli("2")!!
        val line1 = SaleLineDraft("بضاعة متنوعة", price1, qty1)
        assertEquals(40_000L, line1.lineTotalPiasters)

        val price2 = MoneyFormatter.parseEgpToPiasters("150.50")!!
        val qty2 = MoneyFormatter.parseQuantityToMilli("1.5")!!
        val line2 = SaleLineDraft("", price2, qty2) // optional description
        assertEquals(22_575L, line2.lineTotalPiasters)

        val saleRes = accRepo.createSalesInvoice(
            tenantId = tenantAId,
            actor = ownerA,
            customerId = customerId,
            lines = listOf(line1, line2),
            saleType = SaleTypes.CASH,
            paymentMethod = PaymentMethods.CASH,
            paidNowPiasters = 62_575L,
            dueDateMillis = null,
            installments = emptyList(),
            notes = ""
        )
        assertTrue(saleRes is RepoResult.Success)
        val invoice = (saleRes as RepoResult.Success).data
        assertEquals(62_575L, invoice.totalPiasters)
        assertEquals(0L, invoice.remainingPiasters)
        assertEquals(InvoiceStatuses.PAID, invoice.status)
    }

    // 4 & 5. تسجيل بيع كاش نقدي وبيع كاش بمحفظة إلكترونية وتحديث Dashboard KPIs
    @Test
    fun test04_05_cashAndEWalletSalesUpdateDashboardKpis() = runTest {
        val cid = (accRepo.addCustomer(tenantAId, ownerA, "عميل نقدي", "01000000001", "", "", "", 0L) as RepoResult.Success).data.id

        // Cash sale: 500 EGP
        accRepo.createSalesInvoice(
            tenantId = tenantAId, actor = ownerA, customerId = cid,
            lines = listOf(SaleLineDraft("بيع نقدي", 50_000L, 1000L)),
            saleType = SaleTypes.CASH, paymentMethod = PaymentMethods.CASH,
            paidNowPiasters = 50_000L, dueDateMillis = null, installments = emptyList(), notes = ""
        )
        // E-Wallet sale: 300 EGP
        accRepo.createSalesInvoice(
            tenantId = tenantAId, actor = ownerA, customerId = cid,
            lines = listOf(SaleLineDraft("بيع محفظة", 30_000L, 1000L)),
            saleType = SaleTypes.CASH, paymentMethod = PaymentMethods.EWALLET,
            paidNowPiasters = 30_000L, dueDateMillis = null, installments = emptyList(), notes = ""
        )

        val dashboard = accRepo.observeDashboardSummary(tenantAId).first()
        assertEquals(80_000L, dashboard.todaySalesPiasters)
        assertEquals(50_000L, dashboard.todayCashCollectionsPiasters)
        assertEquals(30_000L, dashboard.todayEWalletCollectionsPiasters)
        assertEquals(80_000L, dashboard.todayNetCashflowPiasters)
        assertEquals(0L, dashboard.totalCustomerReceivablesPiasters)
    }

    // 6. تسجيل بيع آجل مع مقدم وحساب المتبقي
    @Test
    fun test06_creditSaleCalculatesRemainingAndCustomerDebt() = runTest {
        val cid = (accRepo.addCustomer(tenantAId, ownerA, "عميل آجل", "01000000002", "", "", "", 0L) as RepoResult.Success).data.id
        val due = EgyptTime.addDays(EgyptTime.nowMillis(), 15)

        val res = accRepo.createSalesInvoice(
            tenantId = tenantAId, actor = ownerA, customerId = cid,
            lines = listOf(SaleLineDraft("بضاعة آجل", 100_000L, 1000L)), // 1,000 EGP
            saleType = SaleTypes.CREDIT, paymentMethod = PaymentMethods.CASH,
            paidNowPiasters = 25_000L, // 250 EGP down payment
            dueDateMillis = due, installments = emptyList(), notes = ""
        )
        assertTrue(res is RepoResult.Success)
        val inv = (res as RepoResult.Success).data
        assertEquals(75_000L, inv.remainingPiasters)
        assertEquals(InvoiceStatuses.PARTIAL, inv.status)

        val customer = accRepo.observeCustomer(tenantAId, cid).first()!!
        assertEquals(75_000L, customer.currentBalancePiasters)
    }

    // 7 & 8. تقسيم فاتورة على عدة تواريخ + دفع دفعة جزئية وتحديث المتبقي + حماية تجاوز الرصيد
    @Test
    fun test07_08_scheduledSaleAndPartialPaymentWithOverpaymentGuard() = runTest {
        val cid = (accRepo.addCustomer(tenantAId, ownerA, "عميل تقسيط", "01000000003", "", "", "", 0L) as RepoResult.Success).data.id
        val now = EgyptTime.nowMillis()
        val schedule = listOf(
            InstallmentDraft(EgyptTime.addDays(now, 10), 300_000L), // 3,000
            InstallmentDraft(EgyptTime.addDays(now, 20), 300_000L), // 3,000
            InstallmentDraft(EgyptTime.addDays(now, 30), 400_000L)  // 4,000
        )

        val saleRes = accRepo.createSalesInvoice(
            tenantId = tenantAId, actor = ownerA, customerId = cid,
            lines = listOf(SaleLineDraft("بضاعة مجمعة", 1_000_000L, 1000L)), // 10,000 EGP
            saleType = SaleTypes.SCHEDULED, paymentMethod = PaymentMethods.NONE,
            paidNowPiasters = 0L, dueDateMillis = null, installments = schedule, notes = ""
        )
        assertTrue(saleRes is RepoResult.Success)
        val invoice = (saleRes as RepoResult.Success).data

        // Pay partial 3,500 EGP (pays installment #1 fully + 500 EGP of installment #2)
        val payRes = accRepo.recordCustomerPayment(
            tenantId = tenantAId, actor = ownerA, customerId = cid,
            amountPiasters = 350_000L, paymentMethod = PaymentMethods.CASH,
            invoiceId = invoice.id
        )
        assertTrue(payRes is RepoResult.Success)

        val updatedInv = accRepo.observeInvoiceById(tenantAId, invoice.id).first()!!
        assertEquals(650_000L, updatedInv.remainingPiasters)
        val insts = accRepo.observeInvoiceInstallments(tenantAId, invoice.id).first()
        assertEquals(3, insts.size)
        assertEquals(0L, insts[0].remainingPiasters)
        assertEquals(250_000L, insts[1].remainingPiasters)
        assertEquals(400_000L, insts[2].remainingPiasters)

        // Overpayment without explicit allowExcessAsCredit MUST be rejected
        val overpayBlocked = accRepo.recordCustomerPayment(
            tenantId = tenantAId, actor = ownerA, customerId = cid,
            amountPiasters = 700_000L, paymentMethod = PaymentMethods.CASH,
            invoiceId = invoice.id, allowExcessAsCredit = false
        )
        assertTrue("Overpayment should be blocked unless explicitly allowed", overpayBlocked is RepoResult.Error)
    }

    // 9, 10 & 11. فاتورة سحب يومي + عدة سحوبات بأوقات مختلفة + إغلاق ومنع التكرار
    @Test
    fun test09_10_11_dailyWithdrawalsTimelineAndSingleCloseGuard() = runTest {
        val cid = (accRepo.addCustomer(tenantAId, ownerA, "عميل سحب يومي", "01000000004", "", "", "", 0L) as RepoResult.Success).data.id
        val daily = (accRepo.createDailyWithdrawalInvoice(tenantAId, ownerA, cid) as RepoResult.Success).data
        assertEquals(DailyInvoiceStatuses.OPEN, daily.status)

        val t0 = EgyptTime.nowMillis()
        // Example from spec: 200*2=400, 150*3=450, 100*1=100, 300*2=600 => Total 1,550 EGP (155,000 piasters)
        accRepo.addDailyWithdrawalItem(tenantAId, ownerA, daily.id, "بضاعة", 20_000L, 2000L, "", t0)
        accRepo.addDailyWithdrawalItem(tenantAId, ownerA, daily.id, "بضاعة", 15_000L, 3000L, "", t0 + 120_000L)
        accRepo.addDailyWithdrawalItem(tenantAId, ownerA, daily.id, "بضاعة", 10_000L, 1000L, "", t0 + 240_000L)
        accRepo.addDailyWithdrawalItem(tenantAId, ownerA, daily.id, "بضاعة", 30_000L, 2000L, "", t0 + 360_000L)

        val items = accRepo.observeDailyItems(tenantAId, daily.id).first()
        assertEquals(4, items.size)

        // Close invoice once
        val closeRes = accRepo.closeDailyWithdrawalInvoice(tenantAId, ownerA, daily.id)
        assertTrue(closeRes is RepoResult.Success)
        val closed = (closeRes as RepoResult.Success).data
        assertEquals(155_000L, closed.totalPiasters)
        assertEquals(DailyInvoiceStatuses.CLOSED, closed.status)

        val customerAfterClose = accRepo.observeCustomer(tenantAId, cid).first()!!
        assertEquals(155_000L, customerAfterClose.currentBalancePiasters)

        // Second close MUST be blocked
        val duplicateClose = accRepo.closeDailyWithdrawalInvoice(tenantAId, ownerA, daily.id)
        assertTrue("Duplicate closing must be rejected", duplicateClose is RepoResult.Error)

        // Reopen with reason, add 100 EGP more, re-close -> customer balance must be 1650 EGP (NOT 1550+1650!)
        val reopenRes = accRepo.reopenDailyWithdrawalInvoice(tenantAId, ownerA, daily.id, "نسيان سحبة إضافية")
        assertTrue(reopenRes is RepoResult.Success)
        accRepo.addDailyWithdrawalItem(tenantAId, ownerA, daily.id, "إضافي", 10_000L, 1000L, "", t0 + 480_000L)
        val recloseRes = accRepo.closeDailyWithdrawalInvoice(tenantAId, ownerA, daily.id)
        assertTrue(recloseRes is RepoResult.Success)

        val customerAfterReclose = accRepo.observeCustomer(tenantAId, cid).first()!!
        assertEquals(165_000L, customerAfterReclose.currentBalancePiasters)
    }

    // 12. إرسال فاتورة عبر WhatsApp + تنسيق الرسالة والرقم وتوليد PDF
    @Test
    fun test12_whatsAppFormattingAndPdfGeneration() {
        runCatching {
            val cacheField = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            cacheField.isAccessible = true
            (cacheField.get(null) as? MutableMap<*, *>)?.clear()
        }
        val intl = PhoneUtils.toWhatsAppInternational("01099762204", "20")
        assertEquals("201099762204", intl)

        val msg = WhatsAppHelper.buildCustomerInvoiceMessage(
            customerName = "محمود حسن",
            shopName = "معرض النور",
            invoiceNumber = "INV-00001",
            dateFormatted = "2026/11/19",
            totalPiasters = 155_000L,
            paidPiasters = 55_000L,
            remainingPiasters = 100_000L
        )
        assertTrue(msg.contains("محمود حسن"))
        assertTrue(msg.contains("INV-00001"))
        assertTrue(msg.contains("1,550"))
        assertTrue(msg.contains("1,000"))

        val (pdfFile, uri) = PdfInvoiceGenerator.generateInvoicePdf(
            context,
            PdfInvoiceGenerator.InvoicePrintModel(
                shopName = "معرض النور",
                shopAddress = "القاهرة",
                shopPhone = "01012345678",
                customerName = "محمود حسن",
                invoiceNumber = "INV-00001",
                dateFormatted = "2026/11/19",
                invoiceTypeLabel = "بيع آجل",
                paymentMethodLabel = "نقدي",
                lines = listOf(PdfInvoiceGenerator.InvoiceLineRow("بضاعة", 20_000L, 2000L, 40_000L)),
                totalPiasters = 40_000L,
                paidPiasters = 10_000L,
                remainingPiasters = 30_000L
            )
        )
        assertTrue("Generated PDF file should exist and be non-empty", pdfFile.exists() && pdfFile.length() > 100L)
        assertNotNull(uri)
    }

    // 13, 14 & 15. نسخ احتياطي مشفر + استعادة دون تكرار + العمل Offline-First
    @Test
    fun test13_14_15_encryptedBackupRestoreDeduplicatedAndOfflineFirst() = runTest {
        val cid = (accRepo.addCustomer(tenantAId, ownerA, "عميل النسخ", "01000000005", "", "", "", 0L) as RepoResult.Success).data.id
        accRepo.createSalesInvoice(
            tenantId = tenantAId, actor = ownerA, customerId = cid,
            lines = listOf(SaleLineDraft("بضاعة", 40_000L, 1000L)),
            saleType = SaleTypes.CREDIT, paymentMethod = PaymentMethods.NONE,
            paidNowPiasters = 0L, dueDateMillis = null, installments = emptyList(), notes = ""
        )

        // Create encrypted backup offline using passphrase
        val backupRes = backupManager.createLocalBackup(
            tenantId = tenantAId,
            actorUserId = ownerA.id,
            actorUsername = ownerA.username,
            passphrase = "MyStrongSecret!2026"
        )
        assertTrue("Local encrypted backup must succeed offline", backupRes is BackupOpResult.Success)
        val localFiles = backupManager.listLocalBackupFiles(tenantAId)
        assertTrue(localFiles.isNotEmpty())
        val encryptedBytes = localFiles.first().readBytes()

        // Ensure ciphertext does not leak plaintext customer name
        assertFalse(encryptedBytes.toString(Charsets.UTF_8).contains("عميل النسخ"))

        // Restore twice into same DB and verify ZERO duplicate customers/invoices/transactions
        val restore1 = backupManager.restoreFromEncryptedBytes(encryptedBytes, tenantAId, ownerA.id, ownerA.username, "MyStrongSecret!2026")
        assertTrue(restore1 is BackupOpResult.Success)
        val restore2 = backupManager.restoreFromEncryptedBytes(encryptedBytes, tenantAId, ownerA.id, ownerA.username, "MyStrongSecret!2026")
        assertTrue(restore2 is BackupOpResult.Success)

        val customersAfter = accRepo.observeCustomers(tenantAId).first()
        val invoicesAfter = accRepo.observeInvoices(tenantAId).first()
        val txsAfter = accRepo.observeCustomerTransactions(tenantAId, cid).first()
        assertEquals(1, customersAfter.size)
        assertEquals(1, invoicesAfter.size)
        assertEquals(1, txsAfter.size)
        assertEquals(40_000L, customersAfter.first().currentBalancePiasters)
    }

    // 16. اختبار عزل بيانات محلين مختلفين (Multi-Tenant Isolation)
    @Test
    fun test16_multiTenantIsolationBetweenTwoShops() = runTest {
        val shopBRes = authRepo.createShopAccount(
            actor = adminUser,
            shopName = "محل البركة",
            ownerName = "سعيد حسن",
            address = "الإسكندرية",
            phone = "01234567890",
            username = "baraka_shop",
            password = "pass5678",
            confirmPassword = "pass5678"
        )
        assertTrue(shopBRes is AuthResult.Success)
        val (tenantB, ownerB) = (shopBRes as AuthResult.Success).data

        // Shop A adds customer A; Shop B adds customer B
        accRepo.addCustomer(tenantAId, ownerA, "عميل محل أ", "01011111111", "", "", "", 10_000L)
        accRepo.addCustomer(tenantB.id, ownerB, "عميل محل ب", "01222222222", "", "", "", 20_000L)

        val listA = accRepo.observeCustomers(tenantAId).first()
        val listB = accRepo.observeCustomers(tenantB.id).first()
        assertEquals(1, listA.size)
        assertEquals("عميل محل أ", listA.first().name)
        assertEquals(1, listB.size)
        assertEquals("عميل محل ب", listB.first().name)

        // Shop Owner cannot perform Master Admin shop creation
        val unauthorizedCreate = authRepo.createShopAccount(
            actor = ownerA,
            shopName = "محاولة غير مصرحة",
            ownerName = "س",
            address = "ع",
            phone = "01012345678",
            username = "hack_shop",
            password = "1234",
            confirmPassword = "1234"
        )
        assertTrue("Shop owner must be blocked from Master Admin operations", unauthorizedCreate is AuthResult.Error)

        // Cross-tenant backup restore MUST be rejected
        val (_, backupBytesA) = backupManager.buildEncryptedBackupBytes(tenantAId, "passA")
        val crossRestore = backupManager.restoreFromEncryptedBytes(backupBytesA, tenantB.id, ownerB.id, ownerB.username, "passA")
        assertTrue("Restoring Tenant A backup into Tenant B must be blocked", crossRestore is BackupOpResult.Error)
    }

    // 17. اختبار الوضع الفاتح والغامق (Light default & Dark toggle persisted)
    @Test
    fun test17_lightAndDarkModeSettingsPersistence() = runTest {
        val initial = accRepo.observeAppSettings(tenantAId).first()
        assertNotNull(initial)
        assertFalse("Light mode must be the default", initial!!.darkMode)

        accRepo.saveAppSettings(tenantAId, initial.copy(darkMode = true))
        val dark = accRepo.observeAppSettings(tenantAId).first()!!
        assertTrue("Dark mode toggle should persist", dark.darkMode)

        accRepo.saveAppSettings(tenantAId, dark.copy(darkMode = false))
        val backToLight = accRepo.observeAppSettings(tenantAId).first()!!
        assertFalse("Switching back to light mode should persist", backToLight.darkMode)
    }

    // 18. اختبار عدم تكرار العمليات عند الضغط المتكرر (Idempotency)
    @Test
    fun test18_idempotencyPreventsDuplicateOperationsOnRapidTaps() = runTest {
        val cid = (accRepo.addCustomer(tenantAId, ownerA, "عميل اختبار التكرار", "01000000009", "", "", "", 0L) as RepoResult.Success).data.id
        val sameKey = "tap_once_key_123"

        // Simulate rapid double-tap on Save Sale with same draft idempotency key
        val r1 = accRepo.createSalesInvoice(
            tenantId = tenantAId, actor = ownerA, customerId = cid,
            lines = listOf(SaleLineDraft("بضاعة", 50_000L, 1000L)),
            saleType = SaleTypes.CREDIT, paymentMethod = PaymentMethods.NONE,
            paidNowPiasters = 0L, dueDateMillis = null, installments = emptyList(), notes = "",
            idempotencyKey = sameKey
        )
        val r2 = accRepo.createSalesInvoice(
            tenantId = tenantAId, actor = ownerA, customerId = cid,
            lines = listOf(SaleLineDraft("بضاعة", 50_000L, 1000L)),
            saleType = SaleTypes.CREDIT, paymentMethod = PaymentMethods.NONE,
            paidNowPiasters = 0L, dueDateMillis = null, installments = emptyList(), notes = "",
            idempotencyKey = sameKey
        )
        assertTrue(r1 is RepoResult.Success && r2 is RepoResult.Success)
        assertEquals((r1 as RepoResult.Success).data.id, (r2 as RepoResult.Success).data.id)

        val invoices = accRepo.observeCustomerInvoices(tenantAId, cid).first()
        assertEquals(1, invoices.size)
        val customer = accRepo.observeCustomer(tenantAId, cid).first()!!
        assertEquals(50_000L, customer.currentBalancePiasters)

        // Same for rapid double-tap on Customer Payment
        val payKey = "pay_once_key_456"
        accRepo.recordCustomerPayment(tenantAId, ownerA, cid, 20_000L, PaymentMethods.CASH, idempotencyKey = payKey)
        accRepo.recordCustomerPayment(tenantAId, ownerA, cid, 20_000L, PaymentMethods.CASH, idempotencyKey = payKey)
        val payments = accRepo.observeCustomerPayments(tenantAId, cid).first()
        assertEquals(1, payments.size)
        assertEquals(30_000L, accRepo.observeCustomer(tenantAId, cid).first()!!.currentBalancePiasters)
    }

    // 19. اختبار كتالوج الأصناف الاختياري + الخصم التلقائي من المخزون + تنبيه النفاد + النسخ الاحتياطي
    @Test
    fun test19_optionalProductsCatalogStockTrackingAlertsAndBackupRestore() = runTest {
        val addRes = accRepo.addProduct(
            tenantId = tenantAId,
            actor = ownerA,
            name = "كرتونة زيت عباد الشمس",
            category = "مواد غذائية",
            skuOrBarcode = "OIL-001",
            unitLabel = "كرتونة",
            salePricePiasters = 85_000L, // 850 EGP
            purchasePricePiasters = 72_000L, // 720 EGP
            stockQuantityMilli = 10_000L, // 10 cartons
            trackStock = true,
            minStockAlertMilli = 3_000L, // alert at <= 3 cartons
            notes = "صنف سريع الحركة"
        )
        assertTrue(addRes is RepoResult.Success)
        val prod = (addRes as RepoResult.Success).data
        assertEquals(10_000L, prod.stockQuantityMilli)

        val cid = (accRepo.addCustomer(tenantAId, ownerA, "عميل أصناف", "01011223344", "", "", "", 0L) as RepoResult.Success).data.id

        // Sell 5 cartons using catalog product -> stock drops from 10 to 5
        val saleRes = accRepo.createSalesInvoice(
            tenantId = tenantAId,
            actor = ownerA,
            customerId = cid,
            lines = listOf(SaleLineDraft(prod.name, prod.salePricePiasters, 5_000L, productId = prod.id)),
            saleType = SaleTypes.CASH,
            paymentMethod = PaymentMethods.CASH,
            paidNowPiasters = 425_000L,
            dueDateMillis = null,
            installments = emptyList(),
            notes = ""
        )
        assertTrue(saleRes is RepoResult.Success)
        assertEquals(5_000L, db.productDao().getProductById(tenantAId, prod.id)!!.stockQuantityMilli)

        // Daily withdrawal of 3 more cartons -> stock drops from 5 to 2 (<= 3 triggers low stock alert!)
        val daily = (accRepo.createDailyWithdrawalInvoice(tenantAId, ownerA, cid) as RepoResult.Success).data
        val dailyItemRes = accRepo.addDailyWithdrawalItem(
            tenantId = tenantAId,
            actor = ownerA,
            dailyInvoiceId = daily.id,
            description = prod.name,
            unitPricePiasters = prod.salePricePiasters,
            quantityMilli = 3_000L,
            notes = "",
            productId = prod.id
        )
        assertTrue(dailyItemRes is RepoResult.Success)
        assertEquals(2_000L, db.productDao().getProductById(tenantAId, prod.id)!!.stockQuantityMilli)

        // Low-stock alert must appear automatically
        val alerts = accRepo.observeAlerts(tenantAId).first()
        assertTrue("Low stock alert must trigger when stock <= minStockAlert", alerts.any { it.id == "lowstock_${prod.id}" })

        // Manual stock restock +8 cartons -> stock becomes 10 again
        val restockRes = accRepo.adjustProductStock(tenantAId, ownerA, prod.id, 8_000L, "توريد جديد")
        assertTrue(restockRes is RepoResult.Success)
        assertEquals(10_000L, db.productDao().getProductById(tenantAId, prod.id)!!.stockQuantityMilli)

        // Encrypted backup & restore must preserve products
        val (_, bytes) = backupManager.buildEncryptedBackupBytes(tenantAId, "pass123")
        db.productDao().deleteProduct(tenantAId, prod.id)
        assertEquals(0, db.productDao().getProducts(tenantAId).size)

        val restoreRes = backupManager.restoreFromEncryptedBytes(bytes, tenantAId, ownerA.id, ownerA.username, "pass123")
        assertTrue(restoreRes is BackupOpResult.Success)
        val restoredProducts = db.productDao().getProducts(tenantAId)
        assertEquals(1, restoredProducts.size)
        assertEquals("كرتونة زيت عباد الشمس", restoredProducts.first().name)
        assertEquals(10_000L, restoredProducts.first().stockQuantityMilli)
    }

    // 20. اختبار نظام تنبيهات تجاوز حد المديونية (حد خاص للعميل + حد عام للمحل + اختفاء التنبيه بعد التحصيل)
    @Test
    fun test20_creditLimitAlertsPerCustomerAndDefaultShopLimit() = runTest {
        // Customer A with custom credit limit = 1,000 EGP (100_000 piasters)
        val cidA = (accRepo.addCustomer(
            tenantId = tenantAId,
            actor = ownerA,
            name = "عميل حد خاص",
            phone = "01022334455",
            whatsappPhone = "",
            address = "",
            notes = "",
            openingBalancePiasters = 0L,
            creditLimitPiasters = 100_000L
        ) as RepoResult.Success).data.id

        // Customer B with NO custom limit (0L)
        val cidB = (accRepo.addCustomer(
            tenantId = tenantAId,
            actor = ownerA,
            name = "عميل حد عام",
            phone = "01066778899",
            whatsappPhone = "",
            address = "",
            notes = "",
            openingBalancePiasters = 70_000L, // 700 EGP debt
            creditLimitPiasters = 0L
        ) as RepoResult.Success).data.id

        // Initially no breach
        assertTrue(accRepo.observeCreditLimitBreaches(tenantAId).first().isEmpty())

        // Credit sale of 1,500 EGP to Customer A -> exceeds 1,000 EGP limit by 500 EGP (50_000 piasters)
        accRepo.createSalesInvoice(
            tenantId = tenantAId,
            actor = ownerA,
            customerId = cidA,
            lines = listOf(SaleLineDraft("بضاعة آجل", 150_000L, 1_000L)),
            saleType = SaleTypes.CREDIT,
            paymentMethod = PaymentMethods.CASH,
            paidNowPiasters = 0L,
            dueDateMillis = null,
            installments = emptyList(),
            notes = ""
        )

        val breachesAfterSale = accRepo.observeCreditLimitBreaches(tenantAId).first()
        assertEquals(1, breachesAfterSale.size)
        assertEquals(cidA, breachesAfterSale.first().customer.id)
        assertEquals(100_000L, breachesAfterSale.first().effectiveLimitPiasters)
        assertEquals(50_000L, breachesAfterSale.first().excessPiasters)
        assertTrue(breachesAfterSale.first().isCustomLimit)

        val alertsAfterSale = accRepo.observeAlerts(tenantAId).first()
        assertTrue(
            "Danger alert must be emitted for customer exceeding credit limit",
            alertsAfterSale.any { it.id == "credit_limit_$cidA" && it.severity == com.example.data.repository.AlertSeverity.DANGER }
        )

        // Collect 600 EGP from Customer A -> balance becomes 900 EGP (below 1,000 EGP custom limit) -> breach clears
        accRepo.recordCustomerPayment(tenantAId, ownerA, cidA, 60_000L, PaymentMethods.CASH)
        assertTrue(accRepo.observeCreditLimitBreaches(tenantAId).first().isEmpty())

        // Now set shop-wide default customer credit limit = 500 EGP (50_000 piasters)
        val currentSettings = db.systemDao().getAppSettings(tenantAId)!!
        db.systemDao().upsertAppSettings(currentSettings.copy(defaultCustomerCreditLimitPiasters = 50_000L))

        // Customer A (900 EGP balance) has custom limit 1,000 EGP -> NOT breached
        // Customer B (700 EGP balance) has 0 custom limit -> falls back to shop default 500 EGP -> BREACHED by 200 EGP!
        val shopDefaultBreaches = accRepo.observeCreditLimitBreaches(tenantAId).first()
        assertEquals(1, shopDefaultBreaches.size)
        assertEquals(cidB, shopDefaultBreaches.first().customer.id)
        assertEquals(50_000L, shopDefaultBreaches.first().effectiveLimitPiasters)
        assertEquals(20_000L, shopDefaultBreaches.first().excessPiasters)
        assertFalse(shopDefaultBreaches.first().isCustomLimit)

        // Raising Customer B's custom limit to 1,000 EGP clears the breach
        val updateRes = accRepo.updateCustomerCreditLimit(tenantAId, ownerA, cidB, 100_000L)
        assertTrue(updateRes is RepoResult.Success)
        assertTrue(accRepo.observeCreditLimitBreaches(tenantAId).first().isEmpty())
    }

    // 21. اختبار فاتورة الشراء الكاملة (تحديث حساب المورد + تزويد مخزون الأصناف + Idempotency + تقرير المشتريات)
    @Test
    fun test21_purchaseInvoiceRestocksInventoryAndPostsSupplierLedger() = runTest {
        val supRes = accRepo.addSupplier(
            tenantId = tenantAId,
            actor = ownerA,
            name = "مؤسسة الفجر للمواد الغذائية",
            phone = "01055443322",
            whatsappPhone = "01055443322",
            address = "العبور - سوق الجملة",
            notes = "",
            openingBalancePiasters = 0L
        )
        assertTrue(supRes is RepoResult.Success)
        val sid = (supRes as RepoResult.Success).data.id

        val prodRes = accRepo.addProduct(
            tenantId = tenantAId,
            actor = ownerA,
            name = "كرتونة أرز مصري 20 كجم",
            category = "مواد غذائية",
            skuOrBarcode = "RICE-20",
            unitLabel = "كرتونة",
            salePricePiasters = 68_000L,
            purchasePricePiasters = 60_000L,
            stockQuantityMilli = 5_000L, // 5 cartons initially
            trackStock = true,
            minStockAlertMilli = 2_000L,
            notes = ""
        )
        assertTrue(prodRes is RepoResult.Success)
        val prod = (prodRes as RepoResult.Success).data

        val idemKey = "purchase-idem-test-001"
        val purRes = accRepo.createPurchaseInvoice(
            tenantId = tenantAId,
            actor = ownerA,
            supplierId = sid,
            lines = listOf(
                PurchaseLineDraft(
                    description = "كرتونة أرز مصري 20 كجم (كرتونة)",
                    unitPricePiasters = 60_000L,
                    quantityMilli = 3_000L, // 3 cartons = 180_000 piasters
                    productId = prod.id
                ),
                PurchaseLineDraft(
                    description = "مصاريف نقل وتحميل",
                    unitPricePiasters = 10_000L,
                    quantityMilli = 2_000L, // 2 units = 20_000 piasters
                    productId = null
                )
            ),
            purchaseType = PurchaseTypes.CREDIT,
            paymentMethod = PaymentMethods.CASH,
            paidNowPiasters = 80_000L, // 800 EGP paid now, 1,200 EGP remaining
            dueDateMillis = EgyptTime.nowMillis() + 7L * 24 * 60 * 60 * 1000,
            notes = "توريد صباحي شامل التفريغ",
            idempotencyKey = idemKey
        )
        assertTrue(purRes is RepoResult.Success)
        val purchase = (purRes as RepoResult.Success).data
        assertEquals("PUR-00001", purchase.invoiceNumber)
        assertEquals(200_000L, purchase.totalPiasters)
        assertEquals(80_000L, purchase.paidPiasters)
        assertEquals(120_000L, purchase.remainingPiasters)
        assertEquals(InvoiceStatuses.PARTIAL, purchase.status)

        // Tracked product stock must increase from 5 to 8 cartons
        val updatedProd = db.productDao().getProductById(tenantAId, prod.id)!!
        assertEquals(8_000L, updatedProd.stockQuantityMilli)

        // Supplier balance must reflect 2,000 EGP due, 800 EGP paid, 1,200 EGP remaining
        val updatedSupplier = accRepo.observeSupplier(tenantAId, sid).first()!!
        assertEquals(200_000L, updatedSupplier.totalDuePiasters)
        assertEquals(80_000L, updatedSupplier.totalPaidPiasters)
        assertEquals(120_000L, updatedSupplier.currentBalancePiasters)

        // Idempotency retry must not duplicate purchase invoice or stock
        val dupRes = accRepo.createPurchaseInvoice(
            tenantId = tenantAId,
            actor = ownerA,
            supplierId = sid,
            lines = listOf(PurchaseLineDraft("بند مكرر", 60_000L, 3_000L, prod.id)),
            purchaseType = PurchaseTypes.CREDIT,
            paymentMethod = PaymentMethods.CASH,
            paidNowPiasters = 80_000L,
            dueDateMillis = null,
            notes = "",
            idempotencyKey = idemKey
        )
        assertTrue(dupRes is RepoResult.Success)
        assertEquals(purchase.id, (dupRes as RepoResult.Success).data.id)
        assertEquals(8_000L, db.productDao().getProductById(tenantAId, prod.id)!!.stockQuantityMilli)

        // Purchase report must reflect the invoice and total quantity (5)
        val report = accRepo.generateReport(
            tenantId = tenantAId,
            type = ReportType.PURCHASES,
            fromMillis = EgyptTime.nowMillis() - 86_400_000L,
            toMillis = EgyptTime.nowMillis() + 86_400_000L,
            query = ""
        )
        assertEquals(1, report.rows.size)
        assertTrue(report.rows.first().contains("PUR-00001"))
        assertTrue(report.rows.first().contains("5"))
    }

    // 22. اختبار ظهور كامل التفاصيل والعدد/الكمية بدون اختصار في رسالة الواتساب وصورة الفاتورة
    @Test
    fun test22_invoiceWhatsAppMessageAndPngRenderIncludeFullQuantitiesAndDetails() {
        val manyLines = (1..16).map { idx ->
            PdfInvoiceGenerator.InvoiceLineRow(
                description = "صنف رقم $idx بوصف تفصيلي طويل للتأكد من عدم اختصار أي نص",
                unitPricePiasters = 15_000L,
                quantityMilli = 2_500L, // 2.5 units each -> 40 total quantity
                lineTotalPiasters = 37_500L
            )
        }
        val waMsg = WhatsAppHelper.buildCustomerInvoiceMessage(
            customerName = "محلات السلام",
            shopName = "معرض النور للتجارة",
            invoiceNumber = "INV-00099",
            dateFormatted = "2026-11-21 12:00",
            totalPiasters = 600_000L,
            paidPiasters = 200_000L,
            remainingPiasters = 400_000L,
            invoiceTypeLabel = "فاتورة آجل",
            lines = manyLines,
            dueDateFormatted = "2026-12-01",
            notes = "ملاحظة التسليم الكاملة في المخزن الشرقي"
        )
        // All 16 lines (past old 12-line limit) and quantities must be present
        assertTrue(waMsg.contains("صنف رقم 16 بوصف تفصيلي طويل"))
        assertTrue(waMsg.contains("عدد البنود: *16*"))
        assertTrue(waMsg.contains("إجمالي العدد/الكمية: *40*"))
        assertTrue(waMsg.contains("2.5"))
        assertTrue(waMsg.contains("2026-12-01"))
        assertTrue(waMsg.contains("ملاحظة التسليم الكاملة في المخزن الشرقي"))

        val purMsg = WhatsAppHelper.buildPurchaseInvoiceMessage(
            supplierName = "شركة الأمل",
            shopName = "معرض النور للتجارة",
            invoiceNumber = "PUR-00007",
            dateFormatted = "2026-11-21 12:30",
            totalPiasters = 600_000L,
            paidPiasters = 600_000L,
            remainingPiasters = 0L,
            lines = manyLines,
            dueDateFormatted = "2026-12-10",
            notes = "استلام كامل بفحص الجودة"
        )
        assertTrue(purMsg.contains("صنف رقم 16 بوصف تفصيلي طويل"))
        assertTrue(purMsg.contains("إجمالي العدد/الكمية: *40*"))
        assertTrue(purMsg.contains("استلام كامل بفحص الجودة"))

        val (pngFile, _) = PdfInvoiceGenerator.generateInvoicePng(
            context = context,
            model = PdfInvoiceGenerator.InvoicePrintModel(
                shopName = "معرض النور للتجارة",
                shopAddress = "القاهرة - مدينة نصر",
                shopPhone = "01012345678",
                customerName = "محلات السلام",
                invoiceNumber = "INV-00099",
                dateFormatted = "2026-11-21 12:00",
                invoiceTypeLabel = "فاتورة آجل",
                paymentMethodLabel = "نقدي",
                lines = manyLines,
                totalPiasters = 600_000L,
                paidPiasters = 200_000L,
                remainingPiasters = 400_000L,
                notes = "ملاحظة التسليم الكاملة في المخزن الشرقي مع مراجعة الأصناف بالكامل",
                signatureName = "أحمد محمود",
                recipientPhone = "01099887766",
                dueDateFormatted = "2026-12-01"
            )
        )
        assertTrue(pngFile.exists())
        assertTrue(pngFile.length() > 1000L)
    }

    @Test
    fun test23_egyptTimeUses12HourArabicFormat() {
        assertEquals("12:00 ص", EgyptTime.formatHourMinute12(0, 0))
        assertEquals("09:05 ص", EgyptTime.formatHourMinute12(9, 5))
        assertEquals("12:00 م", EgyptTime.formatHourMinute12(12, 0))
        assertEquals("03:45 م", EgyptTime.formatHourMinute12(15, 45))
        assertEquals("11:30 م", EgyptTime.formatHourMinute12(23, 30))

        val cal = java.util.Calendar.getInstance(EgyptTime.timeZone).apply {
            set(2026, java.util.Calendar.NOVEMBER, 21, 21, 15, 42)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val millis = cal.timeInMillis
        assertEquals("09:15 م", EgyptTime.formatTimeHm(millis))
        assertEquals("09:15:42 م", EgyptTime.formatTimeHms12(millis))
        assertTrue(EgyptTime.formatDateTimeSeconds(millis).endsWith("09:15:42 م"))
    }

    @Test
    fun test24_contactPhoneCleaningAndContactUriExtraction() {
        assertEquals("01099762204", PhoneUtils.cleanPickedContactPhone("+20 109-976-2204"))
        assertEquals("01099762204", PhoneUtils.cleanPickedContactPhone("00201099762204"))
        assertEquals("01099762204", PhoneUtils.cleanPickedContactPhone("+2001099762204"))
        assertEquals("01099762204", PhoneUtils.cleanPickedContactPhone("\u200E(٠١٠) ٩٩٧٦-٢٢٠٤\u200F"))
        assertEquals("+966501234567", PhoneUtils.cleanPickedContactPhone("+966 50 123 4567"))

        val authority = "com.example.test.contacts"
        org.robolectric.Shadows.shadowOf(context.contentResolver)
        val provider = object : ContentProvider() {
            override fun onCreate(): Boolean = true
            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?
            ): Cursor {
                return MatrixCursor(
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                ).apply {
                    addRow(arrayOf("محمد عبد الرحمن", "+20 101 234 5678"))
                }
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        }
        org.robolectric.Robolectric.buildContentProvider(provider::class.java)
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(authority, provider)

        val fakeContactUri = Uri.parse("content://$authority/data/1")
        val extracted = ContactPickerHelper.extractContactFromUri(context.contentResolver, fakeContactUri)
        assertTrue(extracted is ContactPickResult.Success)
        val contact = (extracted as ContactPickResult.Success).contact
        assertEquals("محمد عبد الرحمن", contact.name)
        assertEquals("01012345678", contact.phone)
    }

    @Test
    fun test25_zeroCloudAutoBackupAndSharePreparation() = runTest {
        assertTrue(backupManager.isDailyDriveReminderEnabled(tenantAId))
        backupManager.setDailyDriveReminderEnabled(tenantAId, false)
        assertFalse(backupManager.isDailyDriveReminderEnabled(tenantAId))

        backupManager.setAutoBackupFolder(tenantAId, "content://com.android.externalstorage/tree/primary%3ABackups", "Backups")
        assertEquals("Backups", backupManager.getAutoBackupFolderLabel(tenantAId))
        backupManager.setAutoBackupFolder(tenantAId, null, null)
        assertEquals(null, backupManager.getAutoBackupFolderLabel(tenantAId))

        val scheduledRes = backupManager.runScheduledBackup(tenantAId)
        assertTrue(scheduledRes is BackupOpResult.Success)

        val shareFileRes = backupManager.createBackupFileForShare(
            tenantId = tenantAId,
            actorUserId = ownerA.id,
            actorUsername = ownerA.username
        )
        assertTrue(shareFileRes is BackupOpResult.Success)
        val file = (shareFileRes as BackupOpResult.Success).data
        assertTrue(file.exists())
        val shareIntent = backupManager.createShareBackupIntent(file)
        assertNotNull(shareIntent)
    }

    @Test
    fun test26_dashboardSummaryCalculatesTotalIncomingOutgoingAndNetBalance() = runTest {
        val cid = (accRepo.addCustomer(tenantAId, ownerA, "عميل الرصيد", "01012345670", "", "", "", 0L) as RepoResult.Success).data.id
        val sid = (accRepo.addSupplier(tenantAId, ownerA, "مورد الرصيد", "01098765432", "", "", "", 0L) as RepoResult.Success).data.id

        val now = EgyptTime.nowMillis()
        val twoDaysAgo = now - 2L * 24 * 60 * 60 * 1000L

        // Yesterday/older cash sale: 1,000 EGP (100_000 piasters)
        accRepo.createSalesInvoice(
            tenantId = tenantAId,
            actor = ownerA,
            customerId = cid,
            lines = listOf(SaleLineDraft("بضاعة سابقة", 100_000L, 1_000L)),
            saleType = SaleTypes.CASH,
            paymentMethod = PaymentMethods.CASH,
            paidNowPiasters = 100_000L,
            dueDateMillis = null,
            installments = emptyList(),
            notes = "",
            occurredAtMillis = twoDaysAgo
        )

        // Today's credit sale: 800 EGP total, 300 EGP paid by bank transfer (500 EGP customer receivable)
        accRepo.createSalesInvoice(
            tenantId = tenantAId,
            actor = ownerA,
            customerId = cid,
            lines = listOf(SaleLineDraft("بضاعة اليوم", 80_000L, 1_000L)),
            saleType = SaleTypes.CREDIT,
            paymentMethod = PaymentMethods.BANK_TRANSFER,
            paidNowPiasters = 30_000L,
            dueDateMillis = null,
            installments = emptyList(),
            notes = "",
            occurredAtMillis = now
        )

        // Older purchase invoice: 600 EGP total, 400 EGP paid (200 EGP supplier payable)
        accRepo.createPurchaseInvoice(
            tenantId = tenantAId,
            actor = ownerA,
            supplierId = sid,
            lines = listOf(PurchaseLineDraft("شراء قديم", 60_000L, 1_000L)),
            purchaseType = PurchaseTypes.CREDIT,
            paymentMethod = PaymentMethods.CASH,
            paidNowPiasters = 40_000L,
            dueDateMillis = null,
            notes = "",
            occurredAtMillis = twoDaysAgo
        )

        // Today's supplier payment: 150 EGP (15_000 piasters) -> supplier payable drops to 50 EGP (5_000 piasters)
        accRepo.recordSupplierTransaction(
            tenantId = tenantAId,
            actor = ownerA,
            supplierId = sid,
            type = SupplierTxTypes.PAYMENT_PARTIAL,
            amountPiasters = 15_000L,
            paymentMethod = PaymentMethods.CASH,
            notes = "دفعة اليوم",
            occurredAtMillis = now
        )

        val summary = accRepo.observeDashboardSummary(tenantAId).first()
        // Total incoming = 100_000 + 30_000 = 130_000
        assertEquals(130_000L, summary.totalIncomingPiasters)
        // Total outgoing = 40_000 + 15_000 = 55_000
        assertEquals(55_000L, summary.totalOutgoingPiasters)
        // Total net balance = 130_000 - 55_000 = 75_000
        assertEquals(75_000L, summary.totalNetBalancePiasters)

        // Today incoming = 30_000, today outgoing = 15_000, today net = 15_000
        assertEquals(30_000L, summary.todayIncomingPiasters)
        assertEquals(15_000L, summary.todaySupplierPaymentsPiasters)
        assertEquals(15_000L, summary.todayNetCashflowPiasters)

        // Receivables & payables
        assertEquals(50_000L, summary.totalCustomerReceivablesPiasters)
        assertEquals(5_000L, summary.totalSupplierPayablesPiasters)
    }

    @Test
    fun test27_quickSalesWithoutCustomerUpdatesTotalsReportsAndBackupRestore() = runTest {
        // Invalid amount (<= 0) must be rejected
        val invalidRes = accRepo.addQuickSale(
            tenantId = tenantAId,
            actor = ownerA,
            amountPiasters = 0L,
            description = ""
        )
        assertTrue(invalidRes is RepoResult.Error)

        val now = EgyptTime.nowMillis()
        val twoDaysAgo = now - 2L * 24 * 60 * 60 * 1000L

        // 1) Older cash quick sale with blank description (optional description allowed)
        val idemKey1 = "qs-idem-1"
        val olderRes = accRepo.addQuickSale(
            tenantId = tenantAId,
            actor = ownerA,
            amountPiasters = 25_000L, // 250 EGP
            description = "",
            paymentMethod = PaymentMethods.CASH,
            occurredAtMillis = twoDaysAgo,
            idempotencyKey = idemKey1
        )
        assertTrue(olderRes is RepoResult.Success)
        val olderSale = (olderRes as RepoResult.Success).data
        assertEquals("", olderSale.description)

        // Duplicate tap with same idempotencyKey must not insert duplicate row
        val dupRes = accRepo.addQuickSale(
            tenantId = tenantAId,
            actor = ownerA,
            amountPiasters = 25_000L,
            description = "",
            paymentMethod = PaymentMethods.CASH,
            occurredAtMillis = twoDaysAgo,
            idempotencyKey = idemKey1
        )
        assertTrue(dupRes is RepoResult.Success)
        assertEquals(olderSale.id, (dupRes as RepoResult.Success).data.id)

        // 2) Today's quick sale with optional description, quantity (3), unit ("كرتونة"), and e-wallet (50 EGP * 3 = 150 EGP)
        val todayRes = accRepo.addQuickSale(
            tenantId = tenantAId,
            actor = ownerA,
            amountPiasters = 5_000L, // 50 EGP per unit
            description = "سحبة قطعة غيار",
            paymentMethod = PaymentMethods.EWALLET,
            quantityMilli = 3_000L,
            unitLabel = "كرتونة",
            occurredAtMillis = now
        )
        assertTrue(todayRes is RepoResult.Success)
        val todaySale = (todayRes as RepoResult.Success).data
        assertEquals(15_000L, todaySale.amountPiasters)
        assertEquals(5_000L, todaySale.unitPricePiasters)
        assertEquals(3_000L, todaySale.quantityMilli)
        assertEquals("كرتونة", todaySale.unitLabel)

        val allQuickSales = accRepo.observeQuickSales(tenantAId).first()
        assertEquals(2, allQuickSales.size)

        // Dashboard summary must include quick sales in incoming, net balance, and today totals
        val summary = accRepo.observeDashboardSummary(tenantAId).first()
        assertEquals(40_000L, summary.totalIncomingPiasters)
        assertEquals(0L, summary.totalOutgoingPiasters)
        assertEquals(40_000L, summary.totalNetBalancePiasters)
        assertEquals(15_000L, summary.todayIncomingPiasters)
        assertEquals(15_000L, summary.todaySalesPiasters)
        assertEquals(15_000L, summary.todayEWalletCollectionsPiasters)
        assertEquals(15_000L, summary.todayNetCashflowPiasters)

        // Dedicated Quick Sales Report must list both entries (and handle blank description + optional quantity/unit)
        val report = accRepo.generateReport(
            tenantId = tenantAId,
            type = ReportType.QUICK_SALES,
            fromMillis = twoDaysAgo - 86_400_000L,
            toMillis = now + 86_400_000L,
            query = ""
        )
        assertEquals(2, report.rows.size)
        assertTrue(report.rows.any { it.contains("سحبة قطعة غيار") && it.contains("3 كرتونة") })
        assertTrue(report.rows.any { it.contains("بدون بيان") })

        // Encrypted backup & restore must preserve quick sales including quantity and unit
        val (_, bytes) = backupManager.buildEncryptedBackupBytes(tenantAId, "passQuick123")
        val delRes = accRepo.deleteQuickSale(tenantAId, ownerA, olderSale.id)
        assertTrue(delRes is RepoResult.Success)
        assertEquals(1, accRepo.observeQuickSales(tenantAId).first().size)

        val summaryAfterDelete = accRepo.observeDashboardSummary(tenantAId).first()
        assertEquals(15_000L, summaryAfterDelete.totalIncomingPiasters)

        val restoreRes = backupManager.restoreFromEncryptedBytes(bytes, tenantAId, ownerA.id, ownerA.username, "passQuick123")
        assertTrue(restoreRes is BackupOpResult.Success)
        assertEquals(2, accRepo.observeQuickSales(tenantAId).first().size)
        assertEquals(40_000L, accRepo.observeDashboardSummary(tenantAId).first().totalIncomingPiasters)
        val restoredToday = db.quickSaleDao().getQuickSaleById(tenantAId, todaySale.id)
        assertEquals(todaySale.amountPiasters, restoredToday?.amountPiasters)
        assertEquals("كرتونة", restoredToday?.unitLabel)
        assertEquals(3_000L, restoredToday?.quantityMilli)
    }

    @Test
    fun test28_dailyWithdrawalsConnectPerCustomerMergeAndDelete() = runTest {
        val cid = (accRepo.addCustomer(tenantAId, ownerA, "عميل متصل", "01055556666", "", "", "", 0L) as RepoResult.Success).data.id

        // 1) Opening daily withdrawal twice for the same customer must connect to the SAME OPEN invoice
        val firstOpen = (accRepo.createDailyWithdrawalInvoice(tenantAId, ownerA, cid) as RepoResult.Success).data
        val secondOpen = (accRepo.createDailyWithdrawalInvoice(tenantAId, ownerA, cid) as RepoResult.Success).data
        assertEquals(firstOpen.id, secondOpen.id)
        assertEquals(1, accRepo.observeDailyInvoices(tenantAId).first().count { it.customerId == cid })

        // 2) Add two withdrawal items and delete one added by mistake
        val item1 = (accRepo.addDailyWithdrawalItem(tenantAId, ownerA, firstOpen.id, "صنف صحيح", 10_000L, 2_000L) as RepoResult.Success).data
        val itemMistake = (accRepo.addDailyWithdrawalItem(tenantAId, ownerA, firstOpen.id, "صنف بالغلط", 5_000L, 1_000L) as RepoResult.Success).data
        assertEquals(25_000L, db.dailyWithdrawalDao().getDailyInvoiceById(tenantAId, firstOpen.id)!!.totalPiasters)

        val delItemRes = accRepo.deleteDailyWithdrawalItem(tenantAId, ownerA, firstOpen.id, itemMistake.id)
        assertTrue(delItemRes is RepoResult.Success)
        val afterItemDelete = db.dailyWithdrawalDao().getDailyInvoiceById(tenantAId, firstOpen.id)!!
        assertEquals(1, afterItemDelete.withdrawalsCount)
        assertEquals(20_000L, afterItemDelete.totalPiasters)

        // 3) Simulate legacy duplicate open daily invoices for the same customer and merge them
        val dupInvoice = com.example.data.local.DailyWithdrawalInvoiceEntity(
            id = "dup-daily-id",
            tenantId = tenantAId,
            customerId = cid,
            invoiceNumber = "DW-99999",
            invoiceDateEpochDay = EgyptTime.todayEpochDay(),
            status = DailyInvoiceStatuses.OPEN,
            createdByUserId = ownerA.id,
            createdAt = EgyptTime.nowMillis() + 5000L
        )
        db.dailyWithdrawalDao().upsertDailyInvoice(dupInvoice)
        accRepo.addDailyWithdrawalItem(tenantAId, ownerA, dupInvoice.id, "من الفاتورة المكررة", 12_000L, 1_000L)
        assertEquals(2, accRepo.observeDailyInvoices(tenantAId).first().count { it.customerId == cid && it.status == DailyInvoiceStatuses.OPEN })

        val mergeRes = accRepo.mergeDuplicateOpenDailyInvoices(tenantAId, ownerA)
        assertTrue(mergeRes is RepoResult.Success)
        assertEquals(1, (mergeRes as RepoResult.Success).data)
        val openAfterMerge = accRepo.observeDailyInvoices(tenantAId).first().filter { it.customerId == cid && it.status == DailyInvoiceStatuses.OPEN }
        assertEquals(1, openAfterMerge.size)
        assertEquals(32_000L, openAfterMerge.first().totalPiasters)
        assertEquals(2, openAfterMerge.first().withdrawalsCount)

        // 4) Close, verify balance = 320 EGP, then delete the whole daily invoice -> balance must cleanly return to 0
        accRepo.closeDailyWithdrawalInvoice(tenantAId, ownerA, firstOpen.id)
        assertEquals(32_000L, accRepo.observeCustomer(tenantAId, cid).first()!!.currentBalancePiasters)

        val delInvRes = accRepo.deleteDailyWithdrawalInvoice(tenantAId, ownerA, firstOpen.id)
        assertTrue(delInvRes is RepoResult.Success)
        assertEquals(0, accRepo.observeDailyInvoices(tenantAId).first().count { it.customerId == cid })
        assertEquals(0L, accRepo.observeCustomer(tenantAId, cid).first()!!.currentBalancePiasters)
        assertTrue(item1.id.isNotBlank())
    }

    @Test
    fun test29_mergeDuplicateCustomersAndDeleteCustomer() = runTest {
        val c1 = (accRepo.addCustomer(tenantAId, ownerA, "asker", "01099762204", "", "", "", 0L) as RepoResult.Success).data
        val c2 = (accRepo.addCustomer(tenantAId, ownerA, "asker", "", "", "", "", 0L) as RepoResult.Success).data

        // c1: 980 EGP sale, 220 EGP paid => 760 EGP remaining
        accRepo.createSalesInvoice(
            tenantId = tenantAId,
            actor = ownerA,
            customerId = c1.id,
            lines = listOf(SaleLineDraft("بضاعة 1", 98_000L, 1_000L)),
            saleType = SaleTypes.CREDIT,
            paymentMethod = PaymentMethods.CASH,
            paidNowPiasters = 22_000L,
            dueDateMillis = null,
            installments = emptyList(),
            notes = ""
        )
        // c2: 250 EGP sale, 50 EGP paid => 200 EGP remaining
        accRepo.createSalesInvoice(
            tenantId = tenantAId,
            actor = ownerA,
            customerId = c2.id,
            lines = listOf(SaleLineDraft("بضاعة 2", 25_000L, 1_000L)),
            saleType = SaleTypes.CREDIT,
            paymentMethod = PaymentMethods.CASH,
            paidNowPiasters = 5_000L,
            dueDateMillis = null,
            installments = emptyList(),
            notes = ""
        )

        // Open daily withdrawal on BOTH duplicate accounts
        val d1 = (accRepo.createDailyWithdrawalInvoice(tenantAId, ownerA, c1.id) as RepoResult.Success).data
        val d2 = (accRepo.createDailyWithdrawalInvoice(tenantAId, ownerA, c2.id) as RepoResult.Success).data
        accRepo.addDailyWithdrawalItem(tenantAId, ownerA, d1.id, "سحبة 1", 10_000L, 1_000L)
        accRepo.addDailyWithdrawalItem(tenantAId, ownerA, d2.id, "سحبة 2", 15_000L, 1_000L)

        // Auto-merge same-name customers ("asker")
        val mergeRes = accRepo.mergeDuplicateSameNameCustomers(tenantAId, ownerA)
        assertTrue(mergeRes is RepoResult.Success)
        assertEquals(1, (mergeRes as RepoResult.Success).data)

        val remainingCustomers = accRepo.observeCustomers(tenantAId).first()
        assertEquals(1, remainingCustomers.size)
        val merged = remainingCustomers.first()
        assertEquals("01099762204", merged.phone)
        assertEquals(123_000L, merged.totalSalesPiasters)
        assertEquals(27_000L, merged.totalPaidPiasters)
        assertEquals(96_000L, merged.currentBalancePiasters)

        // Open daily invoices from both duplicate customers must now be ONE connected invoice with 250 EGP total
        val openDaily = accRepo.observeDailyInvoices(tenantAId).first().filter { it.customerId == merged.id && it.status == DailyInvoiceStatuses.OPEN }
        assertEquals(1, openDaily.size)
        assertEquals(25_000L, openDaily.first().totalPiasters)
        assertEquals(2, openDaily.first().withdrawalsCount)

        // Deleting customer removes everything cleanly
        val delRes = accRepo.deleteCustomer(tenantAId, ownerA, merged.id)
        assertTrue(delRes is RepoResult.Success)
        assertEquals(0, accRepo.observeCustomers(tenantAId).first().size)
        assertEquals(0, accRepo.observeDailyInvoices(tenantAId).first().size)
    }

    @Test
    fun test30_settleCustomerAccountWithOpenDailyAndDiscount() = runTest {
        val cid = (accRepo.addCustomer(tenantAId, ownerA, "عميل تصفية", "01022223333", "", "", "", 0L) as RepoResult.Success).data.id
        val pastDue = EgyptTime.nowMillis() - 2L * 86_400_000L

        // 1) Scheduled sale of 760 EGP (76_000L) with overdue installment
        accRepo.createSalesInvoice(
            tenantId = tenantAId,
            actor = ownerA,
            customerId = cid,
            lines = listOf(SaleLineDraft("مشتريات آجلة", 76_000L, 1_000L)),
            saleType = SaleTypes.SCHEDULED,
            paymentMethod = PaymentMethods.NONE,
            paidNowPiasters = 0L,
            dueDateMillis = pastDue,
            installments = listOf(com.example.data.repository.InstallmentDraft(pastDue, 76_000L)),
            notes = ""
        )

        // 2) Open daily withdrawal of 140 EGP (14_000L) -> total combined due = 900 EGP (90_000L)
        val openDaily = (accRepo.createDailyWithdrawalInvoice(tenantAId, ownerA, cid) as RepoResult.Success).data
        accRepo.addDailyWithdrawalItem(tenantAId, ownerA, openDaily.id, "سحبة يومية", 14_000L, 1_000L)

        // 3) Settle with 850 EGP cash + 50 EGP settlement discount + auto-close open daily invoice
        val settleRes = accRepo.settleCustomerAccount(
            tenantId = tenantAId,
            actor = ownerA,
            customerId = cid,
            paidAmountPiasters = 85_000L,
            paymentMethod = PaymentMethods.CASH,
            closeOpenDailyInvoices = true,
            notes = "تصفية نهائية"
        )
        assertTrue(settleRes is RepoResult.Success)
        val summary = (settleRes as RepoResult.Success).data
        assertEquals(1, summary.closedOpenDailyCount)
        assertEquals(90_000L, summary.settledPreviousBalancePiasters)
        assertEquals(85_000L, summary.paidAmountPiasters)
        assertEquals(5_000L, summary.discountPiasters)
        assertEquals(0L, summary.finalBalancePiasters)

        val settledCust = accRepo.observeCustomer(tenantAId, cid).first()!!
        assertEquals(0L, settledCust.currentBalancePiasters)
        assertEquals(CustomerAccountStatus.CLEAR, settledCust.accountStatus)

        // All invoices, installments, and closed daily invoice must have 0 remaining
        val custInvoices = db.salesInvoiceDao().getCustomerInvoices(tenantAId, cid)
        assertTrue(custInvoices.isNotEmpty() && custInvoices.all { it.remainingPiasters == 0L && it.status == InvoiceStatuses.PAID })
        val custInsts = db.installmentDao().getCustomerInstallments(tenantAId, cid)
        assertTrue(custInsts.isNotEmpty() && custInsts.all { it.remainingPiasters == 0L && it.status == InstallmentStatuses.PAID })
        val settledDaily = db.dailyWithdrawalDao().getDailyInvoiceById(tenantAId, openDaily.id)!!
        assertEquals(DailyInvoiceStatuses.CLOSED, settledDaily.status)
        assertEquals(0L, settledDaily.remainingPiasters)

        // Dashboard incoming reflects ONLY actual cash collected (850 EGP), NOT the 50 EGP discount
        val dash = accRepo.observeDashboardSummary(tenantAId).first()
        assertEquals(85_000L, dash.totalIncomingPiasters)

        // Re-settling already-settled 0 balance fails gracefully
        val repeatRes = accRepo.settleCustomerAccount(tenantAId, ownerA, cid, 0L, PaymentMethods.CASH)
        assertTrue(repeatRes is RepoResult.Error)

        // 4) Negative credit balance settlement returns balance cleanly to 0
        accRepo.recordCustomerPayment(
            tenantId = tenantAId,
            actor = ownerA,
            customerId = cid,
            amountPiasters = 10_000L,
            paymentMethod = PaymentMethods.CASH,
            notes = "دفعة مقدمة"
        )
        assertEquals(-10_000L, accRepo.observeCustomer(tenantAId, cid).first()!!.currentBalancePiasters)
        val creditSettle = accRepo.settleCustomerAccount(tenantAId, ownerA, cid, 0L, PaymentMethods.CASH)
        assertTrue(creditSettle is RepoResult.Success)
        assertEquals(0L, accRepo.observeCustomer(tenantAId, cid).first()!!.currentBalancePiasters)
    }

    @Test
    fun test31_productFourPriceTiersPersistenceBackupAndTierSelection() = runTest {
        // 1) Add product with all 4 price tiers: Purchase, Retail (قطاعي), Half-Wholesale (1/2 جملة), Wholesale (جملة)
        val addRes = accRepo.addProduct(
            tenantId = tenantAId,
            actor = ownerA,
            name = "زيت طعام 1 لتر",
            category = "مواد غذائية",
            skuOrBarcode = "OIL-1L",
            unitLabel = "زجاجة",
            salePricePiasters = 8_500L, // 85 ج.م قطاعي
            purchasePricePiasters = 6_800L, // 68 ج.م شراء
            stockQuantityMilli = 50_000L,
            trackStock = true,
            minStockAlertMilli = 5_000L,
            notes = "",
            halfWholesalePricePiasters = 7_800L, // 78 ج.م 1/2 جملة
            wholesalePricePiasters = 7_400L // 74 ج.م جملة
        )
        assertTrue(addRes is RepoResult.Success)
        val prod = (addRes as RepoResult.Success).data
        assertEquals(6_800L, prod.purchasePricePiasters)
        assertEquals(8_500L, prod.salePricePiasters)
        assertEquals(7_800L, prod.halfWholesalePricePiasters)
        assertEquals(7_400L, prod.wholesalePricePiasters)
        assertEquals(8_500L, prod.priceForTier(com.example.data.local.SalePriceTier.RETAIL))
        assertEquals(7_800L, prod.priceForTier(com.example.data.local.SalePriceTier.HALF_WHOLESALE))
        assertEquals(7_400L, prod.priceForTier(com.example.data.local.SalePriceTier.WHOLESALE))

        // 2) Product with only retail price falls back to retail when selecting half-wholesale or wholesale
        val fallbackProd = (accRepo.addProduct(
            tenantId = tenantAId,
            actor = ownerA,
            name = "سكر أبيض",
            category = "مواد غذائية",
            skuOrBarcode = "",
            unitLabel = "كيلو",
            salePricePiasters = 3_500L,
            purchasePricePiasters = 3_000L,
            stockQuantityMilli = 0L,
            trackStock = false,
            minStockAlertMilli = 0L,
            notes = ""
        ) as RepoResult.Success).data
        assertEquals(3_500L, fallbackProd.priceForTier(com.example.data.local.SalePriceTier.HALF_WHOLESALE))
        assertEquals(3_500L, fallbackProd.priceForTier(com.example.data.local.SalePriceTier.WHOLESALE))

        // 3) Update tiers and verify negative price validation
        val badRes = accRepo.updateProduct(
            tenantId = tenantAId,
            actor = ownerA,
            productId = prod.id,
            name = prod.name,
            category = prod.category,
            skuOrBarcode = prod.skuOrBarcode,
            unitLabel = prod.unitLabel,
            salePricePiasters = 8_500L,
            purchasePricePiasters = 6_800L,
            stockQuantityMilli = 50_000L,
            trackStock = true,
            minStockAlertMilli = 5_000L,
            notes = "",
            halfWholesalePricePiasters = -100L,
            wholesalePricePiasters = 7_400L
        )
        assertTrue(badRes is RepoResult.Error)

        val updRes = accRepo.updateProduct(
            tenantId = tenantAId,
            actor = ownerA,
            productId = prod.id,
            name = prod.name,
            category = prod.category,
            skuOrBarcode = prod.skuOrBarcode,
            unitLabel = prod.unitLabel,
            salePricePiasters = 9_000L,
            purchasePricePiasters = 7_000L,
            stockQuantityMilli = 50_000L,
            trackStock = true,
            minStockAlertMilli = 5_000L,
            notes = "",
            halfWholesalePricePiasters = 8_200L,
            wholesalePricePiasters = 7_600L
        )
        assertTrue(updRes is RepoResult.Success)
        val updated = (updRes as RepoResult.Success).data
        assertEquals(9_000L, updated.salePricePiasters)
        assertEquals(8_200L, updated.halfWholesalePricePiasters)
        assertEquals(7_600L, updated.wholesalePricePiasters)
        assertEquals(7_000L, updated.purchasePricePiasters)

        // 4) Encrypted backup & restore must preserve all 4 price tiers
        val (_, bytes) = backupManager.buildEncryptedBackupBytes(tenantAId, "passTier123")
        accRepo.deleteProduct(tenantAId, ownerA, prod.id)
        assertEquals(null, db.productDao().getProductById(tenantAId, prod.id))

        val restoreRes = backupManager.restoreFromEncryptedBytes(bytes, tenantAId, ownerA.id, ownerA.username, "passTier123")
        assertTrue(restoreRes is BackupOpResult.Success)
        val restored = db.productDao().getProductById(tenantAId, prod.id)!!
        assertEquals(9_000L, restored.salePricePiasters)
        assertEquals(8_200L, restored.halfWholesalePricePiasters)
        assertEquals(7_600L, restored.wholesalePricePiasters)
        assertEquals(7_000L, restored.purchasePricePiasters)
    }
}
