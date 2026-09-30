package com.example.aaharin.ui.tables

import com.example.aaharin.data.ApiTableItem
import com.example.aaharin.data.TableStatus
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Billing a table from the web POS left it grey in the app: the API only ever said
 * "Occupied" or "Available", and a billed order is neither - it is Completed, so it
 * dropped out of the running count and out of cartData.
 */
class TableStatusTest {

    private val gson = GsonBuilder().setLenient().create()

    @Test
    fun `a billed table is green even while this device still thinks it is occupied`() {
        // The exact case reported: the order was placed from the app, so the local
        // overlay is still set, and the bill was printed from the web.
        val status = resolveTableStatus(
            normalisedStatus = "available",
            hasRunningCart = false,
            isLocallyOccupied = true,
            isAwaitingSettlement = true
        )

        assertEquals(TableStatus.BILL_PRINTED, status)
    }

    @Test
    fun `a table reopened after billing goes back to orange`() {
        val status = resolveTableStatus(
            normalisedStatus = "occupied",
            hasRunningCart = true,
            isLocallyOccupied = false,
            isAwaitingSettlement = false
        )

        assertEquals(TableStatus.OCCUPIED, status)
    }

    @Test
    fun `a running cart alone still means occupied`() {
        val status = resolveTableStatus(
            normalisedStatus = "available",
            hasRunningCart = true,
            isLocallyOccupied = false,
            isAwaitingSettlement = false
        )

        assertEquals(TableStatus.OCCUPIED, status)
    }

    @Test
    fun `nothing running and nothing billed is vacant`() {
        val status = resolveTableStatus(
            normalisedStatus = "available",
            hasRunningCart = false,
            isLocallyOccupied = false,
            isAwaitingSettlement = false
        )

        assertEquals(TableStatus.VACANT, status)
    }

    @Test
    fun `the bill status string alone is enough`() {
        assertEquals(true, "bill printed" in BILLED_STATUSES)
        assertEquals(true, "completed" in BILLED_STATUSES)
    }

    @Test
    fun `awaiting_settlement parses off the wire`() {
        // One row as table-list now sends it.
        val json = """
            {
              "table_id": "1",
              "table_name": "Table 1",
              "status": "Bill Printed",
              "awaiting_settlement": [
                {
                  "order_table_id": 792,
                  "order_id": "pRqiEEFd",
                  "billing_id": 586,
                  "bill_no": "MONSEP261452526998",
                  "grand_total": 207.9,
                  "discount": 0
                }
              ]
            }
        """.trimIndent()

        val table = gson.fromJson(json, ApiTableItem::class.java)
        val bill = table.awaitingSettlement?.firstOrNull()

        assertNotNull(bill)
        assertEquals("pRqiEEFd", bill!!.orderId)
        assertEquals("MONSEP261452526998", bill.billNo)
        assertEquals(207.9, (bill.grandTotal as Number).toDouble(), 0.001)
    }

    @Test
    fun `a table with no bill parses to no awaiting entry`() {
        val json = """{"table_id":"2","table_name":"Table 2","status":"Available"}"""

        val table = gson.fromJson(json, ApiTableItem::class.java)

        assertEquals(null, table.awaitingSettlement?.firstOrNull())
    }
}
