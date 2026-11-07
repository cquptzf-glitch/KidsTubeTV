package dev.kidstubetv.proxy

/**
 * Validate and deduplicate IP literals supplied by Android LinkProperties.
 * Never accept the TUN DNS as an underlying resolver.
 */
internal object SystemDnsPolicy {
    fun normalize(raw: Iterable<String>, tunnelDns: String): List<String> =
        raw.map(String::trim)
            .filter { it.isNotEmpty() && it != tunnelDns }
            .filter { address ->
                address.length <= 45 && address.none { it == ',' || it == '%' ||
                    it == '/' || it.isWhitespace() }
            }
            .distinct()
            .take(8)

    /**
     * FlClash Android NetworkObserver passes IP:53 rather than bare IP to
     * Core.updateDNS. Brackets are required for IPv6 socket addresses.
     * Source inputs are IP literals from Android LinkProperties, not hostnames.
     */
    fun coreArgument(servers: List<String>): String = servers.joinToString(",") { address ->
        if (':' in address) "[$address]:53" else "$address:53"
    }
}
