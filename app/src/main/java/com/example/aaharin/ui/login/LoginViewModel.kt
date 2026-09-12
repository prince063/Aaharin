package com.example.aaharin.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aaharin.data.LoginRequest
import com.example.aaharin.data.LoginResponse
import com.example.aaharin.network.NetworkModule
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    var mobile by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun login(onSuccess: (LoginResponse) -> Unit) {
        if (mobile.isBlank() || password.isBlank()) {
            errorMessage = "Please enter mobile and password"
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                // Sending as JSON body per YAML specification
                val request = LoginRequest(
                    mobileNumber = mobile,
                    password = password,
                    loginIp = "127.0.0.1"
                )

                val response = NetworkModule.apiService.login(
                    secretKey = NetworkModule.SECRET_KEY,
                    request = request
                )

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.code == 200 && apiResponse.data != null) {
                        onSuccess(apiResponse.data)
                    } else {
                        errorMessage = apiResponse?.errorMessage ?: "Invalid credentials"
                    }
                } else {
                    errorMessage = "Server error: ${response.code()}"
                }
            } catch (e: Exception) {
                errorMessage = "Check connection: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }
}
