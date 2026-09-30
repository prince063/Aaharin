package com.example.aaharin.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Half portions travel separately from full ones in `item-list-data`
 * (apis/backend/models/Utility.php); these pin the shapes both ways.
 */
class HalfPortionsTest {

    private fun item(id: Int) = MenuItem(
        id = id,
        name = "Dal Makhni",
        price = 200.0,
        categoryId = 1,
        hasHalfOption = true,
        halfPrice = 120.0
    )

    @Test
    fun `only half lines go into halfItemIdQtyArr, keyed for server pricing`() {
        val cart = listOf(
            CartItem(item(19), quantity = 2),
            CartItem(item(19), quantity = 3, isHalf = true),
            CartItem(item(20), quantity = 1, isHalf = true)
        )

        assertEquals(
            mapOf(
                "19" to mapOf(SERVER_PRICED_HALF_KEY to 3),
                "20" to mapOf(SERVER_PRICED_HALF_KEY to 1)
            ),
            cart.halfItemIdQtyMap()
        )
    }

    @Test
    fun `a cart with no halves sends an empty array`() {
        assertTrue(listOf(CartItem(item(19), quantity = 2)).halfItemIdQtyMap().isEmpty())
    }

    @Test
    fun `half_items is read per item and per price bucket`() {
        val json = JsonParser.parseString(
            """{"19": {"120.00": "2", "100.00": 1}, "20": {"60.00": 0}, "remarks": "x"}"""
        )

        assertEquals(
            mapOf(19 to mapOf("120.00" to 2, "100.00" to 1)),
            readHalfItems(json)
        )
    }

    @Test
    fun `missing or malformed half_items reads as none`() {
        assertTrue(readHalfItems(null).isEmpty())
        assertTrue(readHalfItems(JsonParser.parseString("[]")).isEmpty())
    }
}
