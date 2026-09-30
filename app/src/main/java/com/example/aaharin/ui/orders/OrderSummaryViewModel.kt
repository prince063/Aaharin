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
        itemQuantities: Map<Int, Int> = emptyMap(),
        halfItemQuantities: Map<Int, Map<String, Int>> = emptyMap()
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

        if (itemQuantities.isEmpty() && halfItemQuantities.isEmpty()) {
            isLoading = false
            if (orderedItems.isEmpty()) errorMessage = "No running order on this table"
            return
        }

        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.getOrderItems(
                    accessToken = accessToken,
                    request = OrderItemsRequest(
                        itemIds = orderItemIds(itemQuantities, halfItemQuantities),
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
                val liveCart = data.get("cartData")?.takeIf { it.isJsonObject }?.asJsonObject
                    ?.get(tableId.toString())?.takeIf { it.isJsonObject }?.asJsonObject
                val liveQuantities = readCartQuantities(liveCart)
                val liveHalf = readHalfItems(liveCart?.get("half_items"))
                // Trust the live cart only when it has something in it; otherwise fall
                // back to what the table list reported for both kinds of line.
                val useLive = liveQuantities.isNotEmpty() || liveHalf.isNotEmpty()
                val quantities = if (useLive) liveQuantities else itemQuantities
                val halfQuantities = if (useLive) liveHalf else halfItemQuantities

                val fullItems = quantities.mapNotNull { (id, qty) ->
                    val info = priced[id] ?: return@mapNotNull null
                    CartItem(
                        menuItem = MenuItem(
                            id = id,
                            name = info.name,
                            price = info.price,
                            categoryId = 0,
                            isInclusiveTax = info.isInclusiveTax
                        ),
                        quantity = qty,
                        status = ItemStatus.DELIVERED
                    )
                }

                // Each price bucket is its own line. The key is the per-unit price the
                // server stored, which is already the amount it charges GST on, so the
                // line is priced as tax-exclusive whatever the catalog flag says.
                val halfItems = halfQuantities.flatMap { (id, buckets) ->
                    val info = priced[id] ?: return@flatMap emptyList()
                    buckets.map { (priceKey, qty) ->
                        val halfPrice = priceKey.toDoubleOrNull()?.takeIf { it > 0.0 }
                        CartItem(
                            menuItem = MenuItem(
                                id = id,
                                name = info.name,
                                price = info.price,
                                categoryId = 0,
                                isInclusiveTax = halfPrice == null && info.isInclusiveTax,
                                hasHalfOption = true,
                                halfPrice = halfPrice ?: (info.price / 2)
                            ),
                            quantity = qty,
                            isHalf = true,
                            status = ItemStatus.DELIVERED
                        )
                    }
                }
                val items = fullItems + halfItems

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
     * Hands the bill to the web app, which renders the PDF and sends it to the printer.
     *
     * Deliberately the admin app's endpoint and not the API app's. The API one returns
     * the invoice number but writes no PDF and queues no print job - the bill layout is a
     * backend view it cannot render. The admin one does the whole job, and returns the
     * same invoice number.
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
            // The API app's own print-bill, not the admin app's.
            //
            // The admin app authenticates by matching the token against the stored
            // vaidi_users.access_token string, while every other call this app makes is
            // authenticated by decoding the JWT. The moment those two drift apart - a
            // refreshed token, or a second login overwriting the column - the admin route
            // answers HTTP 500 (its unauthorized branch calls send() without returning and
            // then dereferences the empty user row), so no invoice number ever came back
            // and the bill was never announced as printed. This route also queues the
            // actual printer job, which the admin one never did.
            val response = NetworkModule.apiService.printBill(
                accessToken = accessToken,
                request = PrintBillRequest(
                    orderId = orderIdForPrint,
                    tableId = tableId,
                    custName = custName,
                    custMobile = custMobile
                )
            )

            if (!response.isSuccessful) {
                Log.w(TAG, "print-bill HTTP ${response.code()} for $orderIdForPrint")
                return null
            }

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

    /** The fields of an `item-list-data` row the summary prices an order from. */
    private data class PricedItem(
        val name: String,
        val price: Double,
        val isInclusiveTax: Boolean
    )

    /** item_id -> priced row out of the response's category/item tree. */
    private fun readItemPrices(data: JsonObject): Map<Int, PricedItem> {
        val priced = mutableMapOf<Int, PricedItem>()
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
                // Without this an inclusive-tax item gets GST added on top of a price
                // that already contains it, and the summary overstates the bill.
                val isInclusiveTax = item.get("is_inclusive_tax")?.takeIf { it.isJsonPrimitive }
                    ?.asString?.toIntOrNull() == 1
                priced[id] = PricedItem(name, price, isInclusiveTax)
            }
        }
        return priced
    }

    /** Every item id on the order, full and half, as `item_ids` wants them. */
    private fun orderItemIds(
        itemQuantities: Map<Int, Int>,
        halfItemQuantities: Map<Int, Map<String, Int>>
    ): String = (itemQuantities.keys + halfItemQuantities.keys).joinToString(",")

    /** Live full-portion item_id -> quantity for this table, fresher than the table list's snapshot. */
    private fun readCartQuantities(cart: JsonObject?): Map<Int, Int> {
        val quantities = mutableMapOf<Int, Int>()
        if (cart == null) return quantities

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
        /** vaidi_orders.order_id - the string print-bill matches on. */
        orderIdForPrint: String?,
        kotNumber: Int,
        itemQuantities: Map<Int, Int>,
        halfItemQuantities: Map<Int, Map<String, Int>> = emptyMap(),
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
        if (orderTableId <= 0 || (itemQuantities.isEmpty() && halfItemQuantities.isEmpty())) {
            errorMessage = "No running order found for this table - reopen it and try again"
            return
        }

        isLoading = true
        errorMessage = null
        billNumber = null

        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.generateBill(
                    accessToken = accessToken,
                    request = GenerateBillRequest(
                        // item_ids only - the controller needs it to resolve the menu, but
                        // itemIdQtyArr is left empty so nothing is re-inserted. See the
                        // field's comment: passing the order's lines here duplicated them.
                        itemIds = orderItemIds(itemQuantities, halfItemQuantities),
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

                // The order is closed, so this device's running-order overlay is stale. The
                // table's colour now comes from the server's awaiting_settlement.
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
