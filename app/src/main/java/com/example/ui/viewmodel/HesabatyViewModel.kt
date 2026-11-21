package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.EgyptTime
import com.example.core.SecurityUtils
import com.example.data.AppContainer
import com.example.data.backup.BackupOpResult
import com.example.data.backup.DriveFileItem
import com.example.data.local.AccountStatuses
import com.example.data.local.AppSettingEntity
import com.example.data.local.AuditLogEntity
import com.example.data.local.BackupHistoryEntity
import com.example.data.local.CustomerEntity
import com.example.data.local.DailyWithdrawalInvoiceEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.ProductEntity
import com.example.data.local.PurchaseInvoiceEntity
import com.example.data.local.SalesInvoiceEntity
import com.example.data.local.SupplierEntity
import com.example.data.local.TenantEntity
import com.example.data.local.UserEntity
import com.example.data.local.UserRoles
import com.example.data.repository.AppAlert
import com.example.data.repository.AuthResult
import com.example.data.repository.CreditLimitBreach
import com.example.data.repository.DashboardSummary
import com.example.data.repository.InstallmentDraft
import com.example.data.repository.PurchaseLineDraft
import com.example.data.repository.RepoResult
import com.example.data.repository.ReportOutput
import com.example.data.repository.ReportType
import com.example.data.repository.SaleLineDraft
import com.example.data.repository.TrialStatusInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed class SessionUiState {
    data object Loading : SessionUiState()
    data object Unauthenticated : SessionUiState()
    data class TrialExpired(
        val isClockTampered: Boolean,
        val trialStartedAt: Long?,
        val trialExpiresAt: Long?
    ) : SessionUiState()
    data class MasterAdmin(val user: UserEntity) : SessionUiState()
    data class ShopWorkspace(
        val user: UserEntity,
        val tenant: TenantEntity,
        val requiresBiometricUnlock: Boolean = false,
        val trialInfo: TrialStatusInfo? = null
    ) : SessionUiState()
}

data class UiEventMessage(
    val text: String,
    val isError: Boolean = false
)

