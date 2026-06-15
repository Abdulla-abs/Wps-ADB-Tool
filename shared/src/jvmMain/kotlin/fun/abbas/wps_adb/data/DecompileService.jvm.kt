package `fun`.abbas.wps_adb.data

import `fun`.abbas.wps_adb.model.DecompileWorkspace
import `fun`.abbas.wps_adb.model.FileNode
import `fun`.abbas.wps_adb.model.StringConstantItem
import `fun`.abbas.wps_adb.model.DexSearchHit
import jadx.api.JadxDecompiler
import jadx.api.JadxArgs
import jadx.api.JavaClass
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
import org.jf.smali.Smali
import org.jf.smali.SmaliOptions
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
    private var cachedDexDecompiler: JadxDecompiler? = null
    private var cachedDexDecompilerPath: String? = null

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
            cachedDexDecompiler?.close()
            cachedDexDecompiler = null
            cachedDexDecompilerPath = null
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
            setRootDir(outDir)
            isSkipResources = true
        }
        JadxDecompiler(args).use { decompiler ->
            decompiler.load()
            if (decompiler.classes.isEmpty()) {
                throw IllegalStateException("No classes loaded from DEX: ${dexFile.name}")
            }
            decompiler.save(50) { done, total ->
                onProgress(if (total > 0) done.toFloat() / total else 1f)
            }
        }
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
                    originalValue = entry.key,
                    value = entry.key,
                    referenceCount = entry.value,
                )
            }
            .toList()
    }

    override suspend fun saveDexConstants(
        smaliRootsByDex: Map<String, String>,
        constants: List<StringConstantItem>,
    ) = withContext(Dispatchers.IO) {
        val changes = constants.filter { it.isDirty }
        if (changes.isEmpty()) return@withContext

        for ((dexPath, smaliRoot) in smaliRootsByDex) {
            val dexName = File(dexPath).name
            val scopedChanges = changes.filter { item ->
                item.sourceDex.isEmpty() || item.sourceDex == dexName
            }
            if (scopedChanges.isEmpty()) continue

            val smaliDir = File(smaliRoot)
            if (!smaliDir.exists()) continue

            for (item in scopedChanges) {
                replaceConstStringInSmaliTree(smaliDir, item.originalValue, item.value)
            }
        }
    }

    override suspend fun assembleSmaliToDex(smaliRoot: String, outputDexPath: String) = withContext(Dispatchers.IO) {
        val smaliDir = File(smaliRoot)
        if (!smaliDir.exists()) error("Smali directory not found: $smaliRoot")

        val output = File(outputDexPath)
        output.parentFile?.mkdirs()

        val options = SmaliOptions().apply {
            outputDexFile = output.absolutePath
            apiLevel = 23
        }
        if (!Smali.assemble(options, smaliDir.absolutePath)) {
            error("Smali assembly failed for $smaliRoot")
        }
    }

    override suspend fun repackApk(workspace: DecompileWorkspace, outputApkPath: String) = withContext(Dispatchers.IO) {
        val wsDir = File(workspace.workspacePath).canonicalFile
        val output = File(outputApkPath)
        val entries = wsDir.walkTopDown()
            .filter { it.isFile }
            .filter { file -> !shouldExcludeFromRepack(file, wsDir) }
            .map { file ->
                val entryName = file.relativeTo(wsDir).path.replace('\\', '/')
                ApkEntry(entryName, file.readBytes())
            }
            .toList()
        ApkPacker.repack(entries, output)
    }

    override suspend fun signApk(
        unsignedApkPath: String,
        signedApkPath: String,
        adbPath: String,
        keystorePath: String,
    ) = withContext(Dispatchers.IO) {
        DecompileApkSigner.sign(File(unsignedApkPath), File(signedApkPath), adbPath, File(keystorePath))
    }

    override suspend fun decompileSmaliToJava(
        smaliFilePath: String,
        requiresReassembly: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val smaliFile = File(smaliFilePath)
        if (!smaliFile.exists()) error("Smali file not found: $smaliFilePath")

        val smaliRoot = findSmaliOutputRoot(smaliFile)
        val className = smaliFileToClassName(smaliFile, smaliRoot)
        val sourceDex = File(smaliRoot.absolutePath.removeSuffix("_smali"))

        var tempDex: File? = null
        val dexInput = when {
            !requiresReassembly && sourceDex.exists() -> sourceDex
            else -> {
                tempDex = File.createTempFile("wps-smali2java", ".dex").apply { deleteOnExit() }
                assembleSmaliToDex(smaliRoot.absolutePath, tempDex.absolutePath)
                tempDex
            }
        }

        try {
            decompileSingleClassFromDex(dexInput, className, cacheable = tempDex == null)
        } finally {
            tempDex?.delete()
        }
    }

    override suspend fun saveFileContent(workspace: DecompileWorkspace, filePath: String, content: String) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) error("File not found: $filePath")
        val wsDir = File(workspace.workspacePath).canonicalFile
        val target = file.canonicalFile
        if (!target.path.startsWith(wsDir.path)) {
            error("Refusing to write outside workspace: $filePath")
        }
        file.writeText(content)
    }

    override suspend fun closeWorkspace() = withContext(Dispatchers.IO) {
        mutex.withLock {
            cachedDecompiler?.close()
            cachedDecompiler = null
            cachedWorkspacePath = null
            cachedDexDecompiler?.close()
            cachedDexDecompiler = null
            cachedDexDecompilerPath = null
        }
    }

    override suspend fun searchSmaliFiles(
        rootDir: String,
        query: String,
        maxResults: Int,
    ): List<DexSearchHit> = withContext(Dispatchers.IO) {
        val root = File(rootDir)
        if (!root.exists()) return@withContext emptyList()

        val needle = query.lowercase()
        val results = mutableListOf<DexSearchHit>()

        fun scan(dir: File) {
            if (results.size >= maxResults) return
            dir.listFiles()?.forEach { file ->
                if (results.size >= maxResults) return
                when {
                    file.isDirectory -> scan(file)
                    file.extension.equals("smali", ignoreCase = true) -> {
                        file.readLines().forEachIndexed { index, line ->
                            if (results.size >= maxResults) return@forEachIndexed
                            if (line.lowercase().contains(needle)) {
                                results.add(
                                    DexSearchHit(
                                        filePath = file.absolutePath,
                                        lineContent = line.trim(),
                                        lineNumber = index + 1,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        scan(root)
        results
    }

    private fun unzip(zipFile: File, destDir: File) {
        java.util.zip.ZipFile(zipFile).use { zip ->
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

    private fun shouldExcludeFromRepack(file: File, workspaceRoot: File): Boolean {
        val relative = file.relativeTo(workspaceRoot).path.replace('\\', '/')
        if (ApkMetaInf.isSignatureEntry(relative)) return true
        return relative.split('/').any { segment ->
            segment.endsWith("_smali", ignoreCase = true) || segment.endsWith("_java", ignoreCase = true)
        }
    }

    private fun findSmaliOutputRoot(smaliFile: File): File {
        var current = smaliFile.parentFile
        while (current != null) {
            if (current.name.endsWith("_smali", ignoreCase = true)) return current
            current = current.parentFile
        }
        return smaliFile.parentFile ?: smaliFile
    }

    private fun smaliFileToClassName(smaliFile: File, smaliRoot: File): String {
        val relative = smaliFile.relativeTo(smaliRoot).invariantSeparatorsPath
        return relative.removeSuffix(".smali").replace('/', '.')
    }

    private suspend fun decompileSingleClassFromDex(
        dexFile: File,
        className: String,
        cacheable: Boolean,
    ): String {
        if (cacheable) {
            val decompiler = getDexDecompiler(dexFile)
            return extractJavaSource(decompiler, className)
        }

        return createDexDecompiler(dexFile).use { decompiler ->
            extractJavaSource(decompiler, className)
        }
    }

    private fun extractJavaSource(decompiler: JadxDecompiler, className: String): String {
        val javaClass = findJavaClass(decompiler, className)
            ?: error("Class not found in DEX: $className")
        val code = javaClass.code?.trim().orEmpty()
        if (code.isEmpty()) error("No Java output generated for $className")
        return code
    }

    private suspend fun getDexDecompiler(dexFile: File): JadxDecompiler = mutex.withLock {
        val dexPath = dexFile.absolutePath
        if (cachedDexDecompilerPath == dexPath && cachedDexDecompiler != null) {
            return cachedDexDecompiler!!
        }
        cachedDexDecompiler?.close()
        val decompiler = createDexDecompiler(dexFile)
        cachedDexDecompiler = decompiler
        cachedDexDecompilerPath = dexPath
        decompiler
    }

    private fun createDexDecompiler(dexFile: File): JadxDecompiler {
        val args = JadxArgs().apply {
            inputFiles.add(dexFile)
            isSkipResources = true
        }
        val decompiler = JadxDecompiler(args)
        decompiler.load()
        return decompiler
    }

    private fun findJavaClass(decompiler: JadxDecompiler, className: String): JavaClass? {
        decompiler.searchJavaClassByOrigFullName(className)?.let { return it }
        return decompiler.classes.firstOrNull { cls ->
            cls.fullName == className || cls.name == className.substringAfterLast('.')
        }
    }

    private fun replaceConstStringInSmaliTree(dir: File, oldValue: String, newValue: String) {
        if (oldValue == newValue) return
        val escapedOld = Regex.escape(oldValue)
        val pattern = Regex("""(const-string(?:/jumbo)?\s+\S+,\s+")$escapedOld(")""")
        dir.walkTopDown()
            .filter { it.isFile && it.extension.equals("smali", ignoreCase = true) }
            .forEach { file ->
                val content = file.readText()
                val updated = pattern.replace(content) { match ->
                    "${match.groupValues[1]}${escapeSmaliString(newValue)}${match.groupValues[2]}"
                }
                if (updated != content) {
                    file.writeText(updated)
                }
            }
    }

    private fun escapeSmaliString(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}

actual fun getDecompileService(): DecompileService = JvmDecompileService()
