package tech.second.barktopay.wallet

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Connectivity pre-check and error-message humanization. */
object Connectivity {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Maps low-level wallet/network exceptions to something a human can act on. */
    fun friendlyMessage(context: Context, e: Throwable): String {
        if (!isOnline(context)) return "No internet connection"
        val msg = e.message ?: return "Wallet error (${e::class.java.simpleName})"
        return when {
            CONNECTION_HINTS.any { msg.contains(it, ignoreCase = true) } ->
                "Couldn't reach the Ark server. Check your connection and try again."
            else -> msg
        }
    }

    private val CONNECTION_HINTS = listOf(
        "connect", "unreachable", "timeout", "timed out", "dns", "network", "ark server"
    )
}
