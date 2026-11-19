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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.EgyptTime
import com.example.core.MoneyFormatter
import com.example.core.PdfInvoiceGenerator
import com.example.core.WhatsAppHelper
import com.example.data.repository.ReportOutput
import com.example.data.repository.ReportType
import com.example.ui.components.EmptyStateView
import com.example.ui.components.HesabatyCard
import com.example.ui.components.HesabatyDatePickerDialog
import com.example.ui.components.HesabatyTopBar
import com.example.ui.components.KpiMetricCard
import com.example.ui.theme.HesabatyThemeExtras
import com.example.ui.viewmodel.HesabatyViewModel

@Composable
fun ReportsScreen(
    viewModel: HesabatyViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val tenant by viewModel.activeTenant.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val currency = settings?.defaultCurrencyLabel ?: "ج.م"
    val waCountry = settings?.whatsappCountryCode ?: "20"

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedType by remember { mutableStateOf(ReportType.DAILY_SALES) }
    var fromMillis by remember { mutableStateOf(EgyptTime.startOfMonthMillis(EgyptTime.nowMillis())) }
    var toMillis by remember { mutableStateOf(EgyptTime.nowMillis()) }
    var searchQuery by remember { mutableStateOf("") }
    var reportOutput by remember { mutableStateOf<ReportOutput?>(null) }
    var pickingDate by remember { mutableStateOf<String?>(null) }

    val extras = HesabatyThemeExtras.colors

    LaunchedEffect(selectedType, fromMillis, toMillis, searchQuery) {
        reportOutput = viewModel.loadReport(selectedType, fromMillis, toMillis, searchQuery)
    }

    Scaffold(
        topBar = {
            HesabatyTopBar(
                title = "التقارير المالية",
                subtitle = "ملخص حسابات المحل و13 تقريرًا تفصيليًا في قسم مستقل",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("ملخص الحسابات") },
                    modifier = Modifier.testTag("reports_tab_summary")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("التقارير التفصيلية (13)") },
                    modifier = Modifier.testTag("reports_tab_detailed")
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectedTab == 0) {
                    // Complete Live Financial Summary (Moved off Main Screen into Standalone Reports)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiMetricCard(
                                title = "إجمالي مستحقات العملاء",
                                value = MoneyFormatter.formatEgp(summary.totalCustomerReceivablesPiasters, currency),
                                icon = Icons.Default.Groups,
                                accentColor = MaterialTheme.colorScheme.primary,
                                accentContainer = MaterialTheme.colorScheme.primaryContainer,
                                onClick = { onNavigate("customers") },
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                title = "إجمالي مديونيات الموردين",
                                value = MoneyFormatter.formatEgp(summary.totalSupplierPayablesPiasters, currency),
                                icon = Icons.Default.LocalShipping,
                                accentColor = extras.danger,
                                accentContainer = extras.dangerContainer,
                                onClick = { onNavigate("suppliers") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiMetricCard(
                                title = "مبيعات اليوم",
                                value = MoneyFormatter.formatEgp(summary.todaySalesPiasters, currency),
                                icon = Icons.Default.PointOfSale,
                                accentColor = MaterialTheme.colorScheme.primary,
                                accentContainer = MaterialTheme.colorScheme.primaryContainer,
                                onClick = {
                                    selectedType = ReportType.DAILY_SALES
                                    selectedTab = 1
                                },
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                title = "المقبوضات النقدية اليوم",
                                value = MoneyFormatter.formatEgp(summary.todayCashCollectionsPiasters, currency),
                                icon = Icons.Default.Savings,
                                accentColor = extras.success,
                                accentContainer = extras.successContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            KpiMetricCard(
                                title = "المقبوضات بالمحافظ الإلكترونية",
                                value = MoneyFormatter.formatEgp(summary.todayEWalletCollectionsPiasters, currency),
                                icon = Icons.Default.AccountBalanceWallet,
                                accentColor = extras.wallet,
                                accentContainer = extras.walletContainer,
                                modifier = Modifier.weight(1f)
                            )
                            KpiMetricCard(
                                title = "إجمالي المدفوعات اليوم",
                                value = MoneyFormatter.formatEgp(summary.todaySupplierPaymentsPiasters, currency),
                                icon = Icons.Default.Payments,
                                accentColor = extras.danger,
                                accentContainer = extras.dangerContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        KpiMetricCard(
                            title = "صافي حركة اليوم (المتحصلات − المدفوعات)",
                            value = MoneyFormatter.formatEgp(summary.todayNetCashflowPiasters, currency),
                            icon = Icons.Default.SwapVert,
                            accentColor = if (summary.todayNetCashflowPiasters >= 0) extras.success else extras.danger,
                            accentContainer = if (summary.todayNetCashflowPiasters >= 0) extras.successContainer else extras.dangerContainer,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ReportCountCard(
                                label = "عدد العملاء",
                                count = "${summary.customersCount}",
                                color = MaterialTheme.colorScheme.primary,
                                onClick = { onNavigate("customers") },
                                modifier = Modifier.weight(1f)
                            )
                            ReportCountCard(
                                label = "عدد الموردين",
                                count = "${summary.suppliersCount}",
                                color = MaterialTheme.colorScheme.secondary,
                                onClick = { onNavigate("suppliers") },
                                modifier = Modifier.weight(1f)
                            )
                            ReportCountCard(
                                label = "عملاء متأخرون",
                                count = "${summary.overdueCustomersCount}",
                                color = if (summary.overdueCustomersCount > 0) extras.danger else extras.success,
                                onClick = {
                                    selectedType = ReportType.OVERDUE
                                    selectedTab = 1
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        Button(
                            onClick = { selectedTab = 1 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("reports_open_detailed_btn")
                        ) {
                            Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("فتح التقارير التفصيلية (13 تقريرًا)", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Report Type Selector (all 13 reports)
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ReportType.entries) { type ->
                                FilterChip(
                                    selected = selectedType == type,
                                    onClick = { selectedType = type },
                                    label = { Text(type.titleAr) },
                                    modifier = Modifier.testTag("report_chip_${type.name}")
                                )
                            }
                        }
                    }

            // Date Range & Search Filters
            item {
                HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { pickingDate = "FROM" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("من: ${EgyptTime.formatDate(fromMillis)}", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { pickingDate = "TO" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إلى: ${EgyptTime.formatDate(toMillis)}", maxLines = 1)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("بحث باسم العميل أو المورد أو رقم الفاتورة...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Report Summary & Export Actions
            reportOutput?.let { report ->
                item {
                    HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = report.type.titleAr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = report.periodLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        report.summaryPairs.forEach { (k, v) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(k, style = MaterialTheme.typography.bodyMedium)
                                Text(v, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val printModel = PdfInvoiceGenerator.ReportPrintModel(
                                        title = report.type.titleAr,
                                        shopName = tenant?.shopName ?: "حساباتي",
                                        periodLabel = report.periodLabel,
                                        summaryPairs = report.summaryPairs,
                                        columns = report.columns,
                                        rows = report.rows
                                    )
                                    val caption = WhatsAppHelper.buildReportShareMessage(
                                        title = report.type.titleAr,
                                        shopName = tenant?.shopName ?: "حساباتي",
                                        periodLabel = report.periodLabel,
                                        summaryPairs = report.summaryPairs
                                    )
                                    val (_, uri) = PdfInvoiceGenerator.generateReportPdf(context, printModel)
                                    val res = WhatsAppHelper.shareFileViaWhatsAppOrChooser(
                                        context = context,
                                        fileUri = uri,
                                        mimeType = "application/pdf",
                                        caption = caption,
                                        countryCode = waCountry
                                    )
                                    if (!res.launched && res.errorMessage != null) viewModel.emitMessage(res.errorMessage, true)
                                },
                                modifier = Modifier.weight(1f).testTag("report_export_pdf_btn")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تصدير PDF")
                            }
                            Button(
                                onClick = {
                                    val summaryMsg = WhatsAppHelper.buildReportShareMessage(
                                        title = report.type.titleAr,
                                        shopName = tenant?.shopName ?: "حساباتي",
                                        periodLabel = report.periodLabel,
                                        summaryPairs = report.summaryPairs
                                    )
                                    val printModel = PdfInvoiceGenerator.ReportPrintModel(
                                        title = report.type.titleAr,
                                        shopName = tenant?.shopName ?: "حساباتي",
                                        periodLabel = report.periodLabel,
                                        summaryPairs = report.summaryPairs,
                                        columns = report.columns,
                                        rows = report.rows
                                    )
                                    val (_, uri) = PdfInvoiceGenerator.generateReportInvoicePng(
                                        context = context,
                                        model = printModel,
                                        shopAddress = tenant?.address.orEmpty(),
                                        shopPhone = tenant?.phone.orEmpty(),
                                        documentBadge = "تقرير مالي",
                                        footerNote = "ملخص مالي معتمد — ${tenant?.shopName ?: "حساباتي"}"
                                    )
                                    val res = WhatsAppHelper.shareFileViaWhatsAppOrChooser(
                                        context = context,
                                        fileUri = uri,
                                        mimeType = "image/png",
                                        caption = summaryMsg,
                                        countryCode = waCountry
                                    )
                                    if (!res.launched && res.errorMessage != null) viewModel.emitMessage(res.errorMessage, true)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = extras.success),
                                modifier = Modifier.weight(1f).testTag("report_whatsapp_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("واتساب (صورة)")
                            }
                        }
                    }
                }

                // Data Table
                if (report.rows.isEmpty()) {
                    item {
                        EmptyStateView(
                            icon = Icons.Default.Assessment,
                            title = "لا توجد سجلات في هذه الفترة",
                            subtitle = "جرّب توسيع الفترة الزمنية أو إزالة نص البحث."
                        )
                    }
                } else {
                    item {
                        HesabatyCard(modifier = Modifier.fillMaxWidth()) {
                            val borderColor = extras.cardBorder
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(1.dp, borderColor)
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                report.columns.forEach { col ->
                                    Text(
                                        text = col,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            report.rows.forEach { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(0.5.dp, borderColor)
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    row.forEach { cell ->
                                        Text(
                                            text = cell,
                                            style = MaterialTheme.typography.labelSmall,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
            }
        }
    }

    pickingDate?.let { target ->
        HesabatyDatePickerDialog(
            initialMillis = if (target == "FROM") fromMillis else toMillis,
            onDismiss = { pickingDate = null },
            onDateSelected = { chosen ->
                if (target == "FROM") fromMillis = chosen else toMillis = chosen
            }
        )
    }
}

@Composable
private fun ReportCountCard(
    label: String,
    count: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HesabatyCard(onClick = onClick, modifier = modifier) {
        Text(
            text = count,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}
