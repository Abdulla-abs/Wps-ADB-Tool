package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.DeviceKitJsonToXml
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeviceKitJsonToXmlTest {
    @Test
    fun convert_buildsHierarchyXml() {
        val json = """
            {"hierarchy":[{"index":0,"class":"android.widget.FrameLayout","package":"com.example.app","text":"","hint":"","content-desc":"","resource-id":"","checkable":false,"checked":false,"clickable":false,"enabled":true,"focusable":false,"focused":false,"scrollable":false,"long-clickable":false,"password":false,"selected":false,"visible":true,"rect":{"x":0,"y":0,"width":100,"height":200}}]}
        """.trimIndent()
        val xml = DeviceKitJsonToXml.convert(json)
        assertNotNull(xml)
        assertTrue(xml!!.startsWith("<?xml"))
        assertTrue(xml.contains("com.example.app"))
        assertTrue(xml.contains("bounds=\"[0,0][100,200]\""))
    }
}
