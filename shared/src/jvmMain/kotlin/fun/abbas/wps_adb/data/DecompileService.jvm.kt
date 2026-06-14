package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.DecompileWorkspace
import `fun`.abbas.wps_adb.model.FileNode
import `fun`.abbas.wps_adb.model.StringConstantItem
import `fun`.abbas.wps_adb.model.DexSearchHit
import jadx.api.JadxDecompiler
import jadx.api.JadxArgs
import org.jf.baksmali.Baksmali
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.instruction.ReferenceInstruction
import org.jf.dexlib2.iface.reference.StringReference
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class JvmDecompileService : DecompileService {
    private val mutex = Mutex()
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var cachedDecompiler: JadxDecompiler? = null
    private var cachedWorkspacePath: String? = null

    private suspend fun getDecompiler(workspace: DecompileWorkspace): JadxDecompiler = mutex.withLock {
        if (cachedWorkspacePath == workspace.workspacePath && cachedDecompiler != null) {
            return cachedDecompiler!!
        }
        cachedDecompiler?.close()
        
        val args = JadxArgs().apply {
            inputFiles.add(File(workspace.apkPath))
            isSkipResources = false
            isSkipSources = true
        }
        val decompiler = JadxDecompiler(args)
        decompiler.load()
        
        cachedDecompiler = decompiler
        cachedWorkspacePath = workspace.workspacePath
        decompiler
    }

    override suspend fun importApk(apkPath: String, workspaceRoot: String, onProgress: (Float, String) -> Unit): DecompileWorkspace = withContext(Dispatchers.IO) {
        // Clear previous decompiler cache
        mutex.withLock {
            cachedDecompiler?.close()
            cachedDecompiler = null
            cachedWorkspacePath = null
        }

        val apkFile = File(apkPath)
        onProgress(0.1f, "解析 APK 信息...")
        val packageName = ApkBinaryManifestParser.parsePackageName(apkFile) ?: "com.unknown.app"
        
        val wsDir = File(workspaceRoot, "${packageName}_${System.currentTimeMillis()}")
        wsDir.mkdirs()
        
        onProgress(0.3f, "正在解压 APK 资源...")
        unzip(apkFile, wsDir)
        
        onProgress(1.0f, "导入完成")
        DecompileWorkspace(
            apkPath = apkPath,
            workspacePath = wsDir.absolutePath,
            packageName = packageName
        )
    }

    override suspend fun loadFileTree(workspace: DecompileWorkspace): FileNode.Folder = withContext(Dispatchers.IO) {
        // Asynchronously pre-load the JADX decompiler in the background
        serviceScope.launch {
            try {
                getDecompiler(workspace)
            } catch (e: Exception) {
                // Ignore background loading errors
            }
        }
        val wsDir = File(workspace.workspacePath)
        scanDirectory(wsDir, wsDir)
    }

    override suspend fun readFileContent(workspace: DecompileWorkspace, filePath: String): String = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext "File not found: $filePath"
        
        val bytes = file.readBytes()
        if (isBinaryXml(bytes)) {
            // 使用 JADX 反编译二进制 XML (通过加载/查询缓存的 decompiler 保证资源解析正确且极速)
            try {
                val relativePath = filePath.substring(workspace.workspacePath.length).replace('\\', '/').trimStart('/')
                val decompiler = getDecompiler(workspace)
                val res = decompiler.resources.find { 
                    it.originalName.replace('\\', '/').trimStart('/') == relativePath
                } ?: decompiler.resources.firstOrNull { 
                    it.originalName.endsWith(file.name) 
                }
                val text = res?.loadContent()?.text?.codeStr ?: ""
                return@withContext text
            } catch (e: Exception) {
                return@withContext "Decompilation failed: ${e.message}\nShowing binary fallback:\n${bytes.decodeToString()}"
            }
        }
        bytes.decodeToString()
    }

    override suspend fun disassembleDexToSmali(dexPath: String, outputDir: String): FileNode.Folder = withContext(Dispatchers.IO) {
        val dexFile = File(dexPath)
        val outDir = File(outputDir)
        outDir.mkdirs()
        
        val dexFileObj = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
        val options = BaksmaliOptions().apply {
            apiLevel = 23
        }
        
        Baksmali.disassembleDexFile(dexFileObj, outDir, 4, options)
        scanDirectory(outDir, outDir)
    }

    override suspend fun decompileDexToJava(dexPath: String, outputDir: String, onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        val dexFile = File(dexPath)
        val outDir = File(outputDir)
        outDir.mkdirs()
        
        val args = JadxArgs().apply {
            inputFiles.add(dexFile)
            this.outDir = outDir
            isSkipResources = true
        }
        val decompiler = JadxDecompiler(args)
        decompiler.load()
        
        val total = decompiler.classes.size
        decompiler.classes.forEachIndexed { index, jadxClass ->
            jadxClass.decompile()
            onProgress((index + 1).toFloat() / total)
        }
        decompiler.save()
        decompiler.close()
        
        outDir.absolutePath
    }

    override suspend fun loadDexConstants(dexPath: String): List<StringConstantItem> = withContext(Dispatchers.IO) {
        val dexFile = File(dexPath)
        if (!dexFile.exists()) return@withContext emptyList()
        
        val dexFileObj = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
        val stringMap = mutableMapOf<String, Int>() // string -> count
        
        dexFileObj.classes.forEach { classDef ->
            classDef.methods.forEach { method ->
                method.implementation?.instructions?.forEach { inst ->
                    if (inst is ReferenceInstruction) {
                        val ref = inst.reference
                        if (ref is StringReference) {
                            val str = ref.string
                            stringMap[str] = (stringMap[str] ?: 0) + 1
                        }
                    }
                }
            }
        }
        
        stringMap.entries.asSequence()
            .sortedByDescending { it.value }
            .take(200) // 限制展示前200个最常用的常量
            .mapIndexed { idx, entry ->
                StringConstantItem(
                    index = idx + 1,
                    value = entry.key,
                    referenceCount = entry.value
                )
            }
            .toList()
    }

    override suspend fun saveDexConstants(dexPath: String, constants: List<StringConstantItem>) {
        // 在实战中，此操作会写回 DEX 文件常数段。由于目前以反编译+Smali编辑重组为主，该接口留空或在 Smali 层面重组。
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val file = File(destDir, entry.name)
                if (entry.name.contains("../") || entry.name.contains("..\\")) {
                    // 路径安全防护，避免 Zip Slip 漏洞
                    return@forEach
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun scanDirectory(dir: File, baseDir: File): FileNode.Folder {
        val children = mutableListOf<FileNode>()
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                children.add(scanDirectory(file, baseDir))
            } else {
                children.add(
                    FileNode.File(
                        name = file.name,
                        path = file.absolutePath,
                        size = file.length(),
                        extension = file.extension
                    )
                )
            }
        }
        // 文件夹优先排序，然后按名称排序
        val sorted = children.sortedWith(compareBy({ it is FileNode.File }, { it.name.lowercase() }))
        return FileNode.Folder(
            name = dir.name,
            path = dir.absolutePath,
            children = sorted,
            isExpanded = dir == baseDir
        )
    }

    private fun isBinaryXml(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val magic = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
        return magic == 0x00080003
    }
}

actual fun getDecompileService(): DecompileService = JvmDecompileService()
