package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.RecentDecompileProject

actual object DecompileWorkspaceStore {
    actual fun loadRecent(recentFile: String): List<RecentDecompileProject> = emptyList()

    actual fun saveRecent(recentFile: String, project: RecentDecompileProject) = Unit

    actual fun removeRecent(recentFile: String, workspacePath: String) = Unit

    actual fun deleteProject(recentFile: String, project: RecentDecompileProject) = Unit
}
