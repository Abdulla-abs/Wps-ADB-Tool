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
    implementation(libs.compose.material3)
    implementation(libs.json)
    implementation(libs.jcefmaven)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

val macSignEnabled = providers.environmentVariable("MACOS_SIGN")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(
        providers.gradleProperty("compose.desktop.mac.sign")
            .map { it.equals("true", ignoreCase = true) }
            .orElse(false),
    )

tasks.withType<JavaExec>().configureEach {
    dependsOn(
        ":shared:jvmMainClasses",
        ":shared:jvmProcessResources",
    )

    // Prefer shared module class dirs over shared-jvm.jar so IDE run picks up edits without repackaging.
    // Must be configured here (not in doFirst) for configuration-cache compatibility.
    val sharedBuildDir = layout.projectDirectory.dir("../shared/build")
    val sharedClassDirs = files(
        sharedBuildDir.dir("classes/kotlin/jvm/main"),
        sharedBuildDir.dir("classes/java/jvmMain"),
        sharedBuildDir.dir("processedResources/jvm/main"),
    )
    classpath = sharedClassDirs + classpath.filter { file ->
        !(file.name.startsWith("shared-jvm") && file.extension.equals("jar", ignoreCase = true))
    }
}

tasks.register<JavaExec>("runRendererSpike") {
    group = "compose desktop"
    description = "Runs the isolated 3D Three.js Renderer Host Spike"
    mainClass.set("fun.abbas.wps_adb.spike.renderer.RendererSpikeMainKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("verifyRendererSpike") {
    group = "verification"
    description = "Runs automated verification of the 3D Three.js Renderer Host Spike"
    mainClass.set("fun.abbas.wps_adb.spike.renderer.RendererSpikeVerifier")
    classpath = sourceSets["main"].runtimeClasspath
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
            modules(
                "java.compiler",
                "java.instrument",
                "java.sql",
                "jdk.unsupported",
                "jdk.httpserver",
            )
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
