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
import com.example.aaharin.data.readHalfItems
import com.example.aaharin.network.NetworkModule
import com.example.aaharin.util.coerceToDouble
import com.example.aaharin.util.coerceToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Statuses the backend uses to mean "this table has a running order". */
private val RUNNING_STATUSES = setOf("running", "occupied", "active")

/** Statuses that mean a bill has been raised and is waiting to be settled. */
internal val BILLED_STATUSES = setOf("bill printed", "completed")

/**
 * What colour a table gets.
 *
 * Settlement wins over every "running" signal. The order behind a printed bill is
 * Completed, so a cart entry or this device's own overlay still pointing at it is stale -
 * letting either win is what kept a table billed from the web POS from ever going green.
 */
internal fun resolveTableStatus(
    normalisedStatus: String?,
    hasRunningCart: Boolean,
    isLocallyOccupied: Boolean,
    isAwaitingSettlement: Boolean
): TableStatus = when {
    isAwaitingSettlement -> TableStatus.BILL_PRINTED
    normalisedStatus in RUNNING_STATUSES || hasRunningCart || isLocallyOccupied ->
        TableStatus.OCCUPIED
    else -> TableStatus.VACANT
}

class TablesViewModel : ViewModel() {

    var services by mutableStateOf<List<ServiceCategory>>(emptyList())
    var tables by mutableStateOf<List<Table>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    private var fetchJob: Job? = null

    /**
     * Loads the table list.
     *
     * [silent] is for the background auto-refresh: no loading indicator, no settle-in
     * delay, and a failure leaves the list already on screen alone instead of replacing
     * it with an error. It is skipped while another load is still running, so a slow
     * server never has requests piling up behind it.
     */
    fun fetchTables(accessToken: String, restaurantId: Int, silent: Boolean = false) {
        if (silent && fetchJob?.isActive == true) return
        fetchJob?.cancel()

        if (!silent) {
            isLoading = true
            errorMessage = null
        }

        fetchJob = viewModelScope.launch {
            try {
                // Short delay to give backend time to process recent KOTs
                if (!silent) delay(1000)

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
                        val orderIds = mutableMapOf<Int, String>()
                        val kotNumbers = mutableMapOf<Int, Int>()
                        val itemQuantities = mutableMapOf<Int, Map<Int, Int>>()
                        val halfItemQuantities = mutableMapOf<Int, Map<Int, Map<String, Int>>>()
                        if (cartData != null && cartData.isJsonObject) {
                            cartData.asJsonObject.entrySet().forEach { (key, value) ->
                                val tId = key.toIntOrNull() ?: return@forEach
                                occupiedTableIds.add(tId)

                                val cart = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                                cart.get("order_table_id")?.takeIf { it.isJsonPrimitive }
                                    ?.asString?.toIntOrNull()?.let { orderTableIds[tId] = it }
                                // The string print-bill matches on - a different column
                                // from order_table_id, and the only place it is exposed.
                                cart.get("order_id")?.takeIf { it.isJsonPrimitive }
                                    ?.asString?.trim()?.takeIf { it.isNotEmpty() }
                                    ?.let { orderIds[tId] = it }
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

                                // Half portions are kept apart from the full lines above.
                                val half = readHalfItems(cart.get("half_items"))
                                if (half.isNotEmpty()) halfItemQuantities[tId] = half
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

                            // The bill on this table is printed but not settled. Its order
                            // is Completed, so it is in neither the running count nor
                            // cartData - without this the table reads as vacant, which is
                            // why billing from the web POS never turned it green.
                            //
                            // Split bills come back newest first; the app settles them one
                            // at a time and the table stays green until the last one goes.
                            val awaitingBill = apiTable.awaitingSettlement?.firstOrNull()
                            val isAwaitingSettlement =
                                awaitingBill != null || normalisedStatus in BILLED_STATUSES

                            // Billing happened elsewhere, so anything this device still
                            // remembers about a running order here is stale - left alone
                            // the overlay keeps the table orange forever.
                            if (isAwaitingSettlement) DummyData.clearLocalOccupancy(tId)

                            Table(
                                id = tId,
                                name = apiTable.modeName ?: "Table $tId",
                                status = resolveTableStatus(
                                    normalisedStatus = normalisedStatus,
                                    hasRunningCart = occupiedTableIds.contains(tId),
                                    isLocallyOccupied = DummyData.isTableOccupiedLocal(tId),
                                    isAwaitingSettlement = isAwaitingSettlement
                                ),
                                // A billed table carries no cart, so its order comes off
                                // the bill instead - the settle screen needs both.
                                orderId = orderIds[tId]
                                    ?: awaitingBill?.orderId?.trim()?.takeIf { it.isNotEmpty() },
                                orderTableId = orderTableIds[tId]
                                    ?: awaitingBill?.orderTableId.coerceToInt(),
                                // Rupee-rounded, half up, as the web POS and settle-order
                                // both settle against. Paying the raw 913.50 of a bill the
                                // server rounds to 914 left 0.50 due on every such bill.
                                billAmount = Math.round(awaitingBill?.grandTotal.coerceToDouble()).toDouble(),
                                billNumber = awaitingBill?.billNo.orEmpty(),
                                kotNumber = kotNumbers[tId] ?: 1,
                                itemQuantities = itemQuantities[tId] ?: emptyMap(),
                                halfItemQuantities = halfItemQuantities[tId] ?: emptyMap(),
                                serviceMode = apiTable.serviceCategoryName ?: "Other",
                                serviceCategoryId = apiTable.serviceCategoryId.coerceToInt()
                            )
                        }
                        errorMessage = null
                    } else {
                        reportError("API Error: ${apiResponse?.errorMessage}", silent)
                    }
                } else {
                    reportError("Server Error: ${response.code()}", silent)
                }
            } catch (e: CancellationException) {
                // Superseded by a newer load - not a failure worth showing.
                throw e
            } catch (e: Exception) {
                reportError("Sync Error: ${e.localizedMessage}", silent)
            } finally {
                // A load that was cancelled must not switch off the indicator of the
                // one that replaced it.
                if (fetchJob === coroutineContext[Job]) isLoading = false
            }
        }
    }

    private fun reportError(message: String, silent: Boolean) {
        // A background refresh that misses once is not worth replacing a good list for;
        // the next tick will try again.
        if (silent && tables.isNotEmpty()) return
        errorMessage = message
    }
}
