package `fun`.abbas.wps_adb

import `fun`.abbas.wps_adb.data.ApkFileNameParser
import `fun`.abbas.wps_adb.data.ApkMetadataResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ApkFileNameParserTest {
    @Test
    fun extractPackageFromFileName_readsPrefixBeforeVersionSuffix() {
        assertEquals(
            "com.wanpishiky.android",
            ApkFileNameParser.extractPackageFromFileName(
                "com.wanpishiky.android_17____20260610_apk_2__1.apk",
            ),
        )
    }

    @Test
    fun extractPackageFromFileName_readsPrefixBeforeCopySuffix() {
        assertEquals(
            "com.wanpishiky.android",
            ApkFileNameParser.extractPackageFromFileName(
                "com.wanpishiky.android (2).apk",
            ),
        )
    }

    @Test
    fun fromFileName_usesEmbeddedPackageInsteadOfMock() {
        val metadata = ApkMetadataResolver.fromFileName(
            "com.wanpishiky.android_17____20260610_apk_2__1.apk",
        )

        assertEquals("com.wanpishiky.android", metadata.packageName)
        assertFalse(ApkMetadataResolver.isMockPackage(metadata.packageName))
    }

    @Test
    fun extractPackageFromFileName_returnsNullForUnrelatedNames() {
        assertNull(ApkFileNameParser.extractPackageFromFileName("My Demo.apk"))
    }
}
