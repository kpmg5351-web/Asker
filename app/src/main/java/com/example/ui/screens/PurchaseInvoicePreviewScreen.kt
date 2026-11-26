package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.EgyptTime
import com.example.core.MoneyFormatter
import com.example.core.PdfInvoiceGenerator
import com.example.core.WhatsAppHelper
import com.example.data.local.PaymentMethods
import com.example.data.local.PurchaseTypes
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyTopBar
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel

@Composable
fun PurchaseInvoicePreviewScreen(
    purchaseId: String,
    viewModel: HesabatyViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val invoice by remember(purchaseId) { viewModel.observePurchaseInvoiceById(purchaseId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val items by remember(purchaseId) { viewModel.observePurchaseInvoiceItems(purchaseId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val tenant by viewModel.activeTenant.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val currency = settings?.defaultCurrencyLabel ?: "ج.م"
    val waCountry = settings?.whatsappCountryCode ?: "20"
    val extras = HesabatyThemeExtras.colors

    val inv = invoice
    val supplier = suppliers.firstOrNull { it.id == inv?.supplierId }

    fun buildPrintLines(): List<PdfInvoiceGenerator.InvoiceLineRow> {
        val currentInv = inv ?: return emptyList()
        return if (items.isEmpty()) {
            listOf(
                PdfInvoiceGenerator.InvoiceLineRow(
                    description = currentInv.notes.ifBlank { "مشتريات بضاعة" },
                    unitPricePiasters = currentInv.totalPiasters,
                    quantityMilli = 1000L,
                    lineTotalPiasters = currentInv.totalPiasters
                )
            )
        } else {
            items.map {
                PdfInvoiceGenerator.InvoiceLineRow(
                    description = it.description,
                    unitPricePiasters = it.unitPricePiasters,
                    quantityMilli = it.quantityMilli,
                    lineTotalPiasters = it.lineTotalPiasters
                )
            }
        }
    }

    fun buildPrintModel(): PdfInvoiceGenerator.InvoicePrintModel? {
        val currentInv = inv ?: return null
        return PdfInvoiceGenerator.InvoicePrintModel(
            shopName = tenant?.shopName ?: "حساباتي",
            shopAddress = tenant?.address.orEmpty(),
            shopPhone = tenant?.phone.orEmpty(),
            customerName = supplier?.name ?: "مورد",
            invoiceNumber = currentInv.invoiceNumber,
            dateFormatted = EgyptTime.formatDateTime(currentInv.createdAt),
            invoiceTypeLabel = PurchaseTypes.arabicLabel(currentInv.purchaseType),
            paymentMethodLabel = PaymentMethods.arabicLabel(currentInv.paymentMethod),
            lines = buildPrintLines(),
            totalPiasters = currentInv.totalPiasters,
            paidPiasters = currentInv.paidPiasters,
            remainingPiasters = currentInv.remainingPiasters,
            notes = currentInv.notes,
            signatureName = settings?.invoiceSignatureName.orEmpty(),
            recipientPrefix = "السادة / المورد:",
            recipientPhone = supplier?.phone.orEmpty(),
            dueDateFormatted = currentInv.dueDateMillis?.let { EgyptTime.formatDate(it) }
        )
    }

    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = inv?.invoiceNumber ?: "معاينة فاتورة الشراء",
                subtitle = "فاتورة مشتريات تفصيلية RTL",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        if (inv == null) {
            EmptyStateView(
                icon = Icons.Default.Receipt,
                title = "جارٍ تحميل فاتورة الشراء...",
                subtitle = "",
                modifier = Modifier.padding(innerPadding)
            )
            return@Scaffold
        }

        val uiScale = HesabatyThemeExtras.uiScale
        val (totPounds, totPiasters) = MoneyFormatter.splitPoundsAndPiasters(inv.totalPiasters)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(uiScale.screenPadding),
            verticalArrangement = Arrangement.spacedBy(uiScale.sectionSpacing)
        ) {
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tenant?.shopName ?: "المحل",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!tenant?.address.isNullOrBlank()) {
                                    Text(tenant!!.address, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
                                }
                                if (!tenant?.phone.isNullOrBlank()) {
                                    Text("هاتف: ${tenant!!.phone}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f))
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(PurchaseTypes.arabicLabel(inv.purchaseType), style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(inv.invoiceNumber, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("السادة / المورد: ${supplier?.name ?: "—"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (!supplier?.phone.isNullOrBlank()) {
                        Text("هاتف المورد: ${supplier!!.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("التاريخ: ${EgyptTime.formatDateTime(inv.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("طريقة الدفع: ${PaymentMethods.arabicLabel(inv.paymentMethod)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    inv.dueDateMillis?.let { dueMillis ->
                        Text("تاريخ الاستحقاق: ${EgyptTime.formatDate(dueMillis)}", style = MaterialTheme.typography.bodySmall, color = extras.warning, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val borderColor = extras.cardBorder
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.dp, borderColor)
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                    ) {
                        Text("البيان", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2.2f))
                        Text("السعر", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        Text("العدد/الكمية", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.95f))
                        Text("جنيه", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.95f))
                        Text("قرش", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.65f))
                    }

                    val printLines = buildPrintLines()
                    printLines.forEachIndexed { index, row ->
                        val (pounds, piasters) = MoneyFormatter.splitPoundsAndPiasters(row.lineTotalPiasters)
                        val rawItem = items.getOrNull(index)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, borderColor)
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(2.2f)) {
                                Text(row.description, style = MaterialTheme.typography.bodySmall)
                                if (rawItem != null && (rawItem.wholesalePricePiasters > 0L || rawItem.retailPricePiasters > 0L)) {
                                    val tags = buildList {
                                        if (rawItem.wholesalePricePiasters > 0L) add("جملة: ${MoneyFormatter.formatEgp(rawItem.wholesalePricePiasters, currency)}")
                                        if (rawItem.retailPricePiasters > 0L) add("قطاعي: ${MoneyFormatter.formatEgp(rawItem.retailPricePiasters, currency)}")
                                    }.joinToString(" • ")
                                    Text(
                                        text = tags,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(MoneyFormatter.formatEgp(row.unitPricePiasters, ""), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            Text(MoneyFormatter.formatQuantity(row.quantityMilli), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.95f))
                            Text(MoneyFormatter.formatEgp(pounds * 100L, ""), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(0.95f))
                            Text(String.format("%02d", piasters), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.weight(0.65f))
                        }
                    }

                    val totalQtyMilli = printLines.sumOf { it.quantityMilli }
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "عدد البنود: ${printLines.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "إجمالي العدد / الكمية: ${MoneyFormatter.formatQuantity(totalQtyMilli)}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الإجمالي النهائي:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "$totPounds جنيه و $totPiasters قرش",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المدفوع للمورد:", style = MaterialTheme.typography.bodyMedium)
                        Text(MoneyFormatter.formatEgp(inv.paidPiasters, currency), color = extras.success, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المتبقي للمورد:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            MoneyFormatter.formatEgp(inv.remainingPiasters, currency),
                            color = if (inv.remainingPiasters > 0) extras.danger else extras.success,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (inv.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("ملاحظات: ${inv.notes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!settings?.invoiceSignatureName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("المسؤول: ${settings!!.invoiceSignatureName}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item {
                val classyCaption = WhatsAppHelper.buildPurchaseInvoiceMessage(
                    supplierName = supplier?.name ?: "شريكنا الكريم",
                    shopName = tenant?.shopName ?: "المحل",
                    invoiceNumber = inv.invoiceNumber,
                    dateFormatted = EgyptTime.formatDate(inv.createdAt),
                    totalPiasters = inv.totalPiasters,
                    paidPiasters = inv.paidPiasters,
                    remainingPiasters = inv.remainingPiasters,
                    purchaseTypeLabel = PurchaseTypes.arabicLabel(inv.purchaseType),
                    lines = buildPrintLines(),
                    dueDateFormatted = inv.dueDateMillis?.let { EgyptTime.formatDate(it) },
                    notes = inv.notes
                )
                Button(
                    onClick = {
                        val printModel = buildPrintModel() ?: return@Button
                        val (_, uri) = PdfInvoiceGenerator.generateInvoicePng(context, printModel)
                        val res = WhatsAppHelper.shareFileViaWhatsAppOrChooser(
                            context = context,
                            fileUri = uri,
                            mimeType = "image/png",
                            caption = classyCaption,
                            rawPhone = supplier?.whatsappPhone?.ifBlank { supplier.phone },
                            countryCode = waCountry
                        )
                        if (!res.launched && res.errorMessage != null) {
                            viewModel.emitMessage(res.errorMessage, true)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("purchase_whatsapp_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال فاتورة الشراء عبر واتساب (صورة + رسالة)", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val printModel = buildPrintModel() ?: return@OutlinedButton
                            val (_, uri) = PdfInvoiceGenerator.generateInvoicePdf(context, printModel)
                            val res = WhatsAppHelper.shareFileViaWhatsAppOrChooser(
                                context = context,
                                fileUri = uri,
                                mimeType = "application/pdf",
                                caption = classyCaption,
                                rawPhone = supplier?.whatsappPhone?.ifBlank { supplier.phone },
                                countryCode = waCountry
                            )
                            if (!res.launched && res.errorMessage != null) viewModel.emitMessage(res.errorMessage, true)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("purchase_pdf_btn")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة PDF", textAlign = TextAlign.Center)
                    }
                    OutlinedButton(
                        onClick = {
                            val printModel = buildPrintModel() ?: return@OutlinedButton
                            val (_, uri) = PdfInvoiceGenerator.generateInvoicePng(context, printModel)
                            val res = WhatsAppHelper.shareFileViaWhatsAppOrChooser(
                                context = context,
                                fileUri = uri,
                                mimeType = "image/png",
                                caption = classyCaption,
                                rawPhone = supplier?.whatsappPhone?.ifBlank { supplier.phone },
                                countryCode = waCountry
                            )
                            if (!res.launched && res.errorMessage != null) viewModel.emitMessage(res.errorMessage, true)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("purchase_image_btn")
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة صورة", textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
