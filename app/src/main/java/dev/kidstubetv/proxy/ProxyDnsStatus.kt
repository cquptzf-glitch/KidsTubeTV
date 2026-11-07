package dev.kidstubetv.proxy

/** Diagnostic count only. Never expose local resolver addresses or credentials. */
internal object ProxyDnsStatus {
    @Volatile var underlyingCount = 0
}
