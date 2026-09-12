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

                        // Extract IDs from cartData keys to identify occupied tables
                        val occupiedTableIds = mutableSetOf<Int>()
                        if (cartData != null && cartData.isJsonObject) {
                            cartData.asJsonObject.keySet().forEach { key ->
                                key.toIntOrNull()?.let { occupiedTableIds.add(it) }
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
