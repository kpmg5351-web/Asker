package com.example.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Exact financial arithmetic using integer piasters (1 EGP = 100 piasters)
 * and milli-quantities (1 unit = 1000 milli-units) to eliminate floating-point drift.
 */
object MoneyFormatter {

    private val symbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }

    private val intFormat = DecimalFormat("#,##0", symbols)
    private val decFormat = DecimalFormat("#,##0.00", symbols)

    fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input.trim()) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                '٫', '،' -> sb.append('.')
                ',' -> {} // strip thousands separator
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Parses EGP text (e.g. "200", "200.5", "1,550.25", "٢٠٠.٥") into exact integer piasters.
     * Rejects negative values unless [allowNegative] is true.
     */
    fun parseEgpToPiasters(input: String, allowNegative: Boolean = false): Long? {
        val clean = normalizeDigits(input)
        if (clean.isBlank()) return null
        return try {
            val bd = BigDecimal(clean)
            if (!allowNegative && bd < BigDecimal.ZERO) return null
            bd.multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses quantity text (supports decimals such as 1, 2.5, 0.25) into milli-units (1.0 -> 1000L).
     */
    fun parseQuantityToMilli(input: String): Long? {
        val clean = normalizeDigits(input)
        if (clean.isBlank()) return null
        return try {
            val bd = BigDecimal(clean)
            if (bd <= BigDecimal.ZERO) return null
            bd.multiply(BigDecimal(1000))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Calculates exact line total in piasters: round(unitPricePiasters * quantityMilli / 1000).
     */
    fun calculateLineTotalPiasters(unitPricePiasters: Long, quantityMilli: Long): Long {
        val unit = BigDecimal.valueOf(unitPricePiasters)
        val qty = BigDecimal.valueOf(quantityMilli)
        return unit.multiply(qty)
            .divide(BigDecimal.valueOf(1000L), 0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    /**
     * Splits piasters into (wholeEgpPounds, remainderPiasters 0..99) for paper invoice columns.
     */
    fun splitPoundsAndPiasters(piasters: Long): Pair<Long, Long> {
        val absVal = abs(piasters)
        val pounds = absVal / 100L
        val remPiasters = absVal % 100L
        return if (piasters < 0) Pair(-pounds, remPiasters) else Pair(pounds, remPiasters)
    }

    fun formatEgp(piasters: Long, currencyLabel: String = "ج.م", showZeroDecimals: Boolean = false): String {
        val sign = if (piasters < 0) "-" else ""
        val absVal = abs(piasters)
        val pounds = absVal / 100L
        val rem = absVal % 100L
        val numberStr = if (rem == 0L && !showZeroDecimals) {
            intFormat.format(pounds)
        } else {
            decFormat.format(absVal / 100.0)
        }
        return if (currencyLabel.isBlank()) "$sign$numberStr" else "$sign$numberStr $currencyLabel"
    }

    fun formatEgpPlainInput(piasters: Long): String {
        val absVal = abs(piasters)
        val pounds = absVal / 100L
        val rem = absVal % 100L
        val sign = if (piasters < 0) "-" else ""
        return if (rem == 0L) "$sign$pounds" else String.format(Locale.US, "%s%d.%02d", sign, pounds, rem)
    }

    fun formatQuantity(quantityMilli: Long): String {
        val whole = quantityMilli / 1000L
        val frac = abs(quantityMilli % 1000L)
        return when {
            frac == 0L -> whole.toString()
            frac % 100L == 0L -> "$whole.${frac / 100L}"
            frac % 10L == 0L -> "$whole.${String.format(Locale.US, "%02d", frac / 10L)}"
            else -> "$whole.${String.format(Locale.US, "%03d", frac)}"
        }
    }
}
