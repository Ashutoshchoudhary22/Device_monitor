package com.devicemonitor.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

data class NetworkInfo(
    val networkType: String,
    val wifiAvailable: Boolean,
    val mobileDataAvailable: Boolean
)

class NetworkMonitor(private val context: Context) {

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun getNetworkInfo(): NetworkInfo {
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
            ?: return NetworkInfo("none", false, false)

        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        val type = when {
            wifi -> "wifi"
            cellular -> "mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }

        return NetworkInfo(type, wifi, cellular)
    }

    /**
     * Register a callback that fires whenever a usable network becomes available
     * (covers WiFi → Mobile and vice versa, reconnects after airplane mode, etc.)
     * Call [unregisterNetworkCallback] when the service is destroyed.
     */
    fun registerNetworkCallback(onAvailable: () -> Unit) {
        unregisterNetworkCallback() // avoid duplicate registration

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onAvailable()
            }
        }
        cm.registerNetworkCallback(request, networkCallback!!)
    }

    fun unregisterNetworkCallback() {
        networkCallback?.let {
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
            networkCallback = null
        }
    }
}
