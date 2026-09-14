package com.example.aaharin.ui.orders

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aaharin.data.*
import com.example.aaharin.network.NetworkModule
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

class OrderSummaryViewModel : ViewModel() {

    private companion object {
        const val TAG = "OrderSummaryVM"
    }

    var orderId by mutableStateOf<Any?>(null)
    /** Invoice number the printer job was raised against, e.g. "T6-1/00439". */
    var billNumber by mutableStateOf<String?>(null)
    var orderedItems by mutableStateOf<List<CartItem>>(emptyList())
    var totalAmount by mutableDoubleStateOf(0.0)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    /**
     * Loads the running order for a table.
     *
     * The items come from the server, keyed off [itemQuantities] - the item ids the table
     * list reported for this table. Previously this screen could only show what was in the
     * local cache, so an order placed anywhere else (the web POS, a QR order) appeared
     * empty here even though the table showed as occupied.
     *
     * The `order-details` route this used to call does not exist on the server; it answers
     * 404, which is why the local cache was the only thing ever displayed.
     */
    fun fetchOrderDetails(
        accessToken: String,
        restaurantId: Int,
        tableId: Int,
        runningOrderId: String? = null,
        itemQuantities: Map<Int, Int> = emptyMap()
    ) {
        if (tableId == 0) {
            errorMessage = "Invalid Table"
            return
        }

        orderedItems = emptyList()
        totalAmount = 0.0
        orderId = runningOrderId ?: DummyData.getLocalOrderId(tableId)
        isLoading = true
        errorMessage = null

        // Anything ordered on this device shows straight away, then the server view
        // replaces it - that one also covers items added from elsewhere.
        val localItems = DummyData.getLocalOrder(tableId)
        if (!localItems.isNullOrEmpty()) {
            orderedItems = localItems
            totalAmount = localItems.grandTotal()
            isLoading = false
        }

        if (itemQuantities.isEmpty()) {
            isLoading = false
            if (orderedItems.isEmpty()) errorMessage = "No running order on this table"
            return
        }

        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.getOrderItems(
                    accessToken = accessToken,
                    request = OrderItemsRequest(
                        itemIds = itemQuantities.keys.joinToString(","),
                        tableId = tableId,
                        restaurantId = restaurantId
                    )
                )

                val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                val code = body?.get("code")?.asString?.toIntOrNull()
                if (!response.isSuccessful || code != 200) {
                    Log.w(TAG, "item lookup failed for table $tableId: http=${response.code()} code=$code")
                    if (orderedItems.isEmpty()) {
                        errorMessage = body?.get("error_message")?.asString ?: "Could not load the order"
                    }
                    return@launch
                }

                val data = body.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@launch

                val priced = readItemPrices(data)
                val liveQuantities = readCartQuantities(data, tableId)
                val quantities = liveQuantities.ifEmpty { itemQuantities }

                val items = quantities.mapNotNull { (id, qty) ->
                    val info = priced[id] ?: return@mapNotNull null
                    CartItem(
                        menuItem = MenuItem(id = id, name = info.first, price = info.second, categoryId = 0),
                        quantity = qty,
                        status = ItemStatus.DELIVERED
                    )
                }

                if (items.isNotEmpty()) {
                    orderedItems = items
                    totalAmount = items.grandTotal()
                } else if (orderedItems.isEmpty()) {
                    errorMessage = "Could not read the items on this order"
                }
            } catch (e: Exception) {
                Log.w(TAG, "item lookup failed for table $tableId", e)
                if (orderedItems.isEmpty()) errorMessage = "Sync Error"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * `vaidi_orders.order_id` for this table's un-billed order, or null if there is none.
     *
     * Must be called before the bill is generated: getOrderDetails() filters on
     * `bill.bill_no is null`, so once billed it answers "Available" instead.
     */
    private suspend fun fetchOrderIdForPrint(accessToken: String, tableId: Int): String? {
        return try {
            val response = NetworkModule.apiService.getOrderDetails(
                accessToken = accessToken,
                request = OrderDetailsRequest(tableId = tableId)
            )

            val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
            if (!response.isSuccessful || body?.get("code")?.asString?.toIntOrNull() != 200) {
                Log.w(TAG, "order-details failed for table $tableId: http=${response.code()}")
                return null
            }

            // `data` is the order object, or the string "Available" when nothing is open.
            val data = body.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            data.get("order_id")?.takeIf { it.isJsonPrimitive }?.asString
                ?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "order-details lookup failed for table $tableId", e)
            null
        }
    }

    /**
     * Queues the bill on the restaurant's printer and returns the invoice number.
     *
     * Null means nothing was printed - the server answers "This order is in progress."
     * when the order it resolved is not completed, rather than failing outright.
     */
    private suspend fun sendToPrinter(
        accessToken: String,
        orderIdForPrint: String,
        tableId: Int,
        custName: String,
        custMobile: String
    ): String? {
        return try {
            val response = NetworkModule.apiService.printBill(
                accessToken = accessToken,
                request = PrintBillRequest(
                    orderId = orderIdForPrint,
                    tableId = tableId,
                    custName = custName,
                    custMobile = custMobile
                )
            )

            val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
            val invoice = body?.get("data")?.takeIf { it.isJsonPrimitive }?.asString?.trim()

            if (invoice.isNullOrEmpty() || invoice == "This order is in progress.") {
                Log.w(TAG, "print-bill produced no invoice for $orderIdForPrint: $invoice")
                null
            } else {
                invoice
            }
        } catch (e: Exception) {
            Log.w(TAG, "print-bill failed for $orderIdForPrint", e)
            null
        }
    }

