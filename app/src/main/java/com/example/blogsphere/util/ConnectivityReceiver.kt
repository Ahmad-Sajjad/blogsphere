package com.example.blogsphere.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** System broadcast receiver — listens for connectivity changes and toasts. */
class ConnectivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val online = isOnline(context)
        context.toast(if (online) "Back online" else "You are offline")
    }

    companion object {
        fun isOnline(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
}
