package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import java.io.File
import java.io.FileOutputStream

/**
 * Renders Egyptian commercial RTL invoices (matching traditional columns: البيان | السعر | العدد | جنيه | قرش),
 * account statements, payment receipts, and financial reports into crisp, royal-blue commercial
 * invoice-styled PNG images and PDF documents shared via FileProvider.
 */
object PdfInvoiceGenerator {

    data class InvoiceLineRow(
        val description: String,
        val unitPricePiasters: Long,
        val quantityMilli: Long,
        val lineTotalPiasters: Long,
        val timeLabel: String? = null
    )

    data class InvoicePrintModel(
        val shopName: String,
        val shopAddress: String,
        val shopPhone: String,
        val customerName: String,
        val invoiceNumber: String,
        val dateFormatted: String,
        val invoiceTypeLabel: String,
        val paymentMethodLabel: String,
        val lines: List<InvoiceLineRow>,
        val totalPiasters: Long,
        val paidPiasters: Long,
        val remainingPiasters: Long,
        val notes: String = "",
        val signatureName: String = ""
    )

    data class ReportPrintModel(
        val title: String,
        val shopName: String,
        val periodLabel: String,
        val summaryPairs: List<Pair<String, String>>,
        val columns: List<String>,
        val rows: List<List<String>>
    )

    private fun loadCairoTypeface(context: Context): Typeface {
        return try {
            ResourcesCompat.getFont(context, R.font.cairo) ?: Typeface.SANS_SERIF
        } catch (_: Exception) {
            Typeface.SANS_SERIF
        }
    }

    private fun exportsDir(context: Context): File {
        return File(context.cacheDir, "exports").apply { mkdirs() }
    }

    fun generateInvoicePdf(context: Context, model: InvoicePrintModel): Pair<File, Uri> {
        val pageWidth = 595
        val estimatedHeight = (380 + model.lines.size * 34 + 250).coerceAtLeast(842)
        val safeNum = model.invoiceNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val outFile = File(exportsDir(context), "invoice_${safeNum}_${System.currentTimeMillis()}.pdf")

        var document: PdfDocument? = null
        try {
            document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, estimatedHeight, 1).create()
            val page = document.startPage(pageInfo)
            drawInvoiceOnCanvas(context, page.canvas, pageWidth.toFloat(), estimatedHeight.toFloat(), model)
            document.finishPage(page)
            FileOutputStream(outFile).use { document.writeTo(it) }
        } catch (_: IllegalStateException) {
            writeMinimalPdfFallback(
                outFile = outFile,
                title = "Hesabaty Invoice ${model.invoiceNumber}",
                summaryLine = "Total: ${MoneyFormatter.formatEgp(model.totalPiasters, "EGP")}"
            )
        } finally {
            runCatching { document?.close() }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        return outFile to uri
    }

    private fun writeMinimalPdfFallback(outFile: File, title: String, summaryLine: String) {
        val safeTitle = title.replace("(", "[").replace(")", "]")
        val safeSummary = summaryLine.replace("(", "[").replace(")", "]")
        val stream = "BT /F1 12 Tf 40 780 Td ($safeTitle) Tj 0 -20 Td ($safeSummary) Tj ET"
        val pdfText = buildString {
            append("%PDF-1.4\n")
            append("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n")
            append("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n")
            append("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n")
            append("4 0 obj << /Length ${stream.length} >> stream\n$stream\nendstream endobj\n")
            append("5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n")
            append("trailer << /Root 1 0 R >>\n%%EOF\n")
        }
        outFile.writeText(pdfText, Charsets.UTF_8)
    }

    /**
     * Generates a high-resolution (900px wide) royal-blue commercial invoice PNG image
     * suitable for WhatsApp sharing with crystal-clear Arabic typography.
     */
    fun generateInvoicePng(context: Context, model: InvoicePrintModel): Pair<File, Uri> {
        val logicalWidth = 595f
        val extraNotesH = (if (model.notes.isNotBlank()) 28 else 0) + (if (model.signatureName.isNotBlank()) 26 else 0)
        val logicalHeight = (430 + model.lines.size * 34 + extraNotesH + 170).coerceAtLeast(740).toFloat()

        val widthPx = 900
        val scale = widthPx / logicalWidth
        val heightPx = (logicalHeight * scale).toInt()

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale, scale)
        drawInvoiceOnCanvas(context, canvas, logicalWidth, logicalHeight, model)
        canvas.restore()

