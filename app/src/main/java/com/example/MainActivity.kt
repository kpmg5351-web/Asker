package com.example

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.TenantEntity
import com.example.data.local.UserEntity
import com.example.data.repository.TrialStatusInfo
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.BackupScreen
import com.example.ui.screens.BiometricLockScreen
import com.example.ui.screens.CollectionScreen
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DailyWithdrawalDetailScreen
import com.example.ui.screens.DailyWithdrawalsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InvoicePreviewScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.NewPurchaseScreen
import com.example.ui.screens.NewSaleScreen
import com.example.ui.screens.ProductsScreen
import com.example.ui.screens.PurchaseInvoicePreviewScreen
import com.example.ui.screens.PurchasesScreen
import com.example.ui.screens.QuickSalesScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SupplierDetailScreen
import com.example.ui.screens.SuppliersScreen
import com.example.ui.screens.TrialExpiredScreen
import com.example.ui.theme.HesabatyTheme
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryDarkBlue
import com.example.ui.viewmodel.HesabatyViewModel
import com.example.ui.viewmodel.SessionUiState
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as HesabatyApplication).container

        setContent {
            val viewModel: HesabatyViewModel = viewModel(
                factory = HesabatyViewModel.Factory(appContainer)
            )
            val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
            val savedCredentials by viewModel.savedLoginCredentials.collectAsStateWithLifecycle()
            val snackbarHostState = remember { SnackbarHostState() }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.recheckActiveLicense()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(Unit) {
                viewModel.events.collect { ev ->
                    snackbarHostState.showSnackbar(ev.text)
                }
            }

            HesabatyTheme(darkTheme = isDarkMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (val state = sessionState) {
                        is SessionUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is SessionUiState.Unauthenticated -> {
                            LoginScreen(
                                isBusy = isBusy,
                                isDarkMode = isDarkMode,
                                savedCredentials = savedCredentials,
                                deviceLabel = viewModel.currentDeviceLabel,
                                deviceActivationCode = viewModel.currentDeviceActivationCode,
                                onToggleDarkMode = { viewModel.setDarkMode(it) },
                                onLogin = { u, p, rememberMe, onErr -> viewModel.login(u, p, rememberMe, onErr) },
                                onRememberMeChanged = { viewModel.setRememberMeChecked(it) },
                                onRequestBiometricLogin = { onErr ->
                                    if (savedCredentials.biometricUsername == null) {
                                        onErr("سجّل الدخول بحساب المحل أولًا لتفعيل الدخول السريع بالبصمة")
                                    } else {
                                        triggerBiometricPrompt(
                                            onSuccess = { viewModel.loginWithBiometric(onErr) },
                                            onUnavailable = { msg -> onErr(msg) },
                                            onError = { msg -> onErr(msg) }
                                        )
                                    }
                                },
                                onOpenTrial = { onErr -> viewModel.openTrialAccount(onErr) },
                                onCreateOnThisPhone = { adminU, adminP, sName, oName, addr, ph, uName, pass, confPass, onErr ->
                                    viewModel.createAndBindAccountOnThisPhone(
                                        adminUsername = adminU,
                                        adminPassword = adminP,
                                        shopName = sName,
                                        ownerName = oName,
                                        address = addr,
                                        phone = ph,
                                        username = uName,
                                        password = pass,
                                        confirmPassword = confPass
                                    ) { ok, msg ->
                                        if (!ok) onErr(msg)
                                    }
                                },
                                onActivateWithToken = { token, onErr ->
                                    viewModel.activateAccountWithDeviceToken(token) { ok, msg ->
                                        if (!ok) onErr(msg)
                                    }
                                }
                            )
                        }

                        is SessionUiState.TrialExpired -> {
                            TrialExpiredScreen(
                                isClockTampered = state.isClockTampered,
                                trialStartedAt = state.trialStartedAt,
                                trialExpiresAt = state.trialExpiresAt,
                                isDarkMode = isDarkMode,
                                onReturnToLogin = { viewModel.logout() }
                            )
                        }

                        is SessionUiState.MasterAdmin -> {
                            AdminPanelScreen(
                                adminUser = state.user,
                                viewModel = viewModel
                            )
                        }

                        is SessionUiState.ShopWorkspace -> {
                            if (state.requiresBiometricUnlock) {
                                LaunchedEffect(Unit) {
                                    delay(250)
                                    triggerBiometricPrompt(
                                        onSuccess = { viewModel.unlockBiometric() },
                                        onUnavailable = { viewModel.unlockBiometric() },
                                        onError = { msg -> viewModel.emitMessage(msg, isError = true) }
                                    )
                                }
                                BiometricLockScreen(
                                    shopName = state.tenant.shopName,
                                    onRequestBiometric = {
                                        triggerBiometricPrompt(
                                            onSuccess = { viewModel.unlockBiometric() },
                                            onUnavailable = { viewModel.unlockBiometric() },
                                            onError = { msg -> viewModel.emitMessage(msg, isError = true) }
                                        )
                                    },
                                    onLogout = { viewModel.logout() }
                                )
                            } else {
                                ShopWorkspaceHost(
                                    tenant = state.tenant,
                                    user = state.user,
                                    trialInfo = state.trialInfo,
                                    viewModel = viewModel,
                                    snackbarHostState = snackbarHostState
                                )
                            }
                        }
                    }

                    // Show snackbars on Admin or Login screens too
                    if (sessionState !is SessionUiState.ShopWorkspace) {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        ) { data ->
                            Snackbar(snackbarData = data)
                        }
                    }
                }
            }
        }
    }

    private fun triggerBiometricPrompt(
        onSuccess: () -> Unit,
        onUnavailable: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val manager = BiometricManager.from(this)
        val biometricOnly = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        val strongWithCredential = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val canBiometric = manager.canAuthenticate(biometricOnly) == BiometricManager.BIOMETRIC_SUCCESS
        val canStrongOrCredential = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            manager.canAuthenticate(strongWithCredential) == BiometricManager.BIOMETRIC_SUCCESS
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val hasDeviceLock = keyguardManager?.isDeviceSecure == true

        if (!canBiometric && !canStrongOrCredential && !hasDeviceLock) {
            onUnavailable("البصمة أو قفل الشاشة غير مفعّل في إعدادات هذا الهاتف")
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        val msg = errString.toString().ifBlank { "تعذر التحقق من البصمة" }
                        onError(msg)
                    }
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("فتح نظام حساباتي")
            .setSubtitle("استخدم البصمة أو قفل الجهاز للمتابعة")

        when {
            canBiometric -> {
                builder.setAllowedAuthenticators(biometricOnly)
                builder.setNegativeButtonText("إلغاء")
            }
            canStrongOrCredential -> {
                builder.setAllowedAuthenticators(strongWithCredential)
            }
            else -> {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }
        }

        try {
            prompt.authenticate(builder.build())
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "تعذر تشغيل نافذة البصمة على هذا الجهاز")
        }
    }
}

