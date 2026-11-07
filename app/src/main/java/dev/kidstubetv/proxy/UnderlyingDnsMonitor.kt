package dev.kidstubetv.proxy

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Network

/**
 * Mirrors the Android network observer FlClash uses to populate its "system"
 * DNS. Important: a VPN itself appears in ConnectivityManager; using that
 * network's DNS as an upstream would create a recursive lookup black hole.
 */
internal class UnderlyingDnsMonitor(
    context: Context,
    private val tunnelDns: String,
    private val onChanged: (List<String>) -> Unit
) {
    private val connectivity = context.getSystemService(
        Context.CONNECTIVITY_SERVICE
    ) as ConnectivityManager
    private var subscribed = false
    private var lastSent: List<String>? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { refresh() }
        override fun onLost(network: Network) { refresh() }
        override fun onLinkPropertiesChanged(
            network: Network, properties: android.net.LinkProperties
        ) { refresh() }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        // registerNetworkCallback may be restricted by an OEM firmware.
        // A one-shot snapshot still works even if registration fails.
        subscribed = runCatching {
            connectivity.registerNetworkCallback(request, callback)
            true
        }.getOrDefault(false)
        refresh(force = true)
    }

    @Synchronized
    fun refresh(force: Boolean = false) {
        val names = runCatching {
            connectivity.allNetworks.asSequence().filter { network ->
                connectivity.getNetworkCapabilities(network)?.let { caps ->
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                        !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } == true
            }.flatMap { network ->
                connectivity.getLinkProperties(network)?.dnsServers
                    ?.asSequence()?.mapNotNull { it.hostAddress }
                    ?: emptySequence()
            }.toList()
        }.getOrDefault(emptyList())
        val next = SystemDnsPolicy.normalize(names, tunnelDns)
        if (force || lastSent != next) {
            lastSent = next
            onChanged(next)
        }
    }

    fun stop() {
        if (subscribed) runCatching {
            connectivity.unregisterNetworkCallback(callback)
        }
        subscribed = false
    }
}
