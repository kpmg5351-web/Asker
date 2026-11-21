package com.example.core

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/**
 * Prepares warm, refined, commercial-grade Arabic WhatsApp messages and shares
 * invoice-styled PNG images + captions via WhatsApp / Android Share Sheet.
 * Never auto-sends messages without explicit user confirmation inside WhatsApp.
 */
object WhatsAppHelper {

    data class LaunchResult(
        val launched: Boolean,
        val errorMessage: String? = null
    )

    fun buildCustomerInvoiceMessage(
        customerName: String,
        shopName: String,
        invoiceNumber: String,
        dateFormatted: String,
        totalPiasters: Long,
        paidPiasters: Long,
        remainingPiasters: Long,
        invoiceTypeLabel: String = "الفاتورة",
        lines: List<PdfInvoiceGenerator.InvoiceLineRow> = emptyList(),
        dueDateFormatted: String? = null,
        notes: String = ""
    ): String {
        val total = MoneyFormatter.formatEgp(totalPiasters, currencyLabel = "")
        val paid = MoneyFormatter.formatEgp(paidPiasters, currencyLabel = "")
        val remaining = MoneyFormatter.formatEgp(remainingPiasters, currencyLabel = "")
        val totalQtyMilli = lines.sumOf { it.quantityMilli }
        return buildString {
            appendLine("السلام عليكم ورحمة الله وبركاته 🌿")
            appendLine("أستاذنا الفاضل / $customerName 🌹")
            appendLine()
            appendLine("يسعدنا إرسال صورة $invoiceTypeLabel التفصيلية من *$shopName* 🧾✨")
            appendLine()
            appendLine("▫️ رقم الفاتورة: *$invoiceNumber*")
            appendLine("▫️ التاريخ: $dateFormatted")
            if (!dueDateFormatted.isNullOrBlank()) {
                appendLine("▫️ تاريخ الاستحقاق: $dueDateFormatted")
            }
            if (lines.isNotEmpty()) {
                appendLine("📦 عدد البنود: *${lines.size}*  •  إجمالي العدد/الكمية: *${MoneyFormatter.formatQuantity(totalQtyMilli)}*")
                appendLine("📋 تفاصيل الأصناف:")
                lines.forEachIndexed { idx, line ->
                    val qty = MoneyFormatter.formatQuantity(line.quantityMilli)
                    val unitPrice = MoneyFormatter.formatEgp(line.unitPricePiasters, currencyLabel = "")
                    val lineSum = MoneyFormatter.formatEgp(line.lineTotalPiasters, currencyLabel = "")
                    val timePrefix = if (!line.timeLabel.isNullOrBlank()) "[${line.timeLabel}] " else ""
                    appendLine("  ${idx + 1}. $timePrefix${line.description.ifBlank { "بند" }} — الكمية: *$qty* × $unitPrice ج.م = *$lineSum ج.م*")
                }
            }
            appendLine("━━━━━━━━━━━━━━━")
            appendLine("💰 إجمالي الفاتورة: *$total جنيه*")
            appendLine("✅ المبلغ المدفوع: *$paid جنيه*")
            if (remainingPiasters <= 0L) {
                appendLine("🎉 حالة الفاتورة: *خالصة بالكامل — تسلم إيدك* ✅")
            } else {
                appendLine("📌 المبلغ المتبقي: *$remaining جنيه*")
            }
            appendLine("━━━━━━━━━━━━━━━")
            if (notes.isNotBlank()) {
                appendLine("📝 ملاحظات: $notes")
            }
            appendLine()
            appendLine("📎 مرفق لحضرتك صورة الفاتورة للمراجعة والاحتفاظ بها.")
            appendLine("نتشرف دائمًا بخدمتك، وشكرًا لثقتك الغالية فينا ❤️✨")
            appendLine()
            append("مع تحيات: *$shopName*")
        }
    }

