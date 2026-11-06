package dev.kidstubetv.proxy

data class ProxyGroup(val name: String, val selected: String, val nodes: List<String>)

/** Stable parent-facing API; each native backend implements its own protocol. */
object MihomoControl {
    private val backend: ProxyControlBackend = ProxyControlFactory.create()
    fun queryGroups(done: (Result<List<ProxyGroup>>) -> Unit) = backend.queryGroups(done)
    fun select(group: String, node: String, done: (Result<String>) -> Unit) =
        backend.select(group, node, done)
    fun testNode(node: String, done: (Result<String>) -> Unit) =
        backend.testNode(node, done)
    fun traffic(): String = backend.traffic()
    fun parseGroups(raw: String): List<ProxyGroup> = ProxyGroupsParser.parse(raw)
}
