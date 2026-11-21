package com.example

import com.example.core.MoneyFormatter
import com.example.core.UnitMeasureCategory
import com.example.core.UnitMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitMeasurementTest {

    @Test
    fun `kilo and grams sub-unit conversion calculates exact price`() {
        val kgSubOptions = UnitMeasurement.subUnitsFor("كيلو")
        val gramSub = kgSubOptions.first { it.label == "جرام" }

        // 250 grams at 120 EGP/kg = 30 EGP (3000 piasters)
        val primaryMilli = UnitMeasurement.parseInputToPrimaryMilli("250", gramSub)
        assertEquals(250L, primaryMilli)
        assertEquals(
            3000L,
            MoneyFormatter.calculateLineTotalPiasters(unitPricePiasters = 120_00L, quantityMilli = primaryMilli!!)
        )

        // Arabic numerals: ٥٠٠ جرام = 0.5 kg = 60 EGP
        val arabicMilli = UnitMeasurement.parseInputToPrimaryMilli("٥٠٠", gramSub)
        assertEquals(500L, arabicMilli)
        assertEquals(
            6000L,
            MoneyFormatter.calculateLineTotalPiasters(unitPricePiasters = 120_00L, quantityMilli = arabicMilli!!)
        )
    }

    @Test
    fun `dozen and piece conversion computes fraction of dozen`() {
        val dozenOptions = UnitMeasurement.subUnitsFor("دستة")
        val pieceSub = dozenOptions.first { it.label == "قطعة" }

        // 6 pieces = 0.5 dozen -> 500 milli-dozens -> 60 EGP at 120 EGP/dozen
        val halfDozenMilli = UnitMeasurement.parseInputToPrimaryMilli("6", pieceSub)
        assertEquals(500L, halfDozenMilli)
        assertEquals(
            6000L,
            MoneyFormatter.calculateLineTotalPiasters(unitPricePiasters = 120_00L, quantityMilli = halfDozenMilli!!)
        )

        // 3 pieces = 0.25 dozen -> 250 milli-dozens -> 30 EGP
        val quarterDozenMilli = UnitMeasurement.parseInputToPrimaryMilli("3", pieceSub)
        assertEquals(250L, quarterDozenMilli)
        assertEquals(
            3000L,
            MoneyFormatter.calculateLineTotalPiasters(unitPricePiasters = 120_00L, quantityMilli = quarterDozenMilli!!)
        )
    }

    @Test
    fun `ton meter and liter sub-units convert accurately`() {
        val tonKgSub = UnitMeasurement.subUnitsFor("طن").first { it.label == "كيلو" }
        assertEquals(500L, UnitMeasurement.parseInputToPrimaryMilli("500", tonKgSub))

        val meterCmSub = UnitMeasurement.subUnitsFor("متر").first { it.label == "سم" }
        assertEquals(500L, UnitMeasurement.parseInputToPrimaryMilli("50", meterCmSub))

        val literMlSub = UnitMeasurement.subUnitsFor("لتر").first { it.label == "مل" }
        assertEquals(750L, UnitMeasurement.parseInputToPrimaryMilli("750", literMlSub))

        assertNull(UnitMeasurement.parseInputToPrimaryMilli("0", meterCmSub))
        assertNull(UnitMeasurement.parseInputToPrimaryMilli("-5", meterCmSub))
    }

    @Test
    fun `switching between primary and sub unit converts entered value cleanly`() {
        val kgSubs = UnitMeasurement.subUnitsFor("كيلو")
        val kg = kgSubs.first { it.label == "كيلو" }
        val gram = kgSubs.first { it.label == "جرام" }

        assertEquals("500", UnitMeasurement.convertQuantityBetweenSubUnits("0.5", kg, gram))
        assertEquals("0.25", UnitMeasurement.convertQuantityBetweenSubUnits("250", gram, kg))

        val dozenSubs = UnitMeasurement.subUnitsFor("دستة")
        val dozen = dozenSubs.first { it.label == "دستة" }
        val piece = dozenSubs.first { it.label == "قطعة" }

        assertEquals("12", UnitMeasurement.convertQuantityBetweenSubUnits("1", dozen, piece))
        assertEquals("0.5", UnitMeasurement.convertQuantityBetweenSubUnits("6", piece, dozen))
    }

    @Test
    fun `unit category and field labels reflect weight vs count`() {
        assertEquals(UnitMeasureCategory.WEIGHT, UnitMeasurement.categoryForUnit("كيلو"))
        assertEquals(UnitMeasureCategory.COUNT, UnitMeasurement.categoryForUnit("قطعة"))
        assertEquals(UnitMeasureCategory.MEASURE, UnitMeasurement.categoryForUnit("متر"))

        assertEquals("الوزن (كيلو)", UnitMeasurement.quantityFieldLabel("كيلو", "كيلو"))
        assertEquals("الوزن (جرام)", UnitMeasurement.quantityFieldLabel("كيلو", "جرام"))
        assertEquals("الطول (سم)", UnitMeasurement.quantityFieldLabel("متر", "سم"))
        assertEquals("العدد (قطعة)", UnitMeasurement.quantityFieldLabel("قطعة", "قطعة"))
    }

    @Test
    fun `product preview and line description formatting`() {
        val preview = UnitMeasurement.buildProductUnitPreview("دستة", 120_00L, "ج.م")
        assertEquals(3, preview.size)
        assertEquals("10 ج.م", preview[2].second)

        val gramSub = UnitMeasurement.subUnitsFor("كيلو").first { it.label == "جرام" }
        assertEquals(
            "طماطم (500 جرام / كيلو)",
            UnitMeasurement.formatLineDescriptionWithUnit("طماطم", "كيلو", gramSub, "500")
        )
    }
}
