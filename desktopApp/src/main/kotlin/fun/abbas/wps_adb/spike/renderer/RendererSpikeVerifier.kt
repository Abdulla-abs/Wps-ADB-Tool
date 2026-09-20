package `fun`.abbas.wps_adb.spike.renderer

import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Automated verification harness for Phase 0 Three.js Renderer Host Spike.
 * Validates all required capabilities:
 * 1. WebGL & GPU Hardware Acceleration
 * 2. Three.js Scene & GLTF Loader Availability
 * 3. JS → Kotlin IPC Messaging
 * 4. Kotlin → JS Command Execution
 * 5. Object Picking & Selection
 * 6. Viewport Resize Event
 * 7. Browser Dispose & Recreate Lifecycle
 */
object RendererSpikeVerifier {

    private val receivedMessages = ConcurrentHashMap<String, JSONObject>()
    private val latches = ConcurrentHashMap<String, CountDownLatch>()

    fun recordMessage(jsonStr: String) {
        try {
            val obj = JSONObject(jsonStr)
            val type = obj.optString("type")
            val payload = obj.optJSONObject("payload") ?: JSONObject()
            receivedMessages[type] = payload
            latches[type]?.countDown()
        } catch (e: Exception) {
            System.err.println("[Verifier] Failed to parse message: $jsonStr - ${e.message}")
        }
    }

