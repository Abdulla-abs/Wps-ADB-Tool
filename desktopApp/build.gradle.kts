import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

val macSignEnabled = providers.environmentVariable("MACOS_SIGN")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(
        providers.gradleProperty("compose.desktop.mac.sign")
            .map { it.equals("true", ignoreCase = true) }
            .orElse(false),
    )

compose.desktop {
    application {
        mainClass = "fun.abbas.wps_adb.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "WpsAdbTool"
            packageVersion = project.findProperty("wpsAdbTool.version")?.toString() ?: "1.0.0"
            description = "WPS ADB device management tool"
            copyright = "© Abbas"

            macOS {
                bundleID = "fun.abbas.wpsadb"
                minimumSystemVersion = "12.0"

                infoPlist {
                    extraKeysRawXml = """
                        <key>ITSAppUsesNonExemptEncryption</key>
                        <false/>
                    """.trimIndent()
                }

                signing {
                    sign.set(macSignEnabled)
                    identity.set(providers.environmentVariable("MACOS_SIGNING_IDENTITY"))
                    providers.environmentVariable("MACOS_KEYCHAIN_PATH").orNull?.let { path ->
                        keychain.set(path)
                    }
                }

                notarization {
                    appleID.set(providers.environmentVariable("NOTARIZATION_APPLE_ID"))
                    password.set(providers.environmentVariable("NOTARIZATION_PASSWORD"))
                    teamID.set(providers.environmentVariable("NOTARIZATION_TEAM_ID"))
                }
            }
        }
    }
}
