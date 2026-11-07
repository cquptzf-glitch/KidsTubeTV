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
}