    /** item_id -> (name, price) out of the response's category/item tree. */
    private fun readItemPrices(data: JsonObject): Map<Int, Pair<String, Double>> {
        val priced = mutableMapOf<Int, Pair<String, Double>>()
        val tableData = data.get("tableData")?.takeIf { it.isJsonObject }?.asJsonObject ?: return priced

        tableData.entrySet().forEach categories@{ (key, value) ->
            // Categories sit under numeric keys; "order_already_placed" shares the object.
            if (key.toIntOrNull() == null) return@categories
            val category = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@categories

            category.get("items")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach items@{ element ->
                val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@items
                val id = item.get("item_id")?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()
                    ?: return@items
                val name = item.get("item_name")?.takeIf { it.isJsonPrimitive }?.asString ?: "Unknown"
                val price = item.get("item_price")?.takeIf { it.isJsonPrimitive }
                    ?.asString?.toDoubleOrNull() ?: 0.0
                priced[id] = name to price
            }
        }
        return priced
    }

    /** Live item_id -> quantity for this table, fresher than the table list's snapshot. */
    private fun readCartQuantities(data: JsonObject, tableId: Int): Map<Int, Int> {
        val quantities = mutableMapOf<Int, Int>()
        val cart = data.get("cartData")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get(tableId.toString())?.takeIf { it.isJsonObject }?.asJsonObject ?: return quantities

        cart.entrySet().forEach lines@{ (key, value) ->
            val id = key.toIntOrNull() ?: return@lines
            if (!value.isJsonPrimitive) return@lines
            val qty = value.asString.toDoubleOrNull()?.toInt() ?: return@lines
            if (qty > 0) quantities[id] = qty
        }
        return quantities
    }

    /**
     * Bills the order through `item-list-data`, the same call the web POS makes.
     *
     * This both completes the order and writes the vaidi_order_billing record, so the
     * table frees up and the amount is left outstanding. Nothing is collected here -
     * settlement is a separate step.
     *
     * Needs the line items ([itemQuantities]) and [orderTableId] from the table list;
     * without them the server has nothing to bill and would silently open a new order.
     */
    fun generateBill(
        accessToken: String,
        restaurantId: Int,
        userId: Int,
        tableId: Int,
        orderTableId: Int,
        kotNumber: Int,
        itemQuantities: Map<Int, Int>,
        cgst: Double,
        sgst: Double,
        igst: Double,
        custName: String = "",
        custMobile: String = "",
        custGst: String = "",
        custAddress: String = "",
        discount: Double = 0.0,
        paymentType: String = "",
        onSuccess: () -> Unit
    ) {
        if (orderTableId <= 0 || itemQuantities.isEmpty()) {
            errorMessage = "No running order found for this table - reopen it and try again"
            return
        }

        isLoading = true
        errorMessage = null
        billNumber = null

        viewModelScope.launch {
            try {
                // Read the order id BEFORE billing: order-details only returns orders that
                // have no bill yet, and print-bill needs this exact string to find the
                // right order once it is completed.
                val orderIdForPrint = fetchOrderIdForPrint(accessToken, tableId)

                val response = NetworkModule.apiService.generateBill(
                    accessToken = accessToken,
                    request = GenerateBillRequest(
                        // item_ids only - the controller needs it to resolve the menu, but
                        // itemIdQtyArr is left empty so nothing is re-inserted. See the
                        // field's comment: passing the order's lines here duplicated them.
                        itemIds = itemQuantities.keys.joinToString(","),
                        orderTableId = orderTableId,
                        kotNumber = kotNumber,
                        tableId = tableId,
                        restaurantId = restaurantId,
                        userId = userId,
                        cgst = cgst,
                        sgst = sgst,
                        igst = igst,
                        discount = discount,
                        paymentType = paymentType,
                        customerName = custName,
                        customerMobile = custMobile,
                        customerGst = custGst,
                        customerAddress = custAddress
                    )
                )

                if (!response.isSuccessful) {
                    Log.w(TAG, "generate-bill HTTP ${response.code()}")
                    errorMessage = "Billing failed (server error ${response.code()})"
                    return@launch
                }

                val body = response.body()?.takeIf { it.isJsonObject }?.asJsonObject
                // HTTP 200 comes back for failures too; only the body's code is real.
                val code = body?.get("code")?.asString?.toIntOrNull()
                if (code != 200) {
                    errorMessage = body?.get("error_message")?.asString
                        ?: "Billing failed (code ${code ?: "?"})"
                    return@launch
                }

                // The server refuses to issue a second invoice for an order that already
                // has one, and says so with this flag rather than an error.
                val tableDataObj = body.get("data")
                    ?.takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("tableData")?.takeIf { it.isJsonObject }?.asJsonObject
                val alreadyPlaced = tableDataObj?.get("order_already_placed")
                    ?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull() ?: 0

                if (alreadyPlaced == 1) {
                    errorMessage = "This order has already been billed"
                    return@launch
                }

                // Bill exists now - hand it to the printer.
                if (orderIdForPrint != null) {
                    billNumber = sendToPrinter(accessToken, orderIdForPrint, tableId, custName, custMobile)
                    if (billNumber == null) {
                        errorMessage = "Bill generated, but the printer request failed"
                    }
                } else {
                    // Refusing rather than letting print-bill guess from the table id,
                    // which would print whichever order carries that string.
                    Log.w(TAG, "no order id for table $tableId - skipping the print request")
                    errorMessage = "Bill generated, but could not identify the order to print"
                }

                // Order is closed and billed, so the table is free for the next cover.
                if (tableId != 0) {
                    DummyData.clearLocalOccupancy(tableId)
                }
                onSuccess()
            } catch (e: Exception) {
                Log.w(TAG, "generate-bill request failed", e)
                errorMessage = "Billing failed: check the connection and try again"
            } finally {
                isLoading = false
            }
        }
    }
}