    fun buildPurchaseInvoiceMessage(
        supplierName: String,
        shopName: String,
        invoiceNumber: String,
        dateFormatted: String,
        totalPiasters: Long,
        paidPiasters: Long,
        remainingPiasters: Long,
        purchaseTypeLabel: String = "فاتورة شراء",
        lines: List<PdfInvoiceGenerator.InvoiceLineRow> = emptyList(),
        dueDateFormatted: String? = null,
        notes: String = ""
    ): String {
        val total = MoneyFormatter.formatEgp(totalPiasters, currencyLabel = "")
        val paid = MoneyFormatter.formatEgp(paidPiasters, currencyLabel = "")
        val remaining = MoneyFormatter.formatEgp(remainingPiasters, currencyLabel = "")
        val totalQtyMilli = lines.sumOf { it.quantityMilli }
        return buildString {
            appendLine("السلام عليكم ورحمة الله وبركاته 🤝")
            appendLine("السادة / $supplierName الكرام 🌿")
            appendLine()
            appendLine("نُرفق لحضرتكم صورة *$purchaseTypeLabel* الصادرة من *$shopName* 🧾📦")
            appendLine()
            appendLine("▫️ رقم الفاتورة: *$invoiceNumber*")
            appendLine("▫️ التاريخ: $dateFormatted")
            if (!dueDateFormatted.isNullOrBlank()) {
                appendLine("▫️ تاريخ الاستحقاق: $dueDateFormatted")
            }
            if (lines.isNotEmpty()) {
                appendLine("📦 عدد البنود: *${lines.size}*  •  إجمالي العدد/الكمية: *${MoneyFormatter.formatQuantity(totalQtyMilli)}*")
                appendLine("📋 تفاصيل المشتريات:")
                lines.forEachIndexed { idx, line ->
                    val qty = MoneyFormatter.formatQuantity(line.quantityMilli)
                    val unitPrice = MoneyFormatter.formatEgp(line.unitPricePiasters, currencyLabel = "")
                    val lineSum = MoneyFormatter.formatEgp(line.lineTotalPiasters, currencyLabel = "")
                    appendLine("  ${idx + 1}. ${line.description.ifBlank { "بند" }} — الكمية: *$qty* × $unitPrice ج.م = *$lineSum ج.م*")
                }
            }
            appendLine("━━━━━━━━━━━━━━━")
            appendLine("💰 إجمالي الفاتورة: *$total جنيه*")
            appendLine("✅ المبلغ المدفوع: *$paid جنيه*")
            if (remainingPiasters <= 0L) {
                appendLine("🎉 الحالة: *مدفوعة بالكامل* ✅")
            } else {
                appendLine("📌 المبلغ المتبقي للمورد: *$remaining جنيه*")
            }
            appendLine("━━━━━━━━━━━━━━━")
            if (notes.isNotBlank()) {
                appendLine("📝 ملاحظات: $notes")
            }
            appendLine()
            append("مع خالص التقدير والاحترام — *$shopName*")
        }
    }

