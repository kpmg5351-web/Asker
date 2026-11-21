package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.EgyptTime
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel
import java.io.File

@Composable
fun BackupScreen(
    viewModel: HesabatyViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val history by viewModel.tenantBackupHistory.collectAsStateWithLifecycle()
    val localFiles by viewModel.localBackups.collectAsStateWithLifecycle()
    val autoFolderLabel by viewModel.autoBackupFolderLabel.collectAsStateWithLifecycle()
    val dailyDriveReminderEnabled by viewModel.dailyDriveReminderEnabled.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val extras = HesabatyThemeExtras.colors
    val uiScale = HesabatyThemeExtras.uiScale

    var passphrase by remember { mutableStateOf("") }
    var driveStatusMsg by remember { mutableStateOf<String?>(null) }
    var confirmRestoreFile by remember { mutableStateOf<File?>(null) }
    var showTimeDialog by remember { mutableStateOf(false) }

    val isOnline = remember { viewModel.backupManager.isOnline() }
    val latestRecord = history.firstOrNull()

    LaunchedEffect(Unit) {
        viewModel.refreshLocalBackups()
    }

    // SAF Export encrypted backup to user-chosen location
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.createLocalBackup(passphrase.takeIf { it.isNotBlank() }, exportUri = uri)
        }
    }

    // SAF Import encrypted backup file (works directly with Google Drive & Phone storage)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                viewModel.restoreFromBytes(bytes, passphrase.takeIf { it.isNotBlank() })
            }
        }
    }

    // SAF Persistent folder picker for automatic daily copies
    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            val label = uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { "مجلد الهاتف المختار" }
                ?: "مجلد الهاتف المختار"
            viewModel.setAutoBackupFolder(uri.toString(), label)
        }
    }

    // Android 13+ notification permission launcher for daily Drive reminder
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setDailyDriveReminderEnabled(true)
        } else {
            viewModel.setDailyDriveReminderEnabled(false)
            driveStatusMsg = "الإشعارات غير مفعلة لهذا التطبيق؛ سيستمر النسخ التلقائي على الهاتف ومجلد التنزيلات بصمت."
        }
    }

    fun saveDirectToGoogleDrive() {
        viewModel.prepareBackupForDriveOrShare(passphrase.takeIf { it.isNotBlank() }) { file ->
            val driveIntent = viewModel.backupManager.createSaveToDriveIntent(file)
            if (driveIntent != null) {
                driveStatusMsg = "تم تجهيز النسخة — اختر حسابك واضغط حفظ داخل Google Drive."
                context.startActivity(driveIntent)
            } else {
                driveStatusMsg = "اختر Google Drive من نافذة الحفظ أو المشاركة لاختيار مكان النسخة."
                val stamp = EgyptTime.formatDate(EgyptTime.nowMillis()).replace("/", "")
                createDocumentLauncher.launch("hesabaty_backup_$stamp.hbak")
            }
        }
    }

    fun shareBackupToAnyApp() {
        viewModel.prepareBackupForDriveOrShare(passphrase.takeIf { it.isNotBlank() }) { file ->
            context.startActivity(viewModel.backupManager.createShareBackupIntent(file))
        }
    }

    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = "النسخ الاحتياطي والاستعادة",
                subtitle = "تشفير AES-256-GCM • محلي و Google Drive",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(uiScale.screenPadding),
            verticalArrangement = Arrangement.spacedBy(uiScale.sectionSpacing)
        ) {
            // Offline-First Status Card
            item {
                Surface(
                    color = if (isOnline) extras.successContainer else extras.warningContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (isOnline) extras.success else extras.warning
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isOnline) "متصل بالإنترنت — جاهز للنسخ السحابي والمحلي" else "وضع عدم الاتصال (Offline-First)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (latestRecord != null) {
                                    "آخر نسخة: ${EgyptTime.formatDateTime(latestRecord.createdAt)} (${if (latestRecord.status == "SUCCESS") "ناجحة" else "فشلت"})"
                                } else {
                                    "لم يتم إنشاء نسخة احتياطية بعد"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // Encryption Passphrase Card
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تشفير النسخة الاحتياطية (AES-256-GCM)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "تُشفَّر جميع الجداول (العملاء، الموردين، الفواتير، الدفعات، السحوبات، الأقساط، الإعدادات، سجل المراجعة) قبل الحفظ أو الرفع. أدخل كلمة سر اختيارية للنقل بين الهواتف، أو اتركها فارغة لاستخدام Android Keystore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("كلمة سر التشفير / فك التشفير (اختياري)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("backup_passphrase_input")
                    )
                }
            }

            // Zero-Cloud Google Drive & Free Cloud Share Card
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "الحفظ على Google Drive (مجانًا بدون كلاود)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "احفظ نسختك المشفرة مباشرة على تطبيق Google Drive في هاتفك أو شاركها على واتساب وتليجرام دون أي اشتراك أو Google Cloud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (driveStatusMsg != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(driveStatusMsg!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { saveDirectToGoogleDrive() },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("drive_backup_now_btn")
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حفظ النسخة على Google Drive الآن")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("restore_from_drive_or_file_btn")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("استعادة من Drive أو ملف")
                        }
                        OutlinedButton(
                            onClick = { shareBackupToAnyApp() },
                            enabled = !isBusy,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("share_backup_other_apps_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إرسال لواتساب / تليجرام")
                        }
                    }
                }
            }

            // Local Encrypted Backup & SAF Export/Import
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Text("النسخ الاحتياطي المحلي وتصدير ملف", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "تُحفظ النسخ داخل التطبيق وفي مجلد التنزيلات (Downloads/HesabatyBackups) لتبقى آمنة حتى إذا حذفت التطبيق.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.createLocalBackup(passphrase.takeIf { it.isNotBlank() }) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("local_backup_now_btn")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("نسخ محلي الآن")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val stamp = EgyptTime.formatDate(EgyptTime.nowMillis()).replace("/", "")
                                createDocumentLauncher.launch("hesabaty_backup_$stamp.hbak")
                            },
                            enabled = !isBusy,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Text("حفظ نسخة كملف")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استعادة نسخة من ملف على الهاتف")
                    }
                }
            }

            // Auto Daily Backup Scheduler (WorkManager: Internal + Downloads + Custom Folder + Daily Reminder)
            item {
                val autoEnabled = settings?.autoBackupEnabled ?: false
                val hour = settings?.autoBackupHour ?: 23
                val minute = settings?.autoBackupMinute ?: 0
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("نسخ احتياطي تلقائي يومي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = "الموعد المحدد: ${EgyptTime.formatHourMinute12(hour, minute)} بتوقيت مصر",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "يحفظ تلقائيًا داخل التطبيق + مجلد Downloads/HesabatyBackups دون أي اشتراك.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateAppSettings { it.copy(autoBackupEnabled = enabled) }
                            },
                            modifier = Modifier.testTag("auto_backup_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showTimeDialog = true },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تغيير موعد النسخ اليومي")
                        }
                        OutlinedButton(
                            onClick = { openFolderLauncher.launch(null) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("choose_auto_backup_folder_btn")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (autoFolderLabel != null) "تغيير المجلد ($autoFolderLabel)" else "اختيار مجلد إضافي")
                        }
                        if (autoFolderLabel != null) {
                            TextButton(
                                onClick = { viewModel.setAutoBackupFolder(null, null) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "إزالة المجلد المخصص", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إزالة المجلد")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "تذكير يومي لرفع النسخة على Drive",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "يرسل إشعارًا بعد النسخ التلقائي لتتمكن من رفع النسخة لتطبيق Google Drive بضغطة واحدة دون حاجة لحساب Google Cloud.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dailyDriveReminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.setDailyDriveReminderEnabled(true)
                                    } else {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    viewModel.setDailyDriveReminderEnabled(enabled)
                                }
                            },
                            modifier = Modifier.testTag("auto_drive_reminder_switch")
                        )
                    }
                }
            }

            // Local Backup Files List (One-Tap Restore)
            if (localFiles.isNotEmpty()) {
                item { SectionHeader(title = "النسخ المحلية المحفوظة (${localFiles.size})") }
                items(localFiles, key = { it.name }) { file ->
                    HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "${EgyptTime.formatDateTime(file.lastModified())} • ${(file.length() / 1024).coerceAtLeast(1)} KB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { confirmRestoreFile = file }) {
                                Text("استعادة")
                            }
                        }
                    }
                }
            }

            // Backup History Log
            if (history.isNotEmpty()) {
                item { SectionHeader(title = "سجل عمليات النسخ الاحتياطي") }
                items(history.take(15), key = { it.id }) { rec ->
                    HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (rec.destination == "GOOGLE_DRIVE") "Google Drive" else "نسخة محلية",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            StatusBadge(
                                label = if (rec.status == "SUCCESS") "نجح" else "فشل",
                                textColor = if (rec.status == "SUCCESS") extras.success else extras.danger,
                                backgroundColor = if (rec.status == "SUCCESS") extras.successContainer else extras.dangerContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(rec.message, style = MaterialTheme.typography.bodySmall)
                        Text(
                            EgyptTime.formatDateTime(rec.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    confirmRestoreFile?.let { file ->
        AlertDialog(
            onDismissRequest = { confirmRestoreFile = null },
            title = { Text("تأكيد استعادة النسخة الاحتياطية", fontWeight = FontWeight.Bold) },
            text = {
                Text("سيتم استعادة البيانات من ${file.name} بشكل آمن وذري دون تكرار أي حركات مالية.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = file
                        confirmRestoreFile = null
                        viewModel.restoreFromBytes(target.readBytes(), passphrase.takeIf { it.isNotBlank() })
                    }
                ) { Text("تأكيد الاستعادة") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreFile = null }) { Text("إلغاء") }
            }
        )
    }

    if (showTimeDialog) {
        val savedHour24 = (settings?.autoBackupHour ?: 23).coerceIn(0, 23)
        val initialHour12 = when {
            savedHour24 == 0 -> 12
            savedHour24 > 12 -> savedHour24 - 12
            else -> savedHour24
        }
        var hour12Text by remember { mutableStateOf(initialHour12.toString()) }
        var minuteText by remember { mutableStateOf((settings?.autoBackupMinute ?: 0).coerceIn(0, 59).toString().padStart(2, '0')) }
        var isPm by remember { mutableStateOf(savedHour24 >= 12) }

        AlertDialog(
            onDismissRequest = { showTimeDialog = false },
            title = { Text("تحديد موعد النسخ التلقائي اليومي (نظام 12 ساعة)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = hour12Text,
                            onValueChange = { raw ->
                                val filtered = raw.filter { it.isDigit() }.take(2)
                                hour12Text = filtered
                            },
                            label = { Text("الساعة (1-12)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { raw ->
                                val filtered = raw.filter { it.isDigit() }.take(2)
                                minuteText = filtered
                            },
                            label = { Text("الدقيقة (0-59)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = !isPm,
                            onClick = { isPm = false },
                            label = { Text("صباحًا (ص)") }
                        )
                        FilterChip(
                            selected = isPm,
                            onClick = { isPm = true },
                            label = { Text("مساءً (م)") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val h12 = (hour12Text.toIntOrNull() ?: initialHour12).coerceIn(1, 12)
                        val m = (minuteText.toIntOrNull() ?: 0).coerceIn(0, 59)
                        val h24 = when {
                            !isPm && h12 == 12 -> 0
                            !isPm -> h12
                            isPm && h12 == 12 -> 12
                            else -> h12 + 12
                        }
                        showTimeDialog = false
                        viewModel.updateAppSettings {
                            it.copy(autoBackupEnabled = true, autoBackupHour = h24, autoBackupMinute = m)
                        }
                    }
                ) { Text("حفظ الموعد") }
            },
            dismissButton = {
                TextButton(onClick = { showTimeDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
