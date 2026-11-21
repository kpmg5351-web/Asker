package com.example.core

import java.math.BigDecimal
import java.math.RoundingMode

enum class UnitMeasureCategory(val title: String) {
    COUNT("بالعدد"),
    WEIGHT("بالوزن"),
    MEASURE("تعبئة ومقاس")
}

data class UnitSubOption(
    val label: String,
    val numerator: Long = 1L,
    val denominator: Long = 1L
)

object UnitMeasurement {

    val countUnits = listOf("قطعة", "علبة", "عبوة", "كيس", "زجاجة")
    val weightUnits = listOf("كيلو", "جرام", "طن")
    val measureUnits = listOf("كرتونة", "دستة", "شكارة", "متر", "لتر")
    val allQuickUnits = listOf("قطعة", "كيلو", "جرام", "طن", "كرتونة", "دستة", "علبة", "متر", "لتر", "شكارة")

    fun unitsForCategory(category: UnitMeasureCategory): List<String> = when (category) {
        UnitMeasureCategory.COUNT -> countUnits
        UnitMeasureCategory.WEIGHT -> weightUnits
        UnitMeasureCategory.MEASURE -> measureUnits
    }

    fun categoryForUnit(unitLabel: String): UnitMeasureCategory {
        val u = unitLabel.trim()
        return when {
            u in weightUnits || u == "كجم" || u == "جم" -> UnitMeasureCategory.WEIGHT
            u in measureUnits || u == "سم" || u == "مل" -> UnitMeasureCategory.MEASURE
            else -> UnitMeasureCategory.COUNT
        }
    }

    fun isWeightUnit(unitLabel: String): Boolean =
        categoryForUnit(unitLabel) == UnitMeasureCategory.WEIGHT

    fun subUnitsFor(unitLabel: String): List<UnitSubOption> {
        return when (unitLabel.trim()) {
            "كيلو", "كجم" -> listOf(
                UnitSubOption("كيلو", 1L, 1L),
                UnitSubOption("جرام", 1L, 1000L)
            )
            "جرام", "جم" -> listOf(
                UnitSubOption("جرام", 1L, 1L),
                UnitSubOption("كيلو", 1000L, 1L)
            )
            "طن" -> listOf(
                UnitSubOption("طن", 1L, 1L),
                UnitSubOption("كيلو", 1L, 1000L)
            )
            "دستة" -> listOf(
                UnitSubOption("دستة", 1L, 1L),
                UnitSubOption("قطعة", 1L, 12L)
            )
            "متر" -> listOf(
                UnitSubOption("متر", 1L, 1L),
                UnitSubOption("سم", 1L, 100L)
            )
            "لتر" -> listOf(
                UnitSubOption("لتر", 1L, 1L),
                UnitSubOption("مل", 1L, 1000L)
            )
            else -> listOf(UnitSubOption(unitLabel.trim().ifBlank { "قطعة" }, 1L, 1L))
        }
    }

    fun quantityFieldLabel(unitLabel: String, activeSubUnit: String = unitLabel): String {
        val primary = unitLabel.trim().ifBlank { "قطعة" }
        val active = activeSubUnit.trim().ifBlank { primary }
        return when {
            isWeightUnit(primary) || active == "جرام" || active == "كيلو" || active == "طن" -> "الوزن ($active)"
            primary == "متر" || active == "سم" -> "الطول ($active)"
            primary == "لتر" || active == "مل" -> "الحجم ($active)"
            else -> "العدد ($active)"
        }
    }

    fun priceFieldLabel(unitLabel: String): String {
        val clean = unitLabel.trim().ifBlank { "قطعة" }
        return "سعر $clean"
    }

