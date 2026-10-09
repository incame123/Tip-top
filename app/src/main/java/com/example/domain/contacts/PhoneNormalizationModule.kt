package com.example.domain.contacts

import android.telephony.PhoneNumberUtils
import java.util.Locale

/**
 * Module 2: Phone Number Normalization Module.
 *
 * Normalizes raw address-book phone numbers into strict ITU-T E.164 format (+[country][subscriber])
 * using Android's maintained PhoneNumberUtils E.164 formatter with deterministic fallback parsing.
 */
object PhoneNormalizationModule {

    private val E164_REGEX = Regex("^\\+[1-9]\\d{7,14}$")

    /**
     * Normalizes [rawPhone] to E.164 format (e.g. "+14155552671").
     * Returns null if the number is invalid, too short, or unparseable.
     */
    fun normalizeToE164(
        rawPhone: String,
        defaultCountryIso: String = Locale.getDefault().country.ifBlank { "US" }
    ): String? {
        val trimmed = rawPhone.trim()
        if (trimmed.isEmpty()) return null

        // 1. Try Android platform PhoneNumberUtils E.164 formatter when available
        try {
            val platformFormatted = PhoneNumberUtils.formatNumberToE164(
                trimmed,
                defaultCountryIso.uppercase(Locale.US)
            )
            if (!platformFormatted.isNullOrBlank() && E164_REGEX.matches(platformFormatted)) {
                return platformFormatted
            }
        } catch ( ignored: Throwable) {
            // In pure JVM unit tests or devices without telephony tables, fall back to deterministic parser
        }

        // 2. Deterministic E.164 canonicalization fallback
        val cleaned = buildString {
            for ((index, ch) in trimmed.withIndex()) {
                when {
                    ch == '+' && index == 0 -> append('+')
                    ch in '0'..'9' -> append(ch)
                    ch in listOf(' ', '-', '(', ')', '.', '\u00A0') -> Unit
                    else -> return null // Reject alpha characters, extensions, or invalid symbols
                }
            }
        }

        val candidate = when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") && cleaned.length > 4 -> "+" + cleaned.substring(2)
            defaultCountryIso.equals("US", ignoreCase = true) ||
                defaultCountryIso.equals("CA", ignoreCase = true) -> {
                when (cleaned.length) {
                    10 -> "+1$cleaned"
                    11 -> if (cleaned.startsWith("1")) "+$cleaned" else return null
                    else -> return null
                }
            }
            defaultCountryIso.equals("GB", ignoreCase = true) -> {
                if (cleaned.startsWith("0") && cleaned.length in 10..11) {
                    "+44${cleaned.substring(1)}"
                } else return null
            }
            defaultCountryIso.equals("IN", ignoreCase = true) -> {
                when (cleaned.length) {
                    10 -> "+91$cleaned"
                    11 -> if (cleaned.startsWith("0")) "+91${cleaned.substring(1)}" else return null
                    else -> return null
                }
            }
            else -> return null
        }

        return if (E164_REGEX.matches(candidate)) candidate else null
    }

    /**
     * Masks an E.164 number for privacy-safe UI display (e.g., "+1415***2671").
     */
    fun maskE164ForDisplay(e164: String): String {
        if (e164.length < 8) return "***"
        return "${e164.take(5)}***${e164.takeLast(4)}"
    }
}
