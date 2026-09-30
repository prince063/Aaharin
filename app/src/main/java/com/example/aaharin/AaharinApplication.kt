package com.example.aaharin

import android.app.Application
import com.example.aaharin.network.ServerConfig

/**
 * Exists so the saved server address is loaded before anything tries to make a request.
 */
class AaharinApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServerConfig.init(this)
    }
}
