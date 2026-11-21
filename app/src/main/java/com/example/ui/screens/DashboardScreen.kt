package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.EgyptTime
import com.example.core.MoneyFormatter
import com.example.core.PhoneUtils
import com.example.core.WhatsAppHelper
import com.example.data.local.TenantEntity
import com.example.data.local.UserEntity
import com.example.data.repository.AlertSeverity
import com.example.data.repository.AppAlert
import com.example.data.repository.CreditLimitBreach
import com.example.data.repository.DashboardSummary
import com.example.data.repository.ReportType
import com.example.data.repository.TrialStatusInfo
import com.example.ui.components.HesabatyCard
import com.example.ui.components.QuickActionTile
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryDarkBlue
import com.example.ui.viewmodel.HesabatyViewModel

private val AccentGold = Color(0xFFFBBF24)
private val AccentGoldWarm = Color(0xFFF59E0B)

@Composable
fun DashboardScreen(
    tenant: TenantEntity,
    user: UserEntity,
    trialInfo: TrialStatusInfo? = null,
    viewModel: HesabatyViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val creditLimitBreaches by viewModel.creditLimitBreaches.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val invoices by viewModel.invoices.collectAsStateWithLifecycle()
    val purchaseInvoices by viewModel.purchaseInvoices.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val currency = settings?.defaultCurrencyLabel ?: "ج.م"

    var showAlertsDialog by remember { mutableStateOf(false) }
    var showCreditLimitDialog by remember { mutableStateOf(false) }
    var showAccountMenuDialog by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val extras = HesabatyThemeExtras.colors
    val uiScale = HesabatyThemeExtras.uiScale
    val hPad = uiScale.screenPadding
    val gridGap = if (uiScale.isCompact) 8.dp else 12.dp

    val urgentAlertsCount = alerts.count { it.severity == AlertSeverity.DANGER || it.severity == AlertSeverity.WARNING }
    val nonCreditLimitAlerts = remember(alerts) {
        alerts.filterNot { it.id.startsWith("credit_limit_") }
    }

    val trimmedQuery = searchQuery.trim()
    val matchedCustomers = remember(customers, trimmedQuery) {
        if (trimmedQuery.isEmpty()) emptyList()
        else customers.filter {
            it.name.contains(trimmedQuery, ignoreCase = true) ||
                it.phone.contains(trimmedQuery, ignoreCase = true)
        }.take(4)
    }
    val matchedSuppliers = remember(suppliers, trimmedQuery) {
        if (trimmedQuery.isEmpty()) emptyList()
        else suppliers.filter {
            it.name.contains(trimmedQuery, ignoreCase = true) ||
                it.phone.contains(trimmedQuery, ignoreCase = true)
        }.take(4)
    }
    val matchedInvoices = remember(invoices, trimmedQuery) {
        if (trimmedQuery.isEmpty()) emptyList()
        else invoices.filter {
            it.invoiceNumber.contains(trimmedQuery, ignoreCase = true)
        }.take(3)
    }
    val matchedPurchaseInvoices = remember(purchaseInvoices, trimmedQuery) {
        if (trimmedQuery.isEmpty()) emptyList()
        else purchaseInvoices.filter {
            it.invoiceNumber.contains(trimmedQuery, ignoreCase = true)
        }.take(3)
    }
    val matchedProducts = remember(products, trimmedQuery) {
        if (trimmedQuery.isEmpty()) emptyList()
        else products.filter {
            it.name.contains(trimmedQuery, ignoreCase = true) ||
                it.category.contains(trimmedQuery, ignoreCase = true) ||
                it.skuOrBarcode.contains(trimmedQuery, ignoreCase = true)
        }.take(4)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Curved Signature Royal-Blue Header Banner (edge-to-edge under status bar)
        item {
            CurvedDashboardHeader(
                tenant = tenant,
                user = user,
                alertsCount = alerts.size,
                hasUrgentAlerts = urgentAlertsCount > 0,
                isDarkMode = isDark,
                onOpenAlerts = { showAlertsDialog = true },
                onToggleDarkMode = { viewModel.setDarkMode(!isDark) },
                onOpenShopSettings = { onNavigate("settings") },
                onOpenAccountMenu = { showAccountMenuDialog = true },
                onLogoutClick = { showAccountMenuDialog = true }
            )
        }

        // 1.5. Always-visible Live Total Balance + Incoming (الوارد) + Outgoing (الصادر) Card
        item {
            Box(modifier = Modifier.padding(horizontal = hPad)) {
                LiveTotalBalanceCard(
                    summary = dashboardSummary,
                    currency = currency,
                    isDarkMode = isDark,
                    onOpenCustomers = { onNavigate("customers") },
                    onOpenSuppliers = { onNavigate("suppliers") },
                    onOpenReports = { onNavigate("reports") }
                )
            }
        }

        // 2. Floating Pill Search Bar (Live search across customers, suppliers & invoices)
        item {
            Box(modifier = Modifier.padding(horizontal = hPad)) {
                DashboardSearchPill(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    isDarkMode = isDark
                )
            }
        }

        // Live Search Results Dropdown Card (only shown when typing)
        if (trimmedQuery.isNotEmpty()) {
            item {
                Box(modifier = Modifier.padding(horizontal = hPad)) {
                    HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                        val hasAny = matchedCustomers.isNotEmpty() || matchedSuppliers.isNotEmpty() || matchedInvoices.isNotEmpty() || matchedPurchaseInvoices.isNotEmpty() || matchedProducts.isNotEmpty()
                        if (!hasAny) {
                            Text(
                                text = "لا توجد نتائج مطابقة لـ \"$trimmedQuery\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (matchedCustomers.isNotEmpty()) {
                                    Text(
                                        text = "العملاء",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    matchedCustomers.forEach { c ->
                                        SearchResultRow(
                                            icon = Icons.Default.Groups,
                                            title = c.name,
                                            subtitle = "الرصيد: ${MoneyFormatter.formatEgp(c.currentBalancePiasters, currency)}",
                                            onClick = {
                                                searchQuery = ""
                                                onNavigate("customer_detail/${c.id}")
                                            }
                                        )
                                    }
                                }
                                if (matchedSuppliers.isNotEmpty()) {
                                    if (matchedCustomers.isNotEmpty()) HorizontalDivider()
                                    Text(
                                        text = "الموردون",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    matchedSuppliers.forEach { s ->
                                        SearchResultRow(
                                            icon = Icons.Default.LocalShipping,
                                            title = s.name,
                                            subtitle = "المديونية: ${MoneyFormatter.formatEgp(s.currentBalancePiasters, currency)}",
                                            onClick = {
                                                searchQuery = ""
                                                onNavigate("supplier_detail/${s.id}")
                                            }
                                        )
                                    }
                                }
                                if (matchedInvoices.isNotEmpty()) {
                                    if (matchedCustomers.isNotEmpty() || matchedSuppliers.isNotEmpty()) HorizontalDivider()
                                    Text(
                                        text = "الفواتير",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = extras.success
                                    )
                                    matchedInvoices.forEach { inv ->
                                        SearchResultRow(
                                            icon = Icons.Default.Receipt,
                                            title = "فاتورة ${inv.invoiceNumber}",
                                            subtitle = "الإجمالي: ${MoneyFormatter.formatEgp(inv.totalPiasters, currency)}",
                                            onClick = {
                                                searchQuery = ""
                                                onNavigate("invoice_preview/${inv.id}")
                                            }
                                        )
                                    }
                                }
                                if (matchedPurchaseInvoices.isNotEmpty()) {
                                    if (matchedCustomers.isNotEmpty() || matchedSuppliers.isNotEmpty() || matchedInvoices.isNotEmpty()) HorizontalDivider()
                                    Text(
                                        text = "فواتير الشراء",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryDarkBlue
                                    )
                                    matchedPurchaseInvoices.forEach { pur ->
                                        SearchResultRow(
                                            icon = Icons.Default.Receipt,
                                            title = "فاتورة شراء ${pur.invoiceNumber}",
                                            subtitle = "الإجمالي: ${MoneyFormatter.formatEgp(pur.totalPiasters, currency)}",
                                            onClick = {
                                                searchQuery = ""
                                                onNavigate("purchase_preview/${pur.id}")
                                            }
                                        )
                                    }
                                }
                                if (matchedProducts.isNotEmpty()) {
                                    if (matchedCustomers.isNotEmpty() || matchedSuppliers.isNotEmpty() || matchedInvoices.isNotEmpty() || matchedPurchaseInvoices.isNotEmpty()) HorizontalDivider()
                                    Text(
                                        text = "الأصناف",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = extras.warning
                                    )
                                    matchedProducts.forEach { prod ->
                                        SearchResultRow(
                                            icon = Icons.Default.Inventory2,
                                            title = prod.name,
                                            subtitle = "السعر: ${MoneyFormatter.formatEgp(prod.salePricePiasters, currency)} • ${prod.unitLabel}",
                                            onClick = {
                                                searchQuery = ""
                                                onNavigate("products")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. 72-Hour Trial Remaining Time & Near-Expiry Warning Banner (when in trial mode)
        if (trialInfo?.isTrial == true) {
            item {
                val isNear = trialInfo.isNearExpiry
                val remainingLabel = viewModel.formatTrialRemaining(trialInfo.remainingMillis)
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    HesabatyCard(
                        containerColor = if (isNear) extras.warningContainer else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_trial_banner")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isNear) Icons.Default.Warning else Icons.Default.HourglassTop,
                                contentDescription = "مدة الحساب التجريبي",
                                tint = if (isNear) extras.warning else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isNear) {
                                        "تنبيه: الفترة التجريبية قاربت على الانتهاء!"
                                    } else {
                                        "الحساب التجريبي نشط (3 أيام / 72 ساعة)"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNear) extras.warning else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "الوقت المتبقي للتجربة: $remainingLabel",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                trialInfo.expiresAt?.let { exp ->
                                    Text(
                                        text = "تاريخ الانتهاء: ${EgyptTime.formatDateTime(exp)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (isNear) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    WhatsAppHelper.openWhatsAppChat(
                                        context = context,
                                        rawPhone = PhoneUtils.SUPPORT_PHONE_LOCAL,
                                        message = "السلام عليكم، أستخدم الحساب التجريبي لبرنامج حساباتي وأرغب في تفعيل النسخة الكاملة قبل انتهاء التجربة."
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dashboard_trial_activate_button")
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تواصل مع الدعم الفني للتفعيل (${PhoneUtils.SUPPORT_PHONE_LOCAL})")
                            }
                        }
                    }
                }
            }
        }

        // 4. Hero Carousel Banner + 3-Dot Pill Indicator (clean, privacy-safe without financial reports)
        item {
            DashboardHeroCarousel(
                isDarkMode = isDark,
                onNavigate = onNavigate
            )
        }

        // 4.5. Prominent Main-Menu Credit Limit Breach Alert System
        if (creditLimitBreaches.isNotEmpty()) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    CreditLimitBreachAlertCard(
                        breaches = creditLimitBreaches,
                        currency = currency,
                        onOpenCustomer = { customerId -> onNavigate("customer_detail/$customerId") },
                        onCollectCustomer = { customerId -> onNavigate("collection?customerId=$customerId") },
                        onViewAll = { showCreditLimitDialog = true }
                    )
                }
            }
        }

        // 5. Other Urgent Alerts Compact Pill (overdue installments, open daily invoices, low stock)
        if (nonCreditLimitAlerts.isNotEmpty()) {
            item {
                val primaryAlert = nonCreditLimitAlerts.firstOrNull { it.severity == AlertSeverity.DANGER }
                    ?: nonCreditLimitAlerts.firstOrNull { it.severity == AlertSeverity.WARNING }
                    ?: nonCreditLimitAlerts.firstOrNull()
                if (primaryAlert != null) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        HesabatyCard(
                            onClick = { showAlertsDialog = true },
                            containerColor = when (primaryAlert.severity) {
                                AlertSeverity.DANGER -> extras.dangerContainer
                                AlertSeverity.WARNING -> extras.warningContainer
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = when (primaryAlert.severity) {
                                        AlertSeverity.DANGER -> extras.danger
                                        AlertSeverity.WARNING -> extras.warning
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = primaryAlert.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = primaryAlert.message,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    text = "عرض (${alerts.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Signature 3-Column Elevated Rounded-Square Operations Grid
        item {
            Column(
                modifier = Modifier.padding(horizontal = hPad),
                verticalArrangement = Arrangement.spacedBy(gridGap)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gridGap)
                ) {
                    QuickActionTile(
                        label = "تسجيل بيع",
                        icon = Icons.Default.AddShoppingCart,
                        accentColor = PrimaryDarkBlue,
                        accentContainer = Color(0xFFEAF3FF),
                        testTag = "action_new_sale",
                        onClick = { onNavigate("new_sale") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        label = "سحب يومي",
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        accentColor = extras.warning,
                        accentContainer = extras.warningContainer,
                        testTag = "action_daily_withdrawals",
                        onClick = { onNavigate("daily_withdrawals") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        label = "تسجيل تحصيل",
                        icon = Icons.Default.Savings,
                        accentColor = extras.success,
                        accentContainer = extras.successContainer,
                        testTag = "action_collection",
                        onClick = { onNavigate("collection") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gridGap)
                ) {
                    QuickActionTile(
                        label = "العملاء",
                        icon = Icons.Default.Groups,
                        accentColor = PrimaryBlue,
                        accentContainer = Color(0xFFEAF3FF),
                        testTag = "action_customers",
                        onClick = { onNavigate("customers") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        label = "الموردون",
                        icon = Icons.Default.LocalShipping,
                        accentColor = PrimaryDarkBlue,
                        accentContainer = Color(0xFFE6EEFA),
                        testTag = "action_suppliers",
                        onClick = { onNavigate("suppliers") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        label = "دفعة لمورد",
                        icon = Icons.Default.Payments,
                        accentColor = extras.danger,
                        accentContainer = extras.dangerContainer,
                        testTag = "action_supplier_payment",
                        onClick = { onNavigate("suppliers?pay=true") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gridGap)
                ) {
                    QuickActionTile(
                        label = "الأصناف",
                        icon = Icons.Default.Inventory2,
                        accentColor = extras.warning,
                        accentContainer = extras.warningContainer,
                        testTag = "action_products",
                        onClick = { onNavigate("products") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        label = "التقارير",
                        icon = Icons.Default.Assessment,
                        accentColor = extras.wallet,
                        accentContainer = extras.walletContainer,
                        testTag = "action_reports",
                        onClick = { onNavigate("reports") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        label = "النسخ الاحتياطي",
                        icon = Icons.Default.Backup,
                        accentColor = extras.success,
                        accentContainer = extras.successContainer,
                        testTag = "action_backup",
                        onClick = { onNavigate("backup") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gridGap)
                ) {
                    QuickActionTile(
                        label = "فاتورة شراء",
                        icon = Icons.Default.AddShoppingCart,
                        accentColor = PrimaryDarkBlue,
                        accentContainer = Color(0xFFE6EEFA),
                        testTag = "action_new_purchase",
                        onClick = { onNavigate("new_purchase") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        label = "المشتريات",
                        icon = Icons.Default.Receipt,
                        accentColor = PrimaryBlue,
                        accentContainer = Color(0xFFEAF3FF),
                        testTag = "action_purchases",
                        onClick = { onNavigate("purchases") },
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        label = "الإعدادات",
                        icon = Icons.Default.Settings,
                        accentColor = extras.wallet,
                        accentContainer = extras.walletContainer,
                        testTag = "action_settings",
                        onClick = { onNavigate("settings") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 7. Subtle Footer Badge
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Badge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "المستخدم الحالي: ${user.displayName}  •  العمل دون إنترنت (Offline-First)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showAlertsDialog) {
        AlertsDialog(
            alerts = alerts,
            onDismiss = { showAlertsDialog = false },
            onAlertClick = { alert ->
                showAlertsDialog = false
                alert.targetRoute?.let(onNavigate)
            }
        )
    }

    if (showCreditLimitDialog) {
        CreditLimitBreachesDialog(
            breaches = creditLimitBreaches,
            currency = currency,
            onDismiss = { showCreditLimitDialog = false },
            onOpenCustomer = { cid ->
                showCreditLimitDialog = false
                onNavigate("customer_detail/$cid")
            },
            onCollectCustomer = { cid ->
                showCreditLimitDialog = false
                onNavigate("collection?customerId=$cid")
            }
        )
    }

    if (showAccountMenuDialog) {
        AlertDialog(
            onDismissRequest = { showAccountMenuDialog = false },
            title = {
                Text(
                    text = "الحساب الحالي وتسجيل الخروج",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "المستخدم: ${user.displayName} (${user.username})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "المحل: ${tenant.shopName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (user.isTrialAccount) {
                        Text(
                            text = "نوع الجلسة: حساب تجريبي (72 ساعة)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "عند تسجيل الخروج ستعود مباشرةً إلى شاشة تسجيل الدخول للدخول بحساب المدير الرئيسي أو حساب محل آخر أو الحساب التجريبي.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccountMenuDialog = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = extras.danger),
                    modifier = Modifier.testTag("dashboard_confirm_logout_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تسجيل الخروج")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            showAccountMenuDialog = false
                            onNavigate("settings")
                        }
                    ) {
                        Text("الإعدادات")
                    }
                    TextButton(onClick = { showAccountMenuDialog = false }) {
                        Text("إلغاء")
                    }
                }
            }
        )
    }
}

/**
 * Signature curved royal-blue top header banner with golden-amber swooping accent ribbon,
 * greeting avatar, centered Hesabaty brand mark, and quick action icons.
 */
@Composable
private fun CurvedDashboardHeader(
    tenant: TenantEntity,
    user: UserEntity,
    alertsCount: Int,
    hasUrgentAlerts: Boolean,
    isDarkMode: Boolean,
    onOpenAlerts: () -> Unit,
    onToggleDarkMode: () -> Unit,
    onOpenShopSettings: () -> Unit,
    onOpenAccountMenu: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val extras = HesabatyThemeExtras.colors
    val firstName = user.displayName.trim().substringBefore(" ").ifBlank { user.username }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Curved Header Canvas Backdrop
        Canvas(
            modifier = Modifier
                .matchParentSize()
        ) {
            val w = size.width
            val h = size.height

            // Main deep royal blue curved shape
            val headerPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                lineTo(w, h * 0.84f)
                quadraticTo(w * 0.5f, h * 1.04f, 0f, h * 0.84f)
                close()
            }
            drawPath(
                path = headerPath,
                brush = Brush.linearGradient(
                    colors = if (isDarkMode) {
                        listOf(Color(0xFF091B3D), Color(0xFF0E2F6B), Color(0xFF13408F))
                    } else {
                        listOf(Color(0xFF093178), PrimaryDarkBlue, PrimaryBlue)
                    },
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )

            // Soft translucent lighter-blue sweeping highlight on top-right
            val glowPath = Path().apply {
                moveTo(w * 0.58f, 0f)
                lineTo(w, 0f)
                lineTo(w, h * 0.78f)
                cubicTo(w * 0.78f, h * 0.70f, w * 0.62f, h * 0.35f, w * 0.58f, 0f)
                close()
            }
            drawPath(
                path = glowPath,
                color = Color.White.copy(alpha = 0.08f)
            )

            // Swooping golden-amber accent ribbon tracing the bottom curve (signature motif)
            val goldRibbon = Path().apply {
                moveTo(0f, h * 0.80f)
                quadraticTo(w * 0.48f, h * 1.00f, w, h * 0.75f)
                lineTo(w, h * 0.80f)
                quadraticTo(w * 0.48f, h * 1.04f, 0f, h * 0.84f)
                close()
            }
            drawPath(
                path = goldRibbon,
                brush = Brush.horizontalGradient(
                    colors = listOf(AccentGoldWarm, AccentGold, Color(0xFFFDE68A), AccentGoldWarm)
                )
            )
        }

        val uiScale = HesabatyThemeExtras.uiScale
        val headerActionSize = when {
            uiScale.isNarrow -> 32.dp
            uiScale.isCompact -> 34.dp
            else -> 38.dp
        }
        val headerIconSize = if (uiScale.isCompact) 17.dp else 19.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = uiScale.screenPadding,
                    end = uiScale.screenPadding,
                    top = if (uiScale.isCompact) 8.dp else 12.dp,
                    bottom = if (uiScale.isCompact) 20.dp else 28.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Row: Greeting (RTL Start) | Centered Brand Logo | Notifications + Theme (RTL End)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // RTL Start (Right): User Greeting Pill (opens account & logout menu)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .widthIn(max = if (uiScale.isCompact) 66.dp else 96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenAccountMenu)
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .testTag("dashboard_user_profile_button")
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.65f)),
                        modifier = Modifier.size(headerActionSize)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "الحساب وتسجيل الخروج",
                                tint = Color.White,
                                modifier = Modifier.size(headerIconSize)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "أهلاً $firstName",
                        style = if (uiScale.isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Center: Hesabaty Logo + Title
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(if (uiScale.isCompact) 9.dp else 12.dp),
                            color = Color.White,
                            shadowElevation = 4.dp,
                            modifier = Modifier.size(if (uiScale.isCompact) 28.dp else 34.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_hesabaty_brand),
                                contentDescription = "شعار حساباتي",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "حساباتي",
                            style = if (uiScale.isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentGold,
                            maxLines = 1
                        )
                    }
                    if (!uiScale.isNarrow) {
                        Text(
                            text = "H E S A B A T Y",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = if (uiScale.isCompact) 1.2.sp else 2.2.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1
                        )
                    }
                }

                // RTL End (Left): Notifications, Theme Toggle & Quick Logout
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (uiScale.isCompact) 3.dp else 4.dp)
                ) {
                    IconButton(
                        onClick = onOpenAlerts,
                        modifier = Modifier
                            .size(headerActionSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .testTag("dashboard_alerts_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (alertsCount > 0) {
                                    Badge(
                                        containerColor = if (hasUrgentAlerts) extras.danger else AccentGoldWarm,
                                        contentColor = Color.White
                                    ) {
                                        Text("$alertsCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "التنبيهات",
                                tint = Color.White,
                                modifier = Modifier.size(headerIconSize)
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleDarkMode,
                        modifier = Modifier
                            .size(headerActionSize)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .testTag("dashboard_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "تبديل المظهر",
                            tint = Color.White,
                            modifier = Modifier.size(headerIconSize)
                        )
                    }

                    IconButton(
                        onClick = onLogoutClick,
                        modifier = Modifier
                            .size(headerActionSize)
                            .clip(CircleShape)
                            .background(Color(0x33DC2626))
                            .testTag("dashboard_logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "تسجيل الخروج",
                            tint = Color.White,
                            modifier = Modifier.size(headerIconSize)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (uiScale.isCompact) 6.dp else 10.dp))

            // Shop & Date Selector Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenShopSettings)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${tenant.shopName}  •  ${EgyptTime.formatDateArabic(EgyptTime.nowMillis())}",
                    style = if (uiScale.isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Always-visible Live Total Balance Card shown right on app entry with Total Net Balance,
 * Incoming (الوارد), Outgoing (الصادر), and quick receivables/payables summary.
 */
@Composable
private fun LiveTotalBalanceCard(
    summary: DashboardSummary,
    currency: String,
    isDarkMode: Boolean,
    onOpenCustomers: () -> Unit,
    onOpenSuppliers: () -> Unit,
    onOpenReports: () -> Unit
) {
    val extras = HesabatyThemeExtras.colors
    val uiScale = HesabatyThemeExtras.uiScale
    var showTodayOnly by rememberSaveable { mutableStateOf(false) }

    val displayedIncoming = if (showTodayOnly) summary.todayIncomingPiasters else summary.totalIncomingPiasters
    val displayedOutgoing = if (showTodayOnly) summary.todaySupplierPaymentsPiasters else summary.totalOutgoingPiasters
    val displayedNet = if (showTodayOnly) summary.todayNetCashflowPiasters else summary.totalNetBalancePiasters
    val netColor = if (displayedNet >= 0L) extras.success else extras.danger

    Surface(
        shape = RoundedCornerShape(if (uiScale.isCompact) 18.dp else 22.dp),
        color = if (isDarkMode) Color(0xFF132035) else Color.White,
        shadowElevation = if (isDarkMode) 4.dp else 8.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isDarkMode) PrimaryBlue.copy(alpha = 0.35f) else extras.cardBorder.copy(alpha = 0.85f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_total_balance_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = uiScale.cardPadding, vertical = if (uiScale.isCompact) 11.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (uiScale.isCompact) 8.dp else 10.dp)
        ) {
            // Top Row: Title + All-time/Today Mode Toggle + Reports shortcut
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(if (uiScale.isCompact) 32.dp else 36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "الرصيد الإجمالي",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (uiScale.isCompact) 17.dp else 19.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (showTodayOnly) "ملخص حركة اليوم" else "الرصيد الإجمالي",
                            style = if (uiScale.isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "صافي الوارد − الصادر",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Compact All / Today segmented toggle pill
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, extras.cardBorder.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BalancePeriodChip(
                            label = "الإجمالي",
                            selected = !showTodayOnly,
                            onClick = { showTodayOnly = false },
                            modifier = Modifier.testTag("balance_mode_all_chip")
                        )
                        BalancePeriodChip(
                            label = "اليوم",
                            selected = showTodayOnly,
                            onClick = { showTodayOnly = true },
                            modifier = Modifier.testTag("balance_mode_today_chip")
                        )
                    }
                }
            }

            // Primary Net Balance Display
            Surface(
                onClick = onOpenReports,
                shape = RoundedCornerShape(if (uiScale.isCompact) 12.dp else 14.dp),
                color = if (displayedNet >= 0L) {
                    extras.successContainer.copy(alpha = if (isDarkMode) 0.35f else 0.55f)
                } else {
                    extras.dangerContainer.copy(alpha = if (isDarkMode) 0.35f else 0.55f)
                },
                border = BorderStroke(1.dp, netColor.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = if (uiScale.isCompact) 8.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (showTodayOnly) "صافي اليوم" else "الإجمالي",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = MoneyFormatter.formatEgp(displayedNet, currency),
                        style = if (uiScale.isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = netColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("dashboard_total_balance_value")
                    )
                }
            }

            // Side-by-side Incoming (الوارد) and Outgoing (الصادر)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (uiScale.isCompact) 8.dp else 10.dp)
            ) {
                // الوارد (Incoming)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = extras.successContainer.copy(alpha = if (isDarkMode) 0.25f else 0.65f),
                    border = BorderStroke(1.dp, extras.success.copy(alpha = 0.28f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = if (uiScale.isCompact) 8.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "الوارد",
                                tint = extras.success,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showTodayOnly) "وارد اليوم" else "الوارد",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = extras.success,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = MoneyFormatter.formatEgp(displayedIncoming, currency),
                            style = if (uiScale.isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("dashboard_incoming_value")
                        )
                        if (!showTodayOnly) {
                            Text(
                                text = "اليوم: ${MoneyFormatter.formatEgp(summary.todayIncomingPiasters, currency)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // الصادر (Outgoing)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = extras.dangerContainer.copy(alpha = if (isDarkMode) 0.25f else 0.65f),
                    border = BorderStroke(1.dp, extras.danger.copy(alpha = 0.28f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = if (uiScale.isCompact) 8.dp else 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "الصادر",
                                tint = extras.danger,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showTodayOnly) "صادر اليوم" else "الصادر",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = extras.danger,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = MoneyFormatter.formatEgp(displayedOutgoing, currency),
                            style = if (uiScale.isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("dashboard_outgoing_value")
                        )
                        if (!showTodayOnly) {
                            Text(
                                text = "اليوم: ${MoneyFormatter.formatEgp(summary.todaySupplierPaymentsPiasters, currency)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Compact Ledger Breakdown Strip (Receivables vs Payables)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onOpenCustomers,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp)
                ) {
                    Text(
                        text = "لك عند العملاء: ${MoneyFormatter.formatEgp(summary.totalCustomerReceivablesPiasters, currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                    )
                }
                Surface(
                    onClick = onOpenSuppliers,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp)
                ) {
                    Text(
                        text = "عليك للموردين: ${MoneyFormatter.formatEgp(summary.totalSupplierPayablesPiasters, currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = extras.warning,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BalancePeriodChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) PrimaryBlue else Color.Transparent,
        modifier = modifier.heightIn(min = 28.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/**
 * Floating Pill Search Bar matching the clean rounded search input in the reference mockup.
 */
@Composable
private fun DashboardSearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    isDarkMode: Boolean
) {
    val uiScale = HesabatyThemeExtras.uiScale
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isDarkMode) Color(0xFF152238) else Color.White,
        shadowElevation = if (isDarkMode) 4.dp else 8.dp,
        border = BorderStroke(1.dp, HesabatyThemeExtras.colors.cardBorder.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (uiScale.isCompact) 46.dp else 52.dp)
            .testTag("dashboard_search_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (uiScale.isCompact) 14.dp else 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "بحث",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (uiScale.isCompact) 19.dp else 22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "ابحث عن عميل أو مورد أو رقم فاتورة...",
                        style = if (uiScale.isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = if (uiScale.isCompact) 13.sp else 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "مسح البحث",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 3-Slide Hero Carousel Banner + Pill Dot Indicators matching the featured banner in the mockup.
 */
@Composable
private fun DashboardHeroCarousel(
    isDarkMode: Boolean,
    onNavigate: (String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val uiScale = HesabatyThemeExtras.uiScale

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = uiScale.screenPadding),
            pageSpacing = if (uiScale.isCompact) 10.dp else 12.dp,
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) { page ->
            when (page) {
                0 -> HeroBannerSlideOverview(onNavigate = onNavigate, isDarkMode = isDarkMode)
                1 -> HeroBannerSlideAccountsShortcut(onNavigate = onNavigate)
                else -> HeroBannerSlideReportsShortcut(onNavigate = onNavigate)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3-Dot Indicator with stretched active royal-blue pill (exact match to mockup!)
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { idx ->
                val isSelected = pagerState.currentPage == idx
                Box(
                    modifier = Modifier
                        .height(7.dp)
                        .width(if (isSelected) 24.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) PrimaryBlue
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        )
                )
            }
        }
    }
}

@Composable
private fun HeroBannerSlideOverview(
    onNavigate: (String) -> Unit,
    isDarkMode: Boolean
) {
    val uiScale = HesabatyThemeExtras.uiScale
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (uiScale.isCompact) 142.dp else 172.dp)
            .wrapContentHeight()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(if (uiScale.isCompact) 20.dp else 24.dp))
            .clip(RoundedCornerShape(if (uiScale.isCompact) 20.dp else 24.dp))
            .background(
                Brush.horizontalGradient(
                    colors = if (isDarkMode) {
                        listOf(Color(0xFF163D80), Color(0xFF0D2A5E), Color(0xFF091E45))
                    } else {
                        listOf(Color(0xFF3B82F6), PrimaryBlue, PrimaryDarkBlue)
                    }
                )
            )
    ) {
        // Subtle radial light behind 3D accounting hero graphic on left side (RTL end)
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.14f),
                radius = size.height * 0.72f,
                center = Offset(size.width * 0.22f, size.height * 0.45f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (uiScale.isCompact) 12.dp else 16.dp, vertical = if (uiScale.isCompact) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (uiScale.isCompact) 10.dp else 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Right (RTL Start): Headline + Golden curved underline stroke
                Column(
                    modifier = Modifier.weight(1.2f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "كل حسابات محلك",
                        style = if (uiScale.isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "في مكان واحد",
                        style = if (uiScale.isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Canvas(
                        modifier = Modifier
                            .width(if (uiScale.isCompact) 86.dp else 105.dp)
                            .height(8.dp)
                    ) {
                        val curve = Path().apply {
                            moveTo(0f, size.height * 0.8f)
                            quadraticTo(size.width * 0.5f, 0f, size.width, size.height * 0.55f)
                        }
                        drawPath(
                            path = curve,
                            color = AccentGold,
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Left (RTL End): 3D Accounting Illustration
                Image(
                    painter = painterResource(id = R.drawable.img_login_hero),
                    contentDescription = "نظام حساباتي المحاسبي",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(0.85f)
                        .height(if (uiScale.isCompact) 76.dp else 102.dp)
                )
            }

            // Bottom 3 Crisp White Quick-Launch Pill Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (uiScale.isCompact) 6.dp else 8.dp)
            ) {
                BannerActionChip(
                    label = "بيع جديد",
                    icon = Icons.Default.AddShoppingCart,
                    onClick = { onNavigate("new_sale") },
                    modifier = Modifier.weight(1f)
                )
                BannerActionChip(
                    label = "تحصيل",
                    icon = Icons.Default.Savings,
                    onClick = { onNavigate("collection") },
                    modifier = Modifier.weight(1f)
                )
                BannerActionChip(
                    label = "سحب يومي",
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    onClick = { onNavigate("daily_withdrawals") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroBannerSlideAccountsShortcut(
    onNavigate: (String) -> Unit
) {
    val uiScale = HesabatyThemeExtras.uiScale
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (uiScale.isCompact) 142.dp else 172.dp)
            .wrapContentHeight()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(if (uiScale.isCompact) 20.dp else 24.dp))
            .clip(RoundedCornerShape(if (uiScale.isCompact) 20.dp else 24.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF065F46), Color(0xFF047857), Color(0xFF0D9488))
                )
            )
            .padding(if (uiScale.isCompact) 12.dp else 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (uiScale.isCompact) 10.dp else 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "إدارة العملاء والموردين",
                        style = if (uiScale.isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "فواتير احترافية وكشوف حساب مصورة ترسل عبر واتساب بضغطة واحدة",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.size(if (uiScale.isCompact) 38.dp else 46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(if (uiScale.isCompact) 20.dp else 24.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (uiScale.isCompact) 6.dp else 8.dp)
            ) {
                BannerActionChip(
                    label = "العملاء",
                    icon = Icons.Default.Groups,
                    onClick = { onNavigate("customers") },
                    modifier = Modifier.weight(1f)
                )
                BannerActionChip(
                    label = "الموردون",
                    icon = Icons.Default.LocalShipping,
                    onClick = { onNavigate("suppliers") },
                    modifier = Modifier.weight(1f)
                )
                BannerActionChip(
                    label = "الأصناف",
                    icon = Icons.Default.Inventory2,
                    onClick = { onNavigate("products") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroBannerSlideReportsShortcut(
    onNavigate: (String) -> Unit
) {
    val uiScale = HesabatyThemeExtras.uiScale
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (uiScale.isCompact) 142.dp else 172.dp)
            .wrapContentHeight()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(if (uiScale.isCompact) 20.dp else 24.dp))
            .clip(RoundedCornerShape(if (uiScale.isCompact) 20.dp else 24.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF1E3A8A), Color(0xFF1D4ED8), Color(0xFF2563EB))
                )
            )
            .padding(if (uiScale.isCompact) 12.dp else 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (uiScale.isCompact) 10.dp else 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "التقارير والنسخ الاحتياطي",
                        style = if (uiScale.isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ملخص حسابات المحل و${ReportType.entries.size} تقريرًا ماليًا في قسم مستقل وآمن تمامًا",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.size(if (uiScale.isCompact) 38.dp else 46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(if (uiScale.isCompact) 20.dp else 24.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (uiScale.isCompact) 6.dp else 8.dp)
            ) {
                BannerActionChip(
                    label = "قسم التقارير",
                    icon = Icons.Default.Assessment,
                    onClick = { onNavigate("reports") },
                    modifier = Modifier.weight(1f)
                )
                BannerActionChip(
                    label = "نسخ احتياطي",
                    icon = Icons.Default.Backup,
                    onClick = { onNavigate("backup") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BannerActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiScale = HesabatyThemeExtras.uiScale
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = modifier.heightIn(min = 34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (uiScale.isCompact) 5.dp else 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryDarkBlue,
                modifier = Modifier.size(if (uiScale.isCompact) 13.dp else 15.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = PrimaryDarkBlue,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CreditLimitBreachAlertCard(
    breaches: List<CreditLimitBreach>,
    currency: String,
    onOpenCustomer: (String) -> Unit,
    onCollectCustomer: (String) -> Unit,
    onViewAll: () -> Unit
) {
    val extras = HesabatyThemeExtras.colors
    val totalExcess = remember(breaches) { breaches.sumOf { it.excessPiasters } }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = extras.dangerContainer,
        border = BorderStroke(1.2.dp, extras.danger.copy(alpha = 0.45f)),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_credit_limit_alert_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = extras.danger.copy(alpha = 0.16f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "تنبيه تجاوز حد المديونية",
                            tint = extras.danger,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "تنبيه: عملاء تجاوزوا حد المديونية (${breaches.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = extras.danger
                    )
                    Text(
                        text = "إجمالي التجاوز عن الحد المسموح: ${MoneyFormatter.formatEgp(totalExcess, currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            breaches.take(3).forEach { breach ->
                CreditLimitBreachRow(
                    breach = breach,
                    currency = currency,
                    onOpenCustomer = { onOpenCustomer(breach.customer.id) },
                    onCollectCustomer = { onCollectCustomer(breach.customer.id) }
                )
            }

            if (breaches.size > 3) {
                TextButton(
                    onClick = onViewAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_credit_limit_view_all_btn")
                ) {
                    Text(
                        text = "عرض جميع العملاء المتجاوزين (${breaches.size})",
                        fontWeight = FontWeight.Bold,
                        color = extras.danger
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditLimitBreachRow(
    breach: CreditLimitBreach,
    currency: String,
    onOpenCustomer: () -> Unit,
    onCollectCustomer: () -> Unit
) {
    val extras = HesabatyThemeExtras.colors
    Surface(
        onClick = onOpenCustomer,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extras.danger.copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("credit_limit_breach_row_${breach.customer.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = breach.customer.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "الرصيد: ${MoneyFormatter.formatEgp(breach.customer.currentBalancePiasters, currency)}  •  الحد: ${MoneyFormatter.formatEgp(breach.effectiveLimitPiasters, currency)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "تجاوز بمبلغ +${MoneyFormatter.formatEgp(breach.excessPiasters, currency)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = extras.danger
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCollectCustomer,
                colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .testTag("credit_limit_collect_${breach.customer.id}")
            ) {
                Text(
                    text = "تحصيل",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CreditLimitBreachesDialog(
    breaches: List<CreditLimitBreach>,
    currency: String,
    onDismiss: () -> Unit,
    onOpenCustomer: (String) -> Unit,
    onCollectCustomer: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "عملاء تجاوزوا حد المديونية (${breaches.size})",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                breaches.forEach { breach ->
                    CreditLimitBreachRow(
                        breach = breach,
                        currency = currency,
                        onOpenCustomer = { onOpenCustomer(breach.customer.id) },
                        onCollectCustomer = { onCollectCustomer(breach.customer.id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
private fun AlertsDialog(
    alerts: List<AppAlert>,
    onDismiss: () -> Unit,
    onAlertClick: (AppAlert) -> Unit
) {
    val extras = HesabatyThemeExtras.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("التنبيهات داخل التطبيق (${alerts.size})", fontWeight = FontWeight.Bold) },
        text = {
            if (alerts.isEmpty()) {
                Text("لا توجد تنبيهات حالية. جميع الحسابات منتظمة.")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    alerts.take(20).forEach { alert ->
                        val bg = when (alert.severity) {
                            AlertSeverity.DANGER -> extras.dangerContainer
                            AlertSeverity.WARNING -> extras.warningContainer
                            AlertSeverity.SUCCESS -> extras.successContainer
                            AlertSeverity.INFO -> MaterialTheme.colorScheme.primaryContainer
                        }
                        val dotColor = when (alert.severity) {
                            AlertSeverity.DANGER -> extras.danger
                            AlertSeverity.WARNING -> extras.warning
                            AlertSeverity.SUCCESS -> extras.success
                            AlertSeverity.INFO -> MaterialTheme.colorScheme.primary
                        }
                        Surface(
                            onClick = { onAlertClick(alert) },
                            shape = MaterialTheme.shapes.small,
                            color = bg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(alert.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(alert.message, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}