private data class TopLevelTab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val shopTopTabs = listOf(
    TopLevelTab("dashboard", "الرئيسية", Icons.Default.Home),
    TopLevelTab("customers", "العملاء", Icons.Default.Groups),
    TopLevelTab("suppliers", "الموردون", Icons.Default.LocalShipping),
    TopLevelTab("daily_withdrawals", "السحب اليومي", Icons.AutoMirrored.Filled.ReceiptLong),
    TopLevelTab("settings", "الإعدادات", Icons.Default.Settings)
)

@Composable
private fun ShopWorkspaceHost(
    tenant: TenantEntity,
    user: UserEntity,
    trialInfo: TrialStatusInfo?,
    viewModel: HesabatyViewModel,
    snackbarHostState: SnackbarHostState
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "dashboard"
    val currentBaseRoute = currentRoute.substringBefore("?")
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val showMainNav = shopTopTabs.any { it.route == currentBaseRoute }

    fun navigateTopTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.imePadding()
                )
            },
            bottomBar = {
                if (showMainNav && !useNavRail && !isImeVisible) {
                    HesabatyFloatingBottomBar(
                        tabs = shopTopTabs,
                        currentRoute = currentBaseRoute,
                        onSelectTab = { navigateTopTab(it) }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
            ) {
                if (showMainNav && useNavRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        shopTopTabs.forEach { tab ->
                            NavigationRailItem(
                                selected = currentBaseRoute == tab.route,
                                onClick = { navigateTopTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = "dashboard",
                    modifier = Modifier.weight(1f)
                ) {
                    composable("dashboard") {
                        DashboardScreen(
                            tenant = tenant,
                            user = user,
                            trialInfo = trialInfo,
                            viewModel = viewModel,
                            onNavigate = { route ->
                                if (shopTopTabs.any { it.route == route }) {
                                    navigateTopTab(route)
                                } else {
                                    navController.navigate(route)
                                }
                            }
                        )
                    }

                    composable("customers") {
                        CustomersScreen(
                            viewModel = viewModel,
                            onBack = { navigateTopTab("dashboard") },
                            onOpenCustomer = { cid -> navController.navigate("customer_detail/$cid") }
                        )
                    }

                    composable(
                        route = "customer_detail/{customerId}",
                        arguments = listOf(navArgument("customerId") { type = NavType.StringType })
                    ) { entry ->
                        val cid = entry.arguments?.getString("customerId").orEmpty()
                        CustomerDetailScreen(
                            customerId = cid,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNewSaleForCustomer = { navController.navigate("new_sale?customerId=$it") },
                            onCollectForCustomer = { navController.navigate("collection?customerId=$it") },
                            onOpenInvoice = { invId -> navController.navigate("invoice_preview/$invId") },
                            onOpenDailyInvoice = { dailyId -> navController.navigate("daily_detail/$dailyId") }
                        )
                    }

                    composable(
                        route = "suppliers?pay={pay}",
                        arguments = listOf(navArgument("pay") { type = NavType.BoolType; defaultValue = false })
                    ) { entry ->
                        val payMode = entry.arguments?.getBoolean("pay") ?: false
                        SuppliersScreen(
                            viewModel = viewModel,
                            quickPayBanner = payMode,
                            onBack = { navigateTopTab("dashboard") },
                            onOpenSupplier = { sid -> navController.navigate("supplier_detail/$sid") },
                            onOpenPurchases = { navController.navigate("purchases") }
                        )
                    }

                    composable("suppliers") {
                        SuppliersScreen(
                            viewModel = viewModel,
                            quickPayBanner = false,
                            onBack = { navigateTopTab("dashboard") },
                            onOpenSupplier = { sid -> navController.navigate("supplier_detail/$sid") },
                            onOpenPurchases = { navController.navigate("purchases") }
                        )
                    }

                    composable(
                        route = "supplier_detail/{supplierId}",
                        arguments = listOf(navArgument("supplierId") { type = NavType.StringType })
                    ) { entry ->
                        val sid = entry.arguments?.getString("supplierId").orEmpty()
                        SupplierDetailScreen(
                            supplierId = sid,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onCreatePurchase = { navController.navigate("new_purchase?supplierId=$sid") },
                            onOpenPurchaseInvoice = { pid -> navController.navigate("purchase_preview/$pid") }
                        )
                    }

                    composable("purchases") {
                        PurchasesScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onCreatePurchase = { navController.navigate("new_purchase") },
                            onOpenPurchaseInvoice = { pid -> navController.navigate("purchase_preview/$pid") }
                        )
                    }

                    composable(
                        route = "new_purchase?supplierId={supplierId}",
                        arguments = listOf(navArgument("supplierId") { type = NavType.StringType; nullable = true; defaultValue = null })
                    ) { entry ->
                        val sid = entry.arguments?.getString("supplierId")
                        NewPurchaseScreen(
                            preselectedSupplierId = sid,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onPurchaseCreated = { purchaseId ->
                                navController.navigate("purchase_preview/$purchaseId") {
                                    popUpTo("new_purchase?supplierId={supplierId}") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("new_purchase") {
                        NewPurchaseScreen(
                            preselectedSupplierId = null,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onPurchaseCreated = { purchaseId ->
                                navController.navigate("purchase_preview/$purchaseId") {
                                    popUpTo("new_purchase") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(
                        route = "purchase_preview/{purchaseId}",
                        arguments = listOf(navArgument("purchaseId") { type = NavType.StringType })
                    ) { entry ->
                        val pid = entry.arguments?.getString("purchaseId").orEmpty()
                        PurchaseInvoicePreviewScreen(
                            purchaseId = pid,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = "new_sale?customerId={customerId}",
                        arguments = listOf(navArgument("customerId") { type = NavType.StringType; nullable = true; defaultValue = null })
                    ) { entry ->
                        val cid = entry.arguments?.getString("customerId")
                        NewSaleScreen(
                            preselectedCustomerId = cid,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onSaleCreated = { invoiceId ->
                                navController.navigate("invoice_preview/$invoiceId") {
                                    popUpTo("new_sale?customerId={customerId}") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("new_sale") {
                        NewSaleScreen(
                            preselectedCustomerId = null,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onSaleCreated = { invoiceId ->
                                navController.navigate("invoice_preview/$invoiceId") {
                                    popUpTo("new_sale") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(
                        route = "invoice_preview/{invoiceId}",
                        arguments = listOf(navArgument("invoiceId") { type = NavType.StringType })
                    ) { entry ->
                        val invId = entry.arguments?.getString("invoiceId").orEmpty()
                        InvoicePreviewScreen(
                            invoiceId = invId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = "collection?customerId={customerId}",
                        arguments = listOf(navArgument("customerId") { type = NavType.StringType; nullable = true; defaultValue = null })
                    ) { entry ->
                        val cid = entry.arguments?.getString("customerId")
                        CollectionScreen(
                            preselectedCustomerId = cid,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("collection") {
                        CollectionScreen(
                            preselectedCustomerId = null,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("daily_withdrawals") {
                        DailyWithdrawalsScreen(
                            viewModel = viewModel,
                            onBack = { navigateTopTab("dashboard") },
                            onOpenDailyDetail = { dailyId -> navController.navigate("daily_detail/$dailyId") }
                        )
                    }

                    composable(
                        route = "daily_detail/{dailyId}",
                        arguments = listOf(navArgument("dailyId") { type = NavType.StringType })
                    ) { entry ->
                        val dailyId = entry.arguments?.getString("dailyId").orEmpty()
                        DailyWithdrawalDetailScreen(
                            dailyInvoiceId = dailyId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("products") {
                        ProductsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("quick_sales") {
                        QuickSalesScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("reports") {
                        ReportsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onNavigate = { route ->
                                if (shopTopTabs.any { it.route == route }) {
                                    navigateTopTab(route)
                                } else {
                                    navController.navigate(route)
                                }
                            }
                        )
                    }

                    composable("backup") {
                        BackupScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            tenant = tenant,
                            user = user,
                            viewModel = viewModel,
                            onBack = { navigateTopTab("dashboard") },
                            onOpenBackup = { navController.navigate("backup") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HesabatyFloatingBottomBar(
    tabs: List<TopLevelTab>,
    currentRoute: String,
    onSelectTab: (String) -> Unit
) {
    val activeGold = Color(0xFFFBBF24)
    val uiScale = HesabatyThemeExtras.uiScale
    Surface(
        shape = RoundedCornerShape(topStart = if (uiScale.isCompact) 22.dp else 28.dp, topEnd = if (uiScale.isCompact) 22.dp else 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, HesabatyThemeExtras.colors.cardBorder.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    horizontal = if (uiScale.isCompact) 4.dp else 10.dp,
                    vertical = if (uiScale.isCompact) 6.dp else 10.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(if (uiScale.isCompact) 2.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                Box(
                    modifier = Modifier
                        .weight(if (selected) (if (uiScale.isCompact) 1.12f else 1.2f) else 1f)
                        .heightIn(min = if (uiScale.isCompact) 50.dp else 56.dp)
                        .clip(RoundedCornerShape(if (uiScale.isCompact) 14.dp else 18.dp))
                        .then(
                            if (selected) {
                                Modifier.background(
                                    Brush.verticalGradient(
                                        colors = listOf(PrimaryBlue, PrimaryDarkBlue)
                                    )
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onSelectTab(tab.route) }
                        .padding(vertical = if (uiScale.isCompact) 5.dp else 6.dp, horizontal = 2.dp)
                        .testTag("bottom_tab_${tab.route}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) activeGold else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(if (uiScale.isCompact) 19.dp else 22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

