package com.example.core

object PhoneUtils {

    const val SUPPORT_PHONE_LOCAL = "01099762204"

    fun normalizePhoneDigits(raw: String): String {
        val normalized = MoneyFormatter.normalizeDigits(raw)
        return normalized.filter { it.isDigit() || it == '+' }
    }

    /**
     * Validates Egyptian mobile/landline or international phone numbers.
     */
    fun isValidPhone(raw: String): Boolean {
        val digits = normalizePhoneDigits(raw).removePrefix("+")
        if (digits.length < 8 || digits.length > 15) return false
        // Egyptian mobile: 010/011/012/015 + 8 digits (11 total) or 2010... (12 total)
        if (digits.startsWith("01")) return digits.length == 11 && digits[2] in setOf('0', '1', '2', '5')
        if (digits.startsWith("201")) return digits.length == 12 && digits[3] in setOf('0', '1', '2', '5')
        return digits.length in 9..15
    }

    /**
     * Converts Egyptian local phone (e.g. 01099762204) to international WhatsApp format (201099762204).
     */
    fun toWhatsAppInternational(raw: String, countryCode: String = "20"): String? {
        val cleaned = normalizePhoneDigits(raw)
        if (cleaned.isBlank()) return null
        val digits = cleaned.removePrefix("+").removePrefix("00")
        if (digits.length < 8) return null
        return when {
            digits.startsWith("0") -> countryCode + digits.drop(1)
            digits.startsWith(countryCode) -> digits
            else -> digits
        }
    }

    /**
     * Cleans and formats a phone number imported from Android Contacts:
     * - Strips spaces, hyphens, parentheses, and RTL/LTR markers
     * - Normalizes Arabic-Indic digits to ASCII
     * - Converts Egyptian international formats (+20 / 0020 / +200) to local 01... format
     */
    fun cleanPickedContactPhone(raw: String, countryCode: String = "20"): String {
        val ascii = MoneyFormatter.normalizeDigits(raw)
        val filtered = buildString {
            ascii.forEach { ch ->
                when {
                    ch.isDigit() -> append(ch)
                    ch == '+' && isEmpty() -> append(ch)
                }
            }
        }
        if (filtered.isBlank()) return ""

        val normalizedIntl = if (filtered.startsWith("00") && filtered.length > 4) {
            "+" + filtered.drop(2)
        } else {
            filtered
        }

        val withoutPlus = normalizedIntl.removePrefix("+")
        if (countryCode == "20") {
            // Fix accidental "+20010..." saved in contacts
            if (normalizedIntl.startsWith("+200") && withoutPlus.length >= 13) {
                return withoutPlus.drop(2) // -> "010..."
            }
            // Standard "+2010..." or "2010..." (12 digits starting with 201[0,1,2,5])
            if (withoutPlus.startsWith("201") && withoutPlus.length == 12 &&
                withoutPlus[3] in setOf('0', '1', '2', '5')
            ) {
                return "0" + withoutPlus.drop(2)
            }
            // Egyptian landline "+202..." / "+203..." (9-11 digits after 20)
            if (normalizedIntl.startsWith("+20") && withoutPlus.length in 10..11 &&
                withoutPlus[2] in setOf('2', '3', '4', '5', '6', '8', '9')
            ) {
                return "0" + withoutPlus.drop(2)
            }
        }
        return normalizedIntl
    }
}
