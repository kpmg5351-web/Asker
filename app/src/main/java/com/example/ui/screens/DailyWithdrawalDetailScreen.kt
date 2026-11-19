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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.EgyptTime
import com.example.core.MoneyFormatter
import com.example.core.PdfInvoiceGenerator
import com.example.core.WhatsAppHelper
import com.example.data.local.DailyInvoiceStatuses
import com.example.data.local.DailyWithdrawalItemEntity
import com.example.data.local.PaymentMethods
import com.example.data.local.ProductEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusBadge
import com.example.ui.components.SummaryStatPill
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel
import java.util.UUID

@Composable
fun DailyWithdrawalDetailScreen(
    dailyInvoiceId: String,
    viewModel: HesabatyViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val daily by remember(dailyInvoiceId) { viewModel.observeDailyInvoice(dailyInvoiceId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val items by remember(dailyInvoiceId) { viewModel.observeDailyItems(dailyInvoiceId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val tenant by viewModel.activeTenant.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val currency = settings?.defaultCurrencyLabel ?: "ج.م"
    val waCountry = settings?.whatsappCountryCode ?: "20"
    val extras = HesabatyThemeExtras.colors

    var showAddWithdrawalDialog by remember { mutableStateOf(false) }
    var showPayDialog by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    var showReopenDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<DailyWithdrawalItemEntity?>(null) }

    val currentDaily = daily
    val customer = customers.firstOrNull { it.id == currentDaily?.customerId }
    val isOpen = currentDaily?.status == DailyInvoiceStatuses.OPEN

    fun buildDailyPrintModel(): PdfInvoiceGenerator.InvoicePrintModel? {
        val d = currentDaily ?: return null
        return PdfInvoiceGenerator.InvoicePrintModel(
            shopName = tenant?.shopName ?: "حساباتي",
            shopAddress = tenant?.address.orEmpty(),
            shopPhone = tenant?.phone.orEmpty(),
            customerName = customer?.name ?: "عميل",
            invoiceNumber = d.invoiceNumber,
            dateFormatted = EgyptTime.formatEpochDay(d.invoiceDateEpochDay),
            invoiceTypeLabel = "فاتورة سحب يومي",
            paymentMethodLabel = if (d.paidPiasters > 0) "مدفوع جزئيًا / نقدي" else "حساب سحب يومي",
            lines = if (items.isEmpty()) {
                listOf(
                    PdfInvoiceGenerator.InvoiceLineRow(
                        description = "فاتورة سحب يومي",
                        unitPricePiasters = d.totalPiasters,
                        quantityMilli = 1000L,
                        lineTotalPiasters = d.totalPiasters
                    )
                )
            } else {
                items.map {
                    PdfInvoiceGenerator.InvoiceLineRow(
                        description = it.description,
                        unitPricePiasters = it.unitPricePiasters,
                        quantityMilli = it.quantityMilli,
                        lineTotalPiasters = it.lineTotalPiasters,
                        timeLabel = EgyptTime.formatTimeHms24(it.recordedAtMillis)
                    )
                }
            },
            totalPiasters = d.totalPiasters,
            paidPiasters = d.paidPiasters,
            remainingPiasters = d.remainingPiasters,
            signatureName = settings?.invoiceSignatureName.orEmpty()
        )
    }

    fun buildDailyWhatsAppCaption(): String {
        val d = currentDaily ?: return ""
        return WhatsAppHelper.buildCustomerInvoiceMessage(
            customerName = customer?.name ?: "عميلنا العزيز",
            shopName = tenant?.shopName ?: "المحل",
            invoiceNumber = d.invoiceNumber,
            dateFormatted = EgyptTime.formatEpochDay(d.invoiceDateEpochDay),
            totalPiasters = d.totalPiasters,
            paidPiasters = d.paidPiasters,
            remainingPiasters = d.remainingPiasters,
            invoiceTypeLabel = "فاتورة السحب اليومي"
        )
    }

    fun shareDailyInvoiceImageViaWhatsApp() {
        val printModel = buildDailyPrintModel() ?: return
        val (_, uri) = PdfInvoiceGenerator.generateInvoicePng(context, printModel)
        val res = WhatsAppHelper.shareFileViaWhatsAppOrChooser(
            context = context,
            fileUri = uri,
            mimeType = "image/png",
            caption = buildDailyWhatsAppCaption(),
            rawPhone = customer?.whatsappPhone?.ifBlank { customer.phone },
            countryCode = waCountry
        )
        if (!res.launched && res.errorMessage != null) viewModel.emitMessage(res.errorMessage, true)
    }

    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = currentDaily?.invoiceNumber ?: "فاتورة سحب يومي",
                subtitle = customer?.name,
                onBack = onBack,
                actions = {
                    if (currentDaily != null) {
                        IconButton(
                            onClick = {
                                val printModel = buildDailyPrintModel() ?: return@IconButton
                                val (_, uri) = PdfInvoiceGenerator.generateInvoicePdf(context, printModel)
                                val res = WhatsAppHelper.shareFileViaWhatsAppOrChooser(
                                    context = context,
                                    fileUri = uri,
                                    mimeType = "application/pdf",
                                    caption = buildDailyWhatsAppCaption(),
                                    rawPhone = customer?.whatsappPhone?.ifBlank { customer.phone },
                                    countryCode = waCountry
                                )
                                if (!res.launched && res.errorMessage != null) viewModel.emitMessage(res.errorMessage, true)
                            },
                            modifier = Modifier.testTag("daily_pdf_btn")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF")
                        }
                        IconButton(
                            onClick = { shareDailyInvoiceImageViaWhatsApp() },
                            modifier = Modifier.testTag("daily_whatsapp_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال صورة الفاتورة عبر WhatsApp", tint = extras.success)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (currentDaily == null) {
            EmptyStateView(
                icon = Icons.Default.Schedule,
                title = "جارٍ تحميل الفاتورة اليومية...",
                subtitle = "",
                modifier = Modifier.padding(innerPadding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary Header Card
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(customer?.name ?: "عميل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "التاريخ: ${EgyptTime.formatEpochDay(currentDaily.invoiceDateEpochDay)}  •  عدد السحوبات: ${currentDaily.withdrawalsCount}  •  إجمالي الكمية: ${MoneyFormatter.formatQuantity(currentDaily.totalQuantityMilli)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
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
                        SummaryStatPill("الإجمالي", MoneyFormatter.formatEgp(currentDaily.totalPiasters, currency), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        SummaryStatPill("المدفوع", MoneyFormatter.formatEgp(currentDaily.paidPiasters, currency), extras.success, Modifier.weight(1f))
                        SummaryStatPill("المتبقي", MoneyFormatter.formatEgp(currentDaily.remainingPiasters, currency), if (currentDaily.remainingPiasters > 0) extras.danger else extras.success, Modifier.weight(1f))
                    }
                }
            }

            // Action Buttons
            item {
                if (isOpen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddWithdrawalDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("daily_add_item_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة سحبة", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = { showPayDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("daily_pay_btn")
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تسجيل دفعة", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showCloseConfirm = true },
                        enabled = items.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("daily_close_btn")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إغلاق الفاتورة اليومية وترحيلها لحساب العميل", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showPayDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text("تسجيل دفعة", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { showReopenDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("daily_reopen_btn")
                        ) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إعادة فتح (بسبب)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { shareDailyInvoiceImageViaWhatsApp() },
                    colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("daily_whatsapp_card_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال فاتورة السحب عبر واتساب (صورة + رسالة)", fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            item {
                SectionHeader(title = "سجل السحوبات بالتوقيت الدقيق (${items.size})")
            }

            if (items.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.Schedule,
                        title = "لا توجد سحوبات مسجلة بعد",
                        subtitle = "اضغط \"إضافة سحبة\" لتسجيل كل عملية سحب بالتاريخ والساعة والدقيقة والثانية."
                    )
                }
            } else {
                items(items, key = { it.id }) { item ->
                    HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "السعر: ${MoneyFormatter.formatEgp(item.unitPricePiasters, currency)} × العدد: ${MoneyFormatter.formatQuantity(item.quantityMilli)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "الوقت: ${EgyptTime.formatDate(item.recordedAtMillis)}  —  ${EgyptTime.formatTimeHms24(item.recordedAtMillis)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (item.notes.isNotBlank()) {
                                    Text(item.notes, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = MoneyFormatter.formatEgp(item.lineTotalPiasters, currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (isOpen) {
                                    IconButton(onClick = { editingItem = item }) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل السحبة", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddWithdrawalDialog) {
        AddDailyItemDialog(
            products = products,
            currency = currency,
            onDismiss = { showAddWithdrawalDialog = false },
            onQuickAddProduct = { name, cat, sku, unit, saleP, buyP, stockM, track, minM, pNotes, onDone ->
                viewModel.addProduct(name, cat, sku, unit, saleP, buyP, stockM, track, minM, pNotes, onDone)
            },
            onSave = { desc, price, qty, notes, prodId, onErr ->
                viewModel.addDailyWithdrawalItem(dailyInvoiceId, desc, price, qty, notes, prodId) { ok, msg ->
                    if (ok) showAddWithdrawalDialog = false else onErr(msg)
                }
            }
        )
    }

    editingItem?.let { item ->
        EditDailyItemDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = { desc, price, qty, notes, reason, onErr ->
                viewModel.editDailyWithdrawalItem(item.id, dailyInvoiceId, desc, price, qty, notes, reason) { ok, msg ->
                    if (ok) editingItem = null else onErr(msg)
                }
            }
        )
    }

    if (showPayDialog) {
        DailyPaymentDialog(
            onDismiss = { showPayDialog = false },
            onConfirm = { amt, method, notes, onErr ->
                viewModel.recordDailyInvoicePayment(
                    dailyInvoiceId, amt, method, notes, UUID.randomUUID().toString()
                ) { ok, msg ->
                    if (ok) showPayDialog = false else onErr(msg)
                }
            }
        )
    }

    if (showCloseConfirm) {
        AlertDialog(
            onDismissRequest = { showCloseConfirm = false },
            title = { Text("تأكيد إغلاق الفاتورة اليومية", fontWeight = FontWeight.Bold) },
            text = {
                Text("سيتم إغلاق الفاتورة بإجمالي ${MoneyFormatter.formatEgp(currentDaily?.totalPiasters ?: 0L, currency)} وترحيل المبلغ لحساب العميل مرة واحدة فقط دون تكرار.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseConfirm = false
                        viewModel.closeDailyInvoice(dailyInvoiceId)
                    }
                ) { Text("تأكيد الإغلاق") }
            },
            dismissButton = {
                TextButton(onClick = { showCloseConfirm = false }) { Text("إلغاء") }
            }
        )
    }

    if (showReopenDialog) {
        var reason by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showReopenDialog = false },
            title = { Text("إعادة فتح الفاتورة اليومية", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("سيتم تسجيل إعادة الفتح في سجل المراجعة. عند الإغلاق مجددًا سيتم احتساب الفارق فقط دون تكرار.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it; err = null },
                        label = { Text("سبب إعادة الفتح *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.reopenDailyInvoice(dailyInvoiceId, reason) { ok, msg ->
                            if (ok) showReopenDialog = false else err = msg
                        }
                    }
                ) { Text("إعادة فتح") }
            },
            dismissButton = {
                TextButton(onClick = { showReopenDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun AddDailyItemDialog(
    products: List<ProductEntity>,
    currency: String,
    onDismiss: () -> Unit,
    onQuickAddProduct: (String, String, String, String, Long, Long, Long, Boolean, Long, String, (Boolean, String) -> Unit) -> Unit,
    onSave: (String, Long, Long, String, String?, (String) -> Unit) -> Unit
) {
    var desc by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showQuickAdd by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    val previewTotal = remember(priceText, qtyText) {
        val p = MoneyFormatter.parseEgpToPiasters(priceText) ?: 0L
        val q = MoneyFormatter.parseQuantityToMilli(qtyText) ?: 1000L
        MoneyFormatter.calculateLineTotalPiasters(p, q)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل سحبة جديدة", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().testTag("daily_item_pick_product_btn")
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (products.isEmpty()) "اختيار أو إضافة صنف (اختياري)"
                        else "اختيار من الأصناف (${products.size})"
                    )
                }
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it; selectedProductId = null },
                    label = { Text("البيان / ماذا أخذ العميل؟ (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it; err = null },
                        label = { Text("السعر *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("daily_item_price")
                    )
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it; err = null },
                        label = { Text("العدد *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(0.8f).testTag("daily_item_qty")
                    )
                }
                Text(
                    text = "الإجمالي التلقائي: ${MoneyFormatter.formatEgp(previewTotal, currency)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = MoneyFormatter.parseEgpToPiasters(priceText)
                    val q = MoneyFormatter.parseQuantityToMilli(qtyText)
                    if (p == null || p <= 0L) { err = "أدخل سعرًا صحيحًا"; return@Button }
                    if (q == null || q <= 0L) { err = "أدخل عددًا صحيحًا"; return@Button }
                    onSave(desc, p, q, notes, selectedProductId) { err = it }
                },
                modifier = Modifier.testTag("daily_item_save_btn")
            ) { Text("تسجيل السحبة") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )

    if (showPicker) {
        ProductPickerDialog(
            products = products,
            currency = currency,
            onDismiss = { showPicker = false },
            onSelect = { prod ->
                desc = prod.name
                priceText = if (prod.salePricePiasters > 0L) MoneyFormatter.formatEgpPlainInput(prod.salePricePiasters) else ""
                selectedProductId = prod.id
                err = null
                showPicker = false
            },
            onQuickAddNew = {
                showPicker = false
                showQuickAdd = true
            }
        )
    }

    if (showQuickAdd) {
        ProductFormDialog(
            initial = null,
            onDismiss = { showQuickAdd = false },
            onSave = { name, cat, sku, unit, saleP, buyP, stockM, track, minM, pNotes, onErr ->
                onQuickAddProduct(name, cat, sku, unit, saleP, buyP, stockM, track, minM, pNotes) { ok, idOrErr ->
                    if (ok) {
                        desc = name.trim()
                        priceText = if (saleP > 0L) MoneyFormatter.formatEgpPlainInput(saleP) else ""
                        selectedProductId = idOrErr
                        showQuickAdd = false
                    } else onErr(idOrErr)
                }
            }
        )
    }
}

@Composable
private fun EditDailyItemDialog(
    item: DailyWithdrawalItemEntity,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long, String, String, (String) -> Unit) -> Unit
) {
    var desc by remember { mutableStateOf(item.description) }
    var priceText by remember { mutableStateOf(MoneyFormatter.formatEgpPlainInput(item.unitPricePiasters)) }
    var qtyText by remember { mutableStateOf(MoneyFormatter.formatQuantity(item.quantityMilli)) }
    var notes by remember { mutableStateOf(item.notes) }
    var reason by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل سحبة (مسجّل بالمراجعة)", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("البيان") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it; err = null },
                        label = { Text("السعر") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it; err = null },
                        label = { Text("العدد") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(0.8f)
                    )
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") }, singleLine = true)
                OutlinedTextField(value = reason, onValueChange = { reason = it; err = null }, label = { Text("سبب التعديل *") }, singleLine = true)
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = MoneyFormatter.parseEgpToPiasters(priceText)
                    val q = MoneyFormatter.parseQuantityToMilli(qtyText)
                    if (p == null || p <= 0L || q == null || q <= 0L) { err = "تحقق من السعر والعدد"; return@Button }
                    if (reason.trim().length < 2) { err = "اذكر سبب التعديل"; return@Button }
                    onSave(desc, p, q, notes, reason) { err = it }
                }
            ) { Text("حفظ التعديل") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
private fun DailyPaymentDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long, String, String, (String) -> Unit) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PaymentMethods.CASH) }
    var notes by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسجيل دفعة على الفاتورة اليومية", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; err = null },
                    label = { Text("المبلغ المدفوع (جنيه) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = method == PaymentMethods.CASH, onClick = { method = PaymentMethods.CASH }, label = { Text("نقدي") })
                    FilterChip(selected = method == PaymentMethods.EWALLET, onClick = { method = PaymentMethods.EWALLET }, label = { Text("محفظة إلكترونية") })
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = MoneyFormatter.parseEgpToPiasters(amountText)
                    if (p == null || p <= 0L) { err = "أدخل مبلغًا صحيحًا"; return@Button }
                    onConfirm(p, method, notes) { err = it }
                }
            ) { Text("تأكيد الدفعة") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
