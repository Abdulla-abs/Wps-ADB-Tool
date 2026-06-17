package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.DecompileWorkspace
import `fun`.abbas.wps_adb.model.FileNode
import `fun`.abbas.wps_adb.model.StringConstantItem
import `fun`.abbas.wps_adb.model.DexSearchHit

interface DecompileService {
    suspend fun importApk(apkPath: String, workspaceRoot: String, onProgress: (Float, String) -> Unit): DecompileWorkspace
    suspend fun loadFileTree(workspace: DecompileWorkspace): FileNode.Folder
    suspend fun readFileContent(workspace: DecompileWorkspace, filePath: String): String
    suspend fun disassembleDexToSmali(dexPath: String, outputDir: String): FileNode.Folder
    suspend fun decompileDexToJava(dexPath: String, outputDir: String, onProgress: (Float) -> Unit): String
    suspend fun loadDexConstants(dexPath: String): List<StringConstantItem>
    suspend fun saveDexConstants(smaliRootsByDex: Map<String, String>, constants: List<StringConstantItem>)
    suspend fun saveFileContent(workspace: DecompileWorkspace, filePath: String, content: String)
    suspend fun closeWorkspace()
    suspend fun searchSmaliFiles(rootDir: String, query: String, maxResults: Int = 200): List<DexSearchHit>
    suspend fun assembleSmaliToDex(smaliRoot: String, outputDexPath: String)
    suspend fun repackApk(workspace: DecompileWorkspace, outputApkPath: String)
    suspend fun signApk(unsignedApkPath: String, signedApkPath: String, adbPath: String, keystorePath: String)
    suspend fun decompileSmaliToJava(smaliFilePath: String, requiresReassembly: Boolean = false): String
}

expect fun getDecompileService(): DecompileService
