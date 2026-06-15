package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.JvmDecompileService
import kotlinx.coroutines.runBlocking
import org.jf.smali.Smali
import org.jf.smali.SmaliOptions
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecompileServiceTest {

    @Test
    fun searchSmaliFiles_findsMatchingLines() = runBlocking {
        val service = JvmDecompileService()
        val tempDir = createTempDir(prefix = "smali-search")
        val smaliDir = File(tempDir, "com/example")
        smaliDir.mkdirs()
        File(smaliDir, "MainActivity.smali").writeText(
            """
            .class public Lcom/example/MainActivity;
            .super Ljava/lang/Object;

            .method public test()V
                const-string v0, "unique_search_token"
                return-void
            .end method
            """.trimIndent(),
        )

        val hits = service.searchSmaliFiles(tempDir.absolutePath, "unique_search_token")

        assertEquals(1, hits.size)
        assertTrue(hits.first().filePath.endsWith("MainActivity.smali"))
        assertTrue(hits.first().lineContent.contains("unique_search_token"))
        assertTrue(hits.first().lineNumber > 0)
    }

    @Test
    fun disassembleDexToSmali_and_loadDexConstants_work() = runBlocking {
        val service = JvmDecompileService()
        val tempDir = createTempDir(prefix = "dex-roundtrip")
        val dexFile = assembleHelloDex(tempDir)

        val smaliOut = File(tempDir, "out_smali")
        val tree = service.disassembleDexToSmali(dexFile.absolutePath, smaliOut.absolutePath)

        assertTrue(tree.children.isNotEmpty())
        val smaliFiles = smaliOut.walkTopDown().filter { it.extension == "smali" }.toList()
        assertTrue(smaliFiles.isNotEmpty())

        val constants = service.loadDexConstants(dexFile.absolutePath)
        assertTrue(constants.any { it.value.contains("hello world") })
    }

    @Test
    fun decompileDexToJava_writesJavaSources() = runBlocking {
        val service = JvmDecompileService()
        val tempDir = createTempDir(prefix = "dex-java")
        val dexFile = assembleHelloDex(tempDir)
        val javaOut = File(tempDir, "classes.dex_java")

        service.decompileDexToJava(dexFile.absolutePath, javaOut.absolutePath) { }

        val javaFiles = javaOut.walkTopDown().filter { it.extension == "java" }.toList()
        assertTrue(javaFiles.isNotEmpty(), "Expected .java files under ${javaOut.absolutePath}")
        val helloJava = javaFiles.first { it.name == "Hello.java" }
        assertTrue(helloJava.readText().contains("class Hello"))
    }

    @Test
    fun assembleSmaliToDex_roundtrip() = runBlocking {
        val service = JvmDecompileService()
        val tempDir = createTempDir(prefix = "assemble-dex")
        val dexFile = assembleHelloDex(tempDir)
        val smaliOut = File(tempDir, "roundtrip_smali")
        service.disassembleDexToSmali(dexFile.absolutePath, smaliOut.absolutePath)

        val reassembledDex = File(tempDir, "reassembled.dex")
        service.assembleSmaliToDex(smaliOut.absolutePath, reassembledDex.absolutePath)

        assertTrue(reassembledDex.isFile)
        val constants = service.loadDexConstants(reassembledDex.absolutePath)
        assertTrue(constants.any { it.value.contains("hello world") })
    }

    @Test
    fun saveDexConstants_replacesConstStringInSmali() = runBlocking {
        val service = JvmDecompileService()
        val tempDir = createTempDir(prefix = "const-save")
        val smaliRoot = File(tempDir, "classes.dex_smali")
        smaliRoot.mkdirs()
        File(smaliRoot, "Hello.smali").writeText(
            """
            .class public LHello;
            .super Ljava/lang/Object;
            .method public constructor <init>()V
                .locals 1
                const-string v0, "hello world"
                return-void
            .end method
            """.trimIndent(),
        )

        val constants = listOf(
            `fun`.abbas.wps_adb.model.StringConstantItem(
                index = 1,
                originalValue = "hello world",
                value = "modified text",
                referenceCount = 1,
                sourceDex = "classes.dex",
            ),
        )
        service.saveDexConstants(mapOf("/tmp/classes.dex" to smaliRoot.absolutePath), constants)

        val updated = File(smaliRoot, "Hello.smali").readText()
        assertTrue(updated.contains("modified text"))
        assertTrue(!updated.contains("hello world"))
    }

    @Test
    fun repackApk_createsApkZip() = runBlocking {
        val service = JvmDecompileService()
        val tempDir = createTempDir(prefix = "repack")
        val dexFile = assembleHelloDex(tempDir)
        val apkFile = File(tempDir, "mini.apk")
        ZipOutputStream(apkFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("classes.dex"))
            dexFile.inputStream().use { input -> input.copyTo(zos) }
            zos.closeEntry()
        }

        val workspaceRoot = File(tempDir, "workspaces")
        workspaceRoot.mkdirs()
        val workspace = service.importApk(apkFile.absolutePath, workspaceRoot.absolutePath) { _, _ -> }
        val outputApk = File(tempDir, "repacked.apk")
        service.repackApk(workspace, outputApk.absolutePath)

        assertTrue(outputApk.isFile)
        assertTrue(outputApk.length() > 0)
    }

    @Test
    fun repackApk_stripsOriginalSignatureMetaInf() = runBlocking {
        val service = JvmDecompileService()
        val tempDir = createTempDir(prefix = "repack-sig")
        val dexFile = assembleHelloDex(tempDir)
        val apkFile = File(tempDir, "signed-mini.apk")
        ZipOutputStream(apkFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("classes.dex"))
            dexFile.inputStream().use { input -> input.copyTo(zos) }
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            zos.write("Manifest-Version: 1.0\n".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("META-INF/CERT.SF"))
            zos.write("X-Android-APK-Signed: 2\n".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
            zos.write(byteArrayOf(1, 2, 3))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("META-INF/services/com.example.Service"))
            zos.write("impl\n".toByteArray())
            zos.closeEntry()
        }

        val workspaceRoot = File(tempDir, "workspaces")
        workspaceRoot.mkdirs()
        val workspace = service.importApk(apkFile.absolutePath, workspaceRoot.absolutePath) { _, _ -> }
        val outputApk = File(tempDir, "repacked.apk")
        service.repackApk(workspace, outputApk.absolutePath)

        val metaInfEntries = ZipFile(outputApk).use { zip ->
            zip.entries().asSequence().map { it.name }.filter { it.startsWith("META-INF/") }.sorted().toList()
        }
        assertTrue(metaInfEntries.none { it.endsWith(".SF", ignoreCase = true) })
        assertTrue(metaInfEntries.none { it.endsWith(".RSA", ignoreCase = true) })
        assertTrue(metaInfEntries.none { it.equals("META-INF/MANIFEST.MF", ignoreCase = true) })
        assertTrue(metaInfEntries.any { it.contains("services/") })
    }

    @Test
    fun decompileSmaliToJava_returnsJavaSource() = runBlocking {
        val service = JvmDecompileService()
        val tempDir = createTempDir(prefix = "smali-java")
        val dexFile = assembleHelloDex(tempDir)
        val smaliOut = File(tempDir, "classes.dex_smali")
        service.disassembleDexToSmali(dexFile.absolutePath, smaliOut.absolutePath)
        val smaliFile = smaliOut.walkTopDown().first { it.extension == "smali" }

        val javaSource = service.decompileSmaliToJava(smaliFile.absolutePath, requiresReassembly = false)

        assertTrue(javaSource.contains("class"))
    }

    @Test
    fun importApk_extractsDexIntoWorkspace() = runBlocking {
        val service = JvmDecompileService()
        val tempDir = createTempDir(prefix = "apk-import")
        val dexFile = assembleHelloDex(tempDir)
        val apkFile = File(tempDir, "mini.apk")

        ZipOutputStream(apkFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("classes.dex"))
            dexFile.inputStream().use { input -> input.copyTo(zos) }
            zos.closeEntry()
        }

        val workspaceRoot = File(tempDir, "workspaces")
        workspaceRoot.mkdirs()
        val workspace = service.importApk(apkFile.absolutePath, workspaceRoot.absolutePath) { _, _ -> }
        val tree = service.loadFileTree(workspace)

        val dexNodes = collectFiles(tree).filter { it.extension == "dex" }
        assertEquals(1, dexNodes.size)
        assertEquals("classes.dex", dexNodes.first().name)
    }

    private fun assembleHelloDex(tempDir: File): File {
        val smaliInput = File(tempDir, "smali-input")
        smaliInput.mkdirs()
        File(smaliInput, "Hello.smali").writeText(
            """
            .class public LHello;
            .super Ljava/lang/Object;

            .method public constructor <init>()V
                .locals 1

                const-string v0, "hello world"

                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method
            """.trimIndent(),
        )

        val dexFile = File(tempDir, "classes.dex")
        val options = SmaliOptions().apply {
            outputDexFile = dexFile.absolutePath
            apiLevel = 23
        }
        val assembled = Smali.assemble(options, smaliInput.absolutePath)
        assertTrue(assembled, "Smali assembly failed")
        return dexFile
    }

    private fun collectFiles(folder: `fun`.abbas.wps_adb.model.FileNode.Folder): List<`fun`.abbas.wps_adb.model.FileNode.File> {
        val result = mutableListOf<`fun`.abbas.wps_adb.model.FileNode.File>()
        fun walk(node: `fun`.abbas.wps_adb.model.FileNode) {
            when (node) {
                is `fun`.abbas.wps_adb.model.FileNode.File -> result.add(node)
                is `fun`.abbas.wps_adb.model.FileNode.Folder -> node.children.forEach { walk(it) }
            }
        }
        walk(folder)
        return result
    }

    private fun createTempDir(prefix: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "${prefix}_${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }
}
