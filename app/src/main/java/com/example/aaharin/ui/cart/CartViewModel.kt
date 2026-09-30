package com.example.aaharin.ui.cart

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aaharin.data.*
import com.example.aaharin.network.NetworkModule
import kotlinx.coroutines.launch

class CartViewModel : ViewModel() {
    var isSaving by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun saveKOT(
        accessToken: String,
        restaurantId: Int,
        userId: Int,
        tableId: Int,
        cartItems: List<CartItem>,
        isPrint: Boolean = true,
        onSuccess: () -> Unit
    ) {
        if (cartItems.isEmpty()) return

        isSaving = true
        errorMessage = null

        viewModelScope.launch {
            try {
                val itemIdsStr = cartItems.map { it.menuItem.id }.distinct().joinToString(",")
                // Half portions go in their own array - the server inserts every
                // itemIdQtyArr entry at full price, so a half left in there was billed full.
                val qtyMap = cartItems
                    .filter { !it.isHalf }
                    .groupBy { it.menuItem.id.toString() }
                    .mapValues { (_, lines) -> lines.sumOf { it.quantity } }

                val request = SaveKotRequest(
                    confirmOrder = 1,
                    kotPrint = if (isPrint) "Yes" else "No",
                    status = "Running",
                    restaurantId = if (restaurantId > 0) restaurantId else 1,
                    userId = if (userId > 0) userId else 1,
                    tableId = tableId,
                    itemIds = itemIdsStr,
                    itemIdQtyMap = qtyMap,
                    halfItemIdQtyMap = cartItems.halfItemIdQtyMap()
                )

                val response = NetworkModule.apiService.saveKot(
                    accessToken = accessToken,
                    request = request
                )

                if (!response.isSuccessful) {
                    errorMessage = "Server error: ${response.code()}"
                    return@launch
                }

                // The backend answers HTTP 200 even for failures and carries the real
                // status in the body, so response.code() tells us nothing here. Accepting
                // either one meant every save reported success - an unauthorised or
                // rejected KOT still marked the table occupied and moved the user on.
                val apiResponse = response.body()
                if (apiResponse?.code != 200) {
                    errorMessage = apiResponse?.errorMessage ?: "Save KOT failed"
                    return@launch
                }

                DummyData.setTableOccupiedLocal(tableId, cartItems, null)
                onSuccess()
            } catch (e: Exception) {
                errorMessage = "Connection error: ${e.localizedMessage}"
            } finally {
                isSaving = false
            }
        }
    }
}
