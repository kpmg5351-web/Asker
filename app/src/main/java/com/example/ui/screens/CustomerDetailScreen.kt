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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import com.example.data.local.CustomerAccountStatus
import com.example.data.local.CustomerTransactionEntity
import com.example.data.local.CustomerTxTypes
import com.example.data.local.InstallmentStatuses
import com.example.data.local.PaymentMethods
import com.example.data.local.SaleTypes
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.StatusBadge
import com.example.ui.components.SummaryStatPill
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel

@Composable
fun CustomerDetailScreen(
    customerId: String,
    viewModel: HesabatyViewModel,
    onBack: () -> Unit,
    onNewSaleForCustomer: (String) -> Unit,
    onCollectForCustomer: (String) -> Unit,
    onOpenInvoice: (String) -> Unit,
    onOpenDailyInvoice: (String) -> Unit
) {
    val context = LocalContext.current
    val customer by remember(customerId) { viewModel.observeCustomer(customerId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val invoices by remember(customerId) { viewModel.observeCustomerInvoices(customerId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val payments by remember(customerId) { viewModel.observeCustomerPayments(customerId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val transactions by remember(customerId) { viewModel.observeCustomerTransactions(customerId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val installments by remember(customerId) { viewModel.observeCustomerInstallments(customerId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val tenant by viewModel.activeTenant.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val currency = settings?.defaultCurrencyLabel ?: "ج.م"
    val waCountry = settings?.whatsappCountryCode ?: "20"

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var inspectTx by remember { mutableStateOf<CustomerTransactionEntity?>(null) }
    var showEditCreditLimitDialog by remember { mutableStateOf(false) }
    val extras = HesabatyThemeExtras.colors

    fun buildCustomerReportModel(): PdfInvoiceGenerator.ReportPrintModel? {
        val c = customer ?: return null
        return PdfInvoiceGenerator.ReportPrintModel(
            title = "كشف حساب عميل: ${c.name}",
            shopName = tenant?.shopName ?: "حساباتي",
            periodLabel = EgyptTime.formatDate(EgyptTime.nowMillis()),
            summaryPairs = listOf(
                "إجمالي التعاملات" to MoneyFormatter.formatEgp(c.totalSalesPiasters, currency),
                "إجمالي المدفوعات" to MoneyFormatter.formatEgp(c.totalPaidPiasters, currency),
                "الرصيد المتبقي" to MoneyFormatter.formatEgp(c.currentBalancePiasters, currency)
            ),
            columns = listOf("التاريخ", "البيان", "المبلغ", "الرصيد"),
            rows = transactions.map { tx ->
                listOf(
                    EgyptTime.formatDateTime(tx.createdAt),
                    tx.description.ifBlank { CustomerTxTypes.arabicLabel(tx.type) },
                    MoneyFormatter.formatEgp(tx.amountPiasters, ""),
                    MoneyFormatter.formatEgp(tx.runningBalancePiasters, "")
                )
            }
        )
    }

    fun buildCustomerWhatsAppCaption(): String {
        val c = customer ?: return ""
        return WhatsAppHelper.buildCustomerStatementMessage(
            customerName = c.name,
            shopName = tenant?.shopName ?: "المحل",
            totalSalesPiasters = c.totalSalesPiasters,
            totalPaidPiasters = c.totalPaidPiasters,
            remainingPiasters = c.currentBalancePiasters,
            dateFormatted = EgyptTime.formatDate(EgyptTime.nowMillis())
        )
    }

    fun shareCustomerStatementImageViaWhatsApp() {
        val c = customer ?: return
        val report = buildCustomerReportModel() ?: return
        val (_, uri) = PdfInvoiceGenerator.generateReportInvoicePng(
            context = context,
            model = report,
            recipientName = c.name,
            recipientLabel = "السيد / العميل",
            shopAddress = tenant?.address.orEmpty(),
            shopPhone = tenant?.phone.orEmpty(),
            documentBadge = "كشف حساب عميل",
            statusRemainingPiasters = c.currentBalancePiasters,
            footerNote = "شكرًا لثقتكم الغالية — نسعد دائمًا بخدمتكم في ${tenant?.shopName ?: "حساباتي"}"
        )
        val res = WhatsAppHelper.shareFileViaWhatsAppOrChooser(
            context = context,
            fileUri = uri,
            mimeType = "image/png",
            caption = buildCustomerWhatsAppCaption(),
            rawPhone = c.whatsappPhone.ifBlank { c.phone },
            countryCode = waCountry
        )
        if (!res.launched && res.errorMessage != null) {
            viewModel.emitMessage(res.errorMessage, true)
        }
    }

    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = customer?.name ?: "حساب العميل",
                subtitle = customer?.phone?.takeIf { it.isNotBlank() },
                onBack = onBack,
                actions = {
                    if (customer != null) {
                        IconButton(
                            onClick = {
                                val c = customer!!
                                val report = buildCustomerReportModel() ?: return@IconButton
                                val (_, uri) = PdfInvoiceGenerator.generateReportPdf(context, report)
                                val res = WhatsAppHelper.shareFileViaWhatsAppOrChooser(
                                    context = context,
                                    fileUri = uri,
                                    mimeType = "application/pdf",
                                    caption = buildCustomerWhatsAppCaption(),
                                    rawPhone = c.whatsappPhone.ifBlank { c.phone },
                                    countryCode = waCountry
                                )
                                if (!res.launched && res.errorMessage != null) viewModel.emitMessage(res.errorMessage, true)
                            },
                            modifier = Modifier.testTag("customer_pdf_btn")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF")
                        }
                        IconButton(
                            onClick = { shareCustomerStatementImageViaWhatsApp() },
                            modifier = Modifier.testTag("customer_whatsapp_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال كشف الحساب صورة عبر WhatsApp", tint = extras.success)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val c = customer
        if (c == null) {
            EmptyStateView(
                icon = Icons.Default.Receipt,
                title = "جارٍ تحميل بيانات العميل...",
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (c.address.isNotBlank()) Text("العنوان: ${c.address}", style = MaterialTheme.typography.bodySmall)
                            if (c.notes.isNotBlank()) {
                                Text(
                                    "ملاحظات: ${c.notes}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        val (stColor, stBg) = when (c.accountStatus) {
                            CustomerAccountStatus.OVERDUE -> extras.danger to extras.dangerContainer
                            CustomerAccountStatus.OWING -> extras.warning to extras.warningContainer
                            else -> extras.success to extras.successContainer
                        }
                        StatusBadge(CustomerAccountStatus.arabicLabel(c.accountStatus), stColor, stBg)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryStatPill(
                            label = "إجمالي التعاملات",
                            value = MoneyFormatter.formatEgp(c.totalSalesPiasters, currency),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatPill(
                            label = "المدفوعات",
                            value = MoneyFormatter.formatEgp(c.totalPaidPiasters, currency),
                            valueColor = extras.success,
                            modifier = Modifier.weight(1f)
                        )
                        SummaryStatPill(
                            label = "الرصيد المتبقي",
                            value = MoneyFormatter.formatEgp(c.currentBalancePiasters, currency),
                            valueColor = if (c.currentBalancePiasters > 0) extras.danger else extras.success,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val shopDefaultLimit = settings?.defaultCustomerCreditLimitPiasters ?: 0L
                    val effectiveLimit = if (c.creditLimitPiasters > 0L) c.creditLimitPiasters else shopDefaultLimit
                    val isOverLimit = effectiveLimit > 0L && c.currentBalancePiasters > effectiveLimit
                    val excessPiasters = (c.currentBalancePiasters - effectiveLimit).coerceAtLeast(0L)

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val limitLabel = when {
                            c.creditLimitPiasters > 0L -> "حد المديونية: ${MoneyFormatter.formatEgp(c.creditLimitPiasters, currency)} (خاص)"
                            shopDefaultLimit > 0L -> "حد المديونية: ${MoneyFormatter.formatEgp(shopDefaultLimit, currency)} (الحد العام)"
                            else -> "حد المديونية: غير محدد"
                        }
                        Text(
                            text = limitLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isOverLimit) extras.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { showEditCreditLimitDialog = true },
                            modifier = Modifier.testTag("customer_edit_credit_limit_btn")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (c.creditLimitPiasters > 0L) "تعديل الحد" else "تحديد حد")
                        }
                    }

                    if (isOverLimit) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = extras.dangerContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("customer_over_limit_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "تجاوز حد المديونية",
                                    tint = extras.danger,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "تنبيه: العميل تجاوز حد المديونية المسموح بمبلغ ${MoneyFormatter.formatEgp(excessPiasters, currency)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = extras.danger
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onNewSaleForCustomer(c.id) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("customer_new_sale_btn")
                    ) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("بيع", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = { onCollectForCustomer(c.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("customer_collect_btn")
                    ) {
                        Icon(Icons.Default.Savings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("تحصيل", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.createDailyInvoice(c.id) { createdId ->
                                onOpenDailyInvoice(createdId)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("customer_daily_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("سحب يومي", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            item {
                Button(
                    onClick = { shareCustomerStatementImageViaWhatsApp() },
                    colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("customer_whatsapp_statement_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال كشف الحساب عبر واتساب (صورة فاتورة + رسالة)", fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            item {
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("كشف الحساب (${transactions.size})") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("الفواتير (${invoices.size})") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("الدفعات (${payments.size})") })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("المجدولة (${installments.size})") })
                }
            }

            when (selectedTab) {
                    0 -> {
                        if (transactions.isEmpty()) {
                            item { EmptyStateView(Icons.Default.Receipt, "لا توجد حركات", "ستظهر هنا كل الحركات مرتبة زمنيًا.") }
                        } else {
                            items(transactions, key = { it.id }) { tx ->
                                val isDebit = tx.signedDeltaPiasters >= 0L
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
                                                text = tx.description.ifBlank { CustomerTxTypes.arabicLabel(tx.type) },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = EgyptTime.formatDateTime(tx.createdAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = (if (isDebit) "+ " else "- ") + MoneyFormatter.formatEgp(tx.amountPiasters, currency),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDebit) extras.danger else extras.success
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

                    1 -> {
                        if (invoices.isEmpty()) {
                            item { EmptyStateView(Icons.Default.Receipt, "لا توجد فواتير", "اضغط تسجيل بيع لإصدار فاتورة جديدة.") }
                        } else {
                            items(invoices, key = { it.id }) { inv ->
                                HesabatyCard(
                                    onClick = { onOpenInvoice(inv.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(inv.invoiceNumber, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                "${SaleTypes.arabicLabel(inv.saleType)} • ${EgyptTime.formatDateTime(inv.createdAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                MoneyFormatter.formatEgp(inv.totalPiasters, currency),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (inv.remainingPiasters == 0L) "مسددة" else "متبقي ${MoneyFormatter.formatEgp(inv.remainingPiasters, currency)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (inv.remainingPiasters == 0L) extras.success else extras.danger
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        if (payments.isEmpty()) {
                            item { EmptyStateView(Icons.Default.Savings, "لا توجد دفعات", "ستظهر هنا كل التحصيلات المسجلة للعميل.") }
                        } else {
                            items(payments, key = { it.id }) { pay ->
                                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                PaymentMethods.arabicLabel(pay.paymentMethod),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                EgyptTime.formatDateTime(pay.createdAt),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (pay.notes.isNotBlank()) {
                                                Text(pay.notes, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        Text(
                                            MoneyFormatter.formatEgp(pay.amountPiasters, currency),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = extras.success
                                        )
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        if (installments.isEmpty()) {
                            item { EmptyStateView(Icons.Default.Receipt, "لا توجد دفعات مجدولة", "البيع المقسم على تواريخ ينشئ جدول استحقاقات هنا.") }
                        } else {
                            items(installments, key = { it.id }) { inst ->
                                val (cCol, cBg) = when (inst.status) {
                                    InstallmentStatuses.PAID -> extras.success to extras.successContainer
                                    InstallmentStatuses.OVERDUE -> extras.danger to extras.dangerContainer
                                    InstallmentStatuses.DUE, InstallmentStatuses.PARTIAL -> extras.warning to extras.warningContainer
                                    else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
                                }
                                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("دفعة #${inst.installmentIndex}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                "الاستحقاق: ${EgyptTime.formatDate(inst.dueDateMillis)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusBadge(InstallmentStatuses.arabicLabel(inst.status), cCol, cBg)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "القيمة: ${MoneyFormatter.formatEgp(inst.amountPiasters, currency)} | المدفوع: ${MoneyFormatter.formatEgp(inst.paidPiasters, currency)} | المتبقي: ${MoneyFormatter.formatEgp(inst.remainingPiasters, currency)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    inspectTx?.let { tx ->
        AlertDialog(
            onDismissRequest = { inspectTx = null },
            title = { Text(CustomerTxTypes.arabicLabel(tx.type), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("البيان: ${tx.description}")
                    Text("المبلغ: ${MoneyFormatter.formatEgp(tx.amountPiasters, currency)}")
                    Text("الرصيد بعد الحركة: ${MoneyFormatter.formatEgp(tx.runningBalancePiasters, currency)}")
                    Text("طريقة الدفع: ${PaymentMethods.arabicLabel(tx.paymentMethod)}")
                    Text("التاريخ والوقت: ${EgyptTime.formatDateTimeSeconds(tx.createdAt)}")
                }
            },
            confirmButton = {
                if (tx.type == CustomerTxTypes.SALE && tx.referenceId != null) {
                    Button(onClick = {
                        val ref = tx.referenceId
                        inspectTx = null
                        onOpenInvoice(ref)
                    }) {
                        Text("فتح الفاتورة")
                    }
                } else {
                    TextButton(onClick = { inspectTx = null }) { Text("إغلاق") }
                }
            },
            dismissButton = {
                if (tx.type == CustomerTxTypes.SALE && tx.referenceId != null) {
                    TextButton(onClick = { inspectTx = null }) { Text("إغلاق") }
                }
            }
        )
    }

    if (showEditCreditLimitDialog && customer != null) {
        val currentCust = customer!!
        var limitText by remember(currentCust.id, currentCust.creditLimitPiasters) {
            mutableStateOf(
                if (currentCust.creditLimitPiasters > 0L) {
                    MoneyFormatter.formatEgpPlainInput(currentCust.creditLimitPiasters)
                } else ""
            )
        }
        var err by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showEditCreditLimitDialog = false },
            title = { Text("حد المديونية المسموح للعميل", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "عند تجاوز مديونية «${currentCust.name}» هذا المبلغ سيظهر تنبيه تلقائي في القائمة الرئيسية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it; err = null },
                        label = { Text("الحد المسموح به (جنيه)") },
                        supportingText = { Text("اتركه فارغًا أو 0 لإلغاء الحد الخاص وتطبيق الحد العام للمحل") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_customer_credit_limit_input")
                    )
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = if (limitText.isBlank()) 0L else {
                            MoneyFormatter.parseEgpToPiasters(limitText) ?: run {
                                err = "أدخل مبلغًا صحيحًا لحد المديونية"
                                return@Button
                            }
                        }
                        viewModel.updateCustomerCreditLimit(currentCust.id, parsed) { ok, msg ->
                            if (ok) showEditCreditLimitDialog = false else err = msg
                        }
                    },
                    modifier = Modifier.testTag("save_customer_credit_limit_btn")
                ) {
                    Text("حفظ الحد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCreditLimitDialog = false }) { Text("إلغاء") }
            }
        )
    }
}
