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
    override suspend fun saveDexConstants(smaliRootsByDex: Map<String, String>, constants: List<StringConstantItem>) {}
    override suspend fun saveFileContent(workspace: DecompileWorkspace, filePath: String, content: String) {
        throw UnsupportedOperationException("Decompile is not available on Android")
    }
    override suspend fun closeWorkspace() {}
    override suspend fun searchSmaliFiles(rootDir: String, query: String, maxResults: Int): List<DexSearchHit> = emptyList()
    override suspend fun assembleSmaliToDex(smaliRoot: String, outputDexPath: String) {}
    override suspend fun repackApk(workspace: DecompileWorkspace, outputApkPath: String) {}
    override suspend fun signApk(
        unsignedApkPath: String,
        signedApkPath: String,
        adbPath: String,
        keystorePath: String,
    ) {}
    override suspend fun decompileSmaliToJava(smaliFilePath: String, requiresReassembly: Boolean): String = ""
}

actual fun getDecompileService(): DecompileService = NoOpDecompileService()
