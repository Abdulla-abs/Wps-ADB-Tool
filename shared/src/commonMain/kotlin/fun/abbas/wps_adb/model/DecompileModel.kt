package `fun`.abbas.wps_adb.model

import kotlinx.serialization.Serializable

@Serializable
data class DecompileWorkspace(
    val apkPath: String,
    val workspacePath: String,
    val packageName: String,
    val decompileResources: Boolean = false
)

sealed interface FileNode {
    val name: String
    val path: String

    data class Folder(
        override val name: String,
        override val path: String,
        val children: List<FileNode>,
        val isExpanded: Boolean = false
    ) : FileNode

    data class File(
        override val name: String,
        override val path: String,
        val size: Long,
        val extension: String
    ) : FileNode
}

enum class EditorType { XML, SMALI, JAVA }

data class EditorTab(
    val id: String,
    val title: String,
    val filePath: String,
    val initialContent: String,
    var currentContent: String = initialContent,
    val type: EditorType,
    val isDirty: Boolean = false
)

data class DexSearchHit(
    val filePath: String,
    val lineContent: String,
    val lineNumber: Int
)

data class StringConstantItem(
    val index: Int,
    var value: String,
    val referenceCount: Int
)
