package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.EgyptTime
import com.example.core.MoneyFormatter
import com.example.core.PdfInvoiceGenerator
import com.example.core.UnitMeasureCategory
import com.example.core.UnitMeasurement
import com.example.core.WhatsAppHelper
import com.example.data.local.ProductEntity
import com.example.data.local.SalePriceTier
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.StatusBadge
import com.example.ui.components.SummaryStatPill
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryDarkBlue
import com.example.ui.viewmodel.HesabatyViewModel

private val CommonCategories = listOf("عام", "مواد غذائية", "مستلزمات", "قطع غيار", "أدوات", "ملابس", "إلكترونيات")

@Composable
fun ProductsScreen(
    viewModel: HesabatyViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val products by viewModel.products.collectAsStateWithLifecycle()
    val tenant by viewModel.activeTenant.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val currency = settings?.defaultCurrencyLabel ?: "ج.م"
    val waCountry = settings?.whatsappCountryCode ?: "20"
    val extras = HesabatyThemeExtras.colors
    val uiScale = HesabatyThemeExtras.uiScale

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("الكل") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var adjustingStockProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var deletingProduct by remember { mutableStateOf<ProductEntity?>(null) }

    val categories = remember(products) {
        listOf("الكل") + products.map { it.category.ifBlank { "عام" } }.distinct().sorted()
    }

    val filtered = remember(products, searchQuery, selectedCategory) {
        val q = searchQuery.trim()
        products.filter { p ->
            val catOk = selectedCategory == "الكل" || p.category.ifBlank { "عام" } == selectedCategory
            val qOk = q.isEmpty() ||
                p.name.contains(q, ignoreCase = true) ||
                p.category.contains(q, ignoreCase = true) ||
                p.skuOrBarcode.contains(q, ignoreCase = true)
            catOk && qOk
        }
    }

    val trackedProducts = remember(products) { products.filter { it.trackStock } }
    val lowStockCount = remember(trackedProducts) {
        trackedProducts.count { it.minStockAlertMilli > 0L && it.stockQuantityMilli <= it.minStockAlertMilli }
    }
    val totalInventoryValuePiasters = remember(trackedProducts) {
        trackedProducts.sumOf {
            MoneyFormatter.calculateLineTotalPiasters(
                if (it.purchasePricePiasters > 0L) it.purchasePricePiasters else it.salePricePiasters,
                it.stockQuantityMilli
            )
        }
    }

    fun sharePriceListViaWhatsApp() {
        val listToShare = if (filtered.isNotEmpty()) filtered else products
        if (listToShare.isEmpty()) {
            viewModel.emitMessage("أضف صنفًا واحدًا على الأقل لمشاركة قائمة الأسعار", true)
            return
        }
        val today = EgyptTime.formatDate(EgyptTime.nowMillis())
        val summaryPairs = listOf(
            "عدد الأصناف" to "${listToShare.size}",
            "التصنيف" to selectedCategory,
            "التاريخ" to today
        )
        val model = PdfInvoiceGenerator.ReportPrintModel(
            title = "قائمة أسعار الأصناف",
            shopName = tenant?.shopName ?: "حساباتي",
            periodLabel = today,
            summaryPairs = summaryPairs,
            columns = listOf("الصنف", "الوحدة", "بيع جملة", "بيع قطاعي"),
            rows = listToShare.map { p ->
                listOf(
                    p.name,
                    p.unitLabel,
                    if (p.wholesalePricePiasters > 0L) MoneyFormatter.formatEgp(p.wholesalePricePiasters, currency) else "—",
                    MoneyFormatter.formatEgp(p.salePricePiasters, currency)
                )
            }
        )
        val (_, uri) = PdfInvoiceGenerator.generateReportInvoicePng(
            context = context,
            model = model,
            recipientName = "عملاؤنا الكرام",
            shopAddress = tenant?.address.orEmpty(),
            shopPhone = tenant?.phone.orEmpty(),
            documentBadge = "قائمة أسعار رسمية"
        )
        val caption = WhatsAppHelper.buildReportShareMessage(
            title = "قائمة أسعار الأصناف",
            shopName = tenant?.shopName ?: "المحل",
            periodLabel = today,
            summaryPairs = summaryPairs
        )
        val res = WhatsAppHelper.shareFileViaWhatsAppOrChooser(
            context = context,
            fileUri = uri,
            mimeType = "image/png",
            caption = caption,
            rawPhone = null,
            countryCode = waCountry
        )
        if (!res.launched && res.errorMessage != null) {
            viewModel.emitMessage(res.errorMessage, true)
        }
    }

    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = "الأصناف والمنتجات",
                subtitle = "إدارة اختيارية لأسعار الأصناف والمخزون (${products.size})",
                onBack = onBack,
                actions = {
                    if (products.isNotEmpty()) {
                        IconButton(
                            onClick = { sharePriceListViaWhatsApp() },
                            modifier = Modifier.testTag("products_share_pricelist_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "مشاركة قائمة الأسعار عبر واتساب",
                                tint = extras.success
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("إضافة صنف") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("products_add_fab")
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = uiScale.screenPadding, end = uiScale.screenPadding, top = 10.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(uiScale.sectionSpacing)
        ) {
            // Summary KPI Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryStatPill(
                        label = "إجمالي الأصناف",
                        value = "${products.size}",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatPill(
                        label = "قيمة المخزون",
                        value = MoneyFormatter.formatEgp(totalInventoryValuePiasters, currency),
                        valueColor = extras.success,
                        modifier = Modifier.weight(1.3f)
                    )
                    SummaryStatPill(
                        label = "قرب النفاد",
                        value = "$lowStockCount",
                        valueColor = if (lowStockCount > 0) extras.danger else extras.success,
                        modifier = Modifier.weight(0.9f)
                    )
                }
            }

            // Search & Category Filter
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث باسم الصنف أو التصنيف أو الكود...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "مسح البحث")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("products_search_input")
                )
            }

            if (categories.size > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.Inventory2,
                        title = if (products.isEmpty()) "لم تضف أصنافًا بعد" else "لا توجد أصناف مطابقة للبحث",
                        subtitle = if (products.isEmpty()) {
                            "إضافة الأصناف اختيارية تمامًا — يمكنك إضافتها هنا في أي وقت لتسريع الفواتير والسحب اليومي وتحديد الأسعار والمخزون."
                        } else {
                            "جرّب تعديل كلمة البحث أو اختيار تصنيف آخر."
                        }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { product ->
                    val isLowStock = product.trackStock &&
                        product.minStockAlertMilli > 0L &&
                        product.stockQuantityMilli <= product.minStockAlertMilli
                    val profitPerUnit = if (product.purchasePricePiasters > 0L) {
                        product.salePricePiasters - product.purchasePricePiasters
                    } else null

                    HesabatyCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_card_${product.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = product.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = buildString {
                                        append(product.category.ifBlank { "عام" })
                                        append("  •  الوحدة: ${product.unitLabel}")
                                        if (product.skuOrBarcode.isNotBlank()) {
                                            append("  •  كود: ${product.skuOrBarcode}")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = MoneyFormatter.formatEgp(product.salePricePiasters, currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "سعر القطاعي",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (product.purchasePricePiasters > 0L || product.wholesalePricePiasters > 0L) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SummaryStatPill(
                                    label = "سعر الشراء",
                                    value = if (product.purchasePricePiasters > 0L) MoneyFormatter.formatEgp(product.purchasePricePiasters, currency) else "—",
                                    valueColor = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                SummaryStatPill(
                                    label = "بيع جملة",
                                    value = if (product.wholesalePricePiasters > 0L) MoneyFormatter.formatEgp(product.wholesalePricePiasters, currency) else "—",
                                    valueColor = extras.success,
                                    modifier = Modifier.weight(1f)
                                )
                                SummaryStatPill(
                                    label = "بيع قطاعي",
                                    value = MoneyFormatter.formatEgp(product.salePricePiasters, currency),
                                    valueColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (product.trackStock) {
                                    StatusBadge(
                                        label = "المخزون: ${MoneyFormatter.formatQuantity(product.stockQuantityMilli)} ${product.unitLabel}",
                                        textColor = if (isLowStock) extras.danger else extras.success,
                                        backgroundColor = if (isLowStock) extras.dangerContainer else extras.successContainer
                                    )
                                } else {
                                    StatusBadge(
                                        label = "سعر سريع (بدون مخزون)",
                                        textColor = MaterialTheme.colorScheme.primary,
                                        backgroundColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                }
                                if (profitPerUnit != null) {
                                    StatusBadge(
                                        label = "ربح: ${MoneyFormatter.formatEgp(profitPerUnit, currency)}",
                                        textColor = if (profitPerUnit >= 0) extras.success else extras.danger,
                                        backgroundColor = if (profitPerUnit >= 0) extras.successContainer else extras.dangerContainer
                                    )
                                }
                            }
                        }

                        if (product.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = product.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { adjustingStockProduct = product },
                                modifier = Modifier.testTag("product_stock_btn_${product.id}")
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (product.trackStock) "تعديل المخزون" else "تفعيل المخزون")
                            }
                            Row {
                                IconButton(
                                    onClick = { editingProduct = product },
                                    modifier = Modifier.testTag("product_edit_btn_${product.id}")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "تعديل الصنف", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = { deletingProduct = product },
                                    modifier = Modifier.testTag("product_delete_btn_${product.id}")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الصنف", tint = extras.danger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ProductFormDialog(
            initial = null,
            currency = currency,
            onDismiss = { showAddDialog = false },
            onSave = { name, cat, sku, unit, saleP, halfWholesaleP, wholesaleP, buyP, stockM, track, minM, notes, onErr ->
                viewModel.addProduct(
                    name = name,
                    category = cat,
                    skuOrBarcode = sku,
                    unitLabel = unit,
                    salePricePiasters = saleP,
                    purchasePricePiasters = buyP,
                    stockQuantityMilli = stockM,
                    trackStock = track,
                    minStockAlertMilli = minM,
                    notes = notes,
                    halfWholesalePricePiasters = halfWholesaleP,
                    wholesalePricePiasters = wholesaleP
                ) { ok, msg ->
                    if (ok) showAddDialog = false else onErr(msg)
                }
            }
        )
    }

    editingProduct?.let { prod ->
        ProductFormDialog(
            initial = prod,
            currency = currency,
            onDismiss = { editingProduct = null },
            onSave = { name, cat, sku, unit, saleP, halfWholesaleP, wholesaleP, buyP, stockM, track, minM, notes, onErr ->
                viewModel.updateProduct(
                    productId = prod.id,
                    name = name,
                    category = cat,
                    skuOrBarcode = sku,
                    unitLabel = unit,
                    salePricePiasters = saleP,
                    purchasePricePiasters = buyP,
                    stockQuantityMilli = stockM,
                    trackStock = track,
                    minStockAlertMilli = minM,
                    notes = notes,
                    halfWholesalePricePiasters = halfWholesaleP,
                    wholesalePricePiasters = wholesaleP
                ) { ok, msg ->
                    if (ok) editingProduct = null else onErr(msg)
                }
            }
        )
    }

    adjustingStockProduct?.let { prod ->
        AdjustStockDialog(
            product = prod,
            onDismiss = { adjustingStockProduct = null },
            onConfirm = { deltaMilli, reason, onErr ->
                viewModel.adjustProductStock(prod.id, deltaMilli, reason) { ok, msg ->
                    if (ok) adjustingStockProduct = null else onErr(msg)
                }
            }
        )
    }

    deletingProduct?.let { prod ->
        AlertDialog(
            onDismissRequest = { deletingProduct = null },
            title = { Text("تأكيد حذف الصنف", fontWeight = FontWeight.Bold) },
            text = {
                Text("هل تريد حذف الصنف \"${prod.name}\" من قائمة الأصناف؟ لن يؤثر ذلك على الفواتير المسجلة سابقًا.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProduct(prod.id)
                        deletingProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = extras.danger)
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { deletingProduct = null }) { Text("إلغاء") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductFormDialog(
    initial: ProductEntity? = null,
    currency: String = "ج.م",
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        category: String,
        skuOrBarcode: String,
        unitLabel: String,
        salePricePiasters: Long,
        halfWholesalePricePiasters: Long,
        wholesalePricePiasters: Long,
        purchasePricePiasters: Long,
        stockQuantityMilli: Long,
        trackStock: Boolean,
        minStockAlertMilli: Long,
        notes: String,
        onErr: (String) -> Unit
    ) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category?.ifBlank { "عام" } ?: "عام") }
    var showCustomCategory by remember { mutableStateOf(initial != null && initial.category.isNotBlank() && initial.category !in CommonCategories) }
    var sku by remember { mutableStateOf(initial?.skuOrBarcode ?: "") }
    var unitLabel by remember { mutableStateOf(initial?.unitLabel?.ifBlank { "قطعة" } ?: "قطعة") }
    var selectedUnitCategory by remember {
        mutableStateOf(UnitMeasurement.categoryForUnit(initial?.unitLabel ?: "قطعة"))
    }
    var showCustomUnit by remember {
        mutableStateOf(
            initial != null &&
                initial.unitLabel.isNotBlank() &&
                initial.unitLabel !in UnitMeasurement.allQuickUnits
        )
    }
    var salePriceText by remember {
        mutableStateOf(
            if (initial != null && initial.salePricePiasters > 0L) MoneyFormatter.formatEgpPlainInput(initial.salePricePiasters) else ""
        )
    }
    var wholesalePriceText by remember {
        mutableStateOf(
            if (initial != null && initial.wholesalePricePiasters > 0L) MoneyFormatter.formatEgpPlainInput(initial.wholesalePricePiasters) else ""
        )
    }
    var purchasePriceText by remember {
        mutableStateOf(
            if (initial != null && initial.purchasePricePiasters > 0L) MoneyFormatter.formatEgpPlainInput(initial.purchasePricePiasters) else ""
        )
    }
    var trackStock by remember { mutableStateOf(initial?.trackStock ?: false) }
    var stockQtyText by remember {
        mutableStateOf(
            if (initial != null && initial.trackStock) MoneyFormatter.formatQuantity(initial.stockQuantityMilli) else ""
        )
    }
    var minAlertText by remember {
        mutableStateOf(
            if (initial != null && initial.minStockAlertMilli > 0L) MoneyFormatter.formatQuantity(initial.minStockAlertMilli) else ""
        )
    }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var err by remember { mutableStateOf<String?>(null) }

    val extras = HesabatyThemeExtras.colors
    val uiScale = HesabatyThemeExtras.uiScale
    val parsedSalePreview = remember(salePriceText) { MoneyFormatter.parseEgpToPiasters(salePriceText) ?: 0L }
    val parsedWholesalePreview = remember(wholesalePriceText) { MoneyFormatter.parseEgpToPiasters(wholesalePriceText) ?: 0L }
    val parsedBuyPreview = remember(purchasePriceText) { MoneyFormatter.parseEgpToPiasters(purchasePriceText) ?: 0L }
    val previewExamples = remember(unitLabel, parsedSalePreview, currency) {
        UnitMeasurement.buildProductUnitPreview(unitLabel, parsedSalePreview, currency)
    }
    val cleanUnit = unitLabel.trim().ifBlank { "قطعة" }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PrimaryBlue,
        unfocusedBorderColor = extras.cardBorder,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (uiScale.isCompact) 0.96f else 0.94f)
                .widthIn(max = 520.dp)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(if (uiScale.isCompact) 22.dp else 28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, extras.cardBorder),
            tonalElevation = 6.dp,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Royal-Blue Hero Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(PrimaryDarkBlue, PrimaryBlue, Color(0xFF2684FF))
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (initial == null) "إضافة صنف جديد" else "تعديل بيانات الصنف",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "حدّد وحدة الحساب (عدد أو وزن أو مقاس) وسعر البيع",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.86f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إغلاق",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = uiScale.cardPadding, vertical = if (uiScale.isCompact) 10.dp else 14.dp),
                    verticalArrangement = Arrangement.spacedBy(if (uiScale.isCompact) 10.dp else 14.dp)
                ) {
                    // Section 1: Product Name & Category
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, extras.cardBorder.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier.padding(if (uiScale.isCompact) 11.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "بيانات الصنف والتصنيف",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it; err = null },
                                label = { Text("اسم الصنف *") },
                                placeholder = { Text("مثال: سكر أبيض / كرتونة زيت / قماش") },
                                leadingIcon = {
                                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = PrimaryBlue)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("product_form_name")
                            )

                            Text(
                                text = "التصنيف",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CommonCategories.forEach { preset ->
                                    SelectablePillChip(
                                        label = preset,
                                        selected = !showCustomCategory && category == preset,
                                        onClick = {
                                            showCustomCategory = false
                                            category = preset
                                        }
                                    )
                                }
                                SelectablePillChip(
                                    label = "تصنيف آخر",
                                    selected = showCustomCategory,
                                    onClick = { showCustomCategory = true }
                                )
                            }

                            AnimatedVisibility(visible = showCustomCategory) {
                                OutlinedTextField(
                                    value = category,
                                    onValueChange = { category = it },
                                    label = { Text("اكتب التصنيف") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Section 2: Measurement Mode, Unit & Pricing
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, extras.cardBorder.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (selectedUnitCategory == UnitMeasureCategory.WEIGHT) {
                                        Icons.Default.Scale
                                    } else {
                                        Icons.Default.Calculate
                                    },
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "طريقة الحساب والوحدة",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // 3-Mode Segmented Selector: Count / Weight / Pack & Measure
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, extras.cardBorder, RoundedCornerShape(14.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    Triple(UnitMeasureCategory.COUNT, "بالعدد", Icons.Default.Numbers),
                                    Triple(UnitMeasureCategory.WEIGHT, "بالوزن", Icons.Default.Scale),
                                    Triple(UnitMeasureCategory.MEASURE, "تعبئة ومقاس", Icons.Default.Straighten)
                                ).forEach { (cat, title, icon) ->
                                    val active = selectedUnitCategory == cat
                                    val bg by animateColorAsState(
                                        targetValue = if (active) PrimaryBlue else Color.Transparent,
                                        animationSpec = tween(180),
                                        label = "unit_mode_bg"
                                    )
                                    val fg by animateColorAsState(
                                        targetValue = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        animationSpec = tween(180),
                                        label = "unit_mode_fg"
                                    )
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(bg)
                                            .clickable {
                                                selectedUnitCategory = cat
                                                showCustomUnit = false
                                                val presets = UnitMeasurement.unitsForCategory(cat)
                                                if (unitLabel !in presets) {
                                                    unitLabel = presets.first()
                                                }
                                            }
                                            .padding(horizontal = if (uiScale.isCompact) 3.dp else 6.dp, vertical = 6.dp)
                                            .testTag("unit_mode_${cat.name.lowercase()}"),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!uiScale.isNarrow) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = fg,
                                                modifier = Modifier.size(if (uiScale.isCompact) 14.dp else 16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                        }
                                        Text(
                                            text = title,
                                            style = if (uiScale.isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                            color = fg,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Units within the selected category
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                UnitMeasurement.unitsForCategory(selectedUnitCategory).forEach { preset ->
                                    SelectablePillChip(
                                        label = preset,
                                        selected = !showCustomUnit && unitLabel == preset,
                                        onClick = {
                                            showCustomUnit = false
                                            unitLabel = preset
                                        }
                                    )
                                }
                                SelectablePillChip(
                                    label = "وحدة أخرى",
                                    selected = showCustomUnit,
                                    onClick = { showCustomUnit = true }
                                )
                            }

                            AnimatedVisibility(visible = showCustomUnit) {
                                OutlinedTextField(
                                    value = unitLabel,
                                    onValueChange = { unitLabel = it },
                                    label = { Text("اسم الوحدة المخصصة") },
                                    placeholder = { Text("مثال: لفة / شوال / زوج") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // 3 Price Tiers: Purchase + Wholesale (بيع جملة) + Retail (بيع قطاعي)
                            OutlinedTextField(
                                value = purchasePriceText,
                                onValueChange = { purchasePriceText = it; err = null },
                                label = { Text("سعر الشراء ($cleanUnit)") },
                                suffix = { Text(currency, style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("product_form_purchase_price")
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = wholesalePriceText,
                                    onValueChange = { wholesalePriceText = it; err = null },
                                    label = { Text("سعر بيع جملة ($cleanUnit)") },
                                    suffix = { Text(currency, style = MaterialTheme.typography.labelSmall) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("product_form_wholesale_price")
                                )
                                OutlinedTextField(
                                    value = salePriceText,
                                    onValueChange = { salePriceText = it; err = null },
                                    label = { Text("سعر بيع قطاعي ($cleanUnit) *") },
                                    suffix = { Text(currency, style = MaterialTheme.typography.labelSmall) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("product_form_sale_price")
                                )
                            }

                            // Live Calculation Preview Card for Selected Unit
                            AnimatedVisibility(
                                visible = parsedSalePreview > 0L,
                                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                                exit = shrinkVertically(tween(160)) + fadeOut(tween(140))
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = PrimaryBlue.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.25f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "حاسبة الوحدة التلقائية ($cleanUnit):",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryBlue
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            previewExamples.forEach { (qtyLbl, priceStr) ->
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(1.dp, extras.cardBorder),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Text(
                                                            text = qtyLbl,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = priceStr,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (parsedBuyPreview > 0L) {
                                            val retailProfit = parsedSalePreview - parsedBuyPreview
                                            val wholeProfitText = if (parsedWholesalePreview > 0L) {
                                                " • بيع جملة: ${MoneyFormatter.formatEgp(parsedWholesalePreview - parsedBuyPreview, currency)}"
                                            } else ""
                                            Text(
                                                text = "ربح بيع قطاعي: ${MoneyFormatter.formatEgp(retailProfit, currency)}$wholeProfitText",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (retailProfit >= 0L) extras.success else extras.danger
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 3: Stock Tracking, Barcode & Notes
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, extras.cardBorder.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(PrimaryBlue.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "تتبع كمية المخزون تلقائيًا",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "يُخصم بوحدة ($cleanUnit) عند البيع أو السحب",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = trackStock,
                                    onCheckedChange = { trackStock = it },
                                    modifier = Modifier.testTag("product_form_track_stock_switch")
                                )
                            }

                            AnimatedVisibility(visible = trackStock) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = stockQtyText,
                                        onValueChange = { stockQtyText = it; err = null },
                                        label = { Text("الرصيد ($cleanUnit)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("product_form_stock_qty")
                                    )
                                    OutlinedTextField(
                                        value = minAlertText,
                                        onValueChange = { minAlertText = it; err = null },
                                        label = { Text("حد التنبيه ($cleanUnit)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("product_form_min_alert")
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = sku,
                                onValueChange = { sku = it },
                                label = { Text("كود الصنف / باركود (اختياري)") },
                                leadingIcon = {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("ملاحظات (اختياري)") },
                                leadingIcon = {
                                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = fieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    AnimatedVisibility(visible = err != null) {
                        Surface(
                            color = extras.dangerContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = extras.danger,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = err.orEmpty(),
                                    color = extras.danger,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Sticky Bottom Action Bar
                HorizontalDivider(color = extras.cardBorder.copy(alpha = 0.7f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val saleP = if (salePriceText.isBlank()) 0L else MoneyFormatter.parseEgpToPiasters(salePriceText)
                            if (saleP == null || saleP < 0L) {
                                err = "أدخل سعر البيع قطاعي صحيحًا"
                                return@Button
                            }
                            val wholesaleP = if (wholesalePriceText.isBlank()) 0L else (MoneyFormatter.parseEgpToPiasters(wholesalePriceText) ?: -1L)
                            if (wholesaleP < 0L) {
                                err = "أدخل سعر بيع جملة صحيحًا"
                                return@Button
                            }
                            val buyP = if (purchasePriceText.isBlank()) 0L else (MoneyFormatter.parseEgpToPiasters(purchasePriceText) ?: -1L)
                            if (buyP < 0L) {
                                err = "أدخل سعر شراء صحيحًا"
                                return@Button
                            }
                            val stockM = if (!trackStock || stockQtyText.isBlank()) 0L else (MoneyFormatter.parseQuantityToMilli(stockQtyText) ?: -1L)
                            if (stockM < 0L) {
                                err = "أدخل رصيد مخزون صحيحًا"
                                return@Button
                            }
                            val minM = if (!trackStock || minAlertText.isBlank()) 0L else (MoneyFormatter.parseQuantityToMilli(minAlertText) ?: -1L)
                            if (minM < 0L) {
                                err = "أدخل حد تنبيه صحيحًا"
                                return@Button
                            }
                            val preservedHalf = initial?.halfWholesalePricePiasters ?: 0L
                            onSave(name, category, sku, cleanUnit, saleP, preservedHalf, wholesaleP, buyP, stockM, trackStock, minM, notes) { err = it }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 46.dp)
                            .testTag("product_form_save_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (initial == null) "حفظ الصنف" else "حفظ التعديلات",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, extras.cardBorder),
                        modifier = Modifier
                            .weight(0.8f)
                            .heightIn(min = 46.dp)
                    ) {
                        Text("إلغاء", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectablePillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (selected) PrimaryBlue else MaterialTheme.colorScheme.surface,
        animationSpec = tween(160),
        label = "pill_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(160),
        label = "pill_fg"
    )
    val borderCol = if (selected) PrimaryBlue else HesabatyThemeExtras.colors.cardBorder

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, borderCol)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = fg
            )
        }
    }
}

@Composable
private fun AdjustStockDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onConfirm: (deltaMilli: Long, reason: String, onErr: (String) -> Unit) -> Unit
) {
    var isAdd by remember { mutableStateOf(true) }
    var qtyText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل مخزون: ${product.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "الرصيد الحالي: ${MoneyFormatter.formatQuantity(product.stockQuantityMilli)} ${product.unitLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isAdd,
                        onClick = { isAdd = true },
                        label = { Text("+ إضافة وارد") }
                    )
                    FilterChip(
                        selected = !isAdd,
                        onClick = { isAdd = false },
                        label = { Text("− خصم / تالف") }
                    )
                }
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it; err = null },
                    label = { Text("الكمية (${product.unitLabel}) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("السبب / ملاحظة (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = MoneyFormatter.parseQuantityToMilli(qtyText)
                    if (q == null || q <= 0L) {
                        err = "أدخل كمية صحيحة أكبر من صفر"
                        return@Button
                    }
                    onConfirm(if (isAdd) q else -q, reason) { err = it }
                }
            ) { Text("تحديث الرصيد") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

/**
 * Reusable searchable picker allowing the shop owner to optionally choose any saved product
 * inside NewSaleScreen or DailyWithdrawalDetailScreen, or quickly create a new one on the spot.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductPickerDialog(
    products: List<ProductEntity>,
    currency: String,
    onDismiss: () -> Unit,
    onSelect: (ProductEntity) -> Unit = {},
    onQuickAddNew: () -> Unit,
    showPriceTierSelector: Boolean = false,
    isPurchaseMode: Boolean = false,
    onSelectWithPrice: ((ProductEntity, Long) -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    var selectedTier by remember { mutableStateOf(SalePriceTier.RETAIL) }
    val extras = HesabatyThemeExtras.colors
    val uiScale = HesabatyThemeExtras.uiScale
    val filtered = remember(products, query) {
        val q = query.trim()
        if (q.isEmpty()) products
        else products.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true) ||
                it.skuOrBarcode.contains(q, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (uiScale.isCompact) 0.96f else 0.94f)
                .widthIn(max = 520.dp)
                .heightIn(max = 580.dp),
            shape = RoundedCornerShape(if (uiScale.isCompact) 22.dp else 26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, extras.cardBorder),
            shadowElevation = 14.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(PrimaryDarkBlue, PrimaryBlue, Color(0xFF2684FF))
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "اختيار من الأصناف المسجلة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = if (isPurchaseMode) "يتم تعبئة سعر الشراء وأسعار البيع جملة وقطاعي تلقائيًا" else "يتم تعبئة السعر ووحدة الحساب تلقائيًا",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("ابحث باسم الصنف أو الكود...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "مسح")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (showPriceTierSelector) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(SalePriceTier.RETAIL, SalePriceTier.WHOLESALE).forEach { tier ->
                                FilterChip(
                                    selected = selectedTier == tier,
                                    onClick = { selectedTier = tier },
                                    label = {
                                        Text(
                                            text = "بيع ${tier.arabicLabel}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("picker_tier_${tier.name}")
                                )
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (products.isEmpty()) {
                                    "لا توجد أصناف مسجلة بعد. يمكنك إضافة صنف جديد الآن أو الكتابة يدويًا في الفاتورة."
                                } else {
                                    "لا توجد أصناف مطابقة للبحث."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.id }) { prod ->
                                val unitCat = UnitMeasurement.categoryForUnit(prod.unitLabel)
                                val displayPrice = when {
                                    isPurchaseMode -> if (prod.purchasePricePiasters > 0L) prod.purchasePricePiasters else prod.salePricePiasters
                                    showPriceTierSelector -> prod.priceForTier(selectedTier)
                                    else -> prod.salePricePiasters
                                }
                                HesabatyCard(
                                    onClick = {
                                        if (onSelectWithPrice != null) {
                                            onSelectWithPrice(prod, displayPrice)
                                        } else {
                                            onSelect(prod)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(prod.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                StatusBadge(
                                                    label = "${unitCat.title} • ${prod.unitLabel}",
                                                    textColor = PrimaryBlue,
                                                    backgroundColor = PrimaryBlue.copy(alpha = 0.1f)
                                                )
                                                if (prod.wholesalePricePiasters > 0L) {
                                                    StatusBadge(
                                                        label = "جملة: ${MoneyFormatter.formatEgp(prod.wholesalePricePiasters, currency)}",
                                                        textColor = extras.success,
                                                        backgroundColor = extras.successContainer
                                                    )
                                                }
                                                if (isPurchaseMode && prod.salePricePiasters > 0L) {
                                                    StatusBadge(
                                                        label = "قطاعي: ${MoneyFormatter.formatEgp(prod.salePricePiasters, currency)}",
                                                        textColor = PrimaryBlue,
                                                        backgroundColor = PrimaryBlue.copy(alpha = 0.08f)
                                                    )
                                                }
                                                if (prod.trackStock) {
                                                    StatusBadge(
                                                        label = "متاح: ${MoneyFormatter.formatQuantity(prod.stockQuantityMilli)}",
                                                        textColor = extras.success,
                                                        backgroundColor = extras.successContainer
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = MoneyFormatter.formatEgp(displayPrice, currency),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = PrimaryBlue
                                            )
                                            Text(
                                                text = when {
                                                    isPurchaseMode -> "شراء / ${prod.unitLabel}"
                                                    showPriceTierSelector -> "${selectedTier.arabicLabel} / ${prod.unitLabel}"
                                                    else -> "لكل ${prod.unitLabel}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onQuickAddNew,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إضافة صنف جديد الآن", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
