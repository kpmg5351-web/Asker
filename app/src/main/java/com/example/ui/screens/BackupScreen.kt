package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.EgyptTime
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
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
    val driveFiles by viewModel.driveFiles.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val extras = HesabatyThemeExtras.colors

    var passphrase by remember { mutableStateOf("") }
    var driveAccessToken by remember { mutableStateOf<String?>(null) }
    var driveStatusMsg by remember { mutableStateOf<String?>(null) }
    var pendingDriveAction by remember { mutableStateOf<String?>(null) } // "UPLOAD", "LIST", or "RESTORE:<id>"
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

    // SAF Import encrypted backup file
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

    // Google Play Services OAuth Authorization resolution launcher for Google Drive appDataFolder
    val driveAuthResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                val authResult = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data)
                val token = authResult.accessToken
                if (!token.isNullOrBlank()) {
                    driveAccessToken = token
                    driveStatusMsg = "تم ربط حساب Google Drive بنجاح"
                    when (val action = pendingDriveAction) {
                        "UPLOAD" -> viewModel.uploadBackupToGoogleDrive(token, passphrase.takeIf { it.isNotBlank() })
                        "LIST" -> viewModel.fetchDriveBackups(token)
                        else -> if (action != null && action.startsWith("RESTORE:")) {
                            viewModel.restoreFromGoogleDrive(token, action.removePrefix("RESTORE:"), passphrase.takeIf { it.isNotBlank() })
                        }
                    }
                } else {
                    driveStatusMsg = "لم يتم الحصول على رمز صلاحية Google Drive"
                }
            } catch (e: Exception) {
                driveStatusMsg = "تعذر إكمال مصادقة Google Drive: ${e.localizedMessage ?: "خطأ غير معروف"}"
            }
        } else {
            driveStatusMsg = "تم إلغاء ربط حساب Google Drive"
        }
        pendingDriveAction = null
    }

    fun requestDriveAuthorization(action: String) {
        pendingDriveAction = action
        val existingToken = driveAccessToken
        if (!existingToken.isNullOrBlank()) {
            when {
                action == "UPLOAD" -> viewModel.uploadBackupToGoogleDrive(existingToken, passphrase.takeIf { it.isNotBlank() })
                action == "LIST" -> viewModel.fetchDriveBackups(existingToken)
                action.startsWith("RESTORE:") -> viewModel.restoreFromGoogleDrive(existingToken, action.removePrefix("RESTORE:"), passphrase.takeIf { it.isNotBlank() })
            }
            return
        }

        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.appdata")))
            .build()

        Identity.getAuthorizationClient(context)
            .authorize(authRequest)
            .addOnSuccessListener { authResult ->
                if (authResult.hasResolution()) {
                    val pi = authResult.pendingIntent
                    if (pi != null) {
                        driveAuthResolutionLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    }
                } else {
                    val token = authResult.accessToken
                    if (!token.isNullOrBlank()) {
                        driveAccessToken = token
                        driveStatusMsg = "حساب Google Drive متصل ومُصرّح له بمساحة التطبيق"
                        when {
                            action == "UPLOAD" -> viewModel.uploadBackupToGoogleDrive(token, passphrase.takeIf { it.isNotBlank() })
                            action == "LIST" -> viewModel.fetchDriveBackups(token)
                            action.startsWith("RESTORE:") -> viewModel.restoreFromGoogleDrive(token, action.removePrefix("RESTORE:"), passphrase.takeIf { it.isNotBlank() })
                        }
                    } else {
                        driveStatusMsg = "تعذر الحصول على رمز Google Drive"
                    }
                }
            }
            .addOnFailureListener { e ->
                driveStatusMsg = "يتطلب Google Drive تفعيل OAuth Android Client في Google Cloud Console لبصمة التطبيق (SHA-1). بياناتك محفوظة محليًا بأمان ويمكنك التصدير الآن. (${e.localizedMessage ?: ""})"
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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

            // Google Drive Backup Card
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Text("النسخ الاحتياطي عبر Google Drive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "يستخدم مساحة التطبيق الخاصة (appDataFolder) عبر Google Drive API v3 الرسمية والمصادقة المعتمدة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (driveStatusMsg != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(driveStatusMsg!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { requestDriveAuthorization("UPLOAD") },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("drive_backup_now_btn")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("نسخ إلى Drive الآن")
                        }
                        OutlinedButton(
                            onClick = { requestDriveAuthorization("LIST") },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("عرض نسخ Drive")
                        }
                    }

                    if (driveFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        driveFiles.forEach { file ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(file.modifiedTime.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { requestDriveAuthorization("RESTORE:${file.id}") }) {
                                    Text("استعادة")
                                }
                            }
                        }
                    }
                }
            }

            // Local Encrypted Backup & SAF Export/Import
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Text("النسخ الاحتياطي المحلي المشفر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.createLocalBackup(passphrase.takeIf { it.isNotBlank() }) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("local_backup_now_btn")
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("حفظ نسخة كملف")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استعادة نسخة من ملف على الهاتف")
                    }
                }
            }

            // Auto Daily Backup Scheduler (WorkManager)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text("نسخ احتياطي تلقائي يومي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = "الموعد المحدد: ${String.format("%02d:%02d", hour, minute)} بتوقيت مصر",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateAppSettings { it.copy(autoBackupEnabled = enabled) }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(onClick = { showTimeDialog = true }) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تغيير موعد النسخ اليومي")
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
        var h by remember { mutableIntStateOf(settings?.autoBackupHour ?: 23) }
        var m by remember { mutableIntStateOf(settings?.autoBackupMinute ?: 0) }
        AlertDialog(
            onDismissRequest = { showTimeDialog = false },
            title = { Text("تحديد موعد النسخ التلقائي اليومي") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = h.toString(),
                        onValueChange = { h = it.toIntOrNull()?.coerceIn(0, 23) ?: h },
                        label = { Text("الساعة (0-23)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = m.toString(),
                        onValueChange = { m = it.toIntOrNull()?.coerceIn(0, 59) ?: m },
                        label = { Text("الدقيقة (0-59)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTimeDialog = false
                        viewModel.updateAppSettings {
                            it.copy(autoBackupEnabled = true, autoBackupHour = h, autoBackupMinute = m)
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
