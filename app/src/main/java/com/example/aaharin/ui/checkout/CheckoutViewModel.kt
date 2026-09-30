package com.example.aaharin.ui.checkout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aaharin.data.DummyData
import com.example.aaharin.data.PaymentMode
import com.example.aaharin.data.SettleOrderRequest
import com.example.aaharin.network.NetworkModule
import kotlinx.coroutines.launch

class CheckoutViewModel : ViewModel() {
    var isSettling by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun settleOrder(
        accessToken: String,
        orderId: String,
        tableId: Int = 0,
        paymentMode: PaymentMode,
        amount: Double,
        onSuccess: () -> Unit
    ) {
        isSettling = true
        errorMessage = null

        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.settleOrder(
                    accessToken = accessToken,
                    request = SettleOrderRequest(
                        orderId = orderId,
                        tableId = if (tableId > 0) tableId else null,
                        paymentMode = paymentMode.name,
                        amountPaid = amount
                    )
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    if (body?.code == 200 || response.code() == 200) {
                        if (tableId > 0) {
                            DummyData.clearLocalOccupancy(tableId)
                        }
                        onSuccess()
                    } else {
                        errorMessage = body?.errorMessage ?: "Settlement failed"
                    }
                } else if (response.code() == 404 || response.code() == 405) {
                    // This deployment's API app exposes no settle-order route at all, so
                    // the call can only ever 404. Saying "server error" sent staff looking
                    // for a network fault that was not there.
                    errorMessage = "This server has no settlement API - settle the bill from the POS dashboard."
                } else {
                    errorMessage = "Server error: ${response.code()}"
                }
            } catch (e: Exception) {
                errorMessage = "Connection error: ${e.localizedMessage}"
            } finally {
                isSettling = false
            }
        }
    }
}
