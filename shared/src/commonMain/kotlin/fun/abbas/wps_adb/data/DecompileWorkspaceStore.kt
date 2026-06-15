package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.RecentDecompileProject

expect object DecompileWorkspaceStore {
    fun loadRecent(recentFile: String): List<RecentDecompileProject>
    fun saveRecent(recentFile: String, project: RecentDecompileProject)
    fun removeRecent(recentFile: String, workspacePath: String)
    fun deleteProject(recentFile: String, project: RecentDecompileProject)
}
