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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyDatePickerDialog
import com.example.ui.components.HesabatyTopBar
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel
import java.util.UUID

private data class CompletedCollectionReceipt(
    val customerName: String,
    val customerPhone: String,
    val amountPiasters: Long,
    val previousBalancePiasters: Long,
    val remainingAfterPiasters: Long,
    val paymentMethod: String,
    val dateMillis: Long,
    val notes: String,
    val invoiceNumber: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    preselectedCustomerId: String? = null,
    viewModel: HesabatyViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val allInvoices by viewModel.invoices.collectAsStateWithLifecycle()
    val tenant by viewModel.activeTenant.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val currency = settings?.defaultCurrencyLabel ?: "ج.م"
    val waCountry = settings?.whatsappCountryCode ?: "20"
    val extras = HesabatyThemeExtras.colors

    var selectedCustomerId by remember(preselectedCustomerId) { mutableStateOf(preselectedCustomerId ?: "") }
    var customerExpanded by remember { mutableStateOf(false) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }

    val openInvoices = remember(allInvoices, selectedCustomerId) {
        allInvoices.filter { it.customerId == selectedCustomerId && it.remainingPiasters > 0L }
    }
    var selectedInvoiceId by remember(selectedCustomerId) { mutableStateOf<String?>(null) }
    var invoiceExpanded by remember { mutableStateOf(false) }

    var amountText by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(PaymentMethods.CASH) }
    var allowExcessAsCredit by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var occurredAtMillis by remember { mutableStateOf(EgyptTime.nowMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var idempotencyKey by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var completedReceipt by remember { mutableStateOf<CompletedCollectionReceipt?>(null) }

    val selectedCustomer = customers.firstOrNull { it.id == selectedCustomerId }
    val selectedInvoice = openInvoices.firstOrNull { it.id == selectedInvoiceId }

    val uiScale = HesabatyThemeExtras.uiScale
    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = "تحصيل دفعة من عميل",
                subtitle = "تحديث فوري لرصيد العميل والفواتير",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(uiScale.screenPadding),
            verticalArrangement = Arrangement.spacedBy(uiScale.sectionSpacing)
        ) {
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("اختيار العميل *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = { showAddCustomerDialog = true },
                            modifier = Modifier.testTag("collection_add_customer_btn")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("عميل جديد")
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    ExposedDropdownMenuBox(
                        expanded = customerExpanded,
                        onExpandedChange = { customerExpanded = !customerExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCustomer?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("اختر العميل") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor().testTag("collection_customer_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = customerExpanded,
                            onDismissRequest = { customerExpanded = false }
                        ) {
                            if (customers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("لا يوجد عملاء — اضغط لإنشاء عميل جديد") },
                                    onClick = {
                                        customerExpanded = false
                                        showAddCustomerDialog = true
                                    }
                                )
                            } else {
                                customers.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text("${c.name} — مستحق: ${MoneyFormatter.formatEgp(c.currentBalancePiasters, currency)}") },
                                        onClick = {
                                            selectedCustomerId = c.id
                                            selectedInvoiceId = null
                                            customerExpanded = false
                                            errorMsg = null
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (selectedCustomer != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "إجمالي المستحق على العميل: ${MoneyFormatter.formatEgp(selectedCustomer.currentBalancePiasters, currency)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selectedCustomer.currentBalancePiasters > 0) extras.danger else extras.success
                        )
                    }

                    if (openInvoices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("الفاتورة المرتبطة (اختياري):", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        ExposedDropdownMenuBox(
                            expanded = invoiceExpanded,
                            onExpandedChange = { invoiceExpanded = !invoiceExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedInvoice?.let {
                                    "${it.invoiceNumber} (متبقي ${MoneyFormatter.formatEgp(it.remainingPiasters, currency)})"
                                } ?: "دفعة على الحساب العام",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = invoiceExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = invoiceExpanded,
                                onDismissRequest = { invoiceExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("دفعة على الحساب العام") },
                                    onClick = {
                                        selectedInvoiceId = null
                                        invoiceExpanded = false
                                    }
                                )
                                openInvoices.forEach { inv ->
                                    DropdownMenuItem(
                                        text = {
                                            Text("${inv.invoiceNumber} — متبقي ${MoneyFormatter.formatEgp(inv.remainingPiasters, currency)}")
                                        },
                                        onClick = {
                                            selectedInvoiceId = inv.id
                                            invoiceExpanded = false
                                            errorMsg = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; errorMsg = null },
                        label = { Text("المبلغ المستلم (جنيه مصري) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("collection_amount_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("طريقة التحصيل:", style = MaterialTheme.typography.labelMedium)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = paymentMethod == PaymentMethods.CASH,
                            onClick = { paymentMethod = PaymentMethods.CASH },
                            label = { Text("نقدي") }
                        )
                        FilterChip(
                            selected = paymentMethod == PaymentMethods.EWALLET,
                            onClick = { paymentMethod = PaymentMethods.EWALLET },
                            label = { Text("محفظة إلكترونية") }
                        )
                    }

                    if (selectedInvoice != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = allowExcessAsCredit,
                                onCheckedChange = { allowExcessAsCredit = it; errorMsg = null }
                            )
                            Text(
                                text = "السماح بتسجيل أي فرق زائد عن الفاتورة كرصيد دائن للعميل",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("التاريخ والوقت: ${EgyptTime.formatDateTime(occurredAtMillis)}")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    errorMsg?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (selectedCustomerId.isBlank()) {
                                errorMsg = "يرجى اختيار العميل أولًا"
                                return@Button
                            }
                            val piasters = MoneyFormatter.parseEgpToPiasters(amountText)
                            if (piasters == null || piasters <= 0L) {
                                errorMsg = "يرجى إدخال مبلغ صحيح أكبر من صفر"
                                return@Button
                            }
                            val snapshotCustomer = selectedCustomer
                            val snapshotInvoiceNum = selectedInvoice?.invoiceNumber
                            val prevBal = snapshotCustomer?.currentBalancePiasters ?: 0L
                            viewModel.recordCustomerPayment(
                                customerId = selectedCustomerId,
                                amountPiasters = piasters,
                                paymentMethod = paymentMethod,
                                invoiceId = selectedInvoiceId,
                                installmentId = null,
                                allowExcessAsCredit = allowExcessAsCredit,
                                notes = notes,
                                occurredAtMillis = occurredAtMillis,
                                idempotencyKey = idempotencyKey
                            ) { ok, msg ->
                                if (ok) {
                                    idempotencyKey = UUID.randomUUID().toString()
                                    completedReceipt = CompletedCollectionReceipt(
                                        customerName = snapshotCustomer?.name ?: "عميلنا العزيز",
                                        customerPhone = snapshotCustomer?.whatsappPhone?.ifBlank { snapshotCustomer.phone }.orEmpty(),
                                        amountPiasters = piasters,
                                        previousBalancePiasters = prevBal,
                                        remainingAfterPiasters = (prevBal - piasters).coerceAtLeast(0L),
                                        paymentMethod = paymentMethod,
                                        dateMillis = occurredAtMillis,
                                        notes = notes,
                                        invoiceNumber = snapshotInvoiceNum
                                    )
                                } else {
                                    errorMsg = msg
                                }
                            }
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("collection_submit_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تأكيد تسجيل التحصيل", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        HesabatyDatePickerDialog(
            initialMillis = occurredAtMillis,
            onDismiss = { showDatePicker = false },
            onDateSelected = { occurredAtMillis = it }
        )
    }

    completedReceipt?.let { rec ->
        AlertDialog(
            onDismissRequest = {
                completedReceipt = null
                onBack()
            },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = extras.success
                )
            },
            title = {
                Text("تم تسجيل التحصيل بنجاح", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("العميل: ${rec.customerName}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "المبلغ المستلم: ${MoneyFormatter.formatEgp(rec.amountPiasters, currency)}",
                        color = extras.success,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "الرصيد المتبقي بعد الدفعة: ${MoneyFormatter.formatEgp(rec.remainingAfterPiasters, currency)}",
                        color = if (rec.remainingAfterPiasters > 0L) extras.danger else extras.success
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "يمكنك إرسال إيصال الاستلام للعميل الآن على شكل صورة فاتورة أنيقة مع رسالة شكر عبر واتساب.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val methodLabel = PaymentMethods.arabicLabel(rec.paymentMethod)
                        val receiptNo = "RCP-${(rec.dateMillis / 1000L) % 100000L}"
                        val descLine = if (!rec.invoiceNumber.isNullOrBlank()) {
                            "سداد دفعة على فاتورة ${rec.invoiceNumber}"
                        } else {
                            "سداد دفعة نقدية على الحساب"
                        }
                        val printModel = PdfInvoiceGenerator.InvoicePrintModel(
                            shopName = tenant?.shopName ?: "حساباتي",
                            shopAddress = tenant?.address.orEmpty(),
                            shopPhone = tenant?.phone.orEmpty(),
                            customerName = rec.customerName,
                            invoiceNumber = receiptNo,
                            dateFormatted = EgyptTime.formatDateTime(rec.dateMillis),
                            invoiceTypeLabel = "إيصال استلام دفعة",
                            paymentMethodLabel = methodLabel,
                            lines = listOf(
                                PdfInvoiceGenerator.InvoiceLineRow(
                                    description = descLine,
                                    unitPricePiasters = rec.amountPiasters,
                                    quantityMilli = 1000L,
                                    lineTotalPiasters = rec.amountPiasters
                                )
                            ),
                            totalPiasters = rec.previousBalancePiasters.coerceAtLeast(rec.amountPiasters),
                            paidPiasters = rec.amountPiasters,
                            remainingPiasters = rec.remainingAfterPiasters,
                            notes = rec.notes,
                            signatureName = settings?.invoiceSignatureName.orEmpty()
                        )
                        val (_, uri) = PdfInvoiceGenerator.generateInvoicePng(context, printModel)
                        val caption = WhatsAppHelper.buildPaymentReceiptMessage(
                            customerName = rec.customerName,
                            shopName = tenant?.shopName ?: "المحل",
                            amountPiasters = rec.amountPiasters,
                            remainingAfterPiasters = rec.remainingAfterPiasters,
                            paymentMethodLabel = methodLabel,
                            dateFormatted = EgyptTime.formatDate(rec.dateMillis)
                        )
                        val res = WhatsAppHelper.shareFileViaWhatsAppOrChooser(
                            context = context,
                            fileUri = uri,
                            mimeType = "image/png",
                            caption = caption,
                            rawPhone = rec.customerPhone,
                            countryCode = waCountry
                        )
                        if (!res.launched && res.errorMessage != null) {
                            viewModel.emitMessage(res.errorMessage, true)
                        }
                        completedReceipt = null
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                    modifier = Modifier.testTag("collection_whatsapp_receipt_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إرسال إيصال واتساب (صورة)")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        completedReceipt = null
                        onBack()
                    },
                    modifier = Modifier.testTag("collection_done_btn")
                ) {
                    Text("تم والعودة")
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
                        selectedInvoiceId = null
                        errorMsg = null
                        showAddCustomerDialog = false
                    } else onErr(idOrErr)
                }
            }
        )
    }
}
