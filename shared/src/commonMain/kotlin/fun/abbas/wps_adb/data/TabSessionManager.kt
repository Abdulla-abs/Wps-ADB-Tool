package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.TabListenKind

interface TabSessionManager {
    fun start(tabId: String, kind: TabListenKind)
    fun stop(tabId: String, kind: TabListenKind)
    fun stopAll(tabId: String)
    fun stopAll()
    fun activeKinds(tabId: String): Set<TabListenKind>
}

class NoOpTabSessionManager : TabSessionManager {
    private val sessions = mutableMapOf<String, MutableSet<TabListenKind>>()

    override fun start(tabId: String, kind: TabListenKind) {
        sessions.getOrPut(tabId) { mutableSetOf() }.add(kind)
    }

    override fun stop(tabId: String, kind: TabListenKind) {
        sessions[tabId]?.remove(kind)
        if (sessions[tabId]?.isEmpty() == true) {
            sessions.remove(tabId)
        }
    }

    override fun stopAll(tabId: String) {
        sessions.remove(tabId)
    }

    override fun stopAll() {
        sessions.clear()
    }

    override fun activeKinds(tabId: String): Set<TabListenKind> =
        sessions[tabId]?.toSet().orEmpty()
}
