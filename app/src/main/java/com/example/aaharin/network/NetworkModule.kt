package com.example.aaharin.network

import com.example.aaharin.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object NetworkModule {
    // Base URLs (matching openapi.yaml servers)

    /**
     * Emulator only. 10.0.2.2 is the emulator's alias for the host machine and does not
     * resolve anywhere else, so a real phone using this fails with
     * "failed to connect to /10.0.2.2 (port 20080)".
     */
    const val EMULATOR_BASE_URL = "http://10.0.2.2:20080/apis/backend/web/"

    /**
     * The dev machine on the local network - works from a real phone AND from the
     * emulator, as long as both are on the same Wi-Fi.
     *
     * This address is handed out by DHCP, so it can change when the PC reconnects. If
     * the app stops connecting, check the PC's current IPv4 address (`ipconfig`) and
     * update it here.
     */
    const val LAN_BASE_URL = "http://192.168.1.9:20080/apis/backend/web/"

    const val PROD_BASE_URL = "https://ahaar1.vaiditech.in/apis/backend/web/"

    // Active Base URL
    const val BASE_URL = LAN_BASE_URL
    const val SECRET_KEY = "3zA8nD\$wqB4jP@hLz8R5k"

    /**
     * Root of the server, without the `/apis/backend/web/` suffix.
     * Used to build absolute URLs for assets the API returns as relative paths.
     */
    val SERVER_ROOT: String = BASE_URL.substringBefore("/apis/")

    /**
     * Certificate validation is disabled ONLY on debug builds, where the dev server
     * runs behind a self-signed cert. Release builds use the platform trust store.
     *
     * If the production server also serves an untrusted cert, fix the cert — do not
     * flip this to `true`, it would make every release build trivially MITM-able.
     */
    private val allowInsecureSsl: Boolean get() = BuildConfig.DEBUG

    /** Single shared Gson instance — was previously re-created on every menu parse. */
    val gson: Gson by lazy { GsonBuilder().setLenient().create() }

    private fun buildOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // Body logging prints access tokens and passwords — debug only.
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BODY
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (allowInsecureSsl) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, SecureRandom())
            }
            builder
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
        }

        return builder.build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(buildOkHttpClient())
            .build()
            .create(ApiService::class.java)
    }
}
