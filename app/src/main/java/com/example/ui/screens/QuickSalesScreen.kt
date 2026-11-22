package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.EgyptTime
import com.example.core.MoneyFormatter
import com.example.data.local.PaymentMethods
import com.example.data.local.QuickSaleEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.StatusBadge
import com.example.ui.components.SummaryStatPill
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickSalesScreen(
    viewModel: HesabatyViewModel,
    onBack: () -> Unit
) {
    val quickSales by viewModel.quickSales.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val currency = settings?.defaultCurrencyLabel ?: "ج.م"
    val extras = HesabatyThemeExtras.colors
    val uiScale = HesabatyThemeExtras.uiScale
    val focusManager = LocalFocusManager.current

    var amountInput by rememberSaveable { mutableStateOf("") }
    var descriptionInput by rememberSaveable { mutableStateOf("") }
    var paymentMethod by rememberSaveable { mutableStateOf(PaymentMethods.CASH) }
    var draftIdempotencyKey by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showTodayOnly by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<QuickSaleEntity?>(null) }

    val todayKey = remember { EgyptTime.todayEpochDay() }

    val todayItems = remember(quickSales, todayKey) {
        quickSales.filter { EgyptTime.todayEpochDay(it.createdAt) == todayKey }
    }
    val todayTotalPiasters = remember(todayItems) {
        todayItems.sumOf { it.amountPiasters }
    }
    val allTotalPiasters = remember(quickSales) {
        quickSales.sumOf { it.amountPiasters }
    }

    val filteredItems = remember(quickSales, showTodayOnly, searchQuery, todayKey) {
        val base = if (showTodayOnly) {
            quickSales.filter { EgyptTime.todayEpochDay(it.createdAt) == todayKey }
        } else {
            quickSales
        }
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            base
        } else {
            base.filter { item ->
                item.description.contains(q, ignoreCase = true) ||
                    MoneyFormatter.formatEgpPlainInput(item.amountPiasters).contains(q)
            }
        }
    }

    fun submitQuickSale() {
        if (isSaving) return
        val piasters = MoneyFormatter.parseEgpToPiasters(amountInput)
        if (piasters == null || piasters <= 0L) {
            errorMessage = "يرجى إدخال السعر / المبلغ بشكل صحيح أكبر من صفر"
            return
        }
        errorMessage = null
        isSaving = true
        focusManager.clearFocus()
        viewModel.addQuickSale(
            amountPiasters = piasters,
            description = descriptionInput.trim(),
            paymentMethod = paymentMethod,
            idempotencyKey = draftIdempotencyKey
        ) { success, msg ->
            isSaving = false
            if (success) {
                amountInput = ""
                descriptionInput = ""
                draftIdempotencyKey = UUID.randomUUID().toString()
            } else {
                errorMessage = msg
            }
        }
    }

    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = "قسم المبيعات",
                subtitle = "سحوبات سريعة بالسعر فقط بدون عميل",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = uiScale.screenPadding,
                vertical = if (uiScale.isCompact) 10.dp else 14.dp
            ),
            verticalArrangement = Arrangement.spacedBy(uiScale.sectionSpacing)
        ) {
            // 1. Fast Entry Form Card
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "إضافة سحبة مبيعات سريعة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        StatusBadge(
                            label = "بدون عميل",
                            textColor = extras.success,
                            backgroundColor = extras.successContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = {
                            amountInput = it
                            if (errorMessage != null) errorMessage = null
                        },
                        label = { Text("السعر / المبلغ * ($currency)") },
                        placeholder = { Text("مثال: 150") },
                        singleLine = true,
                        isError = errorMessage != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_sale_amount_input")
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.labelMedium,
                            color = extras.danger
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = descriptionInput,
                        onValueChange = { descriptionInput = it },
                        label = { Text("البيان (اختياري)") },
                        placeholder = { Text("يمكن تركه فارغاً أو كتابة وصف مختصر") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitQuickSale() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_sale_note_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "طريقة التحصيل",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            PaymentMethods.CASH to "نقدي",
                            PaymentMethods.EWALLET to "محفظة إلكترونية",
                            PaymentMethods.BANK_TRANSFER to "تحويل بنكي"
                        ).forEach { (methodKey, methodLabel) ->
                            val selected = paymentMethod == methodKey
                            FilterChip(
                                selected = selected,
                                onClick = { paymentMethod = methodKey },
                                label = { Text(methodLabel) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { submitQuickSale() },
                        enabled = !isSaving && amountInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("quick_sale_submit_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSaving) "جاري الحفظ..." else "إضافة للمبيعات",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. Summary Totals
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (uiScale.isCompact) 8.dp else 10.dp)
                ) {
                    SummaryStatPill(
                        label = "مبيعات اليوم (${todayItems.size})",
                        value = MoneyFormatter.formatEgp(todayTotalPiasters, currency),
                        valueColor = extras.success,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatPill(
                        label = "إجمالي القسم (${quickSales.size})",
                        value = MoneyFormatter.formatEgp(allTotalPiasters, currency),
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Filter & Search Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !showTodayOnly,
                            onClick = { showTodayOnly = false },
                            label = { Text("الكل (${quickSales.size})") }
                        )
                        FilterChip(
                            selected = showTodayOnly,
                            onClick = { showTodayOnly = true },
                            label = { Text("اليوم فقط (${todayItems.size})") }
                        )
                    }

                    if (quickSales.isNotEmpty()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("بحث بالبيان أو السعر...") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "بحث")
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "مسح البحث")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 4. Recorded Entries List
            if (filteredItems.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.PointOfSale,
                        title = if (quickSales.isEmpty()) "لا توجد سحوبات مبيعات مسجلة بعد" else "لا توجد نتائج مطابقة",
                        subtitle = if (quickSales.isEmpty()) {
                            "أدخل السعر بالأعلى واضغط «إضافة للمبيعات» للتسجيل الفوري بدون عميل"
                        } else {
                            "جرّب تغيير الفلتر أو مسح نص البحث"
                        }
                    )
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    QuickSaleRowCard(
                        item = item,
                        currency = currency,
                        onDeleteClick = { pendingDelete = item }
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف سحبة المبيعات", fontWeight = FontWeight.Bold) },
            text = {
                val note = target.description.ifBlank { "بدون بيان" }
                Text(
                    "هل تريد حذف سحبة بقيمة ${MoneyFormatter.formatEgp(target.amountPiasters, currency)} ($note)؟ سيتم خصمها من الإجماليات."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = target.id
                        pendingDelete = null
                        viewModel.deleteQuickSale(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = extras.danger)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun QuickSaleRowCard(
    item: QuickSaleEntity,
    currency: String,
    onDeleteClick: () -> Unit
) {
    val extras = HesabatyThemeExtras.colors
    HesabatyCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = MoneyFormatter.formatEgp(item.amountPiasters, currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = extras.success
                    )
                    StatusBadge(
                        label = PaymentMethods.arabicLabel(item.paymentMethod),
                        textColor = MaterialTheme.colorScheme.primary,
                        backgroundColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description.ifBlank { "بدون بيان" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.description.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = EgyptTime.formatDateTimeSeconds(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_quick_sale_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "حذف السحبة",
                    tint = extras.danger
                )
            }
        }
    }
}
