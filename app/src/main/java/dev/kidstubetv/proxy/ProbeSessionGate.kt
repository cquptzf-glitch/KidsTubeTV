package dev.kidstubetv.proxy

/**
 * A bounded probe token for each VPN generation.
 * An invalidated request can never clear a newer request's running marker.
 */
class ProbeSessionGate {
    private var generation = 0L
    private var running: Long? = null

    @Synchronized fun invalidate() {
        generation++
        running = null
    }

    @Synchronized fun begin(): Long? {
        if (running != null) return null
        running = generation
        return generation
    }

    @Synchronized fun complete(token: Long): Boolean {
        if (token != generation || running != token) return false
        running = null
        return true
    }
}
