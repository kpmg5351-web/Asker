package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.MoneyFormatter
import com.example.data.local.CustomerAccountStatus
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.StatusBadge
import com.example.ui.components.SummaryStatPill
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel

@Composable
fun CustomersScreen(
    viewModel: HesabatyViewModel,
    onBack: () -> Unit,
    onOpenCustomer: (String) -> Unit
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val creditLimitBreaches by viewModel.creditLimitBreaches.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val currency = settings?.defaultCurrencyLabel ?: "ج.م"
    val shopDefaultLimit = settings?.defaultCustomerCreditLimitPiasters ?: 0L
    val breachByCustomerId = remember(creditLimitBreaches) {
        creditLimitBreaches.associateBy { it.customer.id }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = remember(customers, searchQuery, statusFilter, breachByCustomerId) {
        val q = searchQuery.trim().lowercase()
        customers.filter { c ->
            val matchQ = q.isEmpty() || c.name.lowercase().contains(q) || c.phone.contains(q) || c.whatsappPhone.contains(q)
            val matchS = when (statusFilter) {
                "ALL" -> true
                "OVER_LIMIT" -> breachByCustomerId.containsKey(c.id)
                else -> c.accountStatus == statusFilter
            }
            matchQ && matchS
        }
    }

    val extras = HesabatyThemeExtras.colors

    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = "العملاء (${customers.size})",
                subtitle = "حسابات العملاء والمستحقات وكشوف الحساب",
                onBack = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_customer_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة عميل جديد")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث باسم العميل أو رقم الهاتف...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("customers_search_input")
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(
                    "ALL" to "الكل (${customers.size})",
                    "OVER_LIMIT" to "تجاوزوا الحد (${breachByCustomerId.size})",
                    CustomerAccountStatus.OWING to "عليه مديونية",
                    CustomerAccountStatus.OVERDUE to "متأخر في السداد",
                    CustomerAccountStatus.CLEAR to "خالص"
                )
                items(options) { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { statusFilter = key },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            if (filtered.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Groups,
                    title = if (customers.isEmpty()) "لا يوجد عملاء مسجلون بعد" else "لا توجد نتائج مطابقة",
                    subtitle = if (customers.isEmpty()) {
                        "أضف أول عميل لتسجيل المبيعات النقدية والآجلة وفواتير السحب اليومي."
                    } else {
                        "جرّب تغيير البحث أو تصفية حالة الحساب."
                    },
                    actionLabel = if (customers.isEmpty()) "إضافة عميل جديد" else null,
                    onAction = if (customers.isEmpty()) ({ showAddDialog = true }) else null
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { customer ->
                        val breach = breachByCustomerId[customer.id]
                        val effectiveLimit = if (customer.creditLimitPiasters > 0L) customer.creditLimitPiasters else shopDefaultLimit
                        val (badgeColor, badgeBg) = when (customer.accountStatus) {
                            CustomerAccountStatus.OVERDUE -> extras.danger to extras.dangerContainer
                            CustomerAccountStatus.OWING -> extras.warning to extras.warningContainer
                            else -> extras.success to extras.successContainer
                        }
                        HesabatyCard(
                            onClick = { onOpenCustomer(customer.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_card_${customer.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = customer.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (customer.phone.isNotBlank()) {
                                        Text(
                                            text = customer.phone,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (breach != null) {
                                        Text(
                                            text = "تجاوز حد المديونية (${MoneyFormatter.formatEgp(effectiveLimit, currency)}) بـ +${MoneyFormatter.formatEgp(breach.excessPiasters, currency)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = extras.danger
                                        )
                                    } else if (effectiveLimit > 0L) {
                                        Text(
                                            text = "حد المديونية: ${MoneyFormatter.formatEgp(effectiveLimit, currency)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (breach != null) {
                                        StatusBadge(
                                            label = "تجاوز الحد",
                                            textColor = extras.danger,
                                            backgroundColor = extras.dangerContainer
                                        )
                                    }
                                    StatusBadge(
                                        label = CustomerAccountStatus.arabicLabel(customer.accountStatus),
                                        textColor = badgeColor,
                                        backgroundColor = badgeBg
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SummaryStatPill(
                                    label = "المبيعات",
                                    value = MoneyFormatter.formatEgp(customer.totalSalesPiasters, currency),
                                    valueColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                SummaryStatPill(
                                    label = "المدفوع",
                                    value = MoneyFormatter.formatEgp(customer.totalPaidPiasters, currency),
                                    valueColor = extras.success,
                                    modifier = Modifier.weight(1f)
                                )
                                SummaryStatPill(
                                    label = "المستحق",
                                    value = MoneyFormatter.formatEgp(customer.currentBalancePiasters, currency),
                                    valueColor = if (customer.currentBalancePiasters > 0) extras.danger else extras.success,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCustomerDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, phone, wa, address, notes, openingPiasters, creditLimitPiasters, onErr ->
                viewModel.addCustomer(name, phone, wa, address, notes, openingPiasters, creditLimitPiasters) { ok, msg ->
                    if (ok) showAddDialog = false else onErr(msg)
                }
            }
        )
    }
}

@Composable
fun AddCustomerDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Long, Long, (String) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var waPhone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var openingText by remember { mutableStateOf("") }
    var creditLimitText by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة عميل جديد", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; err = null },
                    label = { Text("اسم العميل *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("customer_name_input")
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
                    label = { Text("رقم WhatsApp (اختياري)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
                    value = openingText,
                    onValueChange = { openingText = it; err = null },
                    label = { Text("رصيد افتتاحي على العميل (جنيه)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = creditLimitText,
                    onValueChange = { creditLimitText = it; err = null },
                    label = { Text("حد المديونية المسموح به (جنيه - اختياري)") },
                    supportingText = {
                        Text("عند التجاوز يظهر تنبيه في القائمة الرئيسية (اتركه فارغًا لتطبيق الحد العام)")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("customer_credit_limit_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات") },
                    modifier = Modifier.fillMaxWidth()
                )
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val opening = if (openingText.isBlank()) 0L else {
                        MoneyFormatter.parseEgpToPiasters(openingText) ?: run {
                            err = "أدخل رصيدًا افتتاحيًا صحيحًا"
                            return@Button
                        }
                    }
                    val creditLimit = if (creditLimitText.isBlank()) 0L else {
                        MoneyFormatter.parseEgpToPiasters(creditLimitText) ?: run {
                            err = "أدخل حد مديونية صحيحًا"
                            return@Button
                        }
                    }
                    onSave(name, phone, waPhone, address, notes, opening, creditLimit) { err = it }
                },
                modifier = Modifier.testTag("save_customer_button")
            ) {
                Text("حفظ العميل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
