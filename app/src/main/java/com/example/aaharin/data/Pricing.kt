package com.example.aaharin.data

/**
 * Single source of truth for cart arithmetic.
 *
 * The "use halfPrice when isHalf and a half price exists, otherwise full price"
 * rule was duplicated in CartScreen, CartItemRow and OrderSummaryViewModel (twice).
 * The formulas here are identical to what those call sites computed inline.
 */

/**
 * GST charged by the logged-in restaurant, as percentages.
 *
 * This was a hardcoded 5% while the billing call already sent the restaurant's own rates
 * (MainActivity), so anyone on a different rate saw one number in the cart and another on
 * the bill. Set once at login; the defaults are the same ones the billing request falls
 * back to.
 *
 * Read during composition and only ever written at login, before any cart exists - it is
 * not observable state, so nothing recomposes when it changes.
 */
object TaxRates {

    @Volatile
    var cgst: Double = 2.5
        private set

    @Volatile
    var sgst: Double = 2.5
        private set

    @Volatile
    var igst: Double = 0.0
        private set

    /** Combined rate applied to an order, as a percentage. */
    val totalPercent: Double
        get() = cgst + sgst + igst

    /** The combined rate as it reads on a summary line: "5%", "12.5%". */
    val totalLabel: String
        get() = if (totalPercent % 1.0 == 0.0) "${totalPercent.toInt()}%" else "$totalPercent%"

    fun update(cgst: Double, sgst: Double, igst: Double) {
        this.cgst = cgst
        this.sgst = sgst
        this.igst = igst
    }
}

/** Price of one unit, honouring the half-portion override. */
val CartItem.unitPrice: Double
    get() = if (isHalf && menuItem.halfPrice != null) menuItem.halfPrice else menuItem.price

/**
 * What this line reads as on the menu (unit price x quantity).
 *
 * On an inclusive-tax item this already contains GST, so it is what the customer ends up
 * paying rather than an amount tax is added to - see [taxableAmount].
 */
val CartItem.lineTotal: Double
    get() = unitPrice * quantity

/**
 * The tax-exclusive base of this line.
 *
 * An inclusive-tax item's price has GST inside it already, so the base is backed out the
 * same way the server does when it writes the KOT (`price * 100 / (100 + rate)`,
 * apis/backend/models/Utility.php). Adding tax on top of the sticker price instead - what
 * this file used to do for every item - charged it twice and produced a cart total the
 * printed bill never matched.
 */
val CartItem.taxableAmount: Double
    get() = if (menuItem.isInclusiveTax) {
        lineTotal * 100.0 / (100.0 + TaxRates.totalPercent)
    } else {
        lineTotal
    }

/** GST payable on this line. */
val CartItem.taxAmount: Double
    get() = taxableAmount * TaxRates.totalPercent / 100.0

/** Sum of every line, before tax. */
fun List<CartItem>.subtotal(): Double = sumOf { it.taxableAmount }

/** Tax payable on [subtotal]. */
fun List<CartItem>.gst(): Double = sumOf { it.taxAmount }

/** Subtotal + GST. */
fun List<CartItem>.grandTotal(): Double = subtotal() + gst()

/**
 * What the lines add up to at menu prices, with tax left wherever it already sits.
 *
 * The kiosk prints the per-line prices and then one total; a total that did not add up to
 * the lines directly above it would read as a mistake, so that screen totals these rather
 * than the tax-exclusive [subtotal].
 */
fun List<CartItem>.menuTotal(): Double = sumOf { it.lineTotal }
