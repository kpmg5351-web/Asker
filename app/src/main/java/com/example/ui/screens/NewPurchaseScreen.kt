package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.core.UnitMeasureCategory
import com.example.core.UnitMeasurement
import com.example.core.UnitSubOption
import com.example.data.local.PaymentMethods
import com.example.data.local.PurchaseTypes
import com.example.data.repository.PurchaseLineDraft
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyDatePickerDialog
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel
import java.util.UUID

private data class PurchaseLineInputRow(
    val id: String = UUID.randomUUID().toString(),
    val description: String = "",
    val unitPriceText: String = "",
    val quantityText: String = "1",
    val productId: String? = null,
    val unitLabel: String = "قطعة",
    val subUnitLabel: String = "قطعة"
) {
    val activeSubOption: UnitSubOption
        get() {
            val options = UnitMeasurement.subUnitsFor(unitLabel)
            return options.firstOrNull { it.label == subUnitLabel } ?: options.first()
        }

    val primaryQuantityMilli: Long
        get() {
            if (quantityText.isBlank()) {
                return if (activeSubOption.numerator == activeSubOption.denominator) 1000L else 0L
            }
            return UnitMeasurement.parseInputToPrimaryMilli(quantityText, activeSubOption) ?: 0L
        }

    val computedPiasters: Long
        get() {
            val price = MoneyFormatter.parseEgpToPiasters(unitPriceText) ?: return 0L
            val qty = primaryQuantityMilli
            if (qty <= 0L) return 0L
            return MoneyFormatter.calculateLineTotalPiasters(price, qty)
        }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewPurchaseScreen(
    preselectedSupplierId: String? = null,
    viewModel: HesabatyViewModel,
    onBack: () -> Unit,
    onPurchaseCreated: (String) -> Unit
) {
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val currency = settings?.defaultCurrencyLabel ?: "ج.م"

    var selectedSupplierId by remember(preselectedSupplierId) { mutableStateOf(preselectedSupplierId ?: "") }
    var supplierMenuExpanded by remember { mutableStateOf(false) }
    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var showProductPicker by remember { mutableStateOf(false) }
    var showQuickAddProductDialog by remember { mutableStateOf(false) }

    val lineRows = remember { mutableStateListOf(PurchaseLineInputRow()) }
    var purchaseType by remember { mutableStateOf(PurchaseTypes.CASH) }
    var paymentMethod by remember { mutableStateOf(PaymentMethods.CASH) }
    var paidNowText by remember { mutableStateOf("") }
    var dueDateMillis by remember { mutableStateOf(EgyptTime.addDays(EgyptTime.nowMillis(), 14)) }
    var purchaseDateMillis by remember { mutableStateOf(EgyptTime.nowMillis()) }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var datePickerTarget by remember { mutableStateOf<String?>(null) }
    var draftIdempotencyKey by remember { mutableStateOf(UUID.randomUUID().toString()) }

    val grandTotalPiasters = lineRows.sumOf { it.computedPiasters }
    val totalQtyMilli = lineRows.sumOf { it.primaryQuantityMilli }
    val effectivePaidPiasters = when (purchaseType) {
        PurchaseTypes.CASH -> if (paidNowText.isBlank()) grandTotalPiasters else (MoneyFormatter.parseEgpToPiasters(paidNowText) ?: 0L)
        else -> if (paidNowText.isBlank()) 0L else (MoneyFormatter.parseEgpToPiasters(paidNowText) ?: 0L)
    }
    val remainingPiasters = (grandTotalPiasters - effectivePaidPiasters).coerceAtLeast(0L)
    val selectedSupplier = suppliers.firstOrNull { it.id == selectedSupplierId }
    val extras = HesabatyThemeExtras.colors
    val uiScale = HesabatyThemeExtras.uiScale

    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = "تسجيل فاتورة شراء",
                subtitle = "إضافة مشتريات للمورد وتحديث المخزون والحساب تلقائيًا",
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
            // 1. Select Supplier
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("1. اختيار المورد *", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showAddSupplierDialog = true }) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مورد جديد")
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = supplierMenuExpanded,
                        onExpandedChange = { supplierMenuExpanded = !supplierMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedSupplier?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("اختر المورد من القائمة") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supplierMenuExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("purchase_supplier_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = supplierMenuExpanded,
                            onDismissRequest = { supplierMenuExpanded = false }
                        ) {
                            if (suppliers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("لا يوجد موردون — أضف موردًا أولًا") },
                                    onClick = {
                                        supplierMenuExpanded = false
                                        showAddSupplierDialog = true
                                    }
                                )
                            } else {
                                suppliers.forEach { s ->
                                    DropdownMenuItem(
                                        text = {
                                            Text("${s.name} (المستحق: ${MoneyFormatter.formatEgp(s.currentBalancePiasters, currency)})")
                                        },
                                        onClick = {
                                            selectedSupplierId = s.id
                                            supplierMenuExpanded = false
                                            errorMsg = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Purchase Items
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("2. تفاصيل المشتريات والأصناف", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { showProductPicker = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("purchase_pick_product_btn")
                            ) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("من الأصناف")
                            }
                            TextButton(
                                onClick = { lineRows.add(PurchaseLineInputRow()) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("purchase_add_line_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("بند حر")
                            }
                        }
                    }

                    Text(
                        text = "الأصناف المرتبطة بكتالوج المحل تزيد كميتها بالمخزون تلقائيًا عند حفظ الفاتورة.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        lineRows.forEachIndexed { idx, row ->
                            var unitMenuExpanded by remember(row.id) { mutableStateOf(false) }
                            val subOptions = remember(row.unitLabel) { UnitMeasurement.subUnitsFor(row.unitLabel) }
                            val activeSub = row.activeSubOption
                            val unitCategory = UnitMeasurement.categoryForUnit(row.unitLabel)
                            val unitIcon = when (unitCategory) {
                                UnitMeasureCategory.COUNT -> Icons.Default.Numbers
                                UnitMeasureCategory.WEIGHT -> Icons.Default.Scale
                                UnitMeasureCategory.MEASURE -> Icons.Default.Straighten
                            }
                            val presets = remember(row.unitLabel, activeSub.label) {
                                UnitMeasurement.quickPresetsFor(row.unitLabel, activeSub.label)
                            }
                            val normalizedQtyInput = MoneyFormatter.normalizeDigits(row.quantityText)

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "#${idx + 1}",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            Box {
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                                    modifier = Modifier
                                                        .clickable { unitMenuExpanded = true }
                                                        .testTag("purchase_unit_selector_$idx")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(unitIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                                        Text(row.unitLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "تغيير الوحدة", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = unitMenuExpanded,
                                                    onDismissRequest = { unitMenuExpanded = false }
                                                ) {
                                                    UnitMeasurement.allQuickUnits.forEach { candidateUnit ->
                                                        val catLabel = UnitMeasurement.categoryForUnit(candidateUnit).title
                                                        DropdownMenuItem(
                                                            text = { Text("$candidateUnit  ($catLabel)") },
                                                            onClick = {
                                                                val nextSub = UnitMeasurement.subUnitsFor(candidateUnit).first().label
                                                                lineRows[idx] = row.copy(unitLabel = candidateUnit, subUnitLabel = nextSub)
                                                                unitMenuExpanded = false
                                                                errorMsg = null
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            if (subOptions.size > 1) {
                                                val altSub = subOptions.first { it.label != activeSub.label }
                                                Surface(
                                                    shape = RoundedCornerShape(50),
                                                    color = if (activeSub.numerator != activeSub.denominator) extras.success.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (activeSub.numerator != activeSub.denominator) extras.success.copy(alpha = 0.6f)
                                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                                    ),
                                                    modifier = Modifier.clickable {
                                                        val converted = UnitMeasurement.convertQuantityBetweenSubUnits(row.quantityText, activeSub, altSub)
                                                        lineRows[idx] = row.copy(subUnitLabel = altSub.label, quantityText = converted)
                                                        errorMsg = null
                                                    }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = if (activeSub.numerator != activeSub.denominator) extras.success else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                                        Text("بالـ${activeSub.label}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = if (activeSub.numerator != activeSub.denominator) extras.success else MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }

                                        if (lineRows.size > 1) {
                                            IconButton(
                                                onClick = { lineRows.removeAt(idx) },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف البند", tint = extras.danger, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = row.unitPriceText,
                                            onValueChange = { newVal -> lineRows[idx] = row.copy(unitPriceText = newVal); errorMsg = null },
                                            label = { Text("سعر الشراء (${row.unitLabel})") },
                                            placeholder = { Text("لكل ${row.unitLabel}") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(1.15f).testTag("purchase_price_input_$idx")
                                        )
                                        OutlinedTextField(
                                            value = row.quantityText,
                                            onValueChange = { newVal -> lineRows[idx] = row.copy(quantityText = newVal); errorMsg = null },
                                            label = { Text(UnitMeasurement.quantityFieldLabel(row.unitLabel, activeSub.label)) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            modifier = Modifier.weight(0.95f).testTag("purchase_qty_input_$idx")
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        presets.forEach { (presetLabel, presetValue) ->
                                            val isPresetSelected = normalizedQtyInput == presetValue
                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = if (isPresetSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isPresetSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                                ),
                                                modifier = Modifier.clickable { lineRows[idx] = row.copy(quantityText = presetValue); errorMsg = null }
                                            ) {
                                                Text(
                                                    text = presetLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isPresetSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = row.description,
                                        onValueChange = { lineRows[idx] = row.copy(description = it) },
                                        label = { Text("البيان / اسم الصنف المشتَرى (اختياري)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("purchase_desc_input_$idx")
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val priceParsed = MoneyFormatter.parseEgpToPiasters(row.unitPriceText) ?: 0L
                                            val qtyTextDisplay = if (row.primaryQuantityMilli > 0L) {
                                                UnitMeasurement.formatPrimaryWithSubUnit(
                                                    rawInput = row.quantityText.ifBlank { "1" },
                                                    primaryUnit = row.unitLabel,
                                                    subUnit = activeSub,
                                                    primaryMilli = row.primaryQuantityMilli
                                                )
                                            } else "0 ${row.unitLabel}"
                                            Text(
                                                text = if (priceParsed > 0L && row.primaryQuantityMilli > 0L) {
                                                    "$qtyTextDisplay × ${MoneyFormatter.formatEgp(priceParsed, currency)}"
                                                } else "حساب تلقائي بالـ${row.unitLabel}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = MoneyFormatter.formatEgp(row.computedPiasters, currency),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
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
                        Column {
                            Text("إجمالي فاتورة الشراء:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "عدد البنود: ${lineRows.size}  •  إجمالي الكمية: ${MoneyFormatter.formatQuantity(totalQtyMilli)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = MoneyFormatter.formatEgp(grandTotalPiasters, currency),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("purchase_grand_total_text")
                        )
                    }
                }
            }

            // 3. Purchase Payment Type
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Text("3. نوع الشراء والسداد للمورد", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            PurchaseTypes.CASH to "شراء كاش",
                            PurchaseTypes.CREDIT to "شراء آجل على الحساب"
                        ).forEach { (code, label) ->
                            FilterChip(
                                selected = purchaseType == code,
                                onClick = {
                                    purchaseType = code
                                    paidNowText = ""
                                    errorMsg = null
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("purchase_type_$code")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("طريقة الدفع للمورد:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            PaymentMethods.CASH to "نقدي",
                            PaymentMethods.EWALLET to "محفظة إلكترونية",
                            PaymentMethods.BANK_TRANSFER to "تحويل بنكي"
                        ).forEach { (code, label) ->
                            FilterChip(
                                selected = paymentMethod == code,
                                onClick = { paymentMethod = code },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = paidNowText,
                        onValueChange = { paidNowText = it; errorMsg = null },
                        label = {
                            Text(
                                if (purchaseType == PurchaseTypes.CASH) {
                                    "المبلغ المدفوع للمورد الآن"
                                } else {
                                    "الدفعة المقدمة للمورد (اختياري)"
                                }
                            )
                        },
                        placeholder = if (purchaseType == PurchaseTypes.CASH) {
                            { Text("اتركه فارغًا لكامل الإجمالي") }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("purchase_paid_now_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("المدفوع: ${MoneyFormatter.formatEgp(effectivePaidPiasters, currency)}", color = extras.success, fontWeight = FontWeight.SemiBold)
                        Text("المتبقي للمورد: ${MoneyFormatter.formatEgp(remainingPiasters, currency)}", color = if (remainingPiasters > 0) extras.danger else extras.success, fontWeight = FontWeight.Bold)
                    }

                    if (purchaseType == PurchaseTypes.CREDIT) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { datePickerTarget = "DUE" },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تاريخ استحقاق السداد: ${EgyptTime.formatDate(dueDateMillis)}")
                        }
                    }
                }
            }

            // 4. Date & Notes + Save Button
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    SectionHeader(title = "4. التاريخ والملاحظات")
                    OutlinedButton(
                        onClick = { datePickerTarget = "PURCHASE" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تاريخ الفاتورة: ${EgyptTime.formatDateTime(purchaseDateMillis)}")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("ملاحظات إضافية على فاتورة الشراء (اختياري)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMsg != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (selectedSupplierId.isBlank()) {
                                errorMsg = "يرجى اختيار المورد أولًا"
                                return@Button
                            }
                            val parsedLines = mutableListOf<PurchaseLineDraft>()
                            for (row in lineRows) {
                                val price = MoneyFormatter.parseEgpToPiasters(row.unitPriceText)
                                if (price == null || price <= 0L) {
                                    errorMsg = "يرجى إدخال سعر شراء صحيح أكبر من صفر في كل بند"
                                    return@Button
                                }
                                val activeSub = row.activeSubOption
                                val qty = if (row.quantityText.isBlank() && activeSub.numerator == activeSub.denominator) {
                                    1000L
                                } else {
                                    UnitMeasurement.parseInputToPrimaryMilli(row.quantityText, activeSub)
                                }
                                if (qty == null || qty <= 0L) {
                                    errorMsg = "يرجى إدخال ${UnitMeasurement.quantityFieldLabel(row.unitLabel, activeSub.label)} بشكل صحيح أكبر من صفر"
                                    return@Button
                                }
                                val finalDesc = UnitMeasurement.formatLineDescriptionWithUnit(
                                    rawDescription = row.description,
                                    primaryUnit = row.unitLabel,
                                    subUnit = activeSub,
                                    rawQtyInput = row.quantityText.ifBlank { "1" },
                                    defaultFallbackDesc = "مشتريات بضاعة"
                                )
                                parsedLines.add(PurchaseLineDraft(finalDesc, price, qty, row.productId))
                            }

                            viewModel.createPurchaseInvoice(
                                supplierId = selectedSupplierId,
                                lines = parsedLines,
                                purchaseType = purchaseType,
                                paymentMethod = paymentMethod,
                                paidNowPiasters = effectivePaidPiasters,
                                dueDateMillis = if (purchaseType == PurchaseTypes.CREDIT) dueDateMillis else null,
                                notes = notes,
                                occurredAtMillis = purchaseDateMillis,
                                idempotencyKey = draftIdempotencyKey
                            ) { ok, resultOrErr ->
                                if (ok) {
                                    draftIdempotencyKey = UUID.randomUUID().toString()
                                    onPurchaseCreated(resultOrErr)
                                } else {
                                    errorMsg = resultOrErr
                                }
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_purchase_button")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("حفظ وإصدار فاتورة الشراء", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    datePickerTarget?.let { target ->
        HesabatyDatePickerDialog(
            initialMillis = if (target == "DUE") dueDateMillis else purchaseDateMillis,
            onDismiss = { datePickerTarget = null },
            onDateSelected = { chosen ->
                if (target == "DUE") dueDateMillis = chosen else purchaseDateMillis = chosen
            }
        )
    }

    if (showAddSupplierDialog) {
        AddSupplierDialog(
            onDismiss = { showAddSupplierDialog = false },
            onSave = { name, phone, wa, addr, sNotes, opening, onErr ->
                viewModel.addSupplier(name, phone, wa, addr, sNotes, opening) { ok, idOrErr ->
                    if (ok) {
                        selectedSupplierId = idOrErr
                        showAddSupplierDialog = false
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
                val buyOrSale = if (prod.purchasePricePiasters > 0L) prod.purchasePricePiasters else prod.salePricePiasters
                val priceStr = if (buyOrSale > 0L) MoneyFormatter.formatEgpPlainInput(buyOrSale) else ""
                val pickedUnit = prod.unitLabel.ifBlank { "قطعة" }
                val defaultSub = UnitMeasurement.subUnitsFor(pickedUnit).first().label
                val emptyIndex = lineRows.indexOfFirst { it.description.isBlank() && it.unitPriceText.isBlank() }
                if (emptyIndex >= 0) {
                    lineRows[emptyIndex] = lineRows[emptyIndex].copy(
                        description = prod.name,
                        unitPriceText = priceStr,
                        quantityText = "1",
                        productId = prod.id,
                        unitLabel = pickedUnit,
                        subUnitLabel = defaultSub
                    )
                } else {
                    lineRows.add(
                        PurchaseLineInputRow(
                            description = prod.name,
                            unitPriceText = priceStr,
                            quantityText = "1",
                            productId = prod.id,
                            unitLabel = pickedUnit,
                            subUnitLabel = defaultSub
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
            currency = currency,
            onDismiss = { showQuickAddProductDialog = false },
            onSave = { name, cat, sku, unit, saleP, buyP, stockM, track, minM, pNotes, onErr ->
                viewModel.addProduct(name, cat, sku, unit, saleP, buyP, stockM, track, minM, pNotes) { ok, newIdOrErr ->
                    if (ok) {
                        val pickedBuy = if (buyP > 0L) buyP else saleP
                        val priceStr = if (pickedBuy > 0L) MoneyFormatter.formatEgpPlainInput(pickedBuy) else ""
                        val pickedUnit = unit.ifBlank { "قطعة" }
                        val defaultSub = UnitMeasurement.subUnitsFor(pickedUnit).first().label
                        val emptyIndex = lineRows.indexOfFirst { it.description.isBlank() && it.unitPriceText.isBlank() }
                        if (emptyIndex >= 0) {
                            lineRows[emptyIndex] = lineRows[emptyIndex].copy(
                                description = name.trim(),
                                unitPriceText = priceStr,
                                quantityText = "1",
                                productId = newIdOrErr,
                                unitLabel = pickedUnit,
                                subUnitLabel = defaultSub
                            )
                        } else {
                            lineRows.add(
                                PurchaseLineInputRow(
                                    description = name.trim(),
                                    unitPriceText = priceStr,
                                    quantityText = "1",
                                    productId = newIdOrErr,
                                    unitLabel = pickedUnit,
                                    subUnitLabel = defaultSub
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
