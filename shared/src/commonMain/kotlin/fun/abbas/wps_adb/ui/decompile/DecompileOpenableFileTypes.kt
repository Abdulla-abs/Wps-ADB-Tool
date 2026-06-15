package `fun`.abbas.wps_adb.ui.decompile

import `fun`.abbas.wps_adb.model.EditorType

object DecompileOpenableFileTypes {
    private val BINARY_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "ico",
        "so", "arsc", "apk", "jar", "class", "bin", "dat", "db",
        "ttf", "otf", "woff", "woff2", "mp3", "mp4", "wav", "ogg",
        "zip", "gz", "7z", "rar",
    )

    private val EXTENSION_TO_TYPE = mapOf(
        "xml" to EditorType.XML,
        "smali" to EditorType.SMALI,
        "java" to EditorType.JAVA,
        "txt" to EditorType.TEXT,
        "log" to EditorType.TEXT,
        "cfg" to EditorType.TEXT,
        "ini" to EditorType.TEXT,
        "csv" to EditorType.TEXT,
        "json" to EditorType.JSON,
        "properties" to EditorType.PROPERTIES,
        "prop" to EditorType.PROPERTIES,
        "md" to EditorType.MARKDOWN,
        "markdown" to EditorType.MARKDOWN,
        "yml" to EditorType.YAML,
        "yaml" to EditorType.YAML,
        "html" to EditorType.HTML,
        "htm" to EditorType.HTML,
        "css" to EditorType.CSS,
    )

    fun isBinaryExtension(extension: String): Boolean =
        extension.lowercase() in BINARY_EXTENSIONS

    fun resolveEditorType(extension: String): EditorType? {
        val ext = extension.lowercase()
        if (ext == "dex") return null
        if (isBinaryExtension(ext)) return null
        return EXTENSION_TO_TYPE[ext] ?: EditorType.TEXT
    }

    fun syntaxForType(type: EditorType): String = when (type) {
        EditorType.XML -> "xml"
        EditorType.SMALI -> "smali"
        EditorType.JAVA -> "java"
        EditorType.JSON -> "json"
        EditorType.PROPERTIES -> "properties"
        EditorType.MARKDOWN -> "markdown"
        EditorType.YAML -> "yaml"
        EditorType.HTML -> "html"
        EditorType.CSS -> "css"
        EditorType.TEXT -> "text"
    }
}