data class SavedLoginCredentials(
    val username: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val biometricUsername: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class HesabatyViewModel(private val container: AppContainer) : ViewModel() {

    private val authRepo = container.authRepository
    private val accRepo = container.accountingRepository
    private val sessionPrefs = container.sessionPreferences
    val deviceLicenseManager = container.deviceLicenseManager
    val backupManager = container.backupManager

    val currentDeviceLabel: String
        get() = deviceLicenseManager.getDeviceLabel()

    val currentDeviceFingerprintShort: String
        get() = deviceLicenseManager.getDeviceFingerprint().take(12).uppercase()

    val currentDeviceActivationCode: String
        get() = deviceLicenseManager.getDeviceActivationCode()

    fun formatTrialRemaining(remainingMillis: Long): String =
        deviceLicenseManager.formatRemainingArabic(remainingMillis)

    private val _sessionState = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val sessionState: StateFlow<SessionUiState> = _sessionState.asStateFlow()

    private val _activeTenantId = MutableStateFlow<String?>(null)
    val activeTenantId: StateFlow<String?> = _activeTenantId.asStateFlow()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _savedLoginCredentials = MutableStateFlow(SavedLoginCredentials())
    val savedLoginCredentials: StateFlow<SavedLoginCredentials> = _savedLoginCredentials.asStateFlow()

    private fun mapSavedCredentials(stored: com.example.data.local.StoredSession): SavedLoginCredentials {
        val decrypted = stored.savedLoginPasswordEnc
            ?.let { SecurityUtils.decryptLocalSecret(it) }
            .orEmpty()
        return SavedLoginCredentials(
            username = if (stored.savedRememberChecked) stored.savedLoginUsername.orEmpty() else "",
            password = if (stored.savedRememberChecked && decrypted.isNotEmpty()) decrypted else "",
            rememberMe = stored.savedRememberChecked,
            biometricUsername = stored.biometricUsername
        )
    }

    private val _events = MutableSharedFlow<UiEventMessage>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEventMessage> = _events.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _driveFiles = MutableStateFlow<List<DriveFileItem>>(emptyList())
    val driveFiles: StateFlow<List<DriveFileItem>> = _driveFiles.asStateFlow()

    private val _localBackups = MutableStateFlow<List<File>>(emptyList())
    val localBackups: StateFlow<List<File>> = _localBackups.asStateFlow()

    // Master Admin Observables
    val allTenants: StateFlow<List<TenantEntity>> = authRepo.observeAllTenants()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allShopUsers: StateFlow<List<UserEntity>> = authRepo.observeAllShopUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAuditLogs: StateFlow<List<AuditLogEntity>> = authRepo.observeAllAuditLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBackupHistory: StateFlow<List<BackupHistoryEntity>> = authRepo.observeAllBackupHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tenant-Scoped Reactive Observables
    val activeTenant: StateFlow<TenantEntity?> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(null) else authRepo.observeTenant(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val appSettings: StateFlow<AppSettingEntity?> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(null) else accRepo.observeAppSettings(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dashboardSummary: StateFlow<DashboardSummary> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(DashboardSummary()) else accRepo.observeDashboardSummary(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    val alerts: StateFlow<List<AppAlert>> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeAlerts(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val creditLimitBreaches: StateFlow<List<CreditLimitBreach>> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeCreditLimitBreaches(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeCustomers(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suppliers: StateFlow<List<SupplierEntity>> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeSuppliers(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val purchaseInvoices: StateFlow<List<PurchaseInvoiceEntity>> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observePurchaseInvoices(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val invoices: StateFlow<List<SalesInvoiceEntity>> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeInvoices(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<PaymentEntity>> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observePayments(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyInvoices: StateFlow<List<DailyWithdrawalInvoiceEntity>> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeDailyInvoices(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductEntity>> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeProducts(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tenantBackupHistory: StateFlow<List<BackupHistoryEntity>> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeBackupHistory(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tenantAuditLogs: StateFlow<List<AuditLogEntity>> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeAuditLogs(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tenantUsers: StateFlow<List<UserEntity>> = _activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else authRepo.observeTenantUsers(tid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            authRepo.ensureMasterAdminSeeded()
            authRepo.ensureTrialAccountSeeded()
            val stored = withContext(Dispatchers.IO) { sessionPrefs.sessionFlow.first() }
            _savedLoginCredentials.value = withContext(Dispatchers.IO) { mapSavedCredentials(stored) }
            _isDarkMode.value = stored.darkMode

            launch {
                sessionPrefs.sessionFlow
                    .map { mapSavedCredentials(it) }
                    .flowOn(Dispatchers.IO)
                    .collect { _savedLoginCredentials.value = it }
            }

            if (stored.userId != null && stored.rememberMe) {
                when (val res = authRepo.validateOrRestoreSession(stored.userId)) {
                    is AuthResult.Success -> {
                        val restored = res.data
                        if (restored.user.role == UserRoles.MASTER_ADMIN) {
                            _activeTenantId.value = null
                            _sessionState.value = SessionUiState.MasterAdmin(restored.user)
                        } else if (restored.tenant != null) {
                            val settings = accRepo.observeAppSettings(restored.tenant.id).first()
                            _isDarkMode.value = settings?.darkMode ?: stored.darkMode
                            _activeTenantId.value = restored.tenant.id
                            refreshLocalBackups(restored.tenant.id)
                            _sessionState.value = SessionUiState.ShopWorkspace(
                                user = restored.user,
                                tenant = restored.tenant,
                                requiresBiometricUnlock = settings?.biometricLockEnabled == true,
                                trialInfo = restored.trialInfo
                            )
                        } else {
                            _sessionState.value = SessionUiState.Unauthenticated
                        }
                    }

                    is AuthResult.TrialExpired -> {
                        _activeTenantId.value = null
                        _sessionState.value = SessionUiState.TrialExpired(
                            isClockTampered = res.isClockTampered,
                            trialStartedAt = res.trialStartedAt,
                            trialExpiresAt = res.trialExpiresAt
                        )
                    }

                    is AuthResult.Error -> {
                        sessionPrefs.clearSession()
                        _sessionState.value = SessionUiState.Unauthenticated
                    }
                }
            } else {
                _sessionState.value = SessionUiState.Unauthenticated
            }
        }

        // Keep ShopWorkspace tenant fresh when edited
        viewModelScope.launch {
            combine(_sessionState, activeTenant) { state, latestTenant ->
                if (state is SessionUiState.ShopWorkspace && latestTenant != null && latestTenant != state.tenant) {
                    state.copy(tenant = latestTenant)
                } else null
            }.collect { updated ->
                if (updated != null) _sessionState.value = updated
            }
        }
    }

    fun emitMessage(text: String, isError: Boolean = false) {
        _events.tryEmit(UiEventMessage(text, isError))
    }

    fun unlockBiometric() {
        val current = _sessionState.value
        if (current is SessionUiState.ShopWorkspace) {
            _sessionState.value = current.copy(requiresBiometricUnlock = false)
        }
    }

    // =========================================================================
    // Authentication & 72-Hour Trial Activation
    // =========================================================================

    fun login(
        username: String,
        password: String,
        rememberMe: Boolean = true,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (val res = authRepo.login(username, password)) {
                    is AuthResult.Error -> onError(res.message)
                    is AuthResult.TrialExpired -> {
                        _activeTenantId.value = null
                        _sessionState.value = SessionUiState.TrialExpired(
                            isClockTampered = res.isClockTampered,
                            trialStartedAt = res.trialStartedAt,
                            trialExpiresAt = res.trialExpiresAt
                        )
                    }
                    is AuthResult.Success -> {
                        if (res.data.user.role != UserRoles.MASTER_ADMIN) {
                            if (rememberMe) {
                                val encryptedPassword = withContext(Dispatchers.IO) {
                                    SecurityUtils.encryptLocalSecret(password)
                                }
                                sessionPrefs.saveRememberedLogin(
                                    username = res.data.user.username,
                                    encryptedPassword = encryptedPassword
                                )
                            } else {
                                sessionPrefs.clearRememberedLogin()
                            }
                            sessionPrefs.saveBiometricAccount(res.data.user.id, res.data.user.username)
                        }
                        enterAuthenticatedSession(res.data, rememberMe)
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun setRememberMeChecked(checked: Boolean) {
        if (!checked) {
            viewModelScope.launch {
                sessionPrefs.clearRememberedLogin()
            }
        }
    }

    fun loginWithBiometric(onError: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val stored = sessionPrefs.sessionFlow.first()
                val bioUserId = stored.biometricUserId
                if (bioUserId.isNullOrBlank()) {
                    onError("سجّل الدخول بحساب المحل أولًا لتفعيل الدخول السريع بالبصمة")
                    return@launch
                }
                when (val res = authRepo.validateOrRestoreSession(bioUserId)) {
                    is AuthResult.Success -> {
                        if (res.data.user.role == UserRoles.MASTER_ADMIN) {
                            onError("الدخول بالبصمة غير متاح لحساب الإدارة")
                        } else {
                            enterAuthenticatedSession(
                                res.data,
                                rememberMe = stored.savedRememberChecked || stored.rememberMe
                            )
                        }
                    }
                    is AuthResult.TrialExpired -> {
                        _activeTenantId.value = null
                        _sessionState.value = SessionUiState.TrialExpired(
                            isClockTampered = res.isClockTampered,
                            trialStartedAt = res.trialStartedAt,
                            trialExpiresAt = res.trialExpiresAt
                        )
                    }
                    is AuthResult.Error -> {
                        sessionPrefs.clearBiometricAccount()
                        onError(res.message)
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun openTrialAccount(onError: (String) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (val res = authRepo.activateAndLoginTrial()) {
                    is AuthResult.Error -> onError(res.message)
                    is AuthResult.TrialExpired -> {
                        _activeTenantId.value = null
                        _sessionState.value = SessionUiState.TrialExpired(
                            isClockTampered = res.isClockTampered,
                            trialStartedAt = res.trialStartedAt,
                            trialExpiresAt = res.trialExpiresAt
                        )
                    }
                    is AuthResult.Success -> {
                        enterAuthenticatedSession(res.data, rememberMe = true)
                        res.data.trialInfo?.let { info ->
                            emitMessage("مرحباً بك في الحساب التجريبي — المتبقي: ${formatTrialRemaining(info.remainingMillis)}")
                        }
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    /**
     * Re-validates active ShopWorkspace license & 72-hour trial status (e.g., on ON_RESUME).
     * Immediately blocks access if 72 hours elapsed, clock rollback occurred, or Admin suspended/unbound the device.
     */
    fun recheckActiveLicense() {
        val current = _sessionState.value as? SessionUiState.ShopWorkspace ?: return
        viewModelScope.launch {
            when (val res = authRepo.validateOrRestoreSession(current.user.id)) {
                is AuthResult.Success -> {
                    val refreshed = res.data
                    if (refreshed.tenant != null) {
                        _sessionState.value = current.copy(
                            user = refreshed.user,
                            tenant = refreshed.tenant,
                            trialInfo = refreshed.trialInfo
                        )
                    }
                }
                is AuthResult.TrialExpired -> {
                    _activeTenantId.value = null
                    _sessionState.value = SessionUiState.TrialExpired(
                        isClockTampered = res.isClockTampered,
                        trialStartedAt = res.trialStartedAt,
                        trialExpiresAt = res.trialExpiresAt
                    )
                }
                is AuthResult.Error -> {
                    sessionPrefs.clearSession()
                    _activeTenantId.value = null
                    _sessionState.value = SessionUiState.Unauthenticated
                    emitMessage(res.message, isError = true)
                }
            }
        }
    }

    private suspend fun enterAuthenticatedSession(
        session: com.example.data.repository.AuthenticatedSession,
        rememberMe: Boolean
    ) {
        if (session.user.role == UserRoles.MASTER_ADMIN) {
            // Security safeguard: NEVER persist Master Admin auto-login on a device
            // so when Admin activates an account on a customer's phone, closing the app
            // can never leave the Admin Panel accessible to the customer.
            sessionPrefs.clearSession()
            _activeTenantId.value = null
            _sessionState.value = SessionUiState.MasterAdmin(session.user)
        } else if (session.tenant != null) {
            sessionPrefs.saveSession(
                userId = session.user.id,
                tenantId = session.user.tenantId,
                role = session.user.role,
                username = session.user.username,
                rememberMe = rememberMe
            )
            val settings = accRepo.observeAppSettings(session.tenant.id).first()
            val dark = settings?.darkMode ?: false
            _isDarkMode.value = dark
            sessionPrefs.setDarkMode(dark)
            _activeTenantId.value = session.tenant.id
            refreshLocalBackups(session.tenant.id)
            _sessionState.value = SessionUiState.ShopWorkspace(
                user = session.user,
                tenant = session.tenant,
                trialInfo = session.trialInfo
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            sessionPrefs.clearSession()
            _activeTenantId.value = null
            _sessionState.value = SessionUiState.Unauthenticated
        }
    }

    fun changeOwnPassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
        onDone: (Boolean, String) -> Unit
    ) {
        val userId = currentUser()?.id ?: return
        viewModelScope.launch {
            when (val res = authRepo.changeOwnPassword(userId, currentPassword, newPassword, confirmPassword)) {
                is AuthResult.Error -> onDone(false, res.message)
                is AuthResult.TrialExpired -> onDone(false, "انتهت الفترة التجريبية")
                is AuthResult.Success -> {
                    val user = currentUser()
                    if (user != null && user.role != UserRoles.MASTER_ADMIN && savedLoginCredentials.value.rememberMe) {
                        val encryptedPassword = withContext(Dispatchers.IO) {
                            SecurityUtils.encryptLocalSecret(newPassword)
                        }
                        sessionPrefs.saveRememberedLogin(
                            username = user.username,
                            encryptedPassword = encryptedPassword
                        )
                    }
                    emitMessage("تم تغيير كلمة المرور بنجاح")
                    onDone(true, "تم تغيير كلمة المرور بنجاح")
                }
            }
        }
    }

    // =========================================================================
    // Master Admin Actions (Accounts, Device Binding & Trial Lifecycle)
    // =========================================================================

    fun createAndBindAccountOnThisPhone(
        adminUsername: String,
        adminPassword: String,
        shopName: String,
        ownerName: String,
        address: String,
        phone: String,
        username: String,
        password: String,
        confirmPassword: String,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (
                    val res = authRepo.createShopAccountDirectlyOnCustomerPhone(
                        adminUsername = adminUsername,
                        adminPassword = adminPassword,
                        shopName = shopName,
                        ownerName = ownerName,
                        address = address,
                        phone = phone,
                        username = username,
                        password = password,
                        confirmPassword = confirmPassword
                    )
                ) {
                    is AuthResult.Error -> onDone(false, res.message)
                    is AuthResult.TrialExpired -> onDone(false, "تعذر إنشاء الحساب")
                    is AuthResult.Success -> {
                        val encryptedPassword = withContext(Dispatchers.IO) {
                            SecurityUtils.encryptLocalSecret(password)
                        }
                        sessionPrefs.saveRememberedLogin(
                            username = res.data.user.username,
                            encryptedPassword = encryptedPassword
                        )
                        sessionPrefs.saveBiometricAccount(res.data.user.id, res.data.user.username)
                        enterAuthenticatedSession(res.data, rememberMe = true)
                        val msg = "تم إنشاء حساب \"${res.data.tenant?.shopName.orEmpty()}\" وربطه حصريًا بهذا الهاتف بنجاح"
                        emitMessage(msg)
                        onDone(true, msg)
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun activateAccountWithDeviceToken(
        rawToken: String,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (val res = authRepo.activateShopAccountWithDeviceToken(rawToken)) {
                    is AuthResult.Error -> onDone(false, res.message)
                    is AuthResult.TrialExpired -> onDone(false, "تعذر تفعيل الحساب")
                    is AuthResult.Success -> {
                        sessionPrefs.saveBiometricAccount(res.data.user.id, res.data.user.username)
                        enterAuthenticatedSession(res.data, rememberMe = true)
                        val msg = "تم تفعيل حساب \"${res.data.tenant?.shopName.orEmpty()}\" وربطه بهذا الهاتف بنجاح"
                        emitMessage(msg)
                        onDone(true, msg)
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun generateRemoteActivationToken(
        targetDeviceCode: String,
        shopName: String,
        ownerName: String,
        address: String,
        phone: String,
        username: String,
        password: String,
        confirmPassword: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val admin = (sessionState.value as? SessionUiState.MasterAdmin)?.user ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (
                    val res = authRepo.generateRemoteDeviceActivationToken(
                        actor = admin,
                        targetDeviceCode = targetDeviceCode,
                        shopName = shopName,
                        ownerName = ownerName,
                        address = address,
                        phone = phone,
                        username = username,
                        password = password,
                        confirmPassword = confirmPassword
                    )
                ) {
                    is AuthResult.Error -> onResult(false, res.message)
                    is AuthResult.TrialExpired -> onResult(false, "غير متاح")
                    is AuthResult.Success -> {
                        emitMessage("تم توليد كود التفعيل المربوط بجهاز العميل بنجاح")
                        onResult(true, res.data)
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun createShopAccount(
        shopName: String,
        ownerName: String,
        address: String,
        phone: String,
        username: String,
        password: String,
        confirmPassword: String,
        bindToCurrentDevice: Boolean = true,
        onDone: (Boolean, String) -> Unit
    ) {
        val admin = (sessionState.value as? SessionUiState.MasterAdmin)?.user ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (
                    val res = authRepo.createShopAccount(
                        actor = admin,
                        shopName = shopName,
                        ownerName = ownerName,
                        address = address,
                        phone = phone,
                        username = username,
                        password = password,
                        confirmPassword = confirmPassword,
                        bindToCurrentDevice = bindToCurrentDevice
                    )
                ) {
                    is AuthResult.Error -> onDone(false, res.message)
                    is AuthResult.TrialExpired -> onDone(false, "غير متاح")
                    is AuthResult.Success -> {
                        val bindNote = if (bindToCurrentDevice) " ومربوط بهذا الجهاز" else " (سيرتبط بأول جهاز يسجل الدخول)"
                        val msg = "تم إنشاء حساب محل \"${res.data.first.shopName}\" بنجاح$bindNote"
                        emitMessage(msg)
                        onDone(true, msg)
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun switchToShopAccountFromAdmin(
        username: String,
        password: String,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (val res = authRepo.login(username, password)) {
                    is AuthResult.Error -> onError(res.message)
                    is AuthResult.TrialExpired -> onError("الحساب منتهي الصلاحية")
                    is AuthResult.Success -> {
                        enterAuthenticatedSession(res.data, rememberMe = true)
                        emitMessage("تم تسجيل الدخول لحساب \"${res.data.tenant?.shopName.orEmpty()}\" المربوط بهذا الهاتف")
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun bindUserToCurrentDevice(targetUserId: String) {
        val admin = (sessionState.value as? SessionUiState.MasterAdmin)?.user ?: return
        viewModelScope.launch {
            when (val res = authRepo.bindUserToCurrentDevice(admin, targetUserId)) {
                is AuthResult.Error -> emitMessage(res.message, true)
                is AuthResult.TrialExpired -> emitMessage("تعذر الربط", true)
                is AuthResult.Success -> emitMessage("تم ربط الحساب (${res.data.username}) بالجهاز الحالي بنجاح")
            }
        }
    }

    fun unbindUserDevice(targetUserId: String) {
        val admin = (sessionState.value as? SessionUiState.MasterAdmin)?.user ?: return
        viewModelScope.launch {
            when (val res = authRepo.unbindUserDevice(admin, targetUserId)) {
                is AuthResult.Error -> emitMessage(res.message, true)
                is AuthResult.TrialExpired -> emitMessage("تعذر فك الارتباط", true)
                is AuthResult.Success -> emitMessage("تم فك ارتباط الحساب (${res.data.username}) — جاهز للتفعيل على جهاز جديد")
            }
        }
    }

    fun setUserAccountStatus(targetUserId: String, newStatus: String) {
        val admin = (sessionState.value as? SessionUiState.MasterAdmin)?.user ?: return
        viewModelScope.launch {
            when (val res = authRepo.setUserAccountStatus(admin, targetUserId, newStatus)) {
                is AuthResult.Error -> emitMessage(res.message, true)
                is AuthResult.TrialExpired -> emitMessage("تعذر تحديث الحالة", true)
                is AuthResult.Success -> emitMessage("تم تحديث حالة الحساب إلى: ${AccountStatuses.arabicLabel(newStatus)}")
            }
        }
    }

    fun reactivateOrResetTrial(targetUserId: String) {
        val admin = (sessionState.value as? SessionUiState.MasterAdmin)?.user ?: return
        viewModelScope.launch {
            when (val res = authRepo.reactivateOrResetTrial(admin, targetUserId)) {
                is AuthResult.Error -> emitMessage(res.message, true)
                is AuthResult.TrialExpired -> emitMessage("تعذر إعادة التفعيل", true)
                is AuthResult.Success -> emitMessage("تم إعادة تفعيل الحساب التجريبي لمدة 72 ساعة جديدة")
            }
        }
    }

    fun toggleTenantActive(tenantId: String, active: Boolean) {
        val admin = (sessionState.value as? SessionUiState.MasterAdmin)?.user ?: return
        viewModelScope.launch {
            when (val res = authRepo.setTenantActive(admin, tenantId, active)) {
                is AuthResult.Error -> emitMessage(res.message, true)
                is AuthResult.TrialExpired -> emitMessage("تعذر التحديث", true)
                is AuthResult.Success -> emitMessage(if (active) "تم تفعيل حساب المحل" else "تم تعطيل حساب المحل")
            }
        }
    }

    fun updateTenant(
        tenant: TenantEntity,
        shopName: String,
        ownerName: String,
        address: String,
        phone: String,
        whatsappPhone: String,
        invoiceFooter: String = tenant.invoiceFooter,
        logoUri: String? = tenant.logoUri,
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val actor = currentUser() ?: return
        viewModelScope.launch {
            when (
                val res = authRepo.updateTenantInfo(
                    actor, tenant, shopName, ownerName, address, phone, whatsappPhone, invoiceFooter, logoUri
                )
            ) {
                is AuthResult.Error -> {
                    emitMessage(res.message, true)
                    onDone(false, res.message)
                }
                is AuthResult.TrialExpired -> onDone(false, "انتهت الفترة التجريبية")
                is AuthResult.Success -> {
                    emitMessage("تم حفظ بيانات المحل بنجاح")
                    onDone(true, "تم حفظ بيانات المحل")
                }
            }
        }
    }

    fun resetUserPassword(targetUserId: String, newPass: String, confirmPass: String, onDone: (Boolean, String) -> Unit) {
        val actor = currentUser() ?: return
        viewModelScope.launch {
            when (val res = authRepo.resetUserPasswordByAdmin(actor, targetUserId, newPass, confirmPass)) {
                is AuthResult.Error -> onDone(false, res.message)
                is AuthResult.TrialExpired -> onDone(false, "انتهت الفترة التجريبية")
                is AuthResult.Success -> {
                    emitMessage("تم إعادة تعيين كلمة المرور بنجاح")
                    onDone(true, "تم التحديث")
                }
            }
        }
    }

    fun createStaffUser(displayName: String, username: String, password: String, onDone: (Boolean, String) -> Unit) {
        val actor = currentUser() ?: return
        viewModelScope.launch {
            when (val res = authRepo.createStaffUser(actor, displayName, username, password)) {
                is AuthResult.Error -> onDone(false, res.message)
                is AuthResult.TrialExpired -> onDone(false, "انتهت الفترة التجريبية")
                is AuthResult.Success -> {
                    emitMessage("تم إضافة المستخدم ${res.data.displayName}")
                    onDone(true, "تمت الإضافة")
                }
            }
        }
    }

    fun toggleStaffActive(targetUserId: String, active: Boolean) {
        val actor = currentUser() ?: return
        viewModelScope.launch {
            when (val res = authRepo.toggleStaffActive(actor, targetUserId, active)) {
                is AuthResult.Error -> emitMessage(res.message, true)
                is AuthResult.TrialExpired -> emitMessage("انتهت الفترة التجريبية", true)
                is AuthResult.Success -> emitMessage(if (active) "تم تفعيل المستخدم" else "تم تعطيل المستخدم")
            }
        }
    }

    // =========================================================================
    // Shop Accounting Actions
    // =========================================================================

    fun addCustomer(
        name: String,
        phone: String,
        whatsappPhone: String,
        address: String,
        notes: String,
        openingBalancePiasters: Long,
        creditLimitPiasters: Long = 0L,
        onDone: (Boolean, String) -> Unit
    ) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            when (
                val res = accRepo.addCustomer(
                    tid, actor, name, phone, whatsappPhone, address, notes, openingBalancePiasters, creditLimitPiasters
                )
            ) {
                is RepoResult.Error -> onDone(false, res.message)
                is RepoResult.Success -> {
                    emitMessage("تم إضافة العميل ${res.data.name}")
                    onDone(true, res.data.id)
                }
            }
        }
    }

    fun updateCustomerCreditLimit(
        customerId: String,
        creditLimitPiasters: Long,
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            when (val res = accRepo.updateCustomerCreditLimit(tid, actor, customerId, creditLimitPiasters)) {
                is RepoResult.Error -> {
                    emitMessage(res.message, true)
                    onDone(false, res.message)
                }
                is RepoResult.Success -> {
                    val label = if (creditLimitPiasters > 0L) "تم حفظ حد المديونية للعميل" else "تم إلغاء الحد الخاص للمديونية"
                    emitMessage(label)
                    onDone(true, label)
                }
            }
        }
    }

    fun addSupplier(
        name: String,
        phone: String,
        whatsappPhone: String,
        address: String,
        notes: String,
        openingBalancePiasters: Long,
        onDone: (Boolean, String) -> Unit
    ) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            when (
                val res = accRepo.addSupplier(
                    tid, actor, name, phone, whatsappPhone, address, notes, openingBalancePiasters
                )
            ) {
                is RepoResult.Error -> onDone(false, res.message)
                is RepoResult.Success -> {
                    emitMessage("تم إضافة المورد ${res.data.name}")
                    onDone(true, res.data.id)
                }
            }
        }
    }

    fun recordSupplierTransaction(
        supplierId: String,
        type: String,
        amountPiasters: Long,
        paymentMethod: String,
        notes: String,
        occurredAtMillis: Long = EgyptTime.nowMillis(),
        idempotencyKey: String = UUID.randomUUID().toString(),
        isNegativeAdjustment: Boolean = false,
        onDone: (Boolean, String) -> Unit
    ) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            when (
                val res = accRepo.recordSupplierTransaction(
                    tid, actor, supplierId, type, amountPiasters, paymentMethod, notes,
                    occurredAtMillis, idempotencyKey, isNegativeAdjustment
                )
            ) {
                is RepoResult.Error -> onDone(false, res.message)
                is RepoResult.Success -> {
                    emitMessage("تم تسجيل حركة المورد وتحديث الرصيد")
                    onDone(true, res.data.id)
                }
            }
        }
    }

    fun createPurchaseInvoice(
        supplierId: String,
        lines: List<PurchaseLineDraft>,
        purchaseType: String,
        paymentMethod: String,
        paidNowPiasters: Long,
        dueDateMillis: Long?,
        notes: String,
        occurredAtMillis: Long,
        idempotencyKey: String,
        onDone: (Boolean, String) -> Unit
    ) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (
                    val res = accRepo.createPurchaseInvoice(
                        tenantId = tid,
                        actor = actor,
                        supplierId = supplierId,
                        lines = lines,
                        purchaseType = purchaseType,
                        paymentMethod = paymentMethod,
                        paidNowPiasters = paidNowPiasters,
                        dueDateMillis = dueDateMillis,
                        notes = notes,
                        occurredAtMillis = occurredAtMillis,
                        idempotencyKey = idempotencyKey
                    )
                ) {
                    is RepoResult.Error -> onDone(false, res.message)
                    is RepoResult.Success -> {
                        emitMessage("تم حفظ فاتورة الشراء ${res.data.invoiceNumber} وتحديث حساب المورد")
                        onDone(true, res.data.id)
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun createSalesInvoice(
        customerId: String,
        lines: List<SaleLineDraft>,
        saleType: String,
        paymentMethod: String,
        paidNowPiasters: Long,
        dueDateMillis: Long?,
        installments: List<InstallmentDraft>,
        notes: String,
        occurredAtMillis: Long,
        idempotencyKey: String,
        onDone: (Boolean, String) -> Unit
    ) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (
                    val res = accRepo.createSalesInvoice(
                        tid, actor, customerId, lines, saleType, paymentMethod,
                        paidNowPiasters, dueDateMillis, installments, notes, occurredAtMillis, idempotencyKey
                    )
                ) {
                    is RepoResult.Error -> onDone(false, res.message)
                    is RepoResult.Success -> {
                        emitMessage("تم حفظ الفاتورة ${res.data.invoiceNumber} بنجاح")
                        onDone(true, res.data.id)
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun recordCustomerPayment(
        customerId: String,
        amountPiasters: Long,
        paymentMethod: String,
        invoiceId: String?,
        installmentId: String?,
        allowExcessAsCredit: Boolean,
        notes: String,
        occurredAtMillis: Long,
        idempotencyKey: String,
        onDone: (Boolean, String) -> Unit
    ) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (
                    val res = accRepo.recordCustomerPayment(
                        tid, actor, customerId, amountPiasters, paymentMethod,
                        invoiceId, installmentId, allowExcessAsCredit, notes, occurredAtMillis, idempotencyKey
                    )
                ) {
                    is RepoResult.Error -> onDone(false, res.message)
                    is RepoResult.Success -> {
                        emitMessage("تم تسجيل التحصيل وتحديث رصيد العميل")
                        onDone(true, res.data.id)
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    // Daily Withdrawals
    fun createDailyInvoice(customerId: String, onCreated: (String) -> Unit) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            when (val res = accRepo.createDailyWithdrawalInvoice(tid, actor, customerId)) {
                is RepoResult.Error -> emitMessage(res.message, true)
                is RepoResult.Success -> {
                    emitMessage("تم فتح فاتورة سحب يومي ${res.data.invoiceNumber}")
                    onCreated(res.data.id)
                }
            }
        }
    }

    fun addDailyWithdrawalItem(
        dailyInvoiceId: String,
        description: String,
        unitPricePiasters: Long,
        quantityMilli: Long,
        notes: String,
        productId: String? = null,
        onDone: (Boolean, String) -> Unit
    ) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            when (
                val res = accRepo.addDailyWithdrawalItem(
                    tenantId = tid,
                    actor = actor,
                    dailyInvoiceId = dailyInvoiceId,
                    description = description,
                    unitPricePiasters = unitPricePiasters,
                    quantityMilli = quantityMilli,
                    notes = notes,
                    productId = productId
                )
            ) {
                is RepoResult.Error -> onDone(false, res.message)
                is RepoResult.Success -> {
                    emitMessage("تم تسجيل السحبة بنجاح")
                    onDone(true, res.data.id)
                }
            }
        }
    }

    // =========================================================================
    // Products / Items Catalog Management
    // =========================================================================

    fun addProduct(
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
        onDone: (Boolean, String) -> Unit
    ) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            when (
                val res = accRepo.addProduct(
                    tenantId = tid,
                    actor = actor,
                    name = name,
                    category = category,
                    skuOrBarcode = skuOrBarcode,
                    unitLabel = unitLabel,
                    salePricePiasters = salePricePiasters,
                    purchasePricePiasters = purchasePricePiasters,
                    stockQuantityMilli = stockQuantityMilli,
                    trackStock = trackStock,
                    minStockAlertMilli = minStockAlertMilli,
                    notes = notes
                )
            ) {
                is RepoResult.Error -> onDone(false, res.message)
                is RepoResult.Success -> {
                    emitMessage("تم إضافة الصنف \"${res.data.name}\" بنجاح")
                    onDone(true, res.data.id)
                }
            }
        }
    }

    fun updateProduct(
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
        onDone: (Boolean, String) -> Unit
    ) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            when (
                val res = accRepo.updateProduct(
                    tenantId = tid,
                    actor = actor,
                    productId = productId,
                    name = name,
                    category = category,
                    skuOrBarcode = skuOrBarcode,
                    unitLabel = unitLabel,
                    salePricePiasters = salePricePiasters,
                    purchasePricePiasters = purchasePricePiasters,
                    stockQuantityMilli = stockQuantityMilli,
                    trackStock = trackStock,
                    minStockAlertMilli = minStockAlertMilli,
                    notes = notes
                )
            ) {
                is RepoResult.Error -> onDone(false, res.message)
                is RepoResult.Success -> {
                    emitMessage("تم تحديث الصنف \"${res.data.name}\"")
                    onDone(true, res.data.id)
                }
            }
        }
    }

    fun adjustProductStock(
        productId: String,
        deltaMilli: Long,
        reason: String,
        onDone: (Boolean, String) -> Unit
    ) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            when (val res = accRepo.adjustProductStock(tid, actor, productId, deltaMilli, reason)) {
                is RepoResult.Error -> onDone(false, res.message)
                is RepoResult.Success -> {
                    emitMessage("تم تحديث مخزون \"${res.data.name}\"")
                    onDone(true, res.data.id)
                }
            }
        }
    }

    fun deleteProduct(productId: String, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            when (val res = accRepo.deleteProduct(tid, actor, productId)) {
                is RepoResult.Error -> {
                    emitMessage(res.message, true)
                    onDone(false, res.message)
                }
                is RepoResult.Success -> {
                    emitMessage("تم حذف الصنف بنجاح")
                    onDone(true, "")
                }
            }
        }
    }

    fun editDailyWithdrawalItem(
        itemId: String,
        dailyInvoiceId: String,
        newDescription: String,
        newUnitPricePiasters: Long,
        newQuantityMilli: Long,
        newNotes: String,
        reason: String,
        onDone: (Boolean, String) -> Unit
    ) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            when (
                val res = accRepo.editDailyWithdrawalItem(
                    tid, actor, itemId, dailyInvoiceId, newDescription,
                    newUnitPricePiasters, newQuantityMilli, newNotes, reason
                )
            ) {
                is RepoResult.Error -> onDone(false, res.message)
                is RepoResult.Success -> {
                    emitMessage("تم تعديل السحبة وتسجيل المراجعة")
                    onDone(true, res.data.id)
                }
            }
        }
    }

    fun recordDailyInvoicePayment(
        dailyInvoiceId: String,
        amountPiasters: Long,
        paymentMethod: String,
        notes: String,
        idempotencyKey: String,
        onDone: (Boolean, String) -> Unit
    ) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            when (
                val res = accRepo.recordDailyInvoicePayment(
                    tid, actor, dailyInvoiceId, amountPiasters, paymentMethod, notes, idempotencyKey
                )
            ) {
                is RepoResult.Error -> onDone(false, res.message)
                is RepoResult.Success -> {
                    emitMessage("تم تسجيل الدفعة على الفاتورة اليومية")
                    onDone(true, res.data.id)
                }
            }
        }
    }

    fun closeDailyInvoice(dailyInvoiceId: String) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            when (val res = accRepo.closeDailyWithdrawalInvoice(tid, actor, dailyInvoiceId)) {
                is RepoResult.Error -> emitMessage(res.message, true)
                is RepoResult.Success -> emitMessage("تم إغلاق الفاتورة اليومية وترحيلها لحساب العميل")
            }
        }
    }

    fun reopenDailyInvoice(dailyInvoiceId: String, reason: String, onDone: (Boolean, String) -> Unit) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            when (val res = accRepo.reopenDailyWithdrawalInvoice(tid, actor, dailyInvoiceId, reason)) {
                is RepoResult.Error -> onDone(false, res.message)
                is RepoResult.Success -> {
                    emitMessage("تم إعادة فتح الفاتورة اليومية وتسجيل السبب")
                    onDone(true, "تمت إعادة الفتح")
                }
            }
        }
    }

    // =========================================================================
    // Detail Flows Helpers
    // =========================================================================

    fun observeCustomer(customerId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(null) else accRepo.observeCustomer(tid, customerId)
    }

    fun observeCustomerInvoices(customerId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeCustomerInvoices(tid, customerId)
    }

    fun observeCustomerPayments(customerId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeCustomerPayments(tid, customerId)
    }

    fun observeCustomerTransactions(customerId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeCustomerTransactions(tid, customerId)
    }

    fun observeCustomerInstallments(customerId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeCustomerInstallments(tid, customerId)
    }

    fun observeSupplier(supplierId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(null) else accRepo.observeSupplier(tid, supplierId)
    }

    fun observeSupplierTransactions(supplierId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeSupplierTransactions(tid, supplierId)
    }

    fun observeSupplierPurchaseInvoices(supplierId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeSupplierPurchaseInvoices(tid, supplierId)
    }

    fun observePurchaseInvoiceById(purchaseId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(null) else accRepo.observePurchaseInvoiceById(tid, purchaseId)
    }

    fun observePurchaseInvoiceItems(purchaseId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observePurchaseInvoiceItems(tid, purchaseId)
    }

    fun observeInvoiceById(invoiceId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(null) else accRepo.observeInvoiceById(tid, invoiceId)
    }

    fun observeInvoiceItems(invoiceId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeInvoiceItems(tid, invoiceId)
    }

    fun observeInvoiceInstallments(invoiceId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeInvoiceInstallments(tid, invoiceId)
    }

    fun observeDailyInvoice(dailyId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(null) else accRepo.observeDailyInvoice(tid, dailyId)
    }

    fun observeDailyItems(dailyId: String) = activeTenantId.flatMapLatest { tid ->
        if (tid == null) flowOf(emptyList()) else accRepo.observeDailyItems(tid, dailyId)
    }

    // =========================================================================
    // Reports
    // =========================================================================

    suspend fun loadReport(type: ReportType, fromMillis: Long, toMillis: Long, query: String): ReportOutput? {
        val tid = activeTenantId.value ?: return null
        return accRepo.generateReport(tid, type, fromMillis, toMillis, query)
    }

    // =========================================================================
    // Settings & Theme
    // =========================================================================

    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        viewModelScope.launch {
            sessionPrefs.setDarkMode(enabled)
            val tid = activeTenantId.value ?: return@launch
            val current = appSettings.value ?: AppSettingEntity(tenantId = tid)
            accRepo.saveAppSettings(tid, current.copy(darkMode = enabled))
        }
    }

    fun updateAppSettings(transform: (AppSettingEntity) -> AppSettingEntity) {
        val tid = activeTenantId.value ?: return
        viewModelScope.launch {
            val current = appSettings.value ?: AppSettingEntity(tenantId = tid)
            val updated = transform(current)
            accRepo.saveAppSettings(tid, updated)
            backupManager.scheduleDailyAutoBackup(
                tenantId = tid,
                enabled = updated.autoBackupEnabled,
                hour = updated.autoBackupHour,
                minute = updated.autoBackupMinute
            )
            emitMessage("تم حفظ الإعدادات بنجاح")
        }
    }

    // =========================================================================
    // Encrypted Backup & Google Drive Restore
    // =========================================================================

    fun refreshLocalBackups(tenantId: String? = activeTenantId.value) {
        if (tenantId == null) {
            _localBackups.value = emptyList()
            return
        }
        _localBackups.value = backupManager.listLocalBackupFiles(tenantId)
    }

    fun createLocalBackup(passphrase: String?, exportUri: Uri? = null) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (val res = backupManager.createLocalBackup(tid, actor.id, actor.username, passphrase, exportUri)) {
                    is BackupOpResult.Error -> emitMessage(res.message, true)
                    is BackupOpResult.Success -> {
                        refreshLocalBackups(tid)
                        emitMessage("تم إنشاء النسخة الاحتياطية المشفرة بنجاح")
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun restoreFromBytes(bytes: ByteArray, passphrase: String?, onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (val res = backupManager.restoreFromEncryptedBytes(bytes, tid, actor.id, actor.username, passphrase)) {
                    is BackupOpResult.Error -> {
                        emitMessage(res.message, true)
                        onDone(false, res.message)
                    }
                    is BackupOpResult.Success -> {
                        val msg = "تمت استعادة البيانات بنجاح (${res.data.customers.size} عميل، ${res.data.suppliers.size} مورد، ${res.data.salesInvoices.size} فاتورة)"
                        emitMessage(msg)
                        onDone(true, msg)
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun uploadBackupToGoogleDrive(accessToken: String, passphrase: String?) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                when (val res = backupManager.uploadToGoogleDrive(tid, actor.id, actor.username, accessToken, passphrase)) {
                    is BackupOpResult.Error -> emitMessage(res.message, true)
                    is BackupOpResult.Success -> {
                        refreshLocalBackups(tid)
                        emitMessage("تم رفع النسخة المشفرة إلى Google Drive بنجاح")
                        fetchDriveBackups(accessToken)
                    }
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun fetchDriveBackups(accessToken: String) {
        val tid = activeTenantId.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                _driveFiles.value = backupManager.driveApi.listAppDataBackups(accessToken, tid)
            } catch (e: Exception) {
                emitMessage(e.message ?: "تعذر جلب نسخ Google Drive", true)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun restoreFromGoogleDrive(accessToken: String, fileId: String, passphrase: String?) {
        val (tid, actor) = requireShopActor() ?: return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val bytes = backupManager.driveApi.downloadFileBytes(accessToken, fileId)
                when (val res = backupManager.restoreFromEncryptedBytes(bytes, tid, actor.id, actor.username, passphrase)) {
                    is BackupOpResult.Error -> emitMessage(res.message, true)
                    is BackupOpResult.Success -> emitMessage("تمت الاستعادة من Google Drive بنجاح دون تكرار")
                }
            } catch (e: Exception) {
                emitMessage(e.message ?: "فشل الاستعادة من Google Drive", true)
            } finally {
                _isBusy.value = false
            }
        }
    }

    private fun currentUser(): UserEntity? = when (val st = sessionState.value) {
        is SessionUiState.MasterAdmin -> st.user
        is SessionUiState.ShopWorkspace -> st.user
        else -> null
    }

    private fun requireShopActor(): Pair<String, UserEntity>? {
        val st = sessionState.value as? SessionUiState.ShopWorkspace ?: return null
        return st.tenant.id to st.user
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HesabatyViewModel(container) as T
        }
    }
}
