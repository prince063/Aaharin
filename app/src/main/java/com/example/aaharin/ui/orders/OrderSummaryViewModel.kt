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
import kotlinx.coroutines.launch

class OrderSummaryViewModel : ViewModel() {

    private companion object {
        const val TAG = "OrderSummaryVM"
    }

    var orderId by mutableStateOf<Any?>(null)
    var orderedItems by mutableStateOf<List<CartItem>>(emptyList())
    var totalAmount by mutableDoubleStateOf(0.0)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun fetchOrderDetails(accessToken: String, restaurantId: Int, tableId: Int, runningOrderId: String? = null) {
        if (tableId == 0) {
            errorMessage = "Invalid Table"
            return
        }
        
        orderedItems = emptyList()
        totalAmount = 0.0
        orderId = runningOrderId ?: DummyData.getLocalOrderId(tableId)
        isLoading = true
        errorMessage = null

        val localItems = DummyData.getLocalOrder(tableId)
        if (!localItems.isNullOrEmpty()) {
            orderedItems = localItems
            totalAmount = localItems.grandTotal()
            orderId = DummyData.getLocalOrderId(tableId) ?: runningOrderId ?: tableId.toString()
            isLoading = false
        }

        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.getOrderDetails(
                    accessToken = accessToken,
                    request = OrderDetailsRequest(tableId = tableId, orderId = runningOrderId)
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.isJsonObject) {
                        val json = body.asJsonObject
                        if (json.get("code")?.asInt == 200) {
                            val data = if (json.has("data") && json.get("data").isJsonObject) json.get("data").asJsonObject else json
                            val orderStatus = data.get("status")?.asString

                            if (orderStatus?.lowercase() == "completed" && !localItems.isNullOrEmpty()) {
                                orderedItems = localItems
                                totalAmount = localItems.grandTotal()
                                isLoading = false
                                return@launch
                            }
                            
                            val sId = data.get("order_id")?.asString ?: data.get("id")?.asString
                            
                            orderId = sId ?: DummyData.getLocalOrderId(tableId) ?: runningOrderId ?: tableId.toString()

                            totalAmount = if (data.has("bill_detail") && data.get("bill_detail").isJsonObject) {
                                val bill = data.get("bill_detail").asJsonObject
                                val totalStr = bill.get("total")?.asString ?: "0"
                                totalStr.toDoubleOrNull() ?: 0.0
                            } else {
                                data.get("total_amount")?.asDouble ?: 0.0
                            }

                            val itemsElement = data.get("order_detail")
                            if (itemsElement != null && itemsElement.isJsonArray) {
                                orderedItems = itemsElement.asJsonArray.map { element ->
                                    val obj = element.asJsonObject
                                    val qty = when {
                                        obj.has("item_quantity") -> obj.get("item_quantity").asString.toIntOrNull() ?: 1
                                        obj.has("quantity") -> obj.get("quantity").asInt
                                        else -> 1
                                    }
                                    val itemName = obj.get("item_name")?.asString ?: "Unknown"
                                    val portion = obj.get("portion")?.asString ?: ""
                                    val displayName = if (portion.lowercase() == "half") "$itemName (Half)" else itemName
                                    
                                    val price = when {
                                        obj.has("item_price") -> obj.get("item_price").asString.toDoubleOrNull() ?: 0.0
                                        obj.has("price") -> obj.get("price").asDouble
                                        else -> 0.0
                                    }

                                    CartItem(
                                        menuItem = MenuItem(id = 0, name = displayName, price = price, categoryId = 0),
                                        quantity = if (qty <= 0) 1 else qty,
                                        status = ItemStatus.DELIVERED
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (orderedItems.isEmpty()) errorMessage = "Server error: ${response.code()}"
                }
            } catch (e: Exception) {
                Log.w(TAG, "order-details sync failed for table $tableId", e)
                if (orderedItems.isEmpty()) errorMessage = "Sync Error"
            } finally {
                isLoading = false
            }
        }
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

        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.generateBill(
                    accessToken = accessToken,
                    request = GenerateBillRequest(
                        itemIds = itemQuantities.keys.joinToString(","),
                        itemIdQtyMap = itemQuantities.mapKeys { it.key.toString() },
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
