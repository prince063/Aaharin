package com.example.aaharin.ui.tables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aaharin.data.Table
import com.example.aaharin.data.TableStatus
import com.example.aaharin.data.ServiceCategory
import com.example.aaharin.data.DummyData
import com.example.aaharin.network.NetworkModule
import com.example.aaharin.util.coerceToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TablesViewModel : ViewModel() {

    private companion object {
        /** Statuses the backend uses to mean "this table has a running order". */
        val RUNNING_STATUSES = setOf("running", "occupied", "active")
        val BILLED_STATUSES = setOf("bill printed", "completed")
    }

    var services by mutableStateOf<List<ServiceCategory>>(emptyList())
    var tables by mutableStateOf<List<Table>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun fetchTables(accessToken: String, restaurantId: Int) {
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                // Short delay to give backend time to process recent KOTs
                delay(1000)

                // 1. Fetch tables from server
                val response = NetworkModule.apiService.getTableList(
                    headerToken = accessToken,
                    queryToken = accessToken,
                    fieldToken = accessToken,
                    restaurantId = restaurantId
                )

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.code == 200 && apiResponse.data != null) {
                        val tableData = apiResponse.data.tableData
                        val cartData = apiResponse.data.cartData

                        // Extract IDs from cartData keys to identify occupied tables.
                        //
                        // Each entry also holds the running order itself: its numeric id,
                        // kot number, and an item_id -> quantity pair per line. This is the
                        // only place the app can get them - tableData carries none of it and
                        // the server exposes no order-details route - and billing needs all
                        // three.
                        val occupiedTableIds = mutableSetOf<Int>()
                        val orderTableIds = mutableMapOf<Int, Int>()
                        val kotNumbers = mutableMapOf<Int, Int>()
                        val itemQuantities = mutableMapOf<Int, Map<Int, Int>>()
                        if (cartData != null && cartData.isJsonObject) {
                            cartData.asJsonObject.entrySet().forEach { (key, value) ->
                                val tId = key.toIntOrNull() ?: return@forEach
                                occupiedTableIds.add(tId)

                                val cart = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                                cart.get("order_table_id")?.takeIf { it.isJsonPrimitive }
                                    ?.asString?.toIntOrNull()?.let { orderTableIds[tId] = it }
                                cart.get("kot_number")?.takeIf { it.isJsonPrimitive }
                                    ?.asString?.toIntOrNull()?.let { kotNumbers[tId] = it }

                                // The line items are the numeric keys. Everything else in
                                // here is metadata ("remarks", "half_items", "<id>item_note_id"),
                                // and none of those parse as an Int.
                                val qty = mutableMapOf<Int, Int>()
                                cart.entrySet().forEach inner@{ (itemKey, itemValue) ->
                                    val itemId = itemKey.toIntOrNull() ?: return@inner
                                    if (!itemValue.isJsonPrimitive) return@inner
                                    val q = itemValue.asString.toDoubleOrNull()?.toInt() ?: return@inner
                                    if (q > 0) qty[itemId] = q
                                }
                                if (qty.isNotEmpty()) itemQuantities[tId] = qty
                            }
                        }
                        
                        // Extract unique services from table data dynamically
                        services = tableData
                            .filter { it.serviceCategoryName != null }
                            .distinctBy { it.serviceCategoryName }
                            .map { ServiceCategory(it.serviceCategoryId.coerceToInt(), it.serviceCategoryName!!, "", "") }

                        tables = tableData.map { apiTable ->
                            val tId = apiTable.id.coerceToInt()
                            val normalisedStatus = apiTable.status?.lowercase()?.trim()

                            // A table is occupied if its status says so OR it has an active cart OR it was just marked locally
                            val isRunning = normalisedStatus in RUNNING_STATUSES ||
                                           occupiedTableIds.contains(tId) ||
                                           DummyData.isTableOccupiedLocal(tId)

                            Table(
                                id = tId,
                                name = apiTable.modeName ?: "Table $tId",
                                status = when {
                                    isRunning -> TableStatus.OCCUPIED
                                    normalisedStatus in BILLED_STATUSES -> TableStatus.BILL_PRINTED
                                    else -> TableStatus.VACANT
                                },
                                orderTableId = orderTableIds[tId] ?: 0,
                                kotNumber = kotNumbers[tId] ?: 1,
                                itemQuantities = itemQuantities[tId] ?: emptyMap(),
                                serviceMode = apiTable.serviceCategoryName ?: "Other",
                                serviceCategoryId = apiTable.serviceCategoryId.coerceToInt()
                            )
                        }
                    } else {
                        errorMessage = "API Error: ${apiResponse?.errorMessage}"
                    }
                } else {
                    errorMessage = "Server Error: ${response.code()}"
                }
            } catch (e: Exception) {
                errorMessage = "Sync Error: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }
}
