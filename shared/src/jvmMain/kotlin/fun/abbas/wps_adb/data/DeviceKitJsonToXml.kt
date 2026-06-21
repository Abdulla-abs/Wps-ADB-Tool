package `fun`.abbas.wps_adb.data

import org.json.JSONArray
import org.json.JSONObject

internal object DeviceKitJsonToXml {
    fun convert(json: String): String? = try {
        val root = JSONObject(json)
        val hierarchy = root.optJSONArray("hierarchy") ?: return null
        buildString {
            append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>")
            append("<hierarchy rotation=\"0\">")
            for (index in 0 until hierarchy.length()) {
                appendNode(hierarchy.optJSONObject(index))
            }
            append("</hierarchy>")
        }
    } catch (_: Exception) {
        null
    }

    private fun StringBuilder.appendNode(node: JSONObject?) {
        if (node == null) return
        val rect = node.optJSONObject("rect")
        val x = rect?.optInt("x") ?: 0
        val y = rect?.optInt("y") ?: 0
        val width = rect?.optInt("width") ?: 0
        val height = rect?.optInt("height") ?: 0
        val bounds = "[$x,$y][${x + width},${y + height}]"
        append("<node")
        appendAttr("index", node.optInt("index").toString())
        appendAttr("text", node.optString("text"))
        appendAttr("resource-id", node.optString("resource-id"))
        appendAttr("class", node.optString("class"))
        appendAttr("package", node.optString("package"))
        appendAttr("content-desc", node.optString("content-desc"))
        appendBoolAttr("checkable", node.optBoolean("checkable"))
        appendBoolAttr("checked", node.optBoolean("checked"))
        appendBoolAttr("clickable", node.optBoolean("clickable"))
        appendBoolAttr("enabled", node.optBoolean("enabled", true))
        appendBoolAttr("focusable", node.optBoolean("focusable"))
        appendBoolAttr("focused", node.optBoolean("focused"))
        appendBoolAttr("scrollable", node.optBoolean("scrollable"))
        appendBoolAttr("long-clickable", node.optBoolean("long-clickable"))
        appendBoolAttr("password", node.optBoolean("password"))
        appendBoolAttr("selected", node.optBoolean("selected"))
        appendAttr("bounds", bounds)
        val children = node.optJSONArray("children")
        if (children == null || children.length() == 0) {
            append(" />")
            return
        }
        append('>')
        for (index in 0 until children.length()) {
            appendNode(children.optJSONObject(index))
        }
        append("</node>")
    }

    private fun StringBuilder.appendAttr(name: String, value: String) {
        append(' ')
        append(name)
        append("=\"")
        append(escapeXml(value))
        append('"')
    }

    private fun StringBuilder.appendBoolAttr(name: String, value: Boolean) {
        appendAttr(name, value.toString())
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }
}
