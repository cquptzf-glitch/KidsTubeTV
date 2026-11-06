package dev.kidstubetv.proxy

/** Synchronizes the logical lifecycle of a single app-scoped VPN. */
class VpnSessionGate {
    private var generation = 0L
    private var running = false
    @Synchronized fun start(): Long? {
        if (running) return null
        generation++
        running = true
        return generation
    }
    @Synchronized fun current(id: Long) = running && generation == id
    @Synchronized fun stop(): Boolean {
        generation++
        val wasRunning = running
        running = false
        return wasRunning
    }
}
