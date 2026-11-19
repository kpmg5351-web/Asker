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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.EgyptTime
import com.example.core.MoneyFormatter
import com.example.data.local.PaymentMethods
import com.example.data.local.SaleTypes
import com.example.data.repository.InstallmentDraft
import com.example.data.repository.SaleLineDraft
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyDatePickerDialog
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel
import java.util.UUID

private data class LineInputRow(
    val id: String = UUID.randomUUID().toString(),
    val description: String = "",
    val unitPriceText: String = "",
    val quantityText: String = "1",
    val productId: String? = null
) {
    val computedPiasters: Long
        get() {
            val price = MoneyFormatter.parseEgpToPiasters(unitPriceText) ?: return 0L
            val qty = if (quantityText.isBlank()) 1000L else (MoneyFormatter.parseQuantityToMilli(quantityText) ?: 0L)
            return MoneyFormatter.calculateLineTotalPiasters(price, qty)
        }
}

private data class InstallmentInputRow(
    val id: String = UUID.randomUUID().toString(),
    val dueDateMillis: Long,
    val amountText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSaleScreen(
    preselectedCustomerId: String? = null,
    viewModel: HesabatyViewModel,
    onBack: () -> Unit,
    onSaleCreated: (String) -> Unit
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val currency = settings?.defaultCurrencyLabel ?: "ج.م"

    var selectedCustomerId by remember(preselectedCustomerId) { mutableStateOf(preselectedCustomerId ?: "") }
    var customerMenuExpanded by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var showProductPicker by remember { mutableStateOf(false) }
    var showQuickAddProductDialog by remember { mutableStateOf(false) }

    val lineRows = remember { mutableStateListOf(LineInputRow()) }
    var saleType by remember { mutableStateOf(SaleTypes.CASH) }
    var paymentMethod by remember { mutableStateOf(PaymentMethods.CASH) }
    var paidNowText by remember { mutableStateOf("") }
    var dueDateMillis by remember { mutableStateOf(EgyptTime.addDays(EgyptTime.nowMillis(), 7)) }
    var saleDateMillis by remember { mutableStateOf(EgyptTime.nowMillis()) }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val installmentRows = remember {
        mutableStateListOf(
            InstallmentInputRow(dueDateMillis = EgyptTime.addDays(EgyptTime.nowMillis(), 10), amountText = ""),
            InstallmentInputRow(dueDateMillis = EgyptTime.addDays(EgyptTime.nowMillis(), 20), amountText = "")
        )
    }

    var datePickerTarget by remember { mutableStateOf<String?>(null) } // "SALE", "DUE", or "INST:<idx>"
    var editingInstIndex by remember { mutableIntStateOf(-1) }
    // Stable idempotency key per draft prevents duplicate invoices on repeated button taps!
    var draftIdempotencyKey by remember { mutableStateOf(UUID.randomUUID().toString()) }

    val grandTotalPiasters = lineRows.sumOf { it.computedPiasters }
    val effectivePaidPiasters = when (saleType) {
        SaleTypes.CASH -> if (paidNowText.isBlank()) grandTotalPiasters else (MoneyFormatter.parseEgpToPiasters(paidNowText) ?: 0L)
        else -> if (paidNowText.isBlank()) 0L else (MoneyFormatter.parseEgpToPiasters(paidNowText) ?: 0L)
    }
    val remainingPiasters = (grandTotalPiasters - effectivePaidPiasters).coerceAtLeast(0L)
    val selectedCustomer = customers.firstOrNull { it.id == selectedCustomerId }
    val extras = HesabatyThemeExtras.colors

    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = "تسجيل عملية بيع",
                subtitle = "إدخال حر أو اختيار من الأصناف — حساب تلقائي فوري",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Select Customer
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("1. اختيار العميل *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showAddCustomerDialog = true }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("عميل جديد")
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = customerMenuExpanded,
                        onExpandedChange = { customerMenuExpanded = !customerMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCustomer?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("اختر العميل من القائمة") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("sale_customer_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = customerMenuExpanded,
                            onDismissRequest = { customerMenuExpanded = false }
                        ) {
                            if (customers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("لا يوجد عملاء — أضف عميلًا أولًا") },
                                    onClick = {
                                        customerMenuExpanded = false
                                        showAddCustomerDialog = true
                                    }
                                )
                            } else {
                                customers.forEach { c ->
                                    DropdownMenuItem(
                                        text = {
                                            Text("${c.name} (الرصيد: ${MoneyFormatter.formatEgp(c.currentBalancePiasters, currency)})")
                                        },
                                        onClick = {
                                            selectedCustomerId = c.id
                                            customerMenuExpanded = false
                                            errorMsg = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Flexible Sale Items (no product catalog mandatory)
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("2. تفاصيل البيع والمبالغ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { showProductPicker = true },
                                modifier = Modifier.testTag("sale_pick_product_btn")
                            ) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("من الأصناف")
                            }
                            TextButton(
                                onClick = { lineRows.add(LineInputRow()) },
                                modifier = Modifier.testTag("sale_add_line_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("بند حر")
                            }
                        }
                    }

                    Text(
                        text = "يمكنك إدخال المبلغ مباشرة أو اختيار صنف محفوظ لتعبئة اسمه وسعره تلقائيًا.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    lineRows.forEachIndexed { idx, row ->
                        if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = row.unitPriceText,
                                onValueChange = { newVal ->
                                    lineRows[idx] = row.copy(unitPriceText = newVal)
                                    errorMsg = null
                                },
                                label = { Text("السعر / المبلغ") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("sale_price_input_$idx")
                            )
                            OutlinedTextField(
                                value = row.quantityText,
                                onValueChange = { newVal ->
                                    lineRows[idx] = row.copy(quantityText = newVal)
                                    errorMsg = null
                                },
                                label = { Text("العدد") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(0.8f)
                                    .testTag("sale_qty_input_$idx")
                            )
                            if (lineRows.size > 1) {
                                IconButton(onClick = { lineRows.removeAt(idx) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف البند", tint = extras.danger)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = row.description,
                                onValueChange = { lineRows[idx] = row.copy(description = it) },
                                label = { Text("البيان / ماذا أخذ العميل؟ (اختياري)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sale_desc_input_$idx")
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text("الإجمالي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = MoneyFormatter.formatEgp(row.computedPiasters, currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("إجمالي الفاتورة:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = MoneyFormatter.formatEgp(grandTotalPiasters, currency),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("sale_grand_total_text")
                        )
                    }
                }
            }

            // 3. Sale Type (Cash / Credit / Scheduled)
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Text("3. نوع البيع والسداد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            SaleTypes.CASH to "بيع كاش",
                            SaleTypes.CREDIT to "بيع آجل",
                            SaleTypes.SCHEDULED to "مقسم على تواريخ"
                        ).forEach { (code, label) ->
                            FilterChip(
                                selected = saleType == code,
                                onClick = {
                                    saleType = code
                                    paidNowText = ""
                                    errorMsg = null
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("sale_type_$code")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("طريقة التحصيل (للمبلغ المستلم الآن):", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = paymentMethod == PaymentMethods.CASH,
                            onClick = { paymentMethod = PaymentMethods.CASH },
                            label = { Text("نقدي") },
                            modifier = Modifier.testTag("sale_method_cash")
                        )
                        FilterChip(
                            selected = paymentMethod == PaymentMethods.EWALLET,
                            onClick = { paymentMethod = PaymentMethods.EWALLET },
                            label = { Text("محفظة إلكترونية") },
                            modifier = Modifier.testTag("sale_method_ewallet")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = paidNowText,
                        onValueChange = { paidNowText = it; errorMsg = null },
                        label = {
                            Text(
                                if (saleType == SaleTypes.CASH) {
                                    "المبلغ المستلم (اتركه فارغًا لكامل الإجمالي)"
                                } else {
                                    "المبلغ المدفوع مقدمًا (اختياري)"
                                }
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("sale_paid_now_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("المدفوع: ${MoneyFormatter.formatEgp(effectivePaidPiasters, currency)}", color = extras.success, fontWeight = FontWeight.SemiBold)
                        Text("المتبقي: ${MoneyFormatter.formatEgp(remainingPiasters, currency)}", color = if (remainingPiasters > 0) extras.danger else extras.success, fontWeight = FontWeight.Bold)
                    }

                    if (saleType == SaleTypes.CREDIT) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { datePickerTarget = "DUE" },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تاريخ الاستحقاق: ${EgyptTime.formatDate(dueDateMillis)}")
                        }
                    }

                    if (saleType == SaleTypes.SCHEDULED) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("جدول الدفعات المجدولة (بدون فوائد)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            TextButton(
                                onClick = {
                                    if (remainingPiasters > 0 && installmentRows.isNotEmpty()) {
                                        val count = installmentRows.size
                                        val base = remainingPiasters / count
                                        val rem = remainingPiasters - base * count
                                        installmentRows.indices.forEach { i ->
                                            val amt = if (i == count - 1) base + rem else base
                                            installmentRows[i] = installmentRows[i].copy(
                                                amountText = MoneyFormatter.formatEgpPlainInput(amt)
                                            )
                                        }
                                    }
                                }
                            ) {
                                Text("تقسيم تلقائي")
                            }
                        }

                        installmentRows.forEachIndexed { idx, inst ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = inst.amountText,
                                    onValueChange = { installmentRows[idx] = inst.copy(amountText = it); errorMsg = null },
                                    label = { Text("دفعة #${idx + 1}") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = {
                                        editingInstIndex = idx
                                        datePickerTarget = "INST"
                                    }
                                ) {
                                    Text(EgyptTime.formatDate(inst.dueDateMillis))
                                }
                                if (installmentRows.size > 1) {
                                    IconButton(onClick = { installmentRows.removeAt(idx) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الدفعة", tint = extras.danger)
                                    }
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                val lastDate = installmentRows.lastOrNull()?.dueDateMillis ?: EgyptTime.nowMillis()
                                installmentRows.add(
                                    InstallmentInputRow(dueDateMillis = EgyptTime.addDays(lastDate, 10), amountText = "")
                                )
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("إضافة موعد دفعة آخر")
                        }
                    }
                }
            }

            // 4. Date & Notes + Save Button
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(title = "4. التاريخ والملاحظات")
                    OutlinedButton(
                        onClick = { datePickerTarget = "SALE" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تاريخ العملية: ${EgyptTime.formatDateTime(saleDateMillis)}")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات إضافية على الفاتورة (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMsg != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (selectedCustomerId.isBlank()) {
                                errorMsg = "يرجى اختيار العميل أولًا"
                                return@Button
                            }
                            val parsedLines = mutableListOf<SaleLineDraft>()
                            for (row in lineRows) {
                                val price = MoneyFormatter.parseEgpToPiasters(row.unitPriceText)
                                if (price == null || price <= 0L) {
                                    errorMsg = "يرجى إدخال سعر أو مبلغ صحيح أكبر من صفر في كل بند"
                                    return@Button
                                }
                                val qty = if (row.quantityText.isBlank()) 1000L else MoneyFormatter.parseQuantityToMilli(row.quantityText)
                                if (qty == null || qty <= 0L) {
                                    errorMsg = "يرجى إدخال عدد صحيح أكبر من صفر"
                                    return@Button
                                }
                                parsedLines.add(SaleLineDraft(row.description, price, qty, row.productId))
                            }

                            val parsedInstallments = if (saleType == SaleTypes.SCHEDULED) {
                                installmentRows.map { r ->
                                    val amt = MoneyFormatter.parseEgpToPiasters(r.amountText) ?: 0L
                                    InstallmentDraft(r.dueDateMillis, amt)
                                }
                            } else emptyList()

                            viewModel.createSalesInvoice(
                                customerId = selectedCustomerId,
                                lines = parsedLines,
                                saleType = saleType,
                                paymentMethod = paymentMethod,
                                paidNowPiasters = effectivePaidPiasters,
                                dueDateMillis = if (saleType == SaleTypes.CREDIT) dueDateMillis else null,
                                installments = parsedInstallments,
                                notes = notes,
                                occurredAtMillis = saleDateMillis,
                                idempotencyKey = draftIdempotencyKey
                            ) { ok, resultOrErr ->
                                if (ok) {
                                    draftIdempotencyKey = UUID.randomUUID().toString()
                                    onSaleCreated(resultOrErr)
                                } else {
                                    errorMsg = resultOrErr
                                }
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_sale_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ وإصدار الفاتورة", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    datePickerTarget?.let { target ->
        val initial = when (target) {
            "DUE" -> dueDateMillis
            "INST" -> installmentRows.getOrNull(editingInstIndex)?.dueDateMillis ?: EgyptTime.nowMillis()
            else -> saleDateMillis
        }
        HesabatyDatePickerDialog(
            initialMillis = initial,
            onDismiss = { datePickerTarget = null },
            onDateSelected = { chosen ->
                when (target) {
                    "DUE" -> dueDateMillis = chosen
                    "SALE" -> saleDateMillis = chosen
                    "INST" -> if (editingInstIndex in installmentRows.indices) {
                        installmentRows[editingInstIndex] = installmentRows[editingInstIndex].copy(dueDateMillis = chosen)
                    }
                }
            }
        )
    }

    if (showAddCustomerDialog) {
        AddCustomerDialog(
            onDismiss = { showAddCustomerDialog = false },
            onSave = { name, phone, wa, addr, cNotes, opening, creditLimit, onErr ->
                viewModel.addCustomer(name, phone, wa, addr, cNotes, opening, creditLimit) { ok, idOrErr ->
                    if (ok) {
                        selectedCustomerId = idOrErr
                        showAddCustomerDialog = false
                    } else onErr(idOrErr)
                }
            }
        )
    }

    if (showProductPicker) {
        ProductPickerDialog(
            products = products,
            currency = currency,
            onDismiss = { showProductPicker = false },
            onSelect = { prod ->
                val priceStr = if (prod.salePricePiasters > 0L) MoneyFormatter.formatEgpPlainInput(prod.salePricePiasters) else ""
                val emptyIndex = lineRows.indexOfFirst { it.description.isBlank() && it.unitPriceText.isBlank() }
                if (emptyIndex >= 0) {
                    lineRows[emptyIndex] = lineRows[emptyIndex].copy(
                        description = prod.name,
                        unitPriceText = priceStr,
                        quantityText = "1",
                        productId = prod.id
                    )
                } else {
                    lineRows.add(
                        LineInputRow(
                            description = prod.name,
                            unitPriceText = priceStr,
                            quantityText = "1",
                            productId = prod.id
                        )
                    )
                }
                errorMsg = null
                showProductPicker = false
            },
            onQuickAddNew = {
                showProductPicker = false
                showQuickAddProductDialog = true
            }
        )
    }

    if (showQuickAddProductDialog) {
        ProductFormDialog(
            initial = null,
            onDismiss = { showQuickAddProductDialog = false },
            onSave = { name, cat, sku, unit, saleP, buyP, stockM, track, minM, pNotes, onErr ->
                viewModel.addProduct(name, cat, sku, unit, saleP, buyP, stockM, track, minM, pNotes) { ok, newIdOrErr ->
                    if (ok) {
                        val priceStr = if (saleP > 0L) MoneyFormatter.formatEgpPlainInput(saleP) else ""
                        val emptyIndex = lineRows.indexOfFirst { it.description.isBlank() && it.unitPriceText.isBlank() }
                        if (emptyIndex >= 0) {
                            lineRows[emptyIndex] = lineRows[emptyIndex].copy(
                                description = name.trim(),
                                unitPriceText = priceStr,
                                quantityText = "1",
                                productId = newIdOrErr
                            )
                        } else {
                            lineRows.add(
                                LineInputRow(
                                    description = name.trim(),
                                    unitPriceText = priceStr,
                                    quantityText = "1",
                                    productId = newIdOrErr
                                )
                            )
                        }
                        showQuickAddProductDialog = false
                    } else onErr(newIdOrErr)
                }
            }
        )
    }
}
