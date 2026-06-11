import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
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

tasks.withType<JavaExec>().configureEach {
    val sharedClassesDir = layout.projectDirectory.dir("../shared/build/classes/kotlin/jvm/main")
    val sharedResourcesDir = layout.projectDirectory.dir("../shared/build/processedResources/jvm/main")
    dependsOn(
        ":shared:compileKotlinJvm",
        ":shared:jvmProcessResources",
    )
    doFirst {
        val sharedOutputs = listOf(sharedClassesDir.asFile, sharedResourcesDir.asFile).filter { it.exists() }
        if (sharedOutputs.isEmpty()) return@doFirst

        classpath = files(sharedOutputs) + classpath.filter { file ->
            !(file.name.startsWith("shared-jvm") && file.extension.equals("jar", ignoreCase = true))
        }
    }
}

compose.desktop {
    application {
        mainClass = "fun.abbas.wps_adb.MainKt"

        // checkRuntime / jpackage need a full JDK (jlink + jpackage).
        // IDEA's bundled JBR lacks them; defaulting to the Gradle JVM would fail there.
        val packagingJdkFromToolchain = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        }.map { it.metadata.installationPath.asFile.absolutePath }
        javaHome = providers.gradleProperty("wpsAdbTool.packagingJdk")
            .orElse(providers.environmentVariable("WPS_ADB_PACKAGING_JDK"))
            .orElse(packagingJdkFromToolchain)
            .get()

        buildTypes.release.proguard {
            configurationFiles.from(project.file("proguard-rules.pro"))
        }

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
