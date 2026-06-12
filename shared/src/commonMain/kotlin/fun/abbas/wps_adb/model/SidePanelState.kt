package `fun`.abbas.wps_adb.model

enum class SidePanelDrawerState {
    Hidden,
    Collapsed,
    Expanded,
}

data class SidePanelState(
    val tabs: List<SidePanelTab> = emptyList(),
    val activeTabId: String? = null,
    val drawerState: SidePanelDrawerState = SidePanelDrawerState.Hidden,
) {
    val isVisible: Boolean get() = drawerState != SidePanelDrawerState.Hidden

    val isExpanded: Boolean get() = drawerState == SidePanelDrawerState.Expanded

    val activeTab: SidePanelTab?
        get() = tabs.find { it.id == activeTabId } ?: tabs.lastOrNull()
}
