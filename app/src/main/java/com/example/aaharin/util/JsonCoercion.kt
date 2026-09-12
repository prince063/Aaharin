package com.example.aaharin.util

/**
 * The backend types the same field inconsistently (`5`, `"5"`, `5.0`, or null)
 * depending on the endpoint, so every model field that carries an id/price is
 * declared as `Any?` and coerced at the mapping site.
 *
 * These helpers replace the ~10 hand-written `when (val v = x) { is Number -> ... }`
 * blocks that were duplicated across TablesViewModel and MenuViewModel.
 * The coercion rules are byte-for-byte the same as the code they replace.
 */

/** Coerces a loosely-typed JSON value to Int, falling back to [default]. */
fun Any?.coerceToInt(default: Int = 0): Int = when (this) {
    is Number -> toInt()
    is String -> toIntOrNull() ?: default
    else -> default
}

/** Coerces a loosely-typed JSON value to Double, falling back to [default]. */
fun Any?.coerceToDouble(default: Double = 0.0): Double = when (this) {
    is Number -> toDouble()
    is String -> toDoubleOrNull() ?: default
    else -> default
}

/** Truthiness for flags the API sends as `1`, `"1"` or `1.0`. */
fun Any?.coerceToBoolean(): Boolean = when (this) {
    is Number -> toInt() == 1
    is String -> this == "1" || equals("true", ignoreCase = true)
    is Boolean -> this
    else -> false
}
