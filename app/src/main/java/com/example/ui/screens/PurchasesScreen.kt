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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.EgyptTime
import com.example.core.MoneyFormatter
import com.example.data.local.InvoiceStatuses
import com.example.data.local.PurchaseTypes
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.StatusBadge
import com.example.ui.components.SummaryStatPill
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel

@Composable
fun PurchasesScreen(
    viewModel: HesabatyViewModel,
    onBack: () -> Unit,
    onCreatePurchase: () -> Unit,
    onOpenPurchaseInvoice: (String) -> Unit
) {
    val purchaseInvoices by viewModel.purchaseInvoices.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val currency = settings?.defaultCurrencyLabel ?: "ج.م"
    val extras = HesabatyThemeExtras.colors

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val supplierMap = remember(suppliers) { suppliers.associateBy { it.id } }

    val filtered = remember(purchaseInvoices, supplierMap, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) purchaseInvoices
        else purchaseInvoices.filter { inv ->
            val sname = supplierMap[inv.supplierId]?.name?.lowercase().orEmpty()
            inv.invoiceNumber.lowercase().contains(q) || sname.contains(q) || inv.notes.lowercase().contains(q)
        }
    }

    val totalPurchases = purchaseInvoices.sumOf { it.totalPiasters }
    val totalRemaining = purchaseInvoices.sumOf { it.remainingPiasters }

    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = "المشتريات وفواتير الشراء (${purchaseInvoices.size})",
                subtitle = "متابعة فواتير الموردين وتحديث المخزون",
                onBack = onBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreatePurchase,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_purchase_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "فاتورة شراء جديدة")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryStatPill(
                    label = "إجمالي المشتريات",
                    value = MoneyFormatter.formatEgp(totalPurchases, currency),
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                SummaryStatPill(
                    label = "المتبقي للموردين",
                    value = MoneyFormatter.formatEgp(totalRemaining, currency),
                    valueColor = if (totalRemaining > 0L) extras.danger else extras.success,
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث برقم الفاتورة أو اسم المورد...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("purchases_search_input")
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (filtered.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Receipt,
                    title = if (purchaseInvoices.isEmpty()) "لا توجد فواتير شراء بعد" else "لا توجد نتائج مطابقة",
                    subtitle = if (purchaseInvoices.isEmpty()) {
                        "أنشئ أول فاتورة شراء من مورد لتحديث حسابه ومخزون الأصناف تلقائيًا."
                    } else {
                        "جرّب البحث برقم فاتورة أو اسم مورد آخر."
                    },
                    actionLabel = if (purchaseInvoices.isEmpty()) "فاتورة شراء جديدة" else null,
                    onAction = if (purchaseInvoices.isEmpty()) onCreatePurchase else null,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { inv ->
                        val sName = supplierMap[inv.supplierId]?.name ?: "مورد"
                        val (stColor, stBg) = when (inv.status) {
                            InvoiceStatuses.PAID -> extras.success to extras.successContainer
                            InvoiceStatuses.PARTIAL -> extras.warning to extras.warningContainer
                            else -> extras.danger to extras.dangerContainer
                        }
                        HesabatyCard(
                            onClick = { onOpenPurchaseInvoice(inv.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("purchase_card_${inv.id}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${inv.invoiceNumber} — $sName",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${PurchaseTypes.arabicLabel(inv.purchaseType)}  •  ${EgyptTime.formatDateTime(inv.createdAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusBadge(
                                    label = InvoiceStatuses.arabicLabel(inv.status),
                                    textColor = stColor,
                                    backgroundColor = stBg
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SummaryStatPill(
                                    label = "الإجمالي",
                                    value = MoneyFormatter.formatEgp(inv.totalPiasters, currency),
                                    valueColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                SummaryStatPill(
                                    label = "المدفوع",
                                    value = MoneyFormatter.formatEgp(inv.paidPiasters, currency),
                                    valueColor = extras.success,
                                    modifier = Modifier.weight(1f)
                                )
                                SummaryStatPill(
                                    label = "المتبقي",
                                    value = MoneyFormatter.formatEgp(inv.remainingPiasters, currency),
                                    valueColor = if (inv.remainingPiasters > 0L) extras.danger else extras.success,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
