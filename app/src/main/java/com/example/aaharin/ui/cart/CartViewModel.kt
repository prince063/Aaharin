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
                val itemIdsStr = cartItems.map { it.menuItem.id }.joinToString(",")
                val qtyMap = cartItems.associate { it.menuItem.id.toString() to it.quantity }

                val request = SaveKotRequest(
                    confirmOrder = 1,
                    kotPrint = if (isPrint) "Yes" else "No",
                    status = "Running",
                    restaurantId = if (restaurantId > 0) restaurantId else 1,
                    userId = if (userId > 0) userId else 1,
                    tableId = tableId,
                    itemIds = itemIdsStr,
                    itemIdQtyMap = qtyMap
                )

                val response = NetworkModule.apiService.saveKot(
                    accessToken = accessToken,
                    request = request
                )

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.code == 200 || response.code() == 200) {
                        DummyData.setTableOccupiedLocal(tableId, cartItems, null)
                        onSuccess()
                    } else {
                        errorMessage = apiResponse?.errorMessage ?: "Save KOT failed"
                    }
                } else {
                    errorMessage = "Server error: ${response.code()}"
                }
            } catch (e: Exception) {
                errorMessage = "Connection error: ${e.localizedMessage}"
            } finally {
                isSaving = false
            }
        }
    }
}
