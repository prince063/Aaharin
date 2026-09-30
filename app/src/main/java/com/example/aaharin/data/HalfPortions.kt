package com.example.aaharin.data

import com.google.gson.JsonElement

/**
 * Half-portion plumbing for `item-list-data` (apis/backend/models/Utility.php).
 *
 * The server keeps half portions apart from full ones: they go up as
 * `halfItemIdQtyArr` and come back under a table's cart as `half_items`, both shaped
 * item_id -> (price key -> quantity). The price key keeps two half rows of the same item
 * at different prices from merging into one quantity.
 */

/**
 * Price key the app sends with every half portion.
 *
 * Zero tells the server to price the half itself from the catalog and, for an
 * inclusive-tax item, to back GST out of it the way it does for a full portion. A real
 * price in the key is stored untouched, so an inclusive item would then be taxed on top
 * of a price that already contains tax.
 */
const val SERVER_PRICED_HALF_KEY = "0.00"

/** Cart lines -> the `halfItemIdQtyArr` body. Full portions are left out. */
fun List<CartItem>.halfItemIdQtyMap(): Map<String, Map<String, Int>> =
    filter { it.isHalf }
        .groupBy { it.menuItem.id.toString() }
        .mapValues { (_, lines) -> mapOf(SERVER_PRICED_HALF_KEY to lines.sumOf { it.quantity }) }

/**
 * A cart's `half_items` block -> item_id -> (price key -> quantity).
 *
 * The key is the per-unit price the server stored for that row, which is already the
 * amount it charges tax on. Anything unreadable is skipped rather than failing the order.
 */
fun readHalfItems(element: JsonElement?): Map<Int, Map<String, Int>> {
    val halfItems = element?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyMap()
    val result = mutableMapOf<Int, Map<String, Int>>()

    halfItems.entrySet().forEach items@{ (itemKey, buckets) ->
        val itemId = itemKey.toIntOrNull() ?: return@items
        val byPrice = buckets.takeIf { it.isJsonObject }?.asJsonObject ?: return@items

        val quantities = mutableMapOf<String, Int>()
        byPrice.entrySet().forEach prices@{ (priceKey, qty) ->
            if (!qty.isJsonPrimitive) return@prices
            val q = qty.asString.toDoubleOrNull()?.toInt() ?: return@prices
            if (q > 0) quantities[priceKey] = q
        }
        if (quantities.isNotEmpty()) result[itemId] = quantities
    }
    return result
}
