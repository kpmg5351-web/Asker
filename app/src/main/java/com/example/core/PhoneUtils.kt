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
}
