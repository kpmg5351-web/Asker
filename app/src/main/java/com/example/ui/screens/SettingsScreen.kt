package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.core.EgyptTime
import com.example.core.MoneyFormatter
import com.example.core.PhoneUtils
import com.example.core.WhatsAppHelper
import com.example.data.local.AuditLogEntity
import com.example.data.local.TenantEntity
import com.example.data.local.UserEntity
import com.example.data.local.UserRoles
import com.example.ui.components.HesabatyCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryDarkBlue
import com.example.ui.viewmodel.HesabatyViewModel

private val SettingsGold = Color(0xFFFBBF24)
private val SettingsGoldWarm = Color(0xFFF59E0B)

@Composable
fun SettingsScreen(
    tenant: TenantEntity,
    user: UserEntity,
    viewModel: HesabatyViewModel,
    onBack: () -> Unit,
    onOpenBackup: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val auditLogs by viewModel.tenantAuditLogs.collectAsStateWithLifecycle()
    val extras = HesabatyThemeExtras.colors

    val currencyLabel = settings?.defaultCurrencyLabel?.ifBlank { "ج.م" } ?: "ج.م"
    val waCountryCode = settings?.whatsappCountryCode?.ifBlank { "20" } ?: "20"
    val signatureName = settings?.invoiceSignatureName.orEmpty()
    val defaultCreditLimitPiasters = settings?.defaultCustomerCreditLimitPiasters ?: 0L

    var showEditShopDialog by remember { mutableStateOf(false) }
    var showEditInvoiceDialog by remember { mutableStateOf(false) }
    var showCreditLimitDialog by remember { mutableStateOf(false) }
    var showChangePass by remember { mutableStateOf(false) }
    var showAuditDialog by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker for Shop Logo (Google Play Policy compliant)
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.updateTenant(
                tenant = tenant,
                shopName = tenant.shopName,
                ownerName = tenant.ownerName,
                address = tenant.address,
                phone = tenant.phone,
                whatsappPhone = tenant.whatsappPhone,
                invoiceFooter = tenant.invoiceFooter,
                logoUri = uri.toString()
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Edge-to-edge Royal Blue Hero Header with Shop Identity
        item {
            SettingsHeroHeader(
                tenant = tenant,
                user = user,
                onBack = onBack,
                onLogout = { viewModel.logout() },
                onPickLogo = {
                    logoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onEditShop = { showEditShopDialog = true }
            )
        }

        // 2. Quick Settings Snapshot Strip
        item {
            SettingsCenteredBox {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsSnapshotTile(
                        label = "العملة",
                        value = currencyLabel,
                        accent = PrimaryBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { showEditInvoiceDialog = true }
                    )
                    SettingsSnapshotTile(
                        label = "حد المديونية",
                        value = if (defaultCreditLimitPiasters > 0L) {
                            MoneyFormatter.formatEgp(defaultCreditLimitPiasters, currencyLabel)
                        } else {
                            "غير مفعّل"
                        },
                        accent = if (defaultCreditLimitPiasters > 0L) extras.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        onClick = { showCreditLimitDialog = true }
                    )
                    SettingsSnapshotTile(
                        label = "سجل المراجعة",
                        value = "${auditLogs.size} عملية",
                        accent = extras.wallet,
                        modifier = Modifier.weight(1f),
                        onClick = { showAuditDialog = true }
                    )
                }
            }
        }

        // 3. Shop & Invoice Preferences Group
        item {
            SettingsCenteredBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryTitle("إعدادات المحل والفواتير")
                    SettingsGroupCard {
                        val shopSummary = remember(tenant) {
                            listOfNotNull(
                                tenant.address.takeIf { it.isNotBlank() },
                                tenant.whatsappPhone.takeIf { it.isNotBlank() }?.let { "واتساب: $it" }
                            ).joinToString(" • ").ifBlank { "الاسم، المالك، العنوان، الهواتف وتذييل الفاتورة" }
                        }
                        SettingsNavigationRow(
                            icon = Icons.Default.Store,
                            iconTint = PrimaryBlue,
                            iconBg = MaterialTheme.colorScheme.primaryContainer,
                            title = "بيانات المحل والعنوان",
                            subtitle = shopSummary,
                            onClick = { showEditShopDialog = true }
                        )
                        SettingsRowDivider()
                        SettingsNavigationRow(
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            iconTint = extras.wallet,
                            iconBg = extras.walletContainer,
                            title = "الفواتير والعملة و WhatsApp",
                            subtitle = buildString {
                                append("العملة: $currencyLabel • كود: +$waCountryCode")
                                if (signatureName.isNotBlank()) append(" • التوقيع: $signatureName")
                            },
                            onClick = { showEditInvoiceDialog = true }
                        )
                        SettingsRowDivider()
                        SettingsNavigationRow(
                            icon = Icons.Default.Warning,
                            iconTint = if (defaultCreditLimitPiasters > 0L) extras.warning else extras.danger,
                            iconBg = if (defaultCreditLimitPiasters > 0L) extras.warningContainer else extras.dangerContainer,
                            title = "تنبيهات حد المديونية للعملاء",
                            subtitle = if (defaultCreditLimitPiasters > 0L) {
                                "الحد العام: ${MoneyFormatter.formatEgp(defaultCreditLimitPiasters, currencyLabel)}"
                            } else {
                                "متوقف — يعتمد على الحد الخاص لكل عميل فقط"
                            },
                            badgeText = if (defaultCreditLimitPiasters > 0L) "مفعّل" else "متوقف",
                            badgeColor = if (defaultCreditLimitPiasters > 0L) extras.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                            badgeBg = if (defaultCreditLimitPiasters > 0L) extras.warningContainer else MaterialTheme.colorScheme.surfaceVariant,
                            onClick = { showCreditLimitDialog = true }
                        )
                    }
                }
            }
        }

        // 4. Appearance, Security & Backup Group
        item {
            SettingsCenteredBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryTitle("المظهر والأمان والبيانات")
                    SettingsGroupCard {
                        SettingsSwitchRow(
                            icon = Icons.Default.DarkMode,
                            iconTint = PrimaryBlue,
                            iconBg = MaterialTheme.colorScheme.primaryContainer,
                            title = "الوضع الغامق (Dark Mode)",
                            subtitle = if (isDark) "الوضع الغامق مفعّل حاليًا" else "الوضع الفاتح هو الافتراضي",
                            checked = isDark,
                            onCheckedChange = { viewModel.setDarkMode(it) },
                            switchTestTag = "settings_dark_mode_switch"
                        )
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            icon = Icons.Default.Fingerprint,
                            iconTint = extras.success,
                            iconBg = extras.successContainer,
                            title = "قفل التطبيق بالبصمة",
                            subtitle = "طلب التحقق بالبصمة عند فتح التطبيق",
                            checked = settings?.biometricLockEnabled ?: false,
                            onCheckedChange = { enabled ->
                                viewModel.updateAppSettings { it.copy(biometricLockEnabled = enabled) }
                            }
                        )
                        SettingsRowDivider()
                        SettingsNavigationRow(
                            icon = Icons.Default.Key,
                            iconTint = extras.warning,
                            iconBg = extras.warningContainer,
                            title = "تغيير كلمة المرور",
                            subtitle = "تحديث كلمة مرور حساب (${user.username})",
                            onClick = { showChangePass = true }
                        )
                        SettingsRowDivider()
                        SettingsNavigationRow(
                            icon = Icons.Default.Backup,
                            iconTint = extras.wallet,
                            iconBg = extras.walletContainer,
                            title = "النسخ الاحتياطي و Google Drive",
                            subtitle = "تصدير واستيراد نسخة احتياطية كاملة",
                            onClick = onOpenBackup
                        )
                        SettingsRowDivider()
                        SettingsNavigationRow(
                            icon = Icons.Default.History,
                            iconTint = PrimaryBlue,
                            iconBg = MaterialTheme.colorScheme.primaryContainer,
                            title = "سجل المراجعة المالية",
                            subtitle = "تتبع جميع التعديلات والعمليات الحساسة",
                            badgeText = "${auditLogs.size}",
                            badgeColor = PrimaryBlue,
                            badgeBg = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { showAuditDialog = true }
                        )
                    }
                }
            }
        }

        // 6. Support & About Branded Card
        item {
            SettingsCenteredBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCategoryTitle("الدعم الفني ومعلومات التطبيق")
                    HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                shadowElevation = 2.dp,
                                border = BorderStroke(1.dp, extras.cardBorder),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_hesabaty_brand),
                                    contentDescription = "شعار حساباتي",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "حساباتي | HESABATY",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "تطوير: تأسيس لصناعة البرمجيات",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(
                                label = "v${BuildConfig.VERSION_NAME}",
                                textColor = MaterialTheme.colorScheme.primary,
                                backgroundColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "نحوّل أفكار الأعمال إلى أنظمة متكاملة.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "رقم الدعم الفني: ${PhoneUtils.SUPPORT_PHONE_LOCAL} • بناء #${BuildConfig.VERSION_CODE}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val msg = WhatsAppHelper.buildSupportMessage(BuildConfig.VERSION_NAME, tenant.shopName)
                                val res = WhatsAppHelper.openWhatsAppChat(
                                    context = context,
                                    rawPhone = PhoneUtils.SUPPORT_PHONE_LOCAL,
                                    message = msg,
                                    countryCode = "20"
                                )
                                if (!res.launched && res.errorMessage != null) viewModel.emitMessage(res.errorMessage, true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("settings_support_whatsapp_btn")
                        ) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تواصل مع الدعم الفني عبر WhatsApp", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 7. Clean Logout Action
        item {
            SettingsCenteredBox {
                OutlinedButton(
                    onClick = { viewModel.logout() },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, extras.danger.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = extras.dangerContainer.copy(alpha = 0.45f),
                        contentColor = extras.danger
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("settings_logout_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تسجيل الخروج من الحساب", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // --- Focused Scrollable Edit Dialogs ---

    if (showEditShopDialog) {
        EditShopProfileDialog(
            tenant = tenant,
            onDismiss = { showEditShopDialog = false },
            onSave = { shopName, ownerName, address, phone, waPhone, invoiceFooter ->
                viewModel.updateTenant(
                    tenant = tenant,
                    shopName = shopName,
                    ownerName = ownerName,
                    address = address,
                    phone = phone,
                    whatsappPhone = waPhone,
                    invoiceFooter = invoiceFooter,
                    logoUri = tenant.logoUri
                )
                showEditShopDialog = false
            }
        )
    }

    if (showEditInvoiceDialog) {
        EditInvoiceSettingsDialog(
            initialSignature = signatureName,
            initialCurrency = currencyLabel,
            initialCountryCode = waCountryCode,
            onDismiss = { showEditInvoiceDialog = false },
            onSave = { sig, curr, code ->
                viewModel.updateAppSettings {
                    it.copy(
                        invoiceSignatureName = sig.trim(),
                        defaultCurrencyLabel = curr.trim().ifBlank { "ج.م" },
                        whatsappCountryCode = code.filter { ch -> ch.isDigit() }.ifBlank { "20" }
                    )
                }
                showEditInvoiceDialog = false
            }
        )
    }

    if (showCreditLimitDialog) {
        EditDefaultCreditLimitDialog(
            initialLimitPiasters = defaultCreditLimitPiasters,
            currencyLabel = currencyLabel,
            onDismiss = { showCreditLimitDialog = false },
            onSave = { parsedPiasters ->
                viewModel.updateAppSettings {
                    it.copy(defaultCustomerCreditLimitPiasters = parsedPiasters)
                }
                showCreditLimitDialog = false
            }
        )
    }

    if (showChangePass) {
        ChangeOwnPasswordDialog(
            onDismiss = { showChangePass = false },
            onConfirm = { curr, newP, conf, onErr ->
                viewModel.changeOwnPassword(curr, newP, conf) { ok, msg ->
                    if (ok) showChangePass = false else onErr(msg)
                }
            }
        )
    }

    if (showAuditDialog) {
        AuditLogDialog(
            auditLogs = auditLogs,
            onDismiss = { showAuditDialog = false }
        )
    }
}

@Composable
private fun SettingsHeroHeader(
    tenant: TenantEntity,
    user: UserEntity,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onPickLogo: () -> Unit,
    onEditShop: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(PrimaryDarkBlue, PrimaryBlue, Color(0xFF1E5BE0))
                )
            )
    ) {
        // Subtle decorative curves & signature gold ribbon matching DashboardScreen
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = w * 0.30f,
                center = Offset(w * 0.90f, h * 0.14f)
            )
            drawCircle(
                color = SettingsGold.copy(alpha = 0.08f),
                radius = w * 0.22f,
                center = Offset(w * 0.08f, h * 0.78f)
            )
            val goldRibbon = Path().apply {
                moveTo(0f, h - 6f)
                quadraticTo(w * 0.5f, h, w, h - 6f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = goldRibbon,
                brush = Brush.horizontalGradient(
                    colors = listOf(SettingsGoldWarm, SettingsGold, Color(0xFFFDE68A), SettingsGoldWarm)
                )
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Row: Back + Title + Quick Logout Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "الإعدادات",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Surface(
                        onClick = onLogout,
                        shape = RoundedCornerShape(50),
                        color = Color(0x38DC2626),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("settings_topbar_logout_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "تسجيل الخروج",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "خروج",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Shop Profile Identity Card inside Hero
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.13f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Shop Logo Avatar with Camera Badge
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable(onClick = onPickLogo)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color.White.copy(alpha = 0.94f),
                                    border = BorderStroke(2.dp, SettingsGold),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    if (!tenant.logoUri.isNullOrBlank()) {
                                        AsyncImage(
                                            model = tenant.logoUri,
                                            contentDescription = "شعار المحل",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Store,
                                                contentDescription = "شعار المحل",
                                                tint = PrimaryDarkBlue,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = SettingsGold,
                                    border = BorderStroke(1.5.dp, PrimaryDarkBlue),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.BottomEnd)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "تغيير شعار المحل",
                                            tint = PrimaryDarkBlue,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = tenant.shopName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        append(tenant.ownerName)
                                        if (tenant.phone.isNotBlank()) append(" • ${tenant.phone}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.86f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = Color.Black.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = "${user.displayName} • ${if (user.role == UserRoles.SHOP_OWNER) "صاحب المحل" else "مستخدم"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SettingsGold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = onEditShop,
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.94f),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = PrimaryDarkBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "تعديل بيانات المحل",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryDarkBlue,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                onClick = onPickLogo,
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (tenant.logoUri.isNullOrBlank()) "إضافة شعار" else "تغيير الشعار",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsCategoryTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, HesabatyThemeExtras.colors.cardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsIconBadge(
    icon: ImageVector,
    tint: Color,
    background: Color
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        color = HesabatyThemeExtras.colors.cardBorder.copy(alpha = 0.7f)
    )
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    badgeBg: Color = MaterialTheme.colorScheme.primaryContainer,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsIconBadge(icon = icon, tint = iconTint, background = iconBg)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (badgeText != null) {
            StatusBadge(
                label = badgeText,
                textColor = badgeColor,
                backgroundColor = badgeBg
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsIconBadge(icon = icon, tint = iconTint, background = iconBg)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (switchTestTag != null) Modifier.testTag(switchTestTag) else Modifier
        )
    }
}

@Composable
private fun SettingsSnapshotTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, HesabatyThemeExtras.colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EditShopProfileDialog(
    tenant: TenantEntity,
    onDismiss: () -> Unit,
    onSave: (
        shopName: String,
        ownerName: String,
        address: String,
        phone: String,
        waPhone: String,
        invoiceFooter: String
    ) -> Unit
) {
    var shopName by remember(tenant) { mutableStateOf(tenant.shopName) }
    var ownerName by remember(tenant) { mutableStateOf(tenant.ownerName) }
    var address by remember(tenant) { mutableStateOf(tenant.address) }
    var phone by remember(tenant) { mutableStateOf(tenant.phone) }
    var waPhone by remember(tenant) { mutableStateOf(tenant.whatsappPhone) }
    var invoiceFooter by remember(tenant) { mutableStateOf(tenant.invoiceFooter) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بيانات المحل", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = { Text("اسم المحل") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = { Text("اسم صاحب المحل") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("العنوان") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = waPhone,
                    onValueChange = { waPhone = it },
                    label = { Text("رقم WhatsApp") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = invoiceFooter,
                    onValueChange = { invoiceFooter = it },
                    label = { Text("ملاحظة / تذييل الفاتورة (اختياري)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(shopName, ownerName, address, phone, waPhone, invoiceFooter) }) {
                Text("حفظ البيانات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun EditInvoiceSettingsDialog(
    initialSignature: String,
    initialCurrency: String,
    initialCountryCode: String,
    onDismiss: () -> Unit,
    onSave: (signature: String, currency: String, countryCode: String) -> Unit
) {
    var signatureName by remember { mutableStateOf(initialSignature) }
    var currencyLabel by remember { mutableStateOf(initialCurrency) }
    var waCountryCode by remember { mutableStateOf(initialCountryCode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات الفواتير والعملة", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = signatureName,
                    onValueChange = { signatureName = it },
                    label = { Text("اسم المسؤول / التوقيع أسفل الفاتورة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currencyLabel,
                    onValueChange = { currencyLabel = it },
                    label = { Text("رمز العملة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = waCountryCode,
                    onValueChange = { waCountryCode = it },
                    label = { Text("كود دولة WhatsApp (بدون +)") },
                    supportingText = { Text("مثال: 20 لمصر، 966 للسعودية") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(signatureName, currencyLabel, waCountryCode) }) {
                Text("حفظ الإعدادات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun EditDefaultCreditLimitDialog(
    initialLimitPiasters: Long,
    currencyLabel: String,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var limitText by remember(initialLimitPiasters) {
        mutableStateOf(
            initialLimitPiasters
                .takeIf { it > 0L }
                ?.let { MoneyFormatter.formatEgpPlainInput(it) }
                .orEmpty()
        )
    }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنبيهات حد المديونية للعملاء", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "حدّد سقفًا افتراضيًا عامًا لمديونية أي عميل ليظهر تنبيه تلقائي في القائمة الرئيسية عند تجاوزه. يمكنك أيضًا تحديد حد خاص لكل عميل من صفحته.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it; err = null },
                    label = { Text("الحد العام لمديونية العملاء ($currencyLabel)") },
                    supportingText = { Text("اتركه فارغًا أو 0 لإيقاف الحد العام والاكتفاء بالحد الخاص لكل عميل") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_default_credit_limit_input")
                )
                err?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (initialLimitPiasters > 0L) {
                    TextButton(
                        onClick = { onSave(0L) },
                        colors = ButtonDefaults.textButtonColors(contentColor = HesabatyThemeExtras.colors.danger)
                    ) {
                        Text("إيقاف الحد العام")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = if (limitText.isBlank()) 0L else {
                        MoneyFormatter.parseEgpToPiasters(limitText) ?: run {
                            err = "أدخل مبلغًا صحيحًا لحد المديونية"
                            return@Button
                        }
                    }
                    onSave(parsed)
                },
                modifier = Modifier.testTag("settings_save_credit_limit_btn")
            ) {
                Text("حفظ الحد العام")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun AuditLogDialog(
    auditLogs: List<AuditLogEntity>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سجل المراجعة (${auditLogs.size})", fontWeight = FontWeight.Bold) },
        text = {
            if (auditLogs.isEmpty()) {
                Text(
                    text = "لا توجد عمليات مسجلة في سجل المراجعة حتى الآن.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(auditLogs.take(40), key = { it.id }) { log ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${log.action} — ${log.actorUsername}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(log.details, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = EgyptTime.formatDateTimeSeconds(log.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
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
