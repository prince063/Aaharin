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

    /**
     * Where the server lives. Read fresh from [ServerConfig] on every use - it is a
     * device setting now, not a build constant, so one APK can be pointed anywhere.
     */
    val BASE_URL: String get() = ServerConfig.baseUrl

    const val SECRET_KEY = "3zA8nD\$wqB4jP@hLz8R5k"

    /**
     * Root of the server, without the `/apis/backend/web/` suffix.
     * Used to build absolute URLs for assets the API returns as relative paths.
     */
    val SERVER_ROOT: String get() = ServerConfig.serverRoot

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

    @Volatile private var cachedService: ApiService? = null
    @Volatile private var cachedUrl: String? = null

    /**
     * Rebuilt whenever the configured address changes.
     *
     * This was a `by lazy`, which pinned the first URL for the life of the process - so
     * changing the server in Settings did nothing until the app was restarted.
     */
    val apiService: ApiService
        @Synchronized get() {
            val url = BASE_URL
            var service = cachedService
            if (service == null || cachedUrl != url) {
                service = Retrofit.Builder()
                    .baseUrl(url)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .client(buildOkHttpClient())
                    .build()
                    .create(ApiService::class.java)
                cachedService = service
                cachedUrl = url
            }
            return service
        }
}