    private fun waitFor(type: String, timeoutSec: Long = 10): JSONObject? {
        val latch = CountDownLatch(1)
        latches[type] = latch
        receivedMessages[type]?.let {
            return it
        }
        val completed = latch.await(timeoutSec, TimeUnit.SECONDS)
        return if (completed) receivedMessages[type] else null
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("=================================================================")
        println("  Starting Phase 0 / Three.js Renderer Host Spike Verification   ")
        println("=================================================================")

        var passedCount = 0
        val totalTests = 7

        val hostManager = CefHostManager { msg ->
            recordMessage(msg)
        }

        try {
            println("\n[STEP 1] Initializing JCEF Browser Component...")
            val uiComponent = hostManager.initializeBrowser()
            println("UI Component instantiated: ${uiComponent.javaClass.name}")

            val frame = javax.swing.JFrame("Three.js Spike Verifier Host").apply {
                defaultCloseOperation = javax.swing.JFrame.DISPOSE_ON_CLOSE
                size = java.awt.Dimension(800, 600)
                layout = java.awt.BorderLayout()
                add(uiComponent, java.awt.BorderLayout.CENTER)
                isVisible = true
            }

            // 1. WebGL & Scene Ready
            print("\n[VERIFY 1/7] Waiting for SCENE_READY & WebGL info... ")
            val sceneReady = waitFor("SCENE_READY", 20)
            if (sceneReady != null) {
                val webgl = sceneReady.optJSONObject("webgl")
                val isSupported = webgl?.optBoolean("supported") ?: false
                val vendor = webgl?.optString("vendor") ?: "unknown"
                val renderer = webgl?.optString("renderer") ?: "unknown"
                val version = webgl?.optString("version") ?: "unknown"

                if (isSupported) {
                    println("PASS")
                    println("  -> Vendor: $vendor")
                    println("  -> Renderer: $renderer")
                    println("  -> Version: $version")
                    passedCount++
                } else {
                    println("FAIL (WebGL reports unsupported)")
                }
            } else {
                println("FAIL (Timeout waiting for SCENE_READY)")
            }

            // 2. GLTF Loader
            print("\n[VERIFY 2/7] Checking Three.js GLTFLoader availability... ")
            val gltfInfo = waitFor("GLTF_LOADER_AVAILABLE", 5)
            if (gltfInfo != null && gltfInfo.optBoolean("available")) {
                println("PASS (THREE.GLTFLoader is loaded and functional)")
                passedCount++
            } else {
                println("FAIL (GLTFLoader not available)")
            }

            // 3. Object Picking (JS -> Kotlin)
            print("\n[VERIFY 3/7] Testing Object Picking & Selection (JS → Kotlin)... ")
            hostManager.executeJavaScript("window.wpsRenderer.simulatePick('device_pixel_8');")
            val picked = waitFor("OBJECT_SELECTED", 5)
            if (picked != null && picked.optString("id") == "device_pixel_8") {
                println("PASS")
                println("  -> Picked Object: ${picked.optString("name")} [ID: ${picked.optString("id")}]")
                passedCount++
            } else {
                println("FAIL (Expected device_pixel_8, got: $picked)")
            }

            // 4. Kotlin -> JS State Mutation
            print("\n[VERIFY 4/7] Testing Kotlin → JS Object State Mutation... ")
            hostManager.executeJavaScript("window.wpsRenderer.setObjectColor('device_pixel_8', '#ff00aa');")
            val colorUpdated = waitFor("COLOR_UPDATED", 5)

            hostManager.executeJavaScript("window.wpsRenderer.setDeviceStatus('device_pixel_8', 'offline');")
            val statusUpdated = waitFor("STATUS_UPDATED", 5)

            hostManager.executeJavaScript("window.wpsRenderer.toggleRotation();")
            val rotToggled = waitFor("ROTATION_TOGGLED", 5)

            if (colorUpdated != null && statusUpdated != null && rotToggled != null) {
                println("PASS")
                println("  -> Color updated: ${colorUpdated.optString("hexColor")}")
                println("  -> Status updated: ${statusUpdated.optString("status")}")
                println("  -> Rotation toggled: isRotating=${rotToggled.optBoolean("isRotating")}")
                passedCount++
            } else {
                println("FAIL (Failed state mutations: color=$colorUpdated, status=$statusUpdated, rot=$rotToggled)")
            }

            // 5. Resize Event
            print("\n[VERIFY 5/7] Testing Viewport Resize Interaction... ")
            hostManager.executeJavaScript("window.dispatchEvent(new Event('resize'));")
            val resized = waitFor("SCENE_RESIZED", 5)
            if (resized != null) {
                println("PASS (Resize event handled, viewport: ${resized.optInt("width")}x${resized.optInt("height")})")
                passedCount++
            } else {
                println("FAIL (Timeout waiting for SCENE_RESIZED)")
            }

            // 6. Dispose & Recreate
            print("\n[VERIFY 6/7] Testing Dispose & Recreate Renderer Lifecycle... ")
            receivedMessages.remove("SCENE_READY")
            latches.remove("SCENE_READY")

            val recreatedComp = hostManager.recreateBrowser()
            javax.swing.SwingUtilities.invokeLater {
                frame.getContentPane().removeAll()
                frame.getContentPane().add(recreatedComp, java.awt.BorderLayout.CENTER)
                frame.revalidate()
                frame.repaint()
            }
            println("\n  -> Disposed old CefBrowser and recreated new CefBrowser component: ${recreatedComp.javaClass.name}")
            val recreatedSceneReady = waitFor("SCENE_READY", 15)
            if (recreatedSceneReady != null) {
                println("  -> New browser successfully navigated and re-initialized Three.js scene: PASS")
                passedCount++
            } else {
                println("  -> Failed to re-initialize scene after recreation: FAIL")
            }

            // 7. Clean Teardown
            print("\n[VERIFY 7/7] Testing Host Manager Graceful Disposal... ")
            javax.swing.SwingUtilities.invokeLater {
                frame.dispose()
            }
            hostManager.dispose()
            println("PASS (HostManager disposed cleanly without exceptions)")
            passedCount++

        } catch (t: Throwable) {
            println("\n[ERROR] Uncaught exception during verification:")
            t.printStackTrace()
        }

        println("\n=================================================================")
        println("  Verification Summary: $passedCount / $totalTests Passed")
        println("=================================================================")

        if (passedCount == totalTests) {
            println(">>> SPIKE VERIFICATION SUCCESSFUL <<<")
            exitProcess(0)
        } else {
            println(">>> SPIKE VERIFICATION FAILED <<<")
            exitProcess(1)
        }
    }
}
