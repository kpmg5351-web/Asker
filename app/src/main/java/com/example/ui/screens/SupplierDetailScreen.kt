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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.EgyptTime
import com.example.core.MoneyFormatter
import com.example.core.PdfInvoiceGenerator
import com.example.core.WhatsAppHelper
import com.example.data.local.PaymentMethods
import com.example.data.local.SupplierTransactionEntity
import com.example.data.local.SupplierTxTypes
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.SummaryStatPill
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel
import java.util.UUID

@Composable
fun SupplierDetailScreen(
    supplierId: String,
    viewModel: HesabatyViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val supplier by remember(supplierId) { viewModel.observeSupplier(supplierId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val transactions by remember(supplierId) { viewModel.observeSupplierTransactions(supplierId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val tenant by viewModel.activeTenant.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val currency = settings?.defaultCurrencyLabel ?: "ج.م"
    val waCountry = settings?.whatsappCountryCode ?: "20"

    var txDialogMode by remember { mutableStateOf<String?>(null) }
    var inspectTx by remember { mutableStateOf<SupplierTransactionEntity?>(null) }
    val extras = HesabatyThemeExtras.colors

    fun buildSupplierReportModel(): PdfInvoiceGenerator.ReportPrintModel? {
        val s = supplier ?: return null
        return PdfInvoiceGenerator.ReportPrintModel(
            title = "كشف حساب مورد: ${s.name}",
            shopName = tenant?.shopName ?: "حساباتي",
            periodLabel = EgyptTime.formatDate(EgyptTime.nowMillis()),
            summaryPairs = listOf(
                "إجمالي المستحقات" to MoneyFormatter.formatEgp(s.totalDuePiasters, currency),
                "إجمالي المدفوعات" to MoneyFormatter.formatEgp(s.totalPaidPiasters, currency),
                "الرصيد المتبقي" to MoneyFormatter.formatEgp(s.currentBalancePiasters, currency)
            ),
            columns = listOf("التاريخ", "نوع الحركة", "الطريقة", "المبلغ", "الرصيد"),
            rows = transactions.map { tx ->
                listOf(
                    EgyptTime.formatDateTime(tx.createdAt),
                    SupplierTxTypes.arabicLabel(tx.type),
                    PaymentMethods.arabicLabel(tx.paymentMethod),
                    MoneyFormatter.formatEgp(tx.amountPiasters, ""),
                    MoneyFormatter.formatEgp(tx.runningBalancePiasters, "")
                )
            }
        )
    }

    fun buildSupplierWhatsAppCaption(): String {
        val s = supplier ?: return ""
        return WhatsAppHelper.buildSupplierStatementMessage(
            supplierName = s.name,
            shopName = tenant?.shopName ?: "المحل",
            totalDuePiasters = s.totalDuePiasters,
            totalPaidPiasters = s.totalPaidPiasters,
            currentBalancePiasters = s.currentBalancePiasters,
            dateFormatted = EgyptTime.formatDate(EgyptTime.nowMillis())
        )
    }

    fun shareSupplierStatementImageViaWhatsApp() {
        val s = supplier ?: return
        val report = buildSupplierReportModel() ?: return
        val (_, uri) = PdfInvoiceGenerator.generateReportInvoicePng(
            context = context,
            model = report,
            recipientName = s.name,
            recipientLabel = "السادة / المورد",
            shopAddress = tenant?.address.orEmpty(),
            shopPhone = tenant?.phone.orEmpty(),
            documentBadge = "كشف حساب مورد",
            statusRemainingPiasters = s.currentBalancePiasters,
            footerNote = "مع خالص التقدير والاحترام ودوام التعاون المثمر — ${tenant?.shopName ?: "حساباتي"}"
        )
        val res = WhatsAppHelper.shareFileViaWhatsAppOrChooser(
            context = context,
            fileUri = uri,
            mimeType = "image/png",
            caption = buildSupplierWhatsAppCaption(),
            rawPhone = s.whatsappPhone.ifBlank { s.phone },
            countryCode = waCountry
        )
        if (!res.launched && res.errorMessage != null) {
            viewModel.emitMessage(res.errorMessage, true)
        }
    }

    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = supplier?.name ?: "حساب المورد",
                subtitle = supplier?.phone?.takeIf { it.isNotBlank() },
                onBack = onBack,
                actions = {
                    if (supplier != null) {
                        IconButton(
                            onClick = {
                                val s = supplier!!
                                val report = buildSupplierReportModel() ?: return@IconButton
                                val (_, uri) = PdfInvoiceGenerator.generateReportPdf(context, report)
                                val res = WhatsAppHelper.shareFileViaWhatsAppOrChooser(
                                    context = context,
                                    fileUri = uri,
                                    mimeType = "application/pdf",
                                    caption = buildSupplierWhatsAppCaption(),
                                    rawPhone = s.whatsappPhone.ifBlank { s.phone },
                                    countryCode = waCountry
                                )
                                if (!res.launched && res.errorMessage != null) viewModel.emitMessage(res.errorMessage, true)
                            },
                            modifier = Modifier.testTag("supplier_export_pdf_btn")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF")
                        }
                        IconButton(
                            onClick = { shareSupplierStatementImageViaWhatsApp() },
                            modifier = Modifier.testTag("supplier_whatsapp_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال كشف الحساب صورة عبر WhatsApp", tint = extras.success)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val s = supplier
        if (s == null) {
            EmptyStateView(
                icon = Icons.Default.Receipt,
                title = "جارٍ تحميل بيانات المورد...",
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
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    if (s.address.isNotBlank() || s.notes.isNotBlank()) {
                        if (s.address.isNotBlank()) {
                            Text("العنوان: ${s.address}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (s.notes.isNotBlank()) {
                            Text(
                                "ملاحظات: ${s.notes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryStatPill(
                            label = "إجمالي المستحق",
                            value = MoneyFormatter.formatEgp(s.totalDuePiasters, currency),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatPill(
                            label = "إجمالي المدفوع",
                            value = MoneyFormatter.formatEgp(s.totalPaidPiasters, currency),
                            valueColor = extras.success,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatPill(
                            label = "الرصيد المتبقي",
                            value = MoneyFormatter.formatEgp(s.currentBalancePiasters, currency),
                            valueColor = if (s.currentBalancePiasters > 0) extras.danger else extras.success,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { txDialogMode = SupplierTxTypes.DEBT_ADD },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("supplier_add_debt_btn")
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة مديونية")
                    }
                    Button(
                        onClick = { txDialogMode = SupplierTxTypes.PAYMENT_PARTIAL },
                        colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("supplier_pay_btn")
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تسجيل دفعة")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (s.currentBalancePiasters > 0L) {
                        OutlinedButton(
                            onClick = { txDialogMode = SupplierTxTypes.PAYMENT_FULL },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("سداد كامل الرصيد")
                        }
                    }
                    OutlinedButton(
                        onClick = { txDialogMode = SupplierTxTypes.ADJUSTMENT },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("تسوية مراجعة")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { shareSupplierStatementImageViaWhatsApp() },
                    colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("supplier_whatsapp_statement_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال كشف حساب المورد عبر واتساب (صورة فاتورة)", fontWeight = FontWeight.Bold)
                }
            }

            item {
                SectionHeader(title = "كشف حساب المورد (${transactions.size} حركة)")
            }

            if (transactions.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.Receipt,
                        title = "لا توجد حركات مالية بعد",
                        subtitle = "سجّل أول مشتريات أو دفعة للمورد لتظهر هنا بالتاريخ والساعة."
                    )
                }
            } else {
                items(transactions, key = { it.id }) { tx ->
                    val isDebt = tx.signedDeltaPiasters >= 0L
                    HesabatyCard(
                        onClick = { inspectTx = tx },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = SupplierTxTypes.arabicLabel(tx.type),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${EgyptTime.formatDateTime(tx.createdAt)}  •  ${PaymentMethods.arabicLabel(tx.paymentMethod)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (tx.notes.isNotBlank()) {
                                    Text(
                                        text = tx.notes,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = (if (isDebt) "+ " else "- ") + MoneyFormatter.formatEgp(tx.amountPiasters, currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDebt) extras.danger else extras.success
                                )
                                Text(
                                    text = "الرصيد: ${MoneyFormatter.formatEgp(tx.runningBalancePiasters, currency)}",
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

    txDialogMode?.let { mode ->
        val s = supplier ?: return@let
        SupplierTxDialog(
            mode = mode,
            currentBalancePiasters = s.currentBalancePiasters,
            onDismiss = { txDialogMode = null },
            onSubmit = { amount, method, notes, isNegAdj, onErr ->
                viewModel.recordSupplierTransaction(
                    supplierId = supplierId,
                    type = mode,
                    amountPiasters = amount,
                    paymentMethod = method,
                    notes = notes,
                    idempotencyKey = UUID.randomUUID().toString(),
                    isNegativeAdjustment = isNegAdj
                ) { ok, msg ->
                    if (ok) txDialogMode = null else onErr(msg)
                }
            }
        )
    }

    inspectTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { inspectTx = null },
            title = { Text(SupplierTxTypes.arabicLabel(tx.type), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("المبلغ: ${MoneyFormatter.formatEgp(tx.amountPiasters, currency)}")
                    Text("الرصيد بعد الحركة: ${MoneyFormatter.formatEgp(tx.runningBalancePiasters, currency)}")
                    Text("طريقة الدفع: ${PaymentMethods.arabicLabel(tx.paymentMethod)}")
                    Text("التاريخ والوقت: ${EgyptTime.formatDateTimeSeconds(tx.createdAt)}")
                    if (tx.notes.isNotBlank()) Text("ملاحظات: ${tx.notes}")
                    Text(
                        "ملاحظة محاسبية: للحفاظ على سلامة الرصيد لا تُحذف الحركات عشوائيًا؛ لأي تصحيح استخدم تسوية مراجعة.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectTx = null }) { Text("إغلاق") }
            }
        )
    }
}

@Composable
private fun SupplierTxDialog(
    mode: String,
    currentBalancePiasters: Long,
    onDismiss: () -> Unit,
    onSubmit: (Long, String, String, Boolean, (String) -> Unit) -> Unit
) {
    var amountText by remember {
        mutableStateOf(
            if (mode == SupplierTxTypes.PAYMENT_FULL && currentBalancePiasters > 0L) {
                MoneyFormatter.formatEgpPlainInput(currentBalancePiasters)
            } else ""
        )
    }
    var method by remember { mutableStateOf(PaymentMethods.CASH) }
    var notes by remember { mutableStateOf("") }
    var isNegativeAdj by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    val title = when (mode) {
        SupplierTxTypes.DEBT_ADD -> "إضافة مديونية / مشتريات للمورد"
        SupplierTxTypes.PAYMENT_FULL -> "سداد كامل رصيد المورد"
        SupplierTxTypes.ADJUSTMENT -> "تسوية مراجعة لحساب المورد"
        else -> "تسجيل دفعة للمورد"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; err = null },
                    label = { Text("المبلغ (جنيه مصري) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("supplier_tx_amount_input")
                )

                if (mode == SupplierTxTypes.ADJUSTMENT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isNegativeAdj,
                            onClick = { isNegativeAdj = false },
                            label = { Text("زيادة مستحقات (+)") }
                        )
                        FilterChip(
                            selected = isNegativeAdj,
                            onClick = { isNegativeAdj = true },
                            label = { Text("تخفيض مستحقات (−)") }
                        )
                    }
                }

                if (mode != SupplierTxTypes.DEBT_ADD) {
                    Text("طريقة الدفع:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            PaymentMethods.CASH to "نقدي",
                            PaymentMethods.EWALLET to "محفظة",
                            PaymentMethods.BANK_TRANSFER to "تحويل بنكي"
                        ).forEach { (code, label) ->
                            FilterChip(
                                selected = method == code,
                                onClick = { method = code },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(if (mode == SupplierTxTypes.ADJUSTMENT) "سبب التسوية *" else "ملاحظات (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val piasters = MoneyFormatter.parseEgpToPiasters(amountText)
                    if (piasters == null || piasters <= 0L) {
                        err = "يرجى إدخال مبلغ صحيح أكبر من صفر"
                        return@Button
                    }
                    if (mode == SupplierTxTypes.ADJUSTMENT && notes.trim().length < 3) {
                        err = "يرجى كتابة سبب التسوية لتسجيله في سجل المراجعة"
                        return@Button
                    }
                    onSubmit(piasters, method, notes, isNegativeAdj) { err = it }
                },
                modifier = Modifier.testTag("supplier_tx_confirm_btn")
            ) {
                Text("تأكيد وتسجيل")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