        val safeNum = model.invoiceNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val outFile = File(exportsDir(context), "invoice_${safeNum}_${System.currentTimeMillis()}.png")
        FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 96, it) }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        return outFile to uri
    }

    /**
     * Generates a high-resolution (900px wide) invoice-styled PNG image for Customer Statements,
     * Supplier Statements, and Financial Reports — giving every WhatsApp share a uniform,
     * official royal-blue & gold commercial invoice look.
     */
    fun generateReportInvoicePng(
        context: Context,
        model: ReportPrintModel,
        recipientName: String? = null,
        recipientLabel: String = "السيد / العميل",
        shopAddress: String = "",
        shopPhone: String = "",
        documentBadge: String = "كشف حساب",
        statusRemainingPiasters: Long? = null,
        footerNote: String = "شكرًا لتعاملكم وثقتكم الغالية — نسعد دائمًا بخدمتكم"
    ): Pair<File, Uri> {
        val maxRows = 24
        val displayRows = model.rows.take(maxRows)
        val hiddenRowsCount = (model.rows.size - displayRows.size).coerceAtLeast(0)

        val logicalWidth = 595f
        val headerAndMetaH = if (!recipientName.isNullOrBlank()) 225f else 182f
        val summaryCardRows = ((model.summaryPairs.size + 2) / 3).coerceAtLeast(1)
        val summarySectionH = if (model.summaryPairs.isNotEmpty()) summaryCardRows * 64f + 16f else 0f
        val tableHeaderH = if (model.columns.isNotEmpty()) 34f else 0f
        val tableRowsH = if (displayRows.isEmpty()) 48f else displayRows.size * 30f
        val truncationNoticeH = if (hiddenRowsCount > 0) 28f else 0f
        val footerH = 92f

        val logicalHeight = (headerAndMetaH + summarySectionH + tableHeaderH + tableRowsH + truncationNoticeH + footerH)
            .coerceAtLeast(620f)

        val widthPx = 900
        val scale = widthPx / logicalWidth
        val heightPx = (logicalHeight * scale).toInt()

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(scale, scale)
        drawReportInvoiceOnCanvas(
            context = context,
            canvas = canvas,
            width = logicalWidth,
            height = logicalHeight,
            model = model,
            displayRows = displayRows,
            hiddenRowsCount = hiddenRowsCount,
            recipientName = recipientName,
            recipientLabel = recipientLabel,
            shopAddress = shopAddress,
            shopPhone = shopPhone,
            documentBadge = documentBadge,
            statusRemainingPiasters = statusRemainingPiasters,
            footerNote = footerNote
        )
        canvas.restore()

        val outFile = File(exportsDir(context), "statement_${System.currentTimeMillis()}.png")
        FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.PNG, 96, it) }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        return outFile to uri
    }

    private fun drawInvoiceOnCanvas(
        context: Context,
        canvas: Canvas,
        width: Float,
        height: Float,
        model: InvoicePrintModel
    ) {
        val cairo = loadCairoTypeface(context)
        val royalDark = Color.parseColor("#0D47A1")
        val royalBlue = Color.parseColor("#1877F2")
        val goldAccent = Color.parseColor("#FBBF24")
        val textDark = Color.parseColor("#0F172A")
        val textMuted = Color.parseColor("#64748B")
        val borderLight = Color.parseColor("#D8E2F0")
        val tableHeaderBg = Color.parseColor("#E8F1FE")
        val greenColor = Color.parseColor("#15803D")
        val greenBg = Color.parseColor("#DCFCE7")
        val redColor = Color.parseColor("#DC2626")
        val redBg = Color.parseColor("#FEE2E2")
        val blueSoftBg = Color.parseColor("#EFF6FF")

        // Subtle outer invoice paper background & decorative frame
        canvas.drawColor(Color.WHITE)
        val margin = 24f
        val contentWidth = width - margin * 2

        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
            color = borderLight
        }
        canvas.drawRoundRect(12f, 12f, width - 12f, height - 12f, 14f, 14f, framePaint)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.1f
            color = borderLight
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(cairo, Typeface.BOLD)
            textSize = 20f
            color = Color.WHITE
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = cairo
            textSize = 11f
            color = Color.parseColor("#DBEAFE")
            textAlign = Paint.Align.RIGHT
        }
        val boldRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(cairo, Typeface.BOLD)
            textSize = 12f
            color = textDark
            textAlign = Paint.Align.RIGHT
        }
        val regularRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = cairo
            textSize = 11.2f
            color = textDark
            textAlign = Paint.Align.RIGHT
        }
        val mutedRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = cairo
            textSize = 10.5f
            color = textMuted
            textAlign = Paint.Align.RIGHT
        }
        val centerBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(cairo, Typeface.BOLD)
            textSize = 11.5f
            color = royalDark
            textAlign = Paint.Align.CENTER
        }
        val centerRegular = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = cairo
            textSize = 11f
            color = textDark
            textAlign = Paint.Align.CENTER
        }

        // Royal-blue gradient top header card
        val headerTop = margin
        val headerBottom = margin + 96f
        val headerGradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                margin, headerTop, width - margin, headerBottom,
                royalBlue, royalDark, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(margin, headerTop, width - margin, headerBottom, 12f, 12f, headerGradient)

        // Golden-amber bottom accent ribbon
        fillPaint.color = goldAccent
        canvas.drawRoundRect(margin + 12f, headerBottom - 5f, width - margin - 12f, headerBottom, 3f, 3f, fillPaint)

        // Shop Info (Right side)
        canvas.drawText(model.shopName.take(28), width - margin - 16f, headerTop + 34f, titlePaint)
        if (model.shopAddress.isNotBlank()) {
            canvas.drawText(model.shopAddress.take(40), width - margin - 16f, headerTop + 56f, subtitlePaint)
        }
        if (model.shopPhone.isNotBlank()) {
            canvas.drawText("هاتف: ${model.shopPhone}", width - margin - 16f, headerTop + 76f, subtitlePaint)
        }

        // Document Type Pill & Invoice Number (Left side)
        fillPaint.color = Color.argb(44, 255, 255, 255)
        canvas.drawRoundRect(margin + 14f, headerTop + 14f, margin + 186f, headerTop + 44f, 15f, 15f, fillPaint)
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(cairo, Typeface.BOLD)
            textSize = 12.5f
            color = goldAccent
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(model.invoiceTypeLabel.take(20), margin + 100f, headerTop + 34f, badgeTextPaint)

        val leftSubPaint = Paint(subtitlePaint).apply {
            textAlign = Paint.Align.LEFT
            color = Color.WHITE
            textSize = 11.5f
            typeface = Typeface.create(cairo, Typeface.BOLD)
        }
        canvas.drawText("رقم: ${model.invoiceNumber}", margin + 16f, headerTop + 66f, leftSubPaint)
        canvas.drawText(model.dateFormatted, margin + 16f, headerTop + 84f, Paint(subtitlePaint).apply {
            textAlign = Paint.Align.LEFT
            textSize = 10f
        })

        // Customer & Status Card
        var y = headerBottom + 14f
        fillPaint.color = Color.parseColor("#F8FAFC")
        canvas.drawRoundRect(margin, y, width - margin, y + 66f, 10f, 10f, fillPaint)
        canvas.drawRoundRect(margin, y, width - margin, y + 66f, 10f, 10f, strokePaint)

        canvas.drawText("السيد / ${model.customerName.take(30)}", width - margin - 14f, y + 26f, boldRight)
        canvas.drawText("طريقة الدفع: ${model.paymentMethodLabel}   •   العملة: جنيه مصري", width - margin - 14f, y + 49f, mutedRight)

        // Official Paid / Remaining Status Stamp Pill on Left
        val isPaidFull = model.remainingPiasters <= 0L
        val pillBg = if (isPaidFull) greenBg else redBg
        val pillFg = if (isPaidFull) greenColor else redColor
        val pillLabel = if (isPaidFull) "✓ خالصة بالكامل" else "متبقي ${MoneyFormatter.formatEgp(model.remainingPiasters)}"

        fillPaint.color = pillBg
        canvas.drawRoundRect(margin + 12f, y + 17f, margin + 168f, y + 49f, 16f, 16f, fillPaint)
        val pillStroke = Paint(strokePaint).apply { color = pillFg; strokeWidth = 1f }
        canvas.drawRoundRect(margin + 12f, y + 17f, margin + 168f, y + 49f, 16f, 16f, pillStroke)
        canvas.drawText(
            pillLabel.take(22),
            margin + 90f,
            y + 37f,
            Paint(centerBold).apply { color = pillFg; textSize = 10.5f }
        )

        // Paper-style RTL Table Columns (Right to Left):
        // [البيان (wide)] | [السعر] | [العدد] | [جنيه] | [قرش]
        y += 80f
        val colPiasterW = 50f
        val colPoundW = 84f
        val colQtyW = 62f
        val colPriceW = 86f
        val colDescW = contentWidth - (colPiasterW + colPoundW + colQtyW + colPriceW)

        val x0 = margin
        val x1 = x0 + colPiasterW // قرش
        val x2 = x1 + colPoundW   // جنيه
        val x3 = x2 + colQtyW     // العدد
        val x4 = x3 + colPriceW   // السعر
        val x5 = x4 + colDescW    // البيان

        val headerHeight = 34f
        fillPaint.color = tableHeaderBg
        canvas.drawRect(x0, y, x5, y + headerHeight, fillPaint)
        canvas.drawRect(x0, y, x5, y + headerHeight, strokePaint)

        canvas.drawText("قرش", (x0 + x1) / 2f, y + 22f, centerBold)
        canvas.drawText("جنيه", (x1 + x2) / 2f, y + 22f, centerBold)
        canvas.drawText("العدد", (x2 + x3) / 2f, y + 22f, centerBold)
        canvas.drawText("السعر", (x3 + x4) / 2f, y + 22f, centerBold)
        canvas.drawText("البيان", (x4 + x5) / 2f, y + 22f, centerBold)

        listOf(x1, x2, x3, x4).forEach { cx ->
            canvas.drawLine(cx, y, cx, y + headerHeight, strokePaint)
        }
        y += headerHeight

        val rowHeight = 32f
        model.lines.forEachIndexed { idx, row ->
            if (idx % 2 == 1) {
                fillPaint.color = Color.parseColor("#F8FBFF")
                canvas.drawRect(x0, y, x5, y + rowHeight, fillPaint)
            }
            canvas.drawRect(x0, y, x5, y + rowHeight, strokePaint)
            listOf(x1, x2, x3, x4).forEach { cx ->
                canvas.drawLine(cx, y, cx, y + rowHeight, strokePaint)
            }

            val (pounds, piasters) = MoneyFormatter.splitPoundsAndPiasters(row.lineTotalPiasters)
            canvas.drawText(String.format("%02d", piasters), (x0 + x1) / 2f, y + 20f, centerRegular)
            canvas.drawText(
                MoneyFormatter.formatEgp(pounds * 100L, ""),
                (x1 + x2) / 2f,
                y + 20f,
                Paint(centerRegular).apply { typeface = Typeface.create(cairo, Typeface.BOLD) }
            )
            canvas.drawText(MoneyFormatter.formatQuantity(row.quantityMilli), (x2 + x3) / 2f, y + 20f, centerRegular)
            canvas.drawText(MoneyFormatter.formatEgp(row.unitPricePiasters, ""), (x3 + x4) / 2f, y + 20f, centerRegular)

            val descText = buildString {
                if (!row.timeLabel.isNullOrBlank()) append("[${row.timeLabel}] ")
                append(row.description.ifBlank { "بضاعة / مبيعات" })
            }.take(36)
            canvas.drawText(descText, x5 - 10f, y + 20f, regularRight)
            y += rowHeight
        }

        // Totals Summary 3-Card Strip (RTL: Right = الإجمالي, Center = المدفوع, Left = المتبقي)
        y += 16f
        val gap = 10f
        val boxW = (contentWidth - gap * 2) / 3f
        val boxH = 62f

        // Right box: الإجمالي
        drawMetricBox(
            canvas = canvas,
            left = width - margin - boxW,
            top = y,
            right = width - margin,
            bottom = y + boxH,
            bgColor = blueSoftBg,
            accentColor = royalBlue,
            label = "الإجمالي النهائي",
            value = MoneyFormatter.formatEgp(model.totalPiasters),
            cairo = cairo
        )
        // Middle box: المدفوع
        drawMetricBox(
            canvas = canvas,
            left = margin + boxW + gap,
            top = y,
            right = margin + boxW * 2 + gap,
            bottom = y + boxH,
            bgColor = greenBg,
            accentColor = greenColor,
            label = "المبلغ المدفوع",
            value = MoneyFormatter.formatEgp(model.paidPiasters),
            cairo = cairo
        )
        // Left box: المتبقي
        drawMetricBox(
            canvas = canvas,
            left = margin,
            top = y,
            right = margin + boxW,
            bottom = y + boxH,
            bgColor = if (model.remainingPiasters > 0L) redBg else greenBg,
            accentColor = if (model.remainingPiasters > 0L) redColor else greenColor,
            label = "المبلغ المتبقي",
            value = MoneyFormatter.formatEgp(model.remainingPiasters),
            cairo = cairo
        )

        y += boxH + 14f
        val (totPounds, totPiasters) = MoneyFormatter.splitPoundsAndPiasters(model.totalPiasters)
        fillPaint.color = Color.parseColor("#F8FAFC")
        canvas.drawRoundRect(margin, y, width - margin, y + 32f, 8f, 8f, fillPaint)
        canvas.drawRoundRect(margin, y, width - margin, y + 32f, 8f, 8f, strokePaint)
        canvas.drawText(
            "فقط: $totPounds جنيه مصري و $totPiasters قرشًا لا غير",
            width / 2f,
            y + 21f,
            Paint(centerBold).apply { color = textDark; textSize = 11f }
        )

        y += 48f
        if (model.notes.isNotBlank()) {
            canvas.drawText("ملاحظات: ${model.notes.take(70)}", width - margin, y, regularRight)
            y += 22f
        }
        if (model.signatureName.isNotBlank()) {
            canvas.drawText("توقيع المسؤول: ${model.signatureName}", width - margin, y, boldRight)
            y += 22f
        }

        drawBrandedFooter(
            canvas = canvas,
            width = width,
            bottomY = (height - 24f).coerceAtLeast(y + 34f),
            margin = margin,
            thankYouText = "شكرًا لثقتكم الغالية — نسعد دائمًا بخدمتكم في ${model.shopName}",
            cairo = cairo
        )
    }

    private fun drawReportInvoiceOnCanvas(
        context: Context,
        canvas: Canvas,
        width: Float,
        height: Float,
        model: ReportPrintModel,
        displayRows: List<List<String>>,
        hiddenRowsCount: Int,
        recipientName: String?,
        recipientLabel: String,
        shopAddress: String,
        shopPhone: String,
        documentBadge: String,
        statusRemainingPiasters: Long?,
        footerNote: String
    ) {
        val cairo = loadCairoTypeface(context)
        val royalDark = Color.parseColor("#0D47A1")
        val royalBlue = Color.parseColor("#1877F2")
        val goldAccent = Color.parseColor("#FBBF24")
        val textDark = Color.parseColor("#0F172A")
        val textMuted = Color.parseColor("#64748B")
        val borderLight = Color.parseColor("#D8E2F0")
        val tableHeaderBg = Color.parseColor("#E8F1FE")
        val greenColor = Color.parseColor("#15803D")
        val greenBg = Color.parseColor("#DCFCE7")
        val redColor = Color.parseColor("#DC2626")
        val redBg = Color.parseColor("#FEE2E2")
        val blueSoftBg = Color.parseColor("#EFF6FF")

        canvas.drawColor(Color.WHITE)
        val margin = 24f
        val contentWidth = width - margin * 2

        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
            color = borderLight
        }
        canvas.drawRoundRect(12f, 12f, width - 12f, height - 12f, 14f, 14f, framePaint)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
            color = borderLight
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(cairo, Typeface.BOLD)
            textSize = 19.5f
            color = Color.WHITE
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = cairo
            textSize = 11f
            color = Color.parseColor("#DBEAFE")
            textAlign = Paint.Align.RIGHT
        }
        val boldRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(cairo, Typeface.BOLD)
            textSize = 12f
            color = textDark
            textAlign = Paint.Align.RIGHT
        }
        val mutedRight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = cairo
            textSize = 10.5f
            color = textMuted
            textAlign = Paint.Align.RIGHT
        }
        val centerBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(cairo, Typeface.BOLD)
            textSize = 10.8f
            color = royalDark
            textAlign = Paint.Align.CENTER
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = cairo
            textSize = 10f
            color = textDark
            textAlign = Paint.Align.CENTER
        }

        // Royal-Blue Gradient Header Band
        val headerTop = margin
        val headerBottom = margin + 96f
        val headerGradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                margin, headerTop, width - margin, headerBottom,
                royalBlue, royalDark, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(margin, headerTop, width - margin, headerBottom, 12f, 12f, headerGradient)
        fillPaint.color = goldAccent
        canvas.drawRoundRect(margin + 12f, headerBottom - 5f, width - margin - 12f, headerBottom, 3f, 3f, fillPaint)

        // Shop Info on Right
        canvas.drawText(model.shopName.take(28), width - margin - 16f, headerTop + 34f, titlePaint)
        if (shopAddress.isNotBlank()) {
            canvas.drawText(shopAddress.take(40), width - margin - 16f, headerTop + 56f, subtitlePaint)
        }
        if (shopPhone.isNotBlank()) {
            canvas.drawText("هاتف: $shopPhone", width - margin - 16f, headerTop + 76f, subtitlePaint)
        } else if (shopAddress.isBlank()) {
            canvas.drawText(model.title.take(36), width - margin - 16f, headerTop + 60f, subtitlePaint)
        }

        // Document Badge Pill + Date on Left
        fillPaint.color = Color.argb(44, 255, 255, 255)
        canvas.drawRoundRect(margin + 14f, headerTop + 14f, margin + 190f, headerTop + 44f, 15f, 15f, fillPaint)
        canvas.drawText(
            documentBadge.take(20),
            margin + 102f,
            headerTop + 34f,
            Paint(centerBold).apply { color = goldAccent; textSize = 12f }
        )
        canvas.drawText(
            "التاريخ / الفترة:",
            margin + 16f,
            headerTop + 65f,
            Paint(subtitlePaint).apply { textAlign = Paint.Align.LEFT; textSize = 10f }
        )
        canvas.drawText(
            model.periodLabel.take(28),
            margin + 16f,
            headerTop + 82f,
            Paint(subtitlePaint).apply {
                textAlign = Paint.Align.LEFT
                color = Color.WHITE
                typeface = Typeface.create(cairo, Typeface.BOLD)
                textSize = 10.8f
            }
        )

        var y = headerBottom + 14f

        // Recipient / Document Title Card
        if (!recipientName.isNullOrBlank()) {
            fillPaint.color = Color.parseColor("#F8FAFC")
            canvas.drawRoundRect(margin, y, width - margin, y + 62f, 10f, 10f, fillPaint)
            canvas.drawRoundRect(margin, y, width - margin, y + 62f, 10f, 10f, strokePaint)

            canvas.drawText("$recipientLabel: ${recipientName.take(30)}", width - margin - 14f, y + 26f, boldRight)
            canvas.drawText("${model.title.take(44)}  •  ${model.periodLabel}", width - margin - 14f, y + 48f, mutedRight)

            if (statusRemainingPiasters != null) {
                val isClear = statusRemainingPiasters <= 0L
                val pillBg = if (isClear) greenBg else redBg
                val pillFg = if (isClear) greenColor else redColor
                val pillText = if (isClear) "✓ الحساب خالص" else "المتبقي ${MoneyFormatter.formatEgp(statusRemainingPiasters)}"
                fillPaint.color = pillBg
                canvas.drawRoundRect(margin + 12f, y + 15f, margin + 170f, y + 47f, 16f, 16f, fillPaint)
                canvas.drawRoundRect(
                    margin + 12f, y + 15f, margin + 170f, y + 47f, 16f, 16f,
                    Paint(strokePaint).apply { color = pillFg }
                )
                canvas.drawText(pillText.take(22), margin + 91f, y + 35f, Paint(centerBold).apply { color = pillFg; textSize = 10.5f })
            }
            y += 74f
        } else {
            fillPaint.color = Color.parseColor("#F8FAFC")
            canvas.drawRoundRect(margin, y, width - margin, y + 42f, 10f, 10f, fillPaint)
            canvas.drawRoundRect(margin, y, width - margin, y + 42f, 10f, 10f, strokePaint)
            canvas.drawText(model.title.take(48), width - margin - 14f, y + 26f, boldRight)
            y += 54f
        }

        // Summary KPI Metric Cards (up to 3 per row, RTL order)
        if (model.summaryPairs.isNotEmpty()) {
            val pairRows = model.summaryPairs.chunked(3)
            val gap = 10f
            pairRows.forEach { chunk ->
                val count = chunk.size
                val boxW = (contentWidth - gap * (count - 1)) / count
                val boxH = 54f
                chunk.forEachIndexed { idx, (label, value) ->
                    // RTL placement: idx 0 on right
                    val right = width - margin - idx * (boxW + gap)
                    val left = right - boxW
                    val (bgCol, accentCol) = when {
                        label.contains("متبقي") || label.contains("مستحق") || label.contains("رصيد") -> {
                            if (value.startsWith("0")) greenBg to greenColor else redBg to redColor
                        }
                        label.contains("مدفوع") || label.contains("تحصيل") || label.contains("محصل") || label.contains("كاش") -> {
                            greenBg to greenColor
                        }
                        else -> blueSoftBg to royalBlue
                    }
                    drawMetricBox(
                        canvas = canvas,
                        left = left,
                        top = y,
                        right = right,
                        bottom = y + boxH,
                        bgColor = bgCol,
                        accentColor = accentCol,
                        label = label.take(20),
                        value = value.take(22),
                        cairo = cairo
                    )
                }
                y += boxH + 10f
            }
            y += 6f
        }

        // RTL Table of Ledger / Report Rows
        if (model.columns.isNotEmpty()) {
            val colCount = model.columns.size
            val colW = contentWidth / colCount
            val headerH = 32f
            fillPaint.color = tableHeaderBg
            canvas.drawRect(margin, y, width - margin, y + headerH, fillPaint)
            canvas.drawRect(margin, y, width - margin, y + headerH, strokePaint)

            for (i in 1 until colCount) {
                val vx = margin + colW * i
                canvas.drawLine(vx, y, vx, y + headerH, strokePaint)
            }
            model.columns.forEachIndexed { idx, colTitle ->
                val cx = width - margin - colW * idx - colW / 2f
                canvas.drawText(colTitle.take(16), cx, y + 20f, centerBold)
            }
            y += headerH

            if (displayRows.isEmpty()) {
                fillPaint.color = Color.parseColor("#FAFCFF")
                canvas.drawRect(margin, y, width - margin, y + 40f, fillPaint)
                canvas.drawRect(margin, y, width - margin, y + 40f, strokePaint)
                canvas.drawText(
                    "لا توجد حركات مسجلة في هذا الكشف",
                    width / 2f,
                    y + 24f,
                    Paint(cellPaint).apply { color = textMuted }
                )
                y += 40f
            } else {
                val rowH = 29f
                displayRows.forEachIndexed { rIdx, row ->
                    if (rIdx % 2 == 1) {
                        fillPaint.color = Color.parseColor("#F8FBFF")
                        canvas.drawRect(margin, y, width - margin, y + rowH, fillPaint)
                    }
                    canvas.drawRect(margin, y, width - margin, y + rowH, strokePaint)
                    for (i in 1 until colCount) {
                        val vx = margin + colW * i
                        canvas.drawLine(vx, y, vx, y + rowH, strokePaint)
                    }
                    row.forEachIndexed { cIdx, cellVal ->
                        if (cIdx < colCount) {
                            val cx = width - margin - colW * cIdx - colW / 2f
                            canvas.drawText(cellVal.take(20), cx, y + 19f, cellPaint)
                        }
                    }
                    y += rowH
                }
            }

            if (hiddenRowsCount > 0) {
                y += 6f
                canvas.drawText(
                    "+ $hiddenRowsCount حركات أقدم متاحة بالكامل في نسخة PDF",
                    width / 2f,
                    y + 14f,
                    Paint(cellPaint).apply { color = textMuted; textSize = 9.5f }
                )
                y += 22f
            }
        }

        drawBrandedFooter(
            canvas = canvas,
            width = width,
            bottomY = (height - 22f).coerceAtLeast(y + 40f),
            margin = margin,
            thankYouText = footerNote,
            cairo = cairo
        )
    }

    private fun drawMetricBox(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        bgColor: Int,
        accentColor: Int,
        label: String,
        value: String,
        cairo: Typeface
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = accentColor
            alpha = 70
        }
        canvas.drawRoundRect(left, top, right, bottom, 10f, 10f, fill)
        canvas.drawRoundRect(left, top, right, bottom, 10f, 10f, border)

        // Crisp top accent bar
        fill.color = accentColor
        canvas.drawRoundRect(left + 14f, top, right - 14f, top + 3.5f, 2f, 2f, fill)

        val cx = (left + right) / 2f
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = cairo
            textSize = 10f
            color = Color.parseColor("#475569")
            textAlign = Paint.Align.CENTER
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(cairo, Typeface.BOLD)
            textSize = 13f
            color = accentColor
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, cx, top + 22f, labelPaint)
        canvas.drawText(value, cx, top + 43f, valuePaint)
    }

    private fun drawBrandedFooter(
        canvas: Canvas,
        width: Float,
        bottomY: Float,
        margin: Float,
        thankYouText: String,
        cairo: Typeface
    ) {
        val goldLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FBBF24")
            strokeWidth = 1.6f
        }
        canvas.drawLine(margin + 30f, bottomY - 34f, width - margin - 30f, bottomY - 34f, goldLine)

        val thanksPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(cairo, Typeface.BOLD)
            textSize = 10.5f
            color = Color.parseColor("#0D47A1")
            textAlign = Paint.Align.CENTER
        }
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = cairo
            textSize = 9f
            color = Color.parseColor("#64748B")
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(thankYouText.take(64), width / 2f, bottomY - 15f, thanksPaint)
        canvas.drawText(
            "تم الإصدار بواسطة نظام حساباتي | HESABATY — تأسيس لصناعة البرمجيات",
            width / 2f,
            bottomY,
            brandPaint
        )
    }

    fun generateReportPdf(context: Context, model: ReportPrintModel): Pair<File, Uri> {
        val cairo = loadCairoTypeface(context)
        val pageWidth = 595
        val pageHeight = 842
        val outFile = File(exportsDir(context), "report_${System.currentTimeMillis()}.pdf")

        var document: PdfDocument? = null
        try {
            document = PdfDocument()
            val rowsPerPage = 20
            val chunks = if (model.rows.isEmpty()) listOf(emptyList()) else model.rows.chunked(rowsPerPage)

            chunks.forEachIndexed { pageIndex, pageRows ->
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                val margin = 26f
                val contentW = pageWidth - margin * 2
                val primaryBlue = Color.parseColor("#1877F2")
                val textDark = Color.parseColor("#172B4D")
                val borderLight = Color.parseColor("#DCE3EE")

                val fill = Paint(Paint.ANTI_ALIAS_FLAG)
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                    color = borderLight
                }
                val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(cairo, Typeface.BOLD)
                    textSize = 17f
                    color = Color.WHITE
                    textAlign = Paint.Align.RIGHT
                }
                val subP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = cairo
                    textSize = 11f
                    color = Color.parseColor("#E7F0FE")
                    textAlign = Paint.Align.RIGHT
                }
                val boldR = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(cairo, Typeface.BOLD)
                    textSize = 11f
                    color = textDark
                    textAlign = Paint.Align.RIGHT
                }
                val cellP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = cairo
                    textSize = 10f
                    color = textDark
                    textAlign = Paint.Align.CENTER
                }

                fill.color = primaryBlue
                canvas.drawRoundRect(margin, margin, pageWidth - margin, margin + 72f, 8f, 8f, fill)
                canvas.drawText(model.title, pageWidth - margin - 14f, margin + 30f, titleP)
                canvas.drawText("${model.shopName}  |  ${model.periodLabel}", pageWidth - margin - 14f, margin + 54f, subP)

                var y = margin + 88f
                if (pageIndex == 0 && model.summaryPairs.isNotEmpty()) {
                    fill.color = Color.parseColor("#F5F7FB")
                    val sumH = 24f * model.summaryPairs.size + 14f
                    canvas.drawRoundRect(margin, y, pageWidth - margin, y + sumH, 8f, 8f, fill)
                    canvas.drawRoundRect(margin, y, pageWidth - margin, y + sumH, 8f, 8f, stroke)
                    var sy = y + 22f
                    model.summaryPairs.forEach { (k, v) ->
                        canvas.drawText("$k: $v", pageWidth - margin - 14f, sy, boldR)
                        sy += 22f
                    }
                    y += sumH + 14f
                }

                if (model.columns.isNotEmpty()) {
                    val colCount = model.columns.size
                    val colW = contentW / colCount
                    fill.color = Color.parseColor("#E7F0FE")
                    canvas.drawRect(margin, y, pageWidth - margin, y + 28f, fill)
                    canvas.drawRect(margin, y, pageWidth - margin, y + 28f, stroke)
                    model.columns.forEachIndexed { idx, colTitle ->
                        val cx = pageWidth - margin - colW * idx - colW / 2f
                        canvas.drawText(colTitle.take(18), cx, y + 18f, Paint(cellP).apply {
                            typeface = Typeface.create(cairo, Typeface.BOLD)
                        })
                    }
                    y += 28f

                    pageRows.forEachIndexed { rIdx, row ->
                        if (rIdx % 2 == 1) {
                            fill.color = Color.parseColor("#FAFCFF")
                            canvas.drawRect(margin, y, pageWidth - margin, y + 26f, fill)
                        }
                        canvas.drawRect(margin, y, pageWidth - margin, y + 26f, stroke)
                        row.forEachIndexed { cIdx, cellVal ->
                            if (cIdx < colCount) {
                                val cx = pageWidth - margin - colW * cIdx - colW / 2f
                                canvas.drawText(cellVal.take(22), cx, y + 17f, cellP)
                            }
                        }
                        y += 26f
                    }
                }

                val footP = Paint(cellP).apply { color = Color.GRAY; textSize = 9.5f }
                canvas.drawText(
                    "صفحة ${pageIndex + 1} من ${chunks.size} — حساباتي | HESABATY",
                    pageWidth / 2f,
                    pageHeight - 20f,
                    footP
                )
                document.finishPage(page)
            }

            FileOutputStream(outFile).use { document.writeTo(it) }
        } catch (_: IllegalStateException) {
            writeMinimalPdfFallback(
                outFile = outFile,
                title = "Hesabaty Report",
                summaryLine = model.periodLabel
            )
        } finally {
            runCatching { document?.close() }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        return outFile to uri
    }
}
