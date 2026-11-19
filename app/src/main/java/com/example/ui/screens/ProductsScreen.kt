package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.Scaffold
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
import com.example.data.local.ProductEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.StatusBadge
import com.example.ui.components.SummaryStatPill
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel

private val CommonCategories = listOf("عام", "مواد غذائية", "مستلزمات", "قطع غيار", "أدوات", "ملابس", "إلكترونيات")
private val CommonUnits = listOf("قطعة", "كرتونة", "كيلو", "علبة", "دستة", "متر", "شكارة", "عبوة")

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
            columns = listOf("الصنف", "الوحدة", "سعر البيع", "التصنيف"),
            rows = listToShare.map { p ->
                listOf(
                    p.name,
                    p.unitLabel,
                    MoneyFormatter.formatEgp(p.salePricePiasters, currency),
                    p.category.ifBlank { "عام" }
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                if (product.purchasePricePiasters > 0L) {
                                    Text(
                                        text = "التكلفة: ${MoneyFormatter.formatEgp(product.purchasePricePiasters, currency)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
            onDismiss = { showAddDialog = false },
            onSave = { name, cat, sku, unit, saleP, buyP, stockM, track, minM, notes, onErr ->
                viewModel.addProduct(name, cat, sku, unit, saleP, buyP, stockM, track, minM, notes) { ok, msg ->
                    if (ok) showAddDialog = false else onErr(msg)
                }
            }
        )
    }

    editingProduct?.let { prod ->
        ProductFormDialog(
            initial = prod,
            onDismiss = { editingProduct = null },
            onSave = { name, cat, sku, unit, saleP, buyP, stockM, track, minM, notes, onErr ->
                viewModel.updateProduct(prod.id, name, cat, sku, unit, saleP, buyP, stockM, track, minM, notes) { ok, msg ->
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

@Composable
fun ProductFormDialog(
    initial: ProductEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        category: String,
        skuOrBarcode: String,
        unitLabel: String,
        salePricePiasters: Long,
        purchasePricePiasters: Long,
        stockQuantityMilli: Long,
        trackStock: Boolean,
        minStockAlertMilli: Long,
        notes: String,
        onErr: (String) -> Unit
    ) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "عام") }
    var sku by remember { mutableStateOf(initial?.skuOrBarcode ?: "") }
    var unitLabel by remember { mutableStateOf(initial?.unitLabel ?: "قطعة") }
    var salePriceText by remember {
        mutableStateOf(
            if (initial != null && initial.salePricePiasters > 0L) MoneyFormatter.formatEgpPlainInput(initial.salePricePiasters) else ""
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial == null) "إضافة صنف جديد" else "تعديل بيانات الصنف",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; err = null },
                    label = { Text("اسم الصنف *") },
                    placeholder = { Text("مثال: كرتونة زيت / شيكارة أسمنت / تيشيرت") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_form_name")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = salePriceText,
                        onValueChange = { salePriceText = it; err = null },
                        label = { Text("سعر البيع (ج.م) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("product_form_sale_price")
                    )
                    OutlinedTextField(
                        value = purchasePriceText,
                        onValueChange = { purchasePriceText = it; err = null },
                        label = { Text("سعر الشراء (اختياري)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("product_form_purchase_price")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unitLabel,
                        onValueChange = { unitLabel = it },
                        label = { Text("الوحدة") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("التصنيف") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick unit presets
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CommonUnits.forEach { preset ->
                        FilterChip(
                            selected = unitLabel == preset,
                            onClick = { unitLabel = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Quick category presets
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CommonCategories.forEach { preset ->
                        FilterChip(
                            selected = category == preset,
                            onClick = { category = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("كود الصنف / باركود (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("تتبع كمية المخزون تلقائيًا", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "يُخصم تلقائيًا عند اختيار الصنف في المبيعات أو السحب اليومي",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = trackStock,
                        onCheckedChange = { trackStock = it },
                        modifier = Modifier.testTag("product_form_track_stock_switch")
                    )
                }

                if (trackStock) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stockQtyText,
                            onValueChange = { stockQtyText = it; err = null },
                            label = { Text("الرصيد الحالي") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("product_form_stock_qty")
                        )
                        OutlinedTextField(
                            value = minAlertText,
                            onValueChange = { minAlertText = it; err = null },
                            label = { Text("حد التنبيه") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("product_form_min_alert")
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    modifier = Modifier.fillMaxWidth()
                )

                err?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val saleP = if (salePriceText.isBlank()) 0L else MoneyFormatter.parseEgpToPiasters(salePriceText)
                    if (saleP == null || saleP < 0L) {
                        err = "أدخل سعر بيع صحيحًا"
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
                    onSave(name, category, sku, unitLabel, saleP, buyP, stockM, trackStock, minM, notes) { err = it }
                },
                modifier = Modifier.testTag("product_form_save_btn")
            ) {
                Text(if (initial == null) "حفظ الصنف" else "حفظ التعديلات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
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
@Composable
fun ProductPickerDialog(
    products: List<ProductEntity>,
    currency: String,
    onDismiss: () -> Unit,
    onSelect: (ProductEntity) -> Unit,
    onQuickAddNew: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(products, query) {
        val q = query.trim()
        if (q.isEmpty()) products
        else products.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true) ||
                it.skuOrBarcode.contains(q, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار من الأصناف المسجلة", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("بحث باسم الصنف أو الكود...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = if (products.isEmpty()) {
                            "لا توجد أصناف مسجلة بعد. يمكنك إضافة صنف جديد الآن أو الكتابة يدويًا في الفاتورة."
                        } else {
                            "لا توجد أصناف مطابقة للبحث."
                        },
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
                        items(filtered, key = { it.id }) { prod ->
                            HesabatyCard(
                                onClick = { onSelect(prod) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prod.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = buildString {
                                                append("${prod.category} • ${prod.unitLabel}")
                                                if (prod.trackStock) {
                                                    append(" • متاح: ${MoneyFormatter.formatQuantity(prod.stockQuantityMilli)}")
                                                }
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = MoneyFormatter.formatEgp(prod.salePricePiasters, currency),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = onQuickAddNew,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إضافة صنف جديد الآن")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}
