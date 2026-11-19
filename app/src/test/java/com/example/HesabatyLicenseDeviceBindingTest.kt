package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.DeviceIdentityProvider
import com.example.core.DeviceLicenseManager
import com.example.core.LicenseClock
import com.example.data.local.AccountStatuses
import com.example.data.local.HesabatyDatabase
import com.example.data.local.UserEntity
import com.example.data.repository.AccountingRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthResult
import com.example.data.repository.RepoResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric verification suite for:
 * 1) 72-Hour Trial Account ("asker" / "123456") activation from first actual launch
 * 2) Near-expiry warning (<= 24h remaining) and hard lockout at 72h
 * 3) Anti-tamper detection when system wall-clock is rolled backward
 * 4) Single-Device Binding (Device A allowed, Device B rejected with exact Arabic error)
 * 5) Admin-only unbind/rebind, suspend/reactivate, and trial reset lifecycle
 * 6) Strict isolation between trial sandbox tenant and real customer shop tenants
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HesabatyLicenseDeviceBindingTest {

    private class FakeClock(
        var wall: Long = 1_760_000_000_000L,
        var elapsed: Long = 10_000L
    ) : LicenseClock {
        override fun wallClockMillis(): Long = wall
        override fun elapsedRealtimeMillis(): Long = elapsed

        fun advanceHours(hours: Long) {
            val delta = hours * 60L * 60L * 1000L
            wall += delta
            elapsed += delta
        }
    }

    private class FakeDeviceIdentity(
        var fingerprint: String,
        var label: String
    ) : DeviceIdentityProvider {
        override fun getDeviceFingerprint(): String = fingerprint
        override fun getDeviceLabel(): String = label
    }

    private lateinit var context: Context
    private lateinit var db: HesabatyDatabase
    private lateinit var fakeClock: FakeClock
    private lateinit var fakeDevice: FakeDeviceIdentity
    private lateinit var licenseManager: DeviceLicenseManager
    private lateinit var authRepo: AuthRepository
    private lateinit var accRepo: AccountingRepository
    private lateinit var adminUser: UserEntity

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        db = HesabatyDatabase.createInMemory(context)
        fakeClock = FakeClock()
        fakeDevice = FakeDeviceIdentity(
            fingerprint = "fp_device_alpha_998877665544332211",
            label = "Samsung Galaxy A55 (Device A)"
        )
        licenseManager = DeviceLicenseManager(context, fakeClock, fakeDevice)
        authRepo = AuthRepository(db, licenseManager)
        accRepo = AccountingRepository(db)

        val adminLogin = authRepo.login("asker1907", "422004")
        assertTrue(adminLogin is AuthResult.Success)
        adminUser = (adminLogin as AuthResult.Success).data.user
    }

    @After
    fun tearDown() {
        db.close()
        runCatching {
            val cacheField = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            cacheField.isAccessible = true
            (cacheField.get(null) as? MutableMap<*, *>)?.clear()
        }
    }

    @Test
    fun t01_trialDoesNotStartAtSeeding_startsOnFirstActualActivationAndExpiresAt72Hours() = runTest {
        // 1. Seed trial account; verify timer has NOT started yet
        val (_, seededTrial) = authRepo.ensureTrialAccountSeeded()
        assertNull("Trial timer must NOT start at seeding", seededTrial.trialStartedAt)
        assertNull("Trial expiry must NOT be set at seeding", seededTrial.trialExpiresAt)

        // Advance time by 48 hours BEFORE user ever activates the trial
        fakeClock.advanceHours(48)
        val activationTime = fakeClock.wall

        // 2. User taps "فتح حساب تجريبي" (or logs in with asker/123456) -> first actual activation!
        val firstLogin = authRepo.activateAndLoginTrial()
        assertTrue("First trial activation should succeed", firstLogin is AuthResult.Success)
        val session = (firstLogin as AuthResult.Success).data
        val trialInfo = requireNotNull(session.trialInfo)
        assertEquals(activationTime, trialInfo.startedAt)
        assertEquals(activationTime + DeviceLicenseManager.TRIAL_DURATION_MILLIS, trialInfo.expiresAt)
        assertEquals(DeviceLicenseManager.TRIAL_DURATION_MILLIS, trialInfo.remainingMillis)
        assertFalse("Should not warn near expiry at hour 0", trialInfo.isNearExpiry)

        // 3. Advance 54 hours -> 18 hours left -> near-expiry warning triggers!
        fakeClock.advanceHours(54)
        val nearExpiryCheck = authRepo.validateOrRestoreSession(session.user.id)
        assertTrue(nearExpiryCheck is AuthResult.Success)
        val nearInfo = (nearExpiryCheck as AuthResult.Success).data.trialInfo!!
        assertTrue("Near-expiry alert must trigger when <= 24h left", nearInfo.isNearExpiry)
        assertEquals(18L * 60L * 60L * 1000L, nearInfo.remainingMillis)

        // 4. Advance another 19 hours (total 73h) -> hard lockout with TrialExpired
        fakeClock.advanceHours(19)
        val expiredCheck = authRepo.login("asker", "123456")
        assertTrue("Trial must expire and block after 72h", expiredCheck is AuthResult.TrialExpired)
        val expiredState = expiredCheck as AuthResult.TrialExpired
        assertFalse("Normal expiry should not flag clock tampering", expiredState.isClockTampered)
    }

    @Test
    fun t02_clockRollbackTampering_blocksTrialImmediatelyEvenBefore72Hours() = runTest {
        val activation = authRepo.activateAndLoginTrial()
        assertTrue(activation is AuthResult.Success)
        val userId = (activation as AuthResult.Success).data.user.id

        // Normal usage for 10 hours
        fakeClock.advanceHours(10)
        assertTrue(authRepo.validateOrRestoreSession(userId) is AuthResult.Success)

        // User rolls device clock backwards by 2 days to try extending trial
        fakeClock.wall -= 48L * 60L * 60L * 1000L
        fakeClock.elapsed += 5L * 60L * 1000L // monotonic clock still moves forward

        val tamperedResult = authRepo.validateOrRestoreSession(userId)
        assertTrue("Clock rollback must immediately lock trial", tamperedResult is AuthResult.TrialExpired)
        assertTrue("isClockTampered must be true", (tamperedResult as AuthResult.TrialExpired).isClockTampered)

        // Even if user puts the clock back forward, the tamper lock persists until Admin resets it!
        fakeClock.wall += 48L * 60L * 60L * 1000L
        val stillLocked = authRepo.login("asker", "123456")
        assertTrue("Tamper lock must persist after restoring clock", stillLocked is AuthResult.TrialExpired)
        assertTrue((stillLocked as AuthResult.TrialExpired).isClockTampered)
    }

    @Test
    fun t03_deviceBinding_allowsBoundDeviceA_rejectsDeviceBWithExactArabicMessage() = runTest {
        // Master Admin creates shop account bound to Device A
        val created = authRepo.createShopAccount(
            actor = adminUser,
            shopName = "شركة الأمل للأجهزة",
            ownerName = "كريم حسن",
            address = "الإسكندرية - سموحة",
            phone = "01022334455",
            username = "amal_shop",
            password = "secret123",
            confirmPassword = "secret123",
            bindToCurrentDevice = true
        )
        assertTrue(created is AuthResult.Success)
        val ownerUser = (created as AuthResult.Success).data.second
        assertEquals(fakeDevice.fingerprint, ownerUser.boundDeviceFingerprint)

        // Login on Device A succeeds
        val loginOnA = authRepo.login("amal_shop", "secret123")
        assertTrue("Bound Device A must login successfully", loginOnA is AuthResult.Success)

        // Switch to Device B (different phone)
        fakeDevice.fingerprint = "fp_device_beta_112233445566778899"
        fakeDevice.label = "Xiaomi Redmi Note 13 (Device B)"

        // Login on Device B MUST fail with exact required message
        val loginOnB = authRepo.login("amal_shop", "secret123")
        assertTrue("Device B must be rejected", loginOnB is AuthResult.Error)
        assertEquals(
            "هذا الحساب مرتبط بجهاز آخر ولا يمكن استخدامه على هذا الجهاز.",
            (loginOnB as AuthResult.Error).message
        )
    }

    @Test
    fun t04_adminUnbindDevice_transfersAccountToDeviceB_andBlocksOldDeviceA() = runTest {
        val created = authRepo.createShopAccount(
            actor = adminUser,
            shopName = "مخزن السلام",
            ownerName = "طارق سعيد",
            address = "الجيزة - المهندسين",
            phone = "01133445566",
            username = "salam_shop",
            password = "pass9999",
            confirmPassword = "pass9999",
            bindToCurrentDevice = true
        ) as AuthResult.Success
        val owner = created.data.second

        // Non-admin user CANNOT unbind device
        val unauthorizedUnbind = authRepo.unbindUserDevice(owner, owner.id)
        assertTrue(unauthorizedUnbind is AuthResult.Error)

        // Master Admin unbinds Device A
        val unbindRes = authRepo.unbindUserDevice(adminUser, owner.id)
        assertTrue(unbindRes is AuthResult.Success)
        assertNull((unbindRes as AuthResult.Success).data.boundDeviceFingerprint)

        // Customer logs in on new Device B -> succeeds and automatically binds to Device B
        fakeDevice.fingerprint = "fp_device_beta_new_phone_001"
        fakeDevice.label = "Oppo Reno 11 (Device B)"
        val loginOnB = authRepo.login("salam_shop", "pass9999")
        assertTrue("After Admin unbind, Device B should login & bind", loginOnB is AuthResult.Success)
        assertEquals(
            "fp_device_beta_new_phone_001",
            (loginOnB as AuthResult.Success).data.user.boundDeviceFingerprint
        )

        // Now old Device A is rejected!
        fakeDevice.fingerprint = "fp_device_alpha_998877665544332211"
        fakeDevice.label = "Samsung Galaxy A55 (Device A)"
        val loginBackOnA = authRepo.login("salam_shop", "pass9999")
        assertTrue("Old Device A must now be blocked", loginBackOnA is AuthResult.Error)
        assertEquals(AuthRepository.DEVICE_MISMATCH_MESSAGE, (loginBackOnA as AuthResult.Error).message)
    }

    @Test
    fun t05_adminSuspendReactivateAndResetTrial_controlsFullLifecycle() = runTest {
        // 1. Activate and expire trial
        val trialLogin = authRepo.activateAndLoginTrial() as AuthResult.Success
        val trialUser = trialLogin.data.user
        fakeClock.advanceHours(75)
        assertTrue(authRepo.login("asker", "123456") is AuthResult.TrialExpired)

        // 2. Master Admin resets/reactivates trial -> fresh 72h period starts on next login
        val resetRes = authRepo.reactivateOrResetTrial(adminUser, trialUser.id)
        assertTrue(resetRes is AuthResult.Success)

        val freshTrialLogin = authRepo.activateAndLoginTrial()
        assertTrue("Reset trial must grant a fresh 72h window", freshTrialLogin is AuthResult.Success)
        assertEquals(
            DeviceLicenseManager.TRIAL_DURATION_MILLIS,
            (freshTrialLogin as AuthResult.Success).data.trialInfo!!.remainingMillis
        )

        // 3. Master Admin suspends trial account -> blocked with admin suspension error
        val suspendRes = authRepo.setUserAccountStatus(adminUser, trialUser.id, AccountStatuses.SUSPENDED)
        assertTrue(suspendRes is AuthResult.Success)
        val blockedSuspended = authRepo.activateAndLoginTrial()
        assertTrue(blockedSuspended is AuthResult.Error)

        // 4. Master Admin reactivates trial -> works again
        val reactivateRes = authRepo.setUserAccountStatus(adminUser, trialUser.id, AccountStatuses.TRIAL)
        assertTrue(reactivateRes is AuthResult.Success)
        assertTrue(authRepo.activateAndLoginTrial() is AuthResult.Success)
    }

    @Test
    fun t06_trialSandboxData_isCompletelyIsolatedFromRealShopAccount() = runTest {
        // Activate trial and add a customer in the trial sandbox
        val trialSession = (authRepo.activateAndLoginTrial() as AuthResult.Success).data
        val addTrialCust = accRepo.addCustomer(
            tenantId = trialSession.tenant!!.id,
            actor = trialSession.user,
            name = "عميل تجريبي فقط",
            phone = "01055556666",
            whatsappPhone = "01055556666",
            address = "القاهرة",
            notes = "تجربة",
            openingBalancePiasters = 500_00L
        )
        assertTrue(addTrialCust is RepoResult.Success)

        // Create a real commercial shop account via Master Admin
        val shopCreated = (authRepo.createShopAccount(
            actor = adminUser,
            shopName = "محل التوحيد الحقيقي",
            ownerName = "محمود فتحي",
            address = "المنصورة",
            phone = "01077889900",
            username = "tawheed_shop",
            password = "realpass",
            confirmPassword = "realpass"
        ) as AuthResult.Success).data

        // Real shop MUST start completely clean with ZERO trial data leaking into it
        val realShopCustomers = accRepo.observeCustomers(shopCreated.first.id).first()
        assertEquals("Real shop must have 0 customers from trial sandbox", 0, realShopCustomers.size)
        val trialCustomers = accRepo.observeCustomers(trialSession.tenant.id).first()
        assertEquals("Trial sandbox keeps its own customer", 1, trialCustomers.size)
    }

    @Test
    fun t07_deviceRegistryBlocksTrialReuseEvenIfUserRowResetWithoutAdmin() = runTest {
        // Activate trial and expire it
        val trialSession = (authRepo.activateAndLoginTrial() as AuthResult.Success).data
        fakeClock.advanceHours(74)
        assertTrue(authRepo.login("asker", "123456") is AuthResult.TrialExpired)

        // Simulate someone wiping only the UserEntity trial fields without clearing device_trial_registry
        val tamperedUser = trialSession.user.copy(
            accountStatus = AccountStatuses.TRIAL,
            trialStartedAt = null,
            trialExpiresAt = null,
            clockTampered = false,
            licenseSignature = null
        )
        db.userDao().upsertUser(tamperedUser)

        // DeviceTrialRegistryEntity for this device fingerprint MUST still block trial renewal!
        val attemptReuse = authRepo.activateAndLoginTrial()
        assertTrue(
            "Device registry must prevent restarting expired trial on same device",
            attemptReuse is AuthResult.TrialExpired
        )
    }

    @Test
    fun t08_createShopDirectlyOnCustomerPhone_bindsImmediatelyAndRejectsOtherDevices() = runTest {
        // Unauthorized non-admin password must be rejected
        val wrongAdmin = authRepo.createShopAccountDirectlyOnCustomerPhone(
            adminUsername = "asker1907",
            adminPassword = "wrong_password",
            shopName = "محل الفجر",
            ownerName = "حسن علي",
            address = "طنطا",
            phone = "01066778899",
            username = "fajr_shop",
            password = "pass1234",
            confirmPassword = "pass1234"
        )
        assertTrue("Invalid admin password must be rejected", wrongAdmin is AuthResult.Error)

        // Valid Master Admin creates account directly on customer's Device A
        val created = authRepo.createShopAccountDirectlyOnCustomerPhone(
            adminUsername = "asker1907",
            adminPassword = "422004",
            shopName = "محل الفجر",
            ownerName = "حسن علي",
            address = "طنطا",
            phone = "01066778899",
            username = "fajr_shop",
            password = "pass1234",
            confirmPassword = "pass1234"
        )
        assertTrue("Direct creation on customer phone must succeed", created is AuthResult.Success)
        val session = (created as AuthResult.Success).data
        assertEquals(fakeDevice.fingerprint, session.user.boundDeviceFingerprint)

        // Attempt to open the same account on Device B -> MUST fail!
        fakeDevice.fingerprint = "fp_intruder_phone_777777"
        fakeDevice.label = "Other Phone B"
        val loginOnOtherPhone = authRepo.login("fajr_shop", "pass1234")
        assertTrue("Account bound on customer phone must reject any other phone", loginOnOtherPhone is AuthResult.Error)
        assertEquals(AuthRepository.DEVICE_MISMATCH_MESSAGE, (loginOnOtherPhone as AuthResult.Error).message)
    }

    @Test
    fun t09_remoteDeviceLockedToken_activatesOnlyOnTargetCustomerPhone() = runTest {
        // Customer phone A gets its unique Device Code
        val customerACode = licenseManager.getDeviceActivationCode()
        assertTrue(customerACode.startsWith("HSB-"))

        // Master Admin generates a signed, device-locked token for Customer Phone A
        val tokenRes = authRepo.generateRemoteDeviceActivationToken(
            actor = adminUser,
            targetDeviceCode = customerACode,
            shopName = "متجر البركة",
            ownerName = "يوسف سمير",
            address = "أسيوط",
            phone = "01288990011",
            username = "baraka_remote",
            password = "pass5555",
            confirmPassword = "pass5555"
        )
        assertTrue("Remote device token generation must succeed", tokenRes is AuthResult.Success)
        val token = (tokenRes as AuthResult.Success).data
        assertTrue(token.startsWith("HSB1."))

        // If someone forwards the token to a DIFFERENT phone B -> MUST be rejected!
        fakeDevice.fingerprint = "fp_device_b_unauthorized_5555"
        fakeDevice.label = "Unauthorized Device B"
        val wrongDeviceRes = authRepo.activateShopAccountWithDeviceToken(token)
        assertTrue("Token must be rejected on wrong phone", wrongDeviceRes is AuthResult.Error)
        assertEquals(
            "كود التفعيل هذا مرتبط بجهاز آخر ولا يمكن استخدامه على هذا الجهاز.",
            (wrongDeviceRes as AuthResult.Error).message
        )

        // Tampered token on Device A -> MUST be rejected!
        fakeDevice.fingerprint = "fp_device_alpha_998877665544332211"
        fakeDevice.label = "Samsung Galaxy A55 (Device A)"
        val tampered = authRepo.activateShopAccountWithDeviceToken(token + "x")
        assertTrue("Tampered token must be rejected", tampered is AuthResult.Error)

        // Original Customer Phone A activates the token -> SUCCEEDS and binds permanently to Phone A!
        val validActivation = authRepo.activateShopAccountWithDeviceToken(token)
        assertTrue("Token must activate cleanly on matching customer phone", validActivation is AuthResult.Success)
        val activatedSession = (validActivation as AuthResult.Success).data
        assertEquals("متجر البركة", activatedSession.tenant!!.shopName)
        assertEquals(fakeDevice.fingerprint, activatedSession.user.boundDeviceFingerprint)

        // Subsequent login on Phone A succeeds
        assertTrue(authRepo.login("baraka_remote", "pass5555") is AuthResult.Success)
    }

    @Test
    fun t10_invoiceStyledStatementPngAndClassyWhatsAppCaptions_renderCleanly() {
        // FileProvider caches PathStrategy statically across Robolectric test sandboxes;
        // clear sCache so FileProvider resolves this test's fresh cacheDir/exports root.
        runCatching {
            val cacheField = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            cacheField.isAccessible = true
            (cacheField.get(null) as? MutableMap<*, *>)?.clear()
        }
        val report = com.example.core.PdfInvoiceGenerator.ReportPrintModel(
            title = "كشف حساب عميل: محمود حسن",
            shopName = "معرض النور للتجارة",
            periodLabel = "2026/11/19",
            summaryPairs = listOf(
                "إجمالي التعاملات" to "5,000.00 ج.م",
                "إجمالي المدفوعات" to "3,500.00 ج.م",
                "الرصيد المتبقي" to "1,500.00 ج.م"
            ),
            columns = listOf("التاريخ", "البيان", "المبلغ", "الرصيد"),
            rows = listOf(
                listOf("2026/11/19", "فاتورة مبيعات #1", "5,000.00", "5,000.00"),
                listOf("2026/11/19", "تحصيل نقدي", "3,500.00", "1,500.00")
            )
        )
        val (pngFile, uri) = com.example.core.PdfInvoiceGenerator.generateReportInvoicePng(
            context = context,
            model = report,
            recipientName = "محمود حسن",
            shopAddress = "القاهرة - مدينة نصر",
            shopPhone = "01012345678",
            documentBadge = "كشف حساب عميل",
            statusRemainingPiasters = 150_000L
        )
        assertTrue("Generated statement PNG must exist and be non-empty", pngFile.exists() && pngFile.length() > 100L)
        assertNotNull(uri)

        val receiptMsg = com.example.core.WhatsAppHelper.buildPaymentReceiptMessage(
            customerName = "محمود حسن",
            shopName = "معرض النور للتجارة",
            amountPiasters = 350_000L,
            remainingAfterPiasters = 150_000L,
            paymentMethodLabel = "نقدي",
            dateFormatted = "2026/11/19"
        )
        assertTrue(receiptMsg.contains("محمود حسن"))
        assertTrue(receiptMsg.contains("3,500"))
        assertTrue(receiptMsg.contains("1,500"))
    }
}
