package `fun`.abbas.wps_adb.model

data class SidePanelState(
    val tabs: List<SidePanelTab> = emptyList(),
    val activeTabId: String? = null,
) {
    val isVisible: Boolean get() = tabs.isNotEmpty()

    val activeTab: SidePanelTab?
        get() = tabs.find { it.id == activeTabId } ?: tabs.lastOrNull()
}
