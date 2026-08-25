package com.devicemonitor.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

data class NetworkInfo(
    val networkType: String,
    val wifiAvailable: Boolean,
    val mobileDataAvailable: Boolean
)

class NetworkMonitor(private val context: Context) {
    fun getNetworkInfo(): NetworkInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)

        if (caps == null) {
            return NetworkInfo("none", false, false)
        }

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
}
