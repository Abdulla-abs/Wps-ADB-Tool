package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.DecompileWorkspace
import `fun`.abbas.wps_adb.model.FileNode
import `fun`.abbas.wps_adb.model.StringConstantItem
import `fun`.abbas.wps_adb.model.DexSearchHit

class NoOpDecompileService : DecompileService {
    override suspend fun importApk(apkPath: String, workspaceRoot: String, onProgress: (Float, String) -> Unit): DecompileWorkspace {
        return DecompileWorkspace(apkPath, "", "com.mock")
    }
    override suspend fun loadFileTree(workspace: DecompileWorkspace): FileNode.Folder {
        return FileNode.Folder("empty", "", emptyList())
    }
    override suspend fun readFileContent(workspace: DecompileWorkspace, filePath: String): String = ""
    override suspend fun disassembleDexToSmali(dexPath: String, outputDir: String): FileNode.Folder {
        return FileNode.Folder("empty", "", emptyList())
    }
    override suspend fun decompileDexToJava(dexPath: String, outputDir: String, onProgress: (Float) -> Unit): String = ""
    override suspend fun loadDexConstants(dexPath: String): List<StringConstantItem> = emptyList()
    override suspend fun saveDexConstants(dexPath: String, constants: List<StringConstantItem>) {}
}

actual fun getDecompileService(): DecompileService = NoOpDecompileService()