    fun parseInputToPrimaryMilli(input: String, subUnit: UnitSubOption): Long? {
        val clean = MoneyFormatter.normalizeDigits(input)
        if (clean.isBlank()) return null
        return try {
            val bd = BigDecimal(clean)
            if (bd <= BigDecimal.ZERO) return null
            bd.multiply(BigDecimal(1000L))
                .multiply(BigDecimal(subUnit.numerator))
                .divide(BigDecimal(subUnit.denominator), 0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    fun formatPrimaryWithSubUnit(
        rawInput: String,
        primaryUnit: String,
        subUnit: UnitSubOption,
        primaryMilli: Long
    ): String {
        val cleanPrimary = primaryUnit.trim().ifBlank { "قطعة" }
        if (subUnit.numerator == subUnit.denominator) {
            return "${MoneyFormatter.formatQuantity(primaryMilli)} $cleanPrimary"
        }
        val cleanRaw = MoneyFormatter.normalizeDigits(rawInput).ifBlank { "0" }
        return "$cleanRaw ${subUnit.label} (${MoneyFormatter.formatQuantity(primaryMilli)} $cleanPrimary)"
    }

    /**
     * Converts a quantity typed in [from] sub-unit into the equivalent number string in [to] sub-unit.
     * E.g. 0.5 كيلو -> 500 جرام, or 250 جرام -> 0.25 كيلو.
     */
    fun convertQuantityBetweenSubUnits(
        rawInput: String,
        from: UnitSubOption,
        to: UnitSubOption
    ): String {
        if (from == to) return rawInput
        val primaryMilli = parseInputToPrimaryMilli(rawInput, from) ?: return rawInput
        return try {
            val converted = BigDecimal.valueOf(primaryMilli)
                .multiply(BigDecimal.valueOf(to.denominator))
                .divide(BigDecimal.valueOf(1000L * to.numerator), 4, RoundingMode.HALF_UP)
                .stripTrailingZeros()
            converted.toPlainString()
        } catch (_: Exception) {
            rawInput
        }
    }

    /**
     * Formats the saved line description so printed/shared invoices reflect non-default units and sub-units clearly.
     */
    fun formatLineDescriptionWithUnit(
        rawDescription: String,
        primaryUnit: String,
        subUnit: UnitSubOption,
        rawQtyInput: String,
        defaultFallbackDesc: String = "بضاعة / مبيعات"
    ): String {
        val cleanDesc = rawDescription.trim()
        val cleanPrimary = primaryUnit.trim().ifBlank { "قطعة" }
        val base = cleanDesc.ifBlank { defaultFallbackDesc }
        if (subUnit.numerator != subUnit.denominator) {
            val cleanQty = MoneyFormatter.normalizeDigits(rawQtyInput).ifBlank { "1" }
            val tag = "$cleanQty ${subUnit.label} / $cleanPrimary"
            return if (base.contains(subUnit.label)) base else "$base ($tag)"
        }
        if (cleanPrimary == "قطعة" || cleanDesc.contains(cleanPrimary)) {
            return cleanDesc
        }
        return "$base ($cleanPrimary)"
    }

    fun quickPresetsFor(unitLabel: String, activeSubUnit: String): List<Pair<String, String>> {
        val primary = unitLabel.trim()
        val sub = activeSubUnit.trim()
        return when {
            primary == "كيلو" && sub == "جرام" -> listOf(
                "100 جم" to "100",
                "250 جم" to "250",
                "500 جم" to "500",
                "750 جم" to "750"
            )
            primary == "كيلو" -> listOf(
                "ربع (0.25)" to "0.25",
                "نصف (0.5)" to "0.5",
                "1 كيلو" to "1",
                "2 كيلو" to "2",
                "5 كيلو" to "5"
            )
            primary == "جرام" && sub == "كيلو" -> listOf(
                "0.5 كيلو" to "0.5",
                "1 كيلو" to "1",
                "2 كيلو" to "2"
            )
            primary == "جرام" -> listOf(
                "50 جم" to "50",
                "100 جم" to "100",
                "250 جم" to "250",
                "500 جم" to "500"
            )
            primary == "طن" && sub == "كيلو" -> listOf(
                "250 كيلو" to "250",
                "500 كيلو" to "500",
                "750 كيلو" to "750"
            )
            primary == "طن" -> listOf(
                "ربع (0.25)" to "0.25",
                "نصف (0.5)" to "0.5",
                "1 طن" to "1",
                "2 طن" to "2"
            )
            primary == "دستة" && sub == "قطعة" -> listOf(
                "3 قطع" to "3",
                "6 قطع" to "6",
                "12 قطعة" to "12"
            )
            primary == "دستة" -> listOf(
                "½ دستة" to "0.5",
                "1 دستة" to "1",
                "2 دستة" to "2",
                "5 دستات" to "5"
            )
            primary == "متر" && sub == "سم" -> listOf(
                "25 سم" to "25",
                "50 سم" to "50",
                "75 سم" to "75",
                "150 سم" to "150"
            )
            primary == "متر" -> listOf(
                "½ متر" to "0.5",
                "1 متر" to "1",
                "2 متر" to "2",
                "5 متر" to "5"
            )
            primary == "لتر" && sub == "مل" -> listOf(
                "250 مل" to "250",
                "500 مل" to "500",
                "750 مل" to "750"
            )
            primary == "لتر" -> listOf(
                "½ لتر" to "0.5",
                "1 لتر" to "1",
                "2 لتر" to "2",
                "5 لتر" to "5"
            )
            else -> listOf(
                "1" to "1",
                "2" to "2",
                "5" to "5",
                "10" to "10"
            )
        }
    }

    fun buildProductUnitPreview(
        unitLabel: String,
        salePricePiasters: Long,
        currency: String
    ): List<Pair<String, String>> {
        if (salePricePiasters <= 0L) return emptyList()
        val u = unitLabel.trim().ifBlank { "قطعة" }
        return when (u) {
            "كيلو", "كجم" -> listOf(
                "1 كيلو" to MoneyFormatter.formatEgp(salePricePiasters, currency),
                "½ كيلو (500 جم)" to MoneyFormatter.formatEgp(MoneyFormatter.calculateLineTotalPiasters(salePricePiasters, 500L), currency),
                "¼ كيلو (250 جم)" to MoneyFormatter.formatEgp(MoneyFormatter.calculateLineTotalPiasters(salePricePiasters, 250L), currency)
            )
            "جرام", "جم" -> listOf(
                "100 جم" to MoneyFormatter.formatEgp(MoneyFormatter.calculateLineTotalPiasters(salePricePiasters, 100_000L), currency),
                "250 جم" to MoneyFormatter.formatEgp(MoneyFormatter.calculateLineTotalPiasters(salePricePiasters, 250_000L), currency),
                "1 كيلو" to MoneyFormatter.formatEgp(MoneyFormatter.calculateLineTotalPiasters(salePricePiasters, 1_000_000L), currency)
            )
            "طن" -> listOf(
                "1 طن" to MoneyFormatter.formatEgp(salePricePiasters, currency),
                "½ طن (500 كجم)" to MoneyFormatter.formatEgp(MoneyFormatter.calculateLineTotalPiasters(salePricePiasters, 500L), currency),
                "100 كيلو" to MoneyFormatter.formatEgp(MoneyFormatter.calculateLineTotalPiasters(salePricePiasters, 100L), currency)
            )
            "دستة" -> listOf(
                "1 دستة (12)" to MoneyFormatter.formatEgp(salePricePiasters, currency),
                "½ دستة (6)" to MoneyFormatter.formatEgp(MoneyFormatter.calculateLineTotalPiasters(salePricePiasters, 500L), currency),
                "القطعة الواحدة" to MoneyFormatter.formatEgp(
                    BigDecimal.valueOf(salePricePiasters).divide(BigDecimal.valueOf(12L), 0, RoundingMode.HALF_UP).longValueExact(),
                    currency
                )
            )
            "متر" -> listOf(
                "1 متر" to MoneyFormatter.formatEgp(salePricePiasters, currency),
                "½ متر (50 سم)" to MoneyFormatter.formatEgp(MoneyFormatter.calculateLineTotalPiasters(salePricePiasters, 500L), currency),
                "2.5 متر" to MoneyFormatter.formatEgp(MoneyFormatter.calculateLineTotalPiasters(salePricePiasters, 2500L), currency)
            )
            "لتر" -> listOf(
                "1 لتر" to MoneyFormatter.formatEgp(salePricePiasters, currency),
                "½ لتر (500 مل)" to MoneyFormatter.formatEgp(MoneyFormatter.calculateLineTotalPiasters(salePricePiasters, 500L), currency),
                "2 لتر" to MoneyFormatter.formatEgp(MoneyFormatter.calculateLineTotalPiasters(salePricePiasters, 2000L), currency)
            )
            else -> listOf(
                "1 $u" to MoneyFormatter.formatEgp(salePricePiasters, currency),
                "5 $u" to MoneyFormatter.formatEgp(MoneyFormatter.calculateLineTotalPiasters(salePricePiasters, 5000L), currency),
                "10 $u" to MoneyFormatter.formatEgp(MoneyFormatter.calculateLineTotalPiasters(salePricePiasters, 10_000L), currency)
            )
        }
    }
}
