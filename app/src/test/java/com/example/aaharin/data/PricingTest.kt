package com.example.aaharin.data

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The cart used to add GST on top of every price, so an inclusive-tax item was taxed
 * twice and the total never matched the printed bill. These pin the arithmetic to what
 * the server does when it writes the KOT.
 */
class PricingTest {

    private fun item(
        price: Double,
        inclusive: Boolean = false,
        halfPrice: Double? = null
    ) = MenuItem(
        id = 1,
        name = "Paneer Tikka",
        price = price,
        categoryId = 1,
        isInclusiveTax = inclusive,
        halfPrice = halfPrice
    )

    @Before
    fun resetRates() {
        TaxRates.update(cgst = 2.5, sgst = 2.5, igst = 0.0)
    }

    @Test
    fun `exclusive item is taxed on top of the menu price`() {
        val cart = listOf(CartItem(item(100.0), quantity = 2))

        assertEquals(200.0, cart.subtotal(), 0.001)
        assertEquals(10.0, cart.gst(), 0.001)
        assertEquals(210.0, cart.grandTotal(), 0.001)
    }

    @Test
    fun `inclusive item never totals more than its menu price`() {
        val cart = listOf(CartItem(item(100.0, inclusive = true), quantity = 2))

        // 200 / 1.05, the same back-out the server applies at KOT-insert time.
        assertEquals(190.476, cart.subtotal(), 0.001)
        assertEquals(9.524, cart.gst(), 0.001)
        assertEquals(200.0, cart.grandTotal(), 0.001)
    }

    @Test
    fun `a mixed cart taxes each line by its own flag`() {
        val cart = listOf(
            CartItem(item(100.0), quantity = 1),
            CartItem(item(100.0, inclusive = true), quantity = 1)
        )

        assertEquals(105.0 + 100.0, cart.grandTotal(), 0.001)
    }

    @Test
    fun `rates come from the restaurant, not a hardcoded five percent`() {
        TaxRates.update(cgst = 9.0, sgst = 9.0, igst = 0.0)
        val cart = listOf(CartItem(item(100.0), quantity = 1))

        assertEquals(18.0, cart.gst(), 0.001)
        assertEquals(118.0, cart.grandTotal(), 0.001)
        assertEquals("18%", TaxRates.totalLabel)
    }

    @Test
    fun `half portion of an inclusive item backs out the half price`() {
        val cart = listOf(
            CartItem(item(100.0, inclusive = true, halfPrice = 60.0), quantity = 1, isHalf = true)
        )

        assertEquals(60.0, cart.grandTotal(), 0.001)
    }

    @Test
    fun `menuTotal adds up the sticker prices the kiosk lists`() {
        val cart = listOf(
            CartItem(item(100.0, inclusive = true), quantity = 1),
            CartItem(item(50.0), quantity = 2)
        )

        assertEquals(200.0, cart.menuTotal(), 0.001)
    }

    @Test
    fun `a fractional rate keeps its decimal in the label`() {
        TaxRates.update(cgst = 2.5, sgst = 2.5, igst = 7.5)

        assertEquals("12.5%", TaxRates.totalLabel)
    }
}
