package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.EgyptTime
import com.example.data.local.AccountStatuses
import com.example.data.local.TenantEntity
import com.example.data.local.UserEntity
import com.example.data.repository.AuthRepository
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.StatusBadge
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel

@Composable
fun AdminPanelScreen(
    adminUser: UserEntity,
    viewModel: HesabatyViewModel
) {
    val tenants by viewModel.allTenants.collectAsStateWithLifecycle()
    val shopUsers by viewModel.allShopUsers.collectAsStateWithLifecycle()
    val backups by viewModel.allBackupHistory.collectAsStateWithLifecycle()
    val auditLogs by viewModel.allAuditLogs.collectAsStateWithLifecycle()

    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showCreateShopDialog by remember { mutableStateOf(false) }
    var showRemoteTokenDialog by remember { mutableStateOf(false) }
    var createdAccountHandoff by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var editingTenant by remember { mutableStateOf<TenantEntity?>(null) }
    var resetPasswordUser by remember { mutableStateOf<UserEntity?>(null) }
    var showChangeAdminPass by remember { mutableStateOf(false) }

    val usersByTenant = remember(shopUsers) { shopUsers.groupBy { it.tenantId } }
    val trialUser = remember(shopUsers) { shopUsers.firstOrNull { it.isTrialAccount } }
    val commercialTenants = remember(tenants) { tenants.filter { it.id != AuthRepository.TRIAL_TENANT_ID } }
    val latestBackupByTenant = remember(backups) { backups.groupBy { it.tenantId }.mapValues { it.value.firstOrNull() } }
    val extras = HesabatyThemeExtras.colors

    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = "لوحة المدير الرئيسي",
                subtitle = "${adminUser.displayName} (${adminUser.username})",
                actions = {
                    IconButton(
                        onClick = { showChangeAdminPass = true },
                        modifier = Modifier.testTag("admin_change_password_btn")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = "تغيير كلمة مرور المدير")
                    }
                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.testTag("admin_logout_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "تسجيل الخروج")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("المحلات والتراخيص (${commercialTenants.size})") },
                    icon = { Icon(Icons.Default.Store, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("النسخ الاحتياطي") },
                    icon = { Icon(Icons.Default.CloudDone, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("سجل الإدارة") },
                    icon = { Icon(Icons.Default.History, contentDescription = null) }
                )
            }

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Current Device Identity Banner
                        item {
                            HesabatyCard(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "الجهاز الحالي: ${viewModel.currentDeviceLabel}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "كود الجهاز: ${viewModel.currentDeviceActivationCode} • نظام Device Binding نشط",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Create New Shop Account Button (on THIS phone or remote phone)
                        item {
                            Button(
                                onClick = { showCreateShopDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("admin_create_shop_button")
                            ) {
                                Icon(Icons.Default.AddBusiness, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("إنشاء حساب محل وربطه بهذا الهاتف", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { showRemoteTokenDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("admin_generate_remote_token_button")
                            ) {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("توليد كود تفعيل لهاتف عميل آخر (عن بُعد)", fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.logout() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("admin_inline_logout_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    tint = extras.danger,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "تسجيل خروج الإدارة والعودة لشاشة الدخول",
                                    color = extras.danger,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Trial Account License Inspection & Control Card
                        if (trialUser != null) {
                            item {
                                val (statusTextColor, statusBgColor) = accountStatusColors(trialUser.accountStatus, extras)
                                HesabatyCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_trial_account_card")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "الحساب التجريبي المدمج (${trialUser.username})",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "بيئة تجربة معزولة — مدة 3 أيام (72 ساعة)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusBadge(
                                            label = AccountStatuses.arabicLabel(trialUser.accountStatus),
                                            textColor = statusTextColor,
                                            backgroundColor = statusBgColor
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "تاريخ الإنشاء: ${EgyptTime.formatDateTime(trialUser.createdAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (trialUser.trialStartedAt != null && trialUser.trialExpiresAt != null) {
                                        Text(
                                            text = "بداية التجربة: ${EgyptTime.formatDateTime(trialUser.trialStartedAt)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "نهاية التجربة: ${EgyptTime.formatDateTime(trialUser.trialExpiresAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        Text(
                                            text = "لم يتم تفعيل الفترة التجريبية بعد (يبدأ العد التنازلي عند أول تشغيل)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    if (trialUser.clockTampered) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "⚠ تم رصد تلاعب في وقت الجهاز وإيقاف التجربة تلقائيًا",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extras.danger,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (!trialUser.boundDeviceFingerprint.isNullOrBlank()) {
                                            "الجهاز المرتبط: ${trialUser.boundDeviceLabel ?: "جهاز مسجل"} (${trialUser.boundDeviceFingerprint.take(10).uppercase()})"
                                        } else {
                                            "الجهاز المرتبط: غير مرتبط بجهاز حاليًا"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.reactivateOrResetTrial(trialUser.id) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("admin_reset_trial_button")
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("إعادة تفعيل 72 ساعة", maxLines = 1)
                                        }
                                        if (trialUser.accountStatus == AccountStatuses.SUSPENDED) {
                                            OutlinedButton(
                                                onClick = { viewModel.setUserAccountStatus(trialUser.id, AccountStatuses.TRIAL) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("تفعيل")
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = { viewModel.setUserAccountStatus(trialUser.id, AccountStatuses.SUSPENDED) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("إيقاف التجربة")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (commercialTenants.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = Icons.Default.AdminPanelSettings,
                                    title = "لا توجد محلات تجارية مسجلة بعد",
                                    subtitle = "ابدأ بإنشاء حساب محل جديد لصاحب المحل وربطه بجهازه بصورة آمنة."
                                )
                            }
                        } else {
                            items(commercialTenants, key = { it.id }) { tenant ->
                                val ownerUser = usersByTenant[tenant.id]?.firstOrNull()
                                val lastBackup = latestBackupByTenant[tenant.id]
                                val effectiveStatus = ownerUser?.accountStatus
                                    ?: if (tenant.isActive) AccountStatuses.ACTIVE else AccountStatuses.SUSPENDED
                                val (statusTextColor, statusBgColor) = accountStatusColors(effectiveStatus, extras)

                                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tenant.shopName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "صاحب المحل: ${tenant.ownerName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusBadge(
                                            label = AccountStatuses.arabicLabel(effectiveStatus),
                                            textColor = statusTextColor,
                                            backgroundColor = statusBgColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "الهاتف: ${tenant.phone}  |  العنوان: ${tenant.address}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "تاريخ الإنشاء: ${EgyptTime.formatDateTime(tenant.createdAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (ownerUser != null) {
                                        Text(
                                            text = "اسم المستخدم: ${ownerUser.username}  |  معرف المحل: ${tenant.id.take(15)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = if (!ownerUser.boundDeviceFingerprint.isNullOrBlank()) {
                                                "الجهاز المرتبط: ${ownerUser.boundDeviceLabel ?: "جهاز مسجل"} (${ownerUser.boundDeviceFingerprint.take(10).uppercase()})"
                                            } else {
                                                "الجهاز المرتبط: غير مرتبط حاليًا (سيرتبط بأول جهاز يسجل الدخول)"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (!ownerUser.boundDeviceFingerprint.isNullOrBlank()) extras.success else extras.warning,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (lastBackup != null) {
                                        Text(
                                            text = "آخر نسخة: ${EgyptTime.formatDateTime(lastBackup.createdAt)} (${if (lastBackup.status == "SUCCESS") "ناجحة" else "فشلت"})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Row 1: Edit, Reset Password, Enable/Disable switch
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { editingTenant = tenant },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("تعديل")
                                        }
                                        if (ownerUser != null) {
                                            OutlinedButton(
                                                onClick = { resetPasswordUser = ownerUser },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("كلمة السر")
                                            }
                                        }
                                        Switch(
                                            checked = tenant.isActive,
                                            onCheckedChange = { viewModel.toggleTenantActive(tenant.id, it) }
                                        )
                                    }

                                    // Row 2: Device Binding & Reactivate/Suspend Controls
                                    if (ownerUser != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (!ownerUser.boundDeviceFingerprint.isNullOrBlank()) {
                                                OutlinedButton(
                                                    onClick = { viewModel.unbindUserDevice(ownerUser.id) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("admin_unbind_device_${ownerUser.username}")
                                                ) {
                                                    Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("فك ارتباط الجهاز", maxLines = 1)
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = { viewModel.bindUserToCurrentDevice(ownerUser.id) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("admin_bind_device_${ownerUser.username}")
                                                ) {
                                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("ربط بهذا الجهاز", maxLines = 1)
                                                }
                                            }

                                            if (ownerUser.accountStatus != AccountStatuses.ACTIVE || !ownerUser.isActive) {
                                                OutlinedButton(
                                                    onClick = { viewModel.setUserAccountStatus(ownerUser.id, AccountStatuses.ACTIVE) },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("إعادة التفعيل", maxLines = 1)
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = { viewModel.setUserAccountStatus(ownerUser.id, AccountStatuses.SUSPENDED) },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("إيقاف الحساب", maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (backups.isEmpty()) {
                            item {
                                EmptyStateView(
                                    icon = Icons.Default.CloudDone,
                                    title = "لا توجد عمليات نسخ احتياطي بعد",
                                    subtitle = "ستظهر هنا حالة النسخ الاحتياطي لجميع المحلات المسجلة."
                                )
                            }
                        } else {
                            items(backups, key = { it.id }) { b ->
                                val tName = tenants.firstOrNull { it.id == b.tenantId }?.shopName ?: b.tenantId
                                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(tName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        StatusBadge(
                                            label = if (b.status == "SUCCESS") "ناجحة" else "فشلت",
                                            textColor = if (b.status == "SUCCESS") HesabatyThemeExtras.colors.success else HesabatyThemeExtras.colors.danger,
                                            backgroundColor = if (b.status == "SUCCESS") HesabatyThemeExtras.colors.successContainer else HesabatyThemeExtras.colors.dangerContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${b.destination} — ${b.fileName}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${EgyptTime.formatDateTime(b.createdAt)} | ${b.message}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(auditLogs, key = { it.id }) { log ->
                            HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(log.action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        EgyptTime.formatDateTime(log.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(log.details, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "بواسطة: ${log.actorUsername}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateShopDialog) {
        CreateShopDialog(
            currentDeviceLabel = viewModel.currentDeviceLabel,
            onDismiss = { showCreateShopDialog = false },
            onSubmit = { shopName, ownerName, address, phone, username, pass, confirmPass, bindToDevice, onErr ->
                viewModel.createShopAccount(
                    shopName = shopName,
                    ownerName = ownerName,
                    address = address,
                    phone = phone,
                    username = username,
                    password = pass,
                    confirmPassword = confirmPass,
                    bindToCurrentDevice = bindToDevice
                ) { ok, msg ->
                    if (ok) {
                        showCreateShopDialog = false
                        if (bindToDevice) {
                            createdAccountHandoff = Triple(shopName.trim(), username.trim(), pass)
                        }
                    } else {
                        onErr(msg)
                    }
                }
            }
        )
    }

    if (showRemoteTokenDialog) {
        RemoteDeviceTokenGeneratorDialog(
            isBusy = isBusy,
            onDismiss = { showRemoteTokenDialog = false },
            onGenerate = { targetCode, shopName, ownerName, address, phone, username, pass, confirmPass, onRes ->
                viewModel.generateRemoteActivationToken(
                    targetDeviceCode = targetCode,
                    shopName = shopName,
                    ownerName = ownerName,
                    address = address,
                    phone = phone,
                    username = username,
                    password = pass,
                    confirmPassword = confirmPass,
                    onResult = onRes
                )
            }
        )
    }

    createdAccountHandoff?.let { (shopName, username, password) ->
        AccountCreatedHandoffDialog(
            shopName = shopName,
            username = username,
            deviceLabel = viewModel.currentDeviceLabel,
            deviceActivationCode = viewModel.currentDeviceActivationCode,
            onLoginCustomerNow = {
                createdAccountHandoff = null
                viewModel.switchToShopAccountFromAdmin(username, password) { err ->
                    viewModel.emitMessage(err, true)
                }
            },
            onLogoutAdmin = {
                createdAccountHandoff = null
                viewModel.logout()
            },
            onStayInAdmin = { createdAccountHandoff = null }
        )
    }

    editingTenant?.let { t ->
        EditTenantDialog(
            tenant = t,
            onDismiss = { editingTenant = null },
            onSave = { sName, oName, addr, ph ->
                viewModel.updateTenant(t, sName, oName, addr, ph, ph) { ok, _ ->
                    if (ok) editingTenant = null
                }
            }
        )
    }

    resetPasswordUser?.let { target ->
        ResetPasswordDialog(
            username = target.username,
            onDismiss = { resetPasswordUser = null },
            onConfirm = { newPass, confirm, onErr ->
                viewModel.resetUserPassword(target.id, newPass, confirm) { ok, msg ->
                    if (ok) resetPasswordUser = null else onErr(msg)
                }
            }
        )
    }

    if (showChangeAdminPass) {
        ChangeOwnPasswordDialog(
            onDismiss = { showChangeAdminPass = false },
            onConfirm = { curr, newP, conf, onErr ->
                viewModel.changeOwnPassword(curr, newP, conf) { ok, msg ->
                    if (ok) showChangeAdminPass = false else onErr(msg)
                }
            }
        )
    }
}

@Composable
private fun accountStatusColors(
    status: String,
    extras: com.example.ui.theme.HesabatyColors
): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> = when (status) {
    AccountStatuses.ACTIVE -> extras.success to extras.successContainer
    AccountStatuses.TRIAL -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
    AccountStatuses.EXPIRED -> extras.warning to extras.warningContainer
    else -> extras.danger to extras.dangerContainer
}

@Composable
private fun CreateShopDialog(
    currentDeviceLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (
        shopName: String,
        ownerName: String,
        address: String,
        phone: String,
        username: String,
        password: String,
        confirmPassword: String,
        bindToCurrentDevice: Boolean,
        onError: (String) -> Unit
    ) -> Unit
) {
    var shopName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var bindToCurrentDevice by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إنشاء حساب محل جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it; errorMsg = null },
                    label = { Text("1. اسم المحل *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_shop_name")
                )
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it; errorMsg = null },
                    label = { Text("2. اسم العميل / صاحب المحل *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_shop_owner")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it; errorMsg = null },
                    label = { Text("3. العنوان *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_shop_address")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMsg = null },
                    label = { Text("4. رقم الهاتف *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("create_shop_phone")
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMsg = null },
                    label = { Text("5. اسم المستخدم (Username) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_shop_username")
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = null },
                    label = { Text("6. كلمة المرور (Password) *") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("create_shop_password")
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMsg = null },
                    label = { Text("7. تأكيد كلمة المرور *") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("create_shop_confirm_password")
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = bindToCurrentDevice,
                        onCheckedChange = { bindToCurrentDevice = it },
                        modifier = Modifier.testTag("create_shop_bind_device_checkbox")
                    )
                    Column {
                        Text(
                            text = "ربط الحساب بهذا الجهاز فورًا (Device Binding)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (bindToCurrentDevice) {
                                "سيتم تسجيل بصمة الجهاز: $currentDeviceLabel"
                            } else {
                                "سيرتبط الحساب تلقائيًا بأول جهاز يسجل الدخول به"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(
                        shopName, ownerName, address, phone, username, password, confirmPassword, bindToCurrentDevice
                    ) { errorMsg = it }
                },
                modifier = Modifier.testTag("create_shop_submit")
            ) {
                Text("إنشاء وتفعيل الحساب")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun EditTenantDialog(
    tenant: TenantEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var shopName by remember { mutableStateOf(tenant.shopName) }
    var ownerName by remember { mutableStateOf(tenant.ownerName) }
    var address by remember { mutableStateOf(tenant.address) }
    var phone by remember { mutableStateOf(tenant.phone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل بيانات المحل") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = shopName, onValueChange = { shopName = it }, label = { Text("اسم المحل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ownerName, onValueChange = { ownerName = it }, label = { Text("اسم صاحب المحل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("العنوان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("رقم الهاتف") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onSave(shopName, ownerName, address, phone) }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun ResetPasswordDialog(
    username: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, (String) -> Unit) -> Unit
) {
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعادة تعيين كلمة المرور ($username)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it; err = null },
                    label = { Text("كلمة المرور الجديدة") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; err = null },
                    label = { Text("تأكيد كلمة المرور") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(newPass, confirm) { err = it } }) { Text("تحديث") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun ChangeOwnPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, (String) -> Unit) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تغيير كلمة المرور") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it; err = null },
                    label = { Text("كلمة المرور الحالية") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it; err = null },
                    label = { Text("كلمة المرور الجديدة") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; err = null },
                    label = { Text("تأكيد كلمة المرور الجديدة") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(current, newPass, confirm) { err = it } }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
