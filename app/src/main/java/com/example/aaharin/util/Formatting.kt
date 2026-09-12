package com.example.aaharin.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Rupee formatter shared by every screen.
 *
 * Raw `"₹$double"` interpolation was leaking float noise into the UI
 * (e.g. a 325.00 bill rendered as "₹341.25000000000006"). The underlying
 * arithmetic is unchanged — this only affects how the number is displayed.
 */
private val currencyFormat: NumberFormat by lazy {
    NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
}

/** `1234.5` -> `"₹1,234.50"` */
fun Double.asRupees(): String = "₹" + currencyFormat.format(this)

/** `1234.5` -> `"1,234.50"` (no symbol). */
fun Double.asAmount(): String = currencyFormat.format(this)
