package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.EgyptTime
import com.example.core.MoneyFormatter
import com.example.data.local.DailyInvoiceStatuses
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.StatusBadge
import com.example.ui.components.SummaryStatPill
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel

@Composable
fun DailyWithdrawalsScreen(
    viewModel: HesabatyViewModel,
    onBack: () -> Unit,
    onOpenDailyDetail: (String) -> Unit
) {
    val dailyInvoices by viewModel.dailyInvoices.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val currency = settings?.defaultCurrencyLabel ?: "ج.م"

    val customerMap = remember(customers) { customers.associateBy { it.id } }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showPickCustomerDialog by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }

    val openList = remember(dailyInvoices) { dailyInvoices.filter { it.status == DailyInvoiceStatuses.OPEN } }
    val closedList = remember(dailyInvoices) { dailyInvoices.filter { it.status == DailyInvoiceStatuses.CLOSED } }
    val shown = if (selectedTab == 0) openList else closedList
    val extras = HesabatyThemeExtras.colors

    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = "فواتير السحب اليومي",
                subtitle = "تبقى مفتوحة حتى الإغلاق اليدوي",
                onBack = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPickCustomerDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_daily_invoice_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "فتح فاتورة سحب يومي")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("مفتوحة (${openList.size})") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("مغلقة (${closedList.size})") })
            }

            if (shown.isEmpty()) {
                EmptyStateView(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = if (selectedTab == 0) "لا توجد فواتير سحب يومي مفتوحة" else "لا توجد فواتير يومية مغلقة",
                    subtitle = "اختر عميلًا وافتح له فاتورة يومية لتسجيل سحوباته المتتالية بالتوقيت الدقيق.",
                    actionLabel = if (selectedTab == 0) "إنشاء فاتورة يومية" else null,
                    onAction = if (selectedTab == 0) ({ showPickCustomerDialog = true }) else null
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shown, key = { it.id }) { daily ->
                        val cname = customerMap[daily.customerId]?.name ?: "عميل"
                        val isOpen = daily.status == DailyInvoiceStatuses.OPEN
                        HesabatyCard(
                            onClick = { onOpenDailyDetail(daily.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${daily.invoiceNumber} • ${EgyptTime.formatEpochDay(daily.invoiceDateEpochDay)} • ${daily.withdrawalsCount} سحبة",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusBadge(
                                    label = if (isOpen) "مفتوحة" else "مغلقة",
                                    textColor = if (isOpen) extras.warning else extras.success,
                                    backgroundColor = if (isOpen) extras.warningContainer else extras.successContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SummaryStatPill("الإجمالي", MoneyFormatter.formatEgp(daily.totalPiasters, currency), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                                SummaryStatPill("المدفوع", MoneyFormatter.formatEgp(daily.paidPiasters, currency), extras.success, Modifier.weight(1f))
                                SummaryStatPill("المتبقي", MoneyFormatter.formatEgp(daily.remainingPiasters, currency), if (daily.remainingPiasters > 0) extras.danger else extras.success, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPickCustomerDialog) {
        var customerSearch by remember { mutableStateOf("") }
        val filteredCustomers = remember(customers, customerSearch) {
            val q = customerSearch.trim()
            if (q.isEmpty()) customers
            else customers.filter {
                it.name.contains(q, ignoreCase = true) || it.phone.contains(q, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { showPickCustomerDialog = false },
            title = { Text("اختر العميل لفتح فاتورة سحب يومي", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            showPickCustomerDialog = false
                            showAddCustomerDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("daily_pick_create_customer_btn")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إنشاء عميل جديد وفتح فاتورة له", fontWeight = FontWeight.Bold)
                    }

                    if (customers.isEmpty()) {
                        Text(
                            text = "لا يوجد عملاء مسجلون بعد. اضغط الزر أعلاه لإنشاء عميل جديد فورًا وفتح فاتورة السحب اليومي له مباشرة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        OutlinedTextField(
                            value = customerSearch,
                            onValueChange = { customerSearch = it },
                            placeholder = { Text("ابحث باسم العميل أو رقم الهاتف...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (customerSearch.isNotEmpty()) {
                                    IconButton(onClick = { customerSearch = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "مسح")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("daily_pick_customer_search")
                        )

                        if (filteredCustomers.isEmpty()) {
                            Text(
                                text = "لا يوجد عميل مطابق لـ \"$customerSearch\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredCustomers, key = { it.id }) { c ->
                                    Surface(
                                        onClick = {
                                            showPickCustomerDialog = false
                                            viewModel.createDailyInvoice(c.id) { createdId ->
                                                onOpenDailyDetail(createdId)
                                            }
                                        },
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                if (c.phone.isNotBlank()) {
                                                    Text(c.phone, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Text(
                                                text = MoneyFormatter.formatEgp(c.currentBalancePiasters, currency),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (c.currentBalancePiasters > 0L) extras.danger else extras.success
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPickCustomerDialog = false }) { Text("إغلاق") }
            }
        )
    }

    if (showAddCustomerDialog) {
        AddCustomerDialog(
            onDismiss = { showAddCustomerDialog = false },
            onSave = { name, phone, wa, addr, cNotes, opening, creditLimit, onErr ->
                viewModel.addCustomer(name, phone, wa, addr, cNotes, opening, creditLimit) { ok, customerIdOrErr ->
                    if (ok) {
                        showAddCustomerDialog = false
                        viewModel.createDailyInvoice(customerIdOrErr) { createdId ->
                            onOpenDailyDetail(createdId)
                        }
                    } else {
                        onErr(customerIdOrErr)
                    }
                }
            }
        )
    }
}
