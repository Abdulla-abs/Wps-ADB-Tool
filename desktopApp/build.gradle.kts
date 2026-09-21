import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.process.ExecOperations
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import javax.inject.Inject

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

interface BundleRendererExecOps {
    @get:Inject
    val execOperations: ExecOperations
}

val bundleRendererRuntime = tasks.register("bundleRendererRuntime") {
    group = "build"
    description = "Compiles renderer-runtime and outputs to build/generated/resources/scene-runtime"

    val runtimeDir = rootProject.file("renderer-runtime")
    val outputDir = layout.buildDirectory.dir("generated/resources/scene-runtime")
    val execOps = project.objects.newInstance(BundleRendererExecOps::class.java).execOperations

    inputs.dir(runtimeDir.resolve("src"))
    inputs.file(runtimeDir.resolve("index.html"))
    inputs.file(runtimeDir.resolve("package.json"))
    inputs.file(runtimeDir.resolve("package-lock.json"))
    inputs.file(runtimeDir.resolve("vite.config.ts"))
    inputs.file(runtimeDir.resolve("tsconfig.json"))
    outputs.dir(outputDir)

    doLast {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val npmCmd = if (isWindows) "npm.cmd" else "npm"
        val viteBin = runtimeDir.resolve(
            if (isWindows) "node_modules/.bin/vite.cmd" else "node_modules/.bin/vite",
        )

        // Fresh checkout / CI: install locked deps before build.
        if (!viteBin.exists()) {
            execOps.exec {
                workingDir = runtimeDir
                commandLine(npmCmd, "ci")
            }
        }

        // Always rebuild when this task runs. Gradle up-to-date checks (inputs/outputs)
        // already skip the whole task when sources are unchanged; do not reuse a stale dist/.
        val distDir = runtimeDir.resolve("dist")
        if (distDir.exists()) {
            distDir.deleteRecursively()
        }
        execOps.exec {
            workingDir = runtimeDir
            commandLine(npmCmd, "run", "build")
        }

        val target = outputDir.get().asFile
        if (target.exists()) {
            target.deleteRecursively()
        }
        target.mkdirs()
        distDir.copyRecursively(target, overwrite = true)
    }
}

sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("generated/resources"))

tasks.named("processResources") {
    dependsOn(bundleRendererRuntime)
}

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
