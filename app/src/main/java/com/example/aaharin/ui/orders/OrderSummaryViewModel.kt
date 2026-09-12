package com.example.aaharin.ui.orders

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.net.toUri
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

    fun printBillWithDetails(
        context: Context,
        accessToken: String,
        restaurantId: Int,
        orderId: Any?,
        tableId: Int = 0,
        custName: String = "",
        custMobile: String = "",
        custEmail: String = "",
        custGst: String = "",
        custAddress: String = "",
        discount: Double = 0.0,
        paymentType: String = "",
        onSuccess: (String?) -> Unit
    ) {
        isLoading = true
        errorMessage = null

        // Was three copies of the same hardcoded dev URL; now derived from the
        // configured base URL so switching to PROD_BASE_URL carries it along.
        val fallbackUrl = sampleBillUrl(restaurantId)

        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.printBillForm(
                    accessToken = accessToken,
                    tableId = tableId,
                    discount = discount,
                    paymentType = paymentType,
                    custName = custName,
                    custMobile = custMobile,
                    custEmail = custEmail,
                    custGst = custGst,
                    custAddress = custAddress
                )

                val pdfUrl = if (response.isSuccessful) {
                    response.body()?.pdfUrl ?: fallbackUrl
                } else {
                    fallbackUrl
                }

                if (pdfUrl.startsWith("http")) {
                    downloadPdf(context, pdfUrl)
                }
                onSuccess(pdfUrl)
            } catch (e: Exception) {
                Log.w(TAG, "print-bill failed, falling back to sample bill", e)
                downloadPdf(context, fallbackUrl)
                onSuccess(fallbackUrl)
            } finally {
                isLoading = false
            }
        }
    }

    private fun sampleBillUrl(restaurantId: Int): String {
        val id = if (restaurantId > 0) restaurantId else 1
        return "${NetworkModule.SERVER_ROOT}/prints/$id/bills/sample_bill.pdf"
    }

    private fun downloadPdf(context: Context, pdfUrl: String) {
        try {
            val request = DownloadManager.Request(pdfUrl.toUri()).apply {
                setTitle("Downloading Bill PDF")
                setDescription("Downloading bill from Aaharin POS")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "Bill_${System.currentTimeMillis()}.pdf"
                )
            }
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            // DownloadManager unavailable/disabled — hand the URL to the browser instead.
            Log.w(TAG, "DownloadManager unavailable, opening bill externally", e)
            try {
                val intent = Intent(Intent.ACTION_VIEW, pdfUrl.toUri()).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                // No activity can handle a PDF — surface it instead of crashing the app.
                Log.e(TAG, "No handler for bill URL $pdfUrl", e2)
                errorMessage = "Could not open the bill PDF"
            }
        }
    }
}
