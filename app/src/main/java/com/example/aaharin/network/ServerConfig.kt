package com.example.aaharin.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/**
 * Where this device should talk to the server.
 *
 * The address used to be a compile-time constant, which pinned every build to one
 * machine: an APK built against 192.168.1.9 answers "Failed to connect" on any other
 * network, because that is a private LAN address and does not exist outside the router
 * that handed it out. It now lives on the device, so the same APK can be pointed at a
 * LAN address, a Tailscale address or a tunnel URL without a rebuild.
 */
object ServerConfig {

    private const val PREFS_NAME = "aaharin_server"

    /**
     * Bumped when the default address changes, so a device that saved an address under
     * the old key falls back to [DEFAULT_ADDRESS] once instead of silently holding on to
     * a machine that is no longer serving. The old key is deleted on first read, so the
     * reset happens exactly once per bump rather than on every launch.
     */
    private const val KEY_ADDRESS = "server_address_v2"
    private val LEGACY_KEYS = listOf("server_address")

    /** The API lives under this path on every deployment. */
    private const val API_PATH = "apis/backend/web/"
    private const val DEFAULT_PORT = 20080

    /** Used until someone sets an address in Settings. */
    private const val DEFAULT_ADDRESS = "localhost:20080"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        dropLegacyAddresses()
    }

    private fun dropLegacyAddresses() {
        val store = prefs ?: return
        val stale = LEGACY_KEYS.filter { store.contains(it) }
        if (stale.isEmpty()) return
        store.edit().apply { stale.forEach { remove(it) } }.apply()
    }

    /**
     * Exactly what the user typed, for showing back to them. Falls back to
     * [DEFAULT_ADDRESS], so a device that has never been set up is still pointed
     * somewhere usable rather than reading as unconfigured.
     */
    val address: String
        get() = prefs?.getString(KEY_ADDRESS, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_ADDRESS

    val isConfigured: Boolean
        get() = address.isNotBlank()

    /** Full Retrofit base URL. */
    val baseUrl: String
        get() = toBaseUrl(address)

    /**
     * Root of the server without the API path, for assets the API returns as relative
     * paths.
     */
    val serverRoot: String
        get() = baseUrl.substringBefore("/apis/")

    fun save(input: String) {
        prefs?.edit()?.putString(KEY_ADDRESS, input.trim())?.apply()
    }

    /**
     * Turns whatever was typed into a usable base URL, so staff enter an address rather
     * than a full path:
     *
     *   192.168.1.9              -> http://192.168.1.9:20080/apis/backend/web/ (same Wi-Fi)
     *   100.101.102.103          -> http://100.101.102.103:20080/apis/backend/web/ (Tailscale)
     *   https://x.trycloudflare.com -> https://x.trycloudflare.com/apis/backend/web/ (tunnel)
     *   http://host/apis/backend/web/ -> unchanged
     *   localhost 8080           -> http://localhost:8080/apis/backend/web/ (space, not colon)
     *
     * A tunnel URL carries its own port in the scheme, which is why the default port is
     * only added when no scheme was given.
     *
     * On the emulator the loopback host is rewritten - see [resolveLoopback].
     */
    fun toBaseUrl(input: String): String {
        var value = input.trim().removeSuffix("/")
        if (value.isEmpty()) return ""

        // The port gets typed after a space as often as after a colon ("localhost 80").
        // Left alone that reaches OkHttp as a host with a space in it and the request
        // dies with `Invalid URL host` before a connection is ever attempted - an error
        // that says nothing about the typo that caused it.
        val parts = value.split(Regex("""\s+"""))
        value = if (parts.size == 2 && parts[1].all { it.isDigit() }) {
            "${parts[0]}:${parts[1]}"
        } else {
            parts.joinToString("")
        }

        val hasScheme = value.startsWith("http://", true) || value.startsWith("https://", true)
        if (!hasScheme) {
            val hostPart = value.substringBefore("/")
            if (!hostPart.contains(":")) {
                value = value.replaceFirst(hostPart, "$hostPart:$DEFAULT_PORT")
            }
            value = "http://$value"
        }

        value = resolveLoopback(value)

        return if (value.contains("/apis/")) "$value/" else "$value/$API_PATH"
    }

    /**
     * Points a loopback address at the development machine when running on an emulator.
     *
     * `localhost` inside an emulator is the emulated device itself, not the PC hosting
     * it, so an address that works in the host's browser answers "failed to connect" in
     * the app. The emulator publishes the host as 10.0.2.2; `adb reverse` is the usual
     * alternative but it is not always available, and this needs no per-run setup.
     *
     * Only the host is swapped, so the port and path the user typed are kept, and a real
     * device is left alone - there `localhost` really can be reached over `adb reverse`.
     */
    private fun resolveLoopback(url: String): String {
        if (!isEmulator) return url
        return LOOPBACK_HOSTS.fold(url) { acc, host ->
            acc.replaceFirst("://$host:", "://$EMULATOR_HOST:", ignoreCase = true)
        }
    }

    /** The emulator's alias for the machine it is running on. */
    private const val EMULATOR_HOST = "10.0.2.2"

    private val LOOPBACK_HOSTS = listOf("localhost", "127.0.0.1")

    private val isEmulator: Boolean by lazy {
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.FINGERPRINT.contains("generic") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
            Build.PRODUCT.contains("sdk") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu")
    }
}