    fun buildCustomerStatementMessage(
        customerName: String,
        shopName: String,
        totalSalesPiasters: Long,
        totalPaidPiasters: Long,
        remainingPiasters: Long,
        dateFormatted: String
    ): String {
        val sales = MoneyFormatter.formatEgp(totalSalesPiasters, currencyLabel = "")
        val paid = MoneyFormatter.formatEgp(totalPaidPiasters, currencyLabel = "")
        val remaining = MoneyFormatter.formatEgp(remainingPiasters, currencyLabel = "")
        return buildString {
            appendLine("السلام عليكم ورحمة الله وبركاته 🌿")
            appendLine("أستاذنا الفاضل / $customerName 🌹")
            appendLine()
            appendLine("نُرفق لحضرتك صورة كشف حسابك التفصيلي لدى *$shopName* على شكل فاتورة واضحة للمراجعة 📄✨")
            appendLine()
            appendLine("🗓️ تاريخ الكشف: $dateFormatted")
            appendLine("━━━━━━━━━━━━━━━")
            appendLine("🧾 إجمالي التعاملات: *$sales جنيه*")
            appendLine("✅ إجمالي المدفوعات: *$paid جنيه*")
            if (remainingPiasters <= 0L) {
                appendLine("🎉 حالة الحساب: *خالص بالكامل بفضل الله* ✅")
            } else {
                appendLine("📌 الرصيد المتبقي: *$remaining جنيه*")
            }
            appendLine("━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("في خدمتك في أي وقت لأي استفسار، وشكرًا جزيلًا لثقتك وتعاملك الراقي معنا ❤️🌹")
            appendLine()
            append("مع تحيات: *$shopName*")
        }
    }

    fun buildSupplierStatementMessage(
        supplierName: String,
        shopName: String,
        totalDuePiasters: Long,
        totalPaidPiasters: Long,
        currentBalancePiasters: Long,
        dateFormatted: String
    ): String {
        val due = MoneyFormatter.formatEgp(totalDuePiasters, currencyLabel = "")
        val paid = MoneyFormatter.formatEgp(totalPaidPiasters, currencyLabel = "")
        val balance = MoneyFormatter.formatEgp(currentBalancePiasters, currencyLabel = "")
        return buildString {
            appendLine("السلام عليكم ورحمة الله وبركاته 🤝")
            appendLine("السادة / $supplierName الكرام 🌿")
            appendLine()
            appendLine("نُرفق لحضرتكم صورة كشف الحساب التفصيلي لدى *$shopName* على شكل فاتورة للمراجعة والمطابقة 📄✨")
            appendLine()
            appendLine("🗓️ تاريخ الكشف: $dateFormatted")
            appendLine("━━━━━━━━━━━━━━━")
            appendLine("📦 إجمالي المستحقات: *$due جنيه*")
            appendLine("✅ إجمالي المدفوعات: *$paid جنيه*")
            if (currentBalancePiasters <= 0L) {
                appendLine("🎉 الرصيد الحالي: *0.00 جنيه — الحساب خالص بالكامل* ✅")
            } else {
                appendLine("📌 الرصيد الحالي: *$balance جنيه*")
            }
            appendLine("━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("مع خالص التقدير والاحترام ودوام التعاون المثمر بيننا 🌹")
            appendLine()
            append("مع تحيات: *$shopName*")
        }
    }

    fun buildPaymentReceiptMessage(
        customerName: String,
        shopName: String,
        amountPiasters: Long,
        remainingAfterPiasters: Long,
        paymentMethodLabel: String,
        dateFormatted: String
    ): String {
        val amount = MoneyFormatter.formatEgp(amountPiasters, currencyLabel = "")
        val remaining = MoneyFormatter.formatEgp(remainingAfterPiasters, currencyLabel = "")
        return buildString {
            appendLine("السلام عليكم ورحمة الله وبركاته 🌿")
            appendLine("أستاذنا الفاضل / $customerName 🌹")
            appendLine()
            appendLine("تم بحمد الله استلام دفعتكم الكريمة لدى *$shopName* بنجاح ✅✨")
            appendLine()
            appendLine("🗓️ التاريخ: $dateFormatted")
            appendLine("💳 طريقة الدفع: $paymentMethodLabel")
            appendLine("━━━━━━━━━━━━━━━")
            appendLine("💵 المبلغ المستلم: *$amount جنيه*")
            if (remainingAfterPiasters <= 0L) {
                appendLine("🎉 حالة الحساب: *خالص بالكامل — ألف شكر* ✅")
            } else {
                appendLine("📌 الرصيد المتبقي بعد الدفعة: *$remaining جنيه*")
            }
            appendLine("━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("📎 مرفق لحضرتك صورة إيصال الاستلام المعتمد.")
            appendLine("تسلم إيدك، ونتشرف دائمًا بخدمتك ❤️🌹")
            appendLine()
            append("مع تحيات: *$shopName*")
        }
    }

    fun buildReportShareMessage(
        title: String,
        shopName: String,
        periodLabel: String,
        summaryPairs: List<Pair<String, String>>
    ): String {
        return buildString {
            appendLine("السلام عليكم ورحمة الله وبركاته 🌿")
            appendLine()
            appendLine("مرفق لحضرتك صورة *$title* من *$shopName* على شكل فاتورة ملخصة 📊🧾")
            appendLine("🗓️ الفترة: $periodLabel")
            if (summaryPairs.isNotEmpty()) {
                appendLine("━━━━━━━━━━━━━━━")
                summaryPairs.forEach { (k, v) ->
                    appendLine("▫️ $k: *$v*")
                }
                appendLine("━━━━━━━━━━━━━━━")
            }
            appendLine()
            append("تم الإصدار من نظام *حساباتي | HESABATY* ✨")
        }
    }

    fun buildSupportMessage(appVersion: String, shopName: String?): String {
        return buildString {
            appendLine("مرحبًا فريق الدعم الفني — تأسيس لصناعة البرمجيات")
            appendLine("التطبيق: حساباتي | HESABATY")
            appendLine("الإصدار: $appVersion")
            if (!shopName.isNullOrBlank()) {
                appendLine("المحل: $shopName")
            }
            appendLine("وصف المشكلة أو الاستفسار:")
        }
    }

    fun buildDeviceCodeRequestMessage(deviceLabel: String, deviceCode: String): String {
        return buildString {
            appendLine("السلام عليكم فريق تأسيس لصناعة البرمجيات 🌿")
            appendLine("أرغب في تفعيل حساب برنامج *حساباتي | HESABATY* على هاتفي:")
            appendLine()
            appendLine("📱 نوع الجهاز: $deviceLabel")
            appendLine("🔐 كود الجهاز: *$deviceCode*")
            appendLine()
            append("برجاء إرسال كود التفعيل المربوط بجهازي، شكرًا لكم 🌹")
        }
    }

    fun buildActivationTokenDeliveryMessage(
        shopName: String,
        ownerName: String,
        username: String,
        deviceCode: String,
        activationToken: String
    ): String {
        return buildString {
            appendLine("السلام عليكم أستاذنا الفاضل / $ownerName 🌹")
            appendLine("تم تجهيز وتفعيل حساب *$shopName* على نظام *حساباتي | HESABATY* بنجاح ✅✨")
            appendLine()
            appendLine("🔐 هذا الترخيص مربوط حصريًا بهاتفك (كود: $deviceCode) ولا يعمل على أي جهاز آخر.")
            appendLine("👤 اسم المستخدم: *$username*")
            appendLine()
            appendLine("📌 خطوات التفعيل:")
            appendLine("1️⃣ افتح تطبيق حساباتي واضغط (إنشاء / تفعيل حساب على هذا الجهاز).")
            appendLine("2️⃣ اختر تبويب (تفعيل بكود من الإدارة).")
            appendLine("3️⃣ الصق كود التفعيل التالي واضغط تفعيل:")
            appendLine()
            appendLine(activationToken)
            appendLine()
            append("نتمنى لك دوام التوفيق والبركة — تأسيس لصناعة البرمجيات 💙")
        }
    }

    fun openWhatsAppChat(
        context: Context,
        rawPhone: String,
        message: String,
        countryCode: String = "20"
    ): LaunchResult {
        val intlPhone = PhoneUtils.toWhatsAppInternational(rawPhone, countryCode)
            ?: return LaunchResult(false, "رقم هاتف WhatsApp غير مسجل أو غير صالح")

        val encoded = URLEncoder.encode(message, "UTF-8")
        val uri = Uri.parse("https://wa.me/$intlPhone?text=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            LaunchResult(true)
        } catch (_: Exception) {
            LaunchResult(false, "تعذر فتح تطبيق WhatsApp أو المتصفح على هذا الجهاز")
        }
    }

    fun shareFileViaWhatsAppOrChooser(
        context: Context,
        fileUri: Uri,
        mimeType: String,
        caption: String,
        rawPhone: String? = null,
        countryCode: String = "20"
    ): LaunchResult {
        val clip = ClipData.newRawUri("hesabaty_invoice", fileUri)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_TEXT, caption)
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // If WhatsApp or WhatsApp Business is installed, target it directly with jid hint
        val pm = context.packageManager
        val waPackage = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }

        if (waPackage != null) {
            val intl = rawPhone?.let { PhoneUtils.toWhatsAppInternational(it, countryCode) }
            val waIntent = Intent(sendIntent).apply {
                setPackage(waPackage)
                clipData = clip
                if (intl != null) {
                    putExtra("jid", "$intl@s.whatsapp.net")
                }
            }
            try {
                context.startActivity(waIntent)
                return LaunchResult(true)
            } catch (_: Exception) {
                // Fall back to system chooser
            }
        }

        return try {
            val chooser = Intent.createChooser(sendIntent, "مشاركة صورة الكشف عبر واتساب").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
            LaunchResult(true)
        } catch (_: Exception) {
            LaunchResult(false, "لا يوجد تطبيق متاح لمشاركة هذا الملف")
        }
    }
}
