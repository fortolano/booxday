package com.boox.day.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** The tablet's network right now, as far as the system knows. */
enum class NetworkState {
    /** No network: Wi-Fi is off or not connected */
    NONE,

    /** Connected to a network, but the system found no internet through it */
    NO_INTERNET,

    /** Connected, and the system reached the internet through it */
    ONLINE,

    /** Can't tell (not set up yet, unit tests) */
    UNKNOWN
}

/**
 * Whether the tablet is online (Oct 2026). Only used to say why an AI request failed (no Wi-Fi,
 * Wi-Fi without internet); it never stops a request, since the system's view can lag behind.
 */
object NetworkStatus {

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun current(): NetworkState {
        val manager = appContext?.getSystemService(ConnectivityManager::class.java) ?: return NetworkState.UNKNOWN
        return try {
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return NetworkState.NONE
            when {
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkState.NONE
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkState.NO_INTERNET
                else -> NetworkState.ONLINE
            }
        } catch (e: Exception) {
            NetworkState.UNKNOWN
        }
    }
}
