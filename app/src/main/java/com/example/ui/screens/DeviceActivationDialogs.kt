package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.PhoneUtils
import com.example.core.WhatsAppHelper
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryDarkBlue

private fun copyToClipboard(context: Context, label: String, text: String, toastMessage: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}

/**
 * Shown from LoginScreen on the customer's phone:
 * - Tab 0: Create & permanently bind a new shop account directly on THIS phone (Admin verified).
 * - Tab 1: Activate a remote device-locked token (HSB1...) sent by Admin for THIS phone's Device Code.
 */
@Composable
fun CustomerPhoneActivationDialog(
    deviceLabel: String,
    deviceActivationCode: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onCreateDirectlyOnPhone: (
        adminUsername: String,
        adminPassword: String,
        shopName: String,
        ownerName: String,
        address: String,
        phone: String,
        username: String,
        password: String,
        confirmPassword: String,
        onError: (String) -> Unit
    ) -> Unit,
    onActivateWithToken: (rawToken: String, onError: (String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val extras = HesabatyThemeExtras.colors

    var selectedTab by remember { mutableIntStateOf(0) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Tab 0 fields (Direct on customer phone)
    var adminUsername by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // Tab 1 field (Remote activation token)
    var activationToken by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = PrimaryBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "تفعيل / إنشاء حساب على هذا الهاتف",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Locked-to-this-phone security badge
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PrimaryBlue.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = PrimaryDarkBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "الجهاز الحالي: $deviceLabel",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryDarkBlue
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "كود الجهاز: $deviceActivationCode",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.5.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "🔒 أي حساب يتم إنشاؤه أو تفعيله هنا يرتبط حصريًا ببصمة هذا الهاتف ولا يمكن فتحه على أي جهاز آخر.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; errorMsg = null },
                        text = { Text("إنشاء على الهاتف", maxLines = 1) },
                        modifier = Modifier.testTag("activation_tab_direct")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; errorMsg = null },
                        text = { Text("تفعيل بكود إدارة", maxLines = 1) },
                        modifier = Modifier.testTag("activation_tab_token")
                    )
                }

                if (selectedTab == 0) {
                    // Section 1: Admin Authorization
                    Text(
                        text = "أولًا: تأكيد هوية المدير الرئيسي",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDarkBlue
                    )
                    OutlinedTextField(
                        value = adminUsername,
                        onValueChange = { adminUsername = it; errorMsg = null },
                        label = { Text("اسم مستخدم المدير *") },
                        leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("direct_admin_username")
                    )
                    OutlinedTextField(
                        value = adminPassword,
                        onValueChange = { adminPassword = it; errorMsg = null },
                        label = { Text("كلمة مرور المدير *") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("direct_admin_password")
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    // Section 2: Customer Shop Data (7 required fields)
                    Text(
                        text = "ثانيًا: بيانات حساب العميل (يُربط بهذا الهاتف فورًا)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDarkBlue
                    )
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it; errorMsg = null },
                        label = { Text("1. اسم المحل *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("direct_shop_name")
                    )
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it; errorMsg = null },
                        label = { Text("2. اسم العميل / صاحب المحل *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("direct_owner_name")
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it; errorMsg = null },
                        label = { Text("3. العنوان *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("direct_address")
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it; errorMsg = null },
                        label = { Text("4. رقم الهاتف *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("direct_phone")
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; errorMsg = null },
                        label = { Text("5. اسم المستخدم للعميل *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("direct_username")
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMsg = null },
                        label = { Text("6. كلمة مرور العميل *") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("direct_password")
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMsg = null },
                        label = { Text("7. تأكيد كلمة المرور *") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("direct_confirm_password")
                    )
                } else {
                    // Tab 1: Remote Activation via Device-Locked Token
                    Text(
                        text = "1. أرسل كود جهازك للإدارة لتوليد ترخيص مربوط بهاتفك فقط:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, "device_code", deviceActivationCode, "تم نسخ كود الجهاز")
                            },
                            modifier = Modifier.weight(1f).testTag("copy_device_code_btn")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ الكود", maxLines = 1)
                        }
                        Button(
                            onClick = {
                                WhatsAppHelper.openWhatsAppChat(
                                    context = context,
                                    rawPhone = PhoneUtils.SUPPORT_PHONE_LOCAL,
                                    message = WhatsAppHelper.buildDeviceCodeRequestMessage(
                                        deviceLabel = deviceLabel,
                                        deviceCode = deviceActivationCode
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                            modifier = Modifier.weight(1f).testTag("send_device_code_wa_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إرسال واتساب", maxLines = 1)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "2. الصق كود التفعيل المرسل لك من الإدارة (يبدأ بـ HSB1):",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = activationToken,
                        onValueChange = { activationToken = it; errorMsg = null },
                        label = { Text("كود التفعيل المربوط بالجهاز") },
                        placeholder = { Text("HSB1....") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().testTag("remote_activation_token_input")
                    )
                }

                errorMsg?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        onCreateDirectlyOnPhone(
                            adminUsername, adminPassword,
                            shopName, ownerName, address, phone,
                            username, password, confirmPassword
                        ) { errorMsg = it }
                    },
                    modifier = Modifier.testTag("direct_create_submit_btn")
                ) {
                    Text("إنشاء وربط بهذا الهاتف")
                }
            } else {
                Button(
                    enabled = !isBusy && activationToken.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                    onClick = {
                        onActivateWithToken(activationToken) { errorMsg = it }
                    },
                    modifier = Modifier.testTag("remote_token_activate_btn")
                ) {
                    Text("تفعيل الحساب الآن")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("إلغاء")
            }
        }
    )
}

/**
 * Shown in AdminPanelScreen so Master Admin can generate a cryptographically signed,
 * device-locked activation token for a customer's remote phone using their Device Code.
 */
@Composable
fun RemoteDeviceTokenGeneratorDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (
        targetDeviceCode: String,
        shopName: String,
        ownerName: String,
        address: String,
        phone: String,
        username: String,
        password: String,
        confirmPassword: String,
        onResult: (Boolean, String) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val extras = HesabatyThemeExtras.colors

    var targetDeviceCode by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var generatedToken by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Text(
                text = if (generatedToken == null) "توليد كود تفعيل لهاتف عميل (عن بُعد)" else "كود التفعيل جاهز للإرسال",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (generatedToken == null) {
                    Text(
                        text = "أدخل كود جهاز العميل (HSB-XXXX-XXXX-XXXX) المرسل منه لتوليد ترخيص يعمل حصريًا على هاتفه فقط ولا يفتح على أي جهاز آخر.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = targetDeviceCode,
                        onValueChange = { targetDeviceCode = it; errorMsg = null },
                        label = { Text("كود جهاز العميل * (HSB-XXXX-XXXX-XXXX)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("remote_gen_device_code")
                    )
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it; errorMsg = null },
                        label = { Text("1. اسم المحل *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it; errorMsg = null },
                        label = { Text("2. اسم صاحب المحل *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it; errorMsg = null },
                        label = { Text("3. العنوان *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it; errorMsg = null },
                        label = { Text("4. رقم هاتف العميل *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; errorMsg = null },
                        label = { Text("5. اسم المستخدم للعميل *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMsg = null },
                        label = { Text("6. كلمة المرور *") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMsg = null },
                        label = { Text("7. تأكيد كلمة المرور *") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorMsg?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = extras.successContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "✓ الترخيص مربوط حصريًا بجهاز: ${targetDeviceCode.uppercase()}",
                                fontWeight = FontWeight.Bold,
                                color = extras.success
                            )
                            Text(
                                text = "المحل: $shopName  •  المستخدم: $username",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    OutlinedTextField(
                        value = generatedToken!!,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("كود التفعيل الموقّع (HSB1)") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, "activation_token", generatedToken!!, "تم نسخ كود التفعيل")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ الكود")
                        }
                        Button(
                            onClick = {
                                val msg = WhatsAppHelper.buildActivationTokenDeliveryMessage(
                                    shopName = shopName.trim(),
                                    ownerName = ownerName.trim(),
                                    username = username.trim(),
                                    deviceCode = targetDeviceCode.trim().uppercase(),
                                    activationToken = generatedToken!!
                                )
                                WhatsAppHelper.openWhatsAppChat(
                                    context = context,
                                    rawPhone = phone,
                                    message = msg
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إرسال واتساب")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (generatedToken == null) {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        onGenerate(
                            targetDeviceCode, shopName, ownerName, address, phone,
                            username, password, confirmPassword
                        ) { ok, res ->
                            if (ok) generatedToken = res else errorMsg = res
                        }
                    },
                    modifier = Modifier.testTag("remote_gen_submit_btn")
                ) {
                    Text("توليد كود التفعيل")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("تم وإغلاق")
                }
            }
        },
        dismissButton = {
            if (generatedToken == null) {
                TextButton(onClick = onDismiss, enabled = !isBusy) { Text("إلغاء") }
            }
        }
    )
}

/**
 * Shown right after Master Admin creates a shop account on the customer's phone:
 * allows 1-tap handoff straight into the customer's device-locked shop workspace
 * (logging Master Admin out cleanly so the phone is ready to hand back to the customer).
 */
@Composable
fun AccountCreatedHandoffDialog(
    shopName: String,
    username: String,
    deviceLabel: String,
    deviceActivationCode: String,
    onLoginCustomerNow: () -> Unit,
    onLogoutAdmin: () -> Unit,
    onStayInAdmin: () -> Unit
) {
    val extras = HesabatyThemeExtras.colors
    AlertDialog(
        onDismissRequest = onStayInAdmin,
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = extras.success,
                modifier = Modifier.size(34.dp)
            )
        },
        title = {
            Text(
                text = "تم إنشاء وربط الحساب بهذا الهاتف",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("المحل: $shopName", fontWeight = FontWeight.Bold)
                Text("اسم المستخدم: $username")
                Text(
                    text = "🔒 مربوط حصريًا بجهاز: $deviceLabel ($deviceActivationCode) ولا يمكن فتحه على أي هاتف آخر.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrimaryDarkBlue
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "اختر (دخول مباشر لحساب العميل) لفتح حسابه فورًا، أو (تسجيل خروج الإدارة) للعودة إلى شاشة الدخول وتسليم الهاتف للعميل.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onLoginCustomerNow,
                colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                modifier = Modifier.testTag("handoff_login_customer_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("دخول مباشر لحساب العميل")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onLogoutAdmin,
                    modifier = Modifier.testTag("handoff_logout_admin_btn")
                ) {
                    Text("تسجيل خروج الإدارة", color = extras.danger, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onStayInAdmin) {
                    Text("بقاء")
                }
            }
        }
    )
}
