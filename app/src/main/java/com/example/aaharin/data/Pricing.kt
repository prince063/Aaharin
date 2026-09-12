package com.example.aaharin.data

/**
 * Single source of truth for cart arithmetic.
 *
 * The "use halfPrice when isHalf and a half price exists, otherwise full price"
 * rule was duplicated in CartScreen, CartItemRow and OrderSummaryViewModel (twice).
 * The formulas here are identical to what those call sites computed inline.
 */

/** GST applied to a running order (5% total: 2.5% CGST + 2.5% SGST). */
const val GST_RATE = 0.05

/** Price of one unit, honouring the half-portion override. */
val CartItem.unitPrice: Double
    get() = if (isHalf && menuItem.halfPrice != null) menuItem.halfPrice else menuItem.price

/** Price of this line (unit price × quantity). */
val CartItem.lineTotal: Double
    get() = unitPrice * quantity

/** Sum of every line, before tax. */
fun List<CartItem>.subtotal(): Double = sumOf { it.lineTotal }

/** Tax payable on [subtotal]. */
fun List<CartItem>.gst(): Double = subtotal() * GST_RATE

/** Subtotal + GST. */
fun List<CartItem>.grandTotal(): Double = subtotal() * (1 + GST_RATE)
