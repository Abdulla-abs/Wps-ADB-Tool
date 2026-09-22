package `fun`.abbas.wps_adb.spike.renderer

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.CefInitializationException
import me.friwi.jcefmaven.UnsupportedPlatformException
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefMessageRouter.CefMessageRouterConfig
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.awt.Component
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the embedded local resource server and the JCEF Chromium browser lifecycle.
 */
class CefHostManager(
    val resourceRoot: String = "spike-renderer",
    val scenesRoot: File? = null,
    private val onJsMessage: (String) -> Unit
) {
    private var httpServer: HttpServer? = null
    private var serverPort: Int = 0
    private var cefApp: CefApp? = null
    private var cefClient: CefClient? = null
    private var cefBrowser: CefBrowser? = null
    private val isDisposed = AtomicBoolean(false)

    val serverUrl: String
        get() = "http://127.0.0.1:$serverPort/index.html"

    init {
        startLocalServer()
    }

    private fun startLocalServer() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        serverPort = server.address.port
        val origin = "http://127.0.0.1:$serverPort"
        server.createContext("/", ResourceHttpHandler(resourceRoot, origin))
        server.createContext("/scenes/", ScenesHttpHandler(scenesRoot, origin))
        server.executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "Spike-HttpServer").apply { isDaemon = true }
        }
        server.start()
        httpServer = server
        println("[CefHostManager] Embedded HTTP resource server started on port $serverPort (serving: $resourceRoot, scenes: ${scenesRoot?.name ?: "disabled"})")
    }

    private class ResourceHttpHandler(
        private val resourceRoot: String,
        private val allowedOrigin: String
    ) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val rawPath = exchange.requestURI.path.orEmpty()

                // 1) Reject traversal markers on the raw path before normalize can erase them
                //    (e.g. "/../secret" → normalize → "/secret" would otherwise bypass a post-normalize ".." check).
                if (isUnsafeRawPath(rawPath)) {
                    sendForbidden(exchange, "403 Forbidden: Invalid resource path")
                    return
                }

                // 2) Normalize only after raw-path checks pass
                val normalized = java.net.URI(null, null, rawPath, null).normalize().path
                    ?.trimStart('/')
                    .orEmpty()

                // 3) Post-normalize hardening
                if (normalized.contains("..") || normalized.contains('\\') || normalized.startsWith("/")) {
                    sendForbidden(exchange, "403 Forbidden: Invalid resource path")
                    return
                }

                val subPath = if (normalized.isEmpty()) "index.html" else normalized
                val resourcePath = "$resourceRoot/$subPath"

                // 4) Ensure the resolved classpath resource stays under resourceRoot/
                if (!resourcePath.startsWith("$resourceRoot/") || resourcePath.contains("..")) {
                    sendForbidden(exchange, "403 Forbidden: Path traversal detected")
                    return
                }

                val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
                    ?: ResourceHttpHandler::class.java.classLoader.getResourceAsStream(resourcePath)
                    ?: ResourceHttpHandler::class.java.getResourceAsStream("/$resourcePath")

                if (inputStream == null) {
                    val notFound = "404 Not Found: Resource '$subPath' not found in '$resourceRoot'".toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
                    exchange.responseHeaders.set("Access-Control-Allow-Origin", allowedOrigin)
                    exchange.sendResponseHeaders(404, notFound.size.toLong())
                    exchange.responseBody.use { it.write(notFound) }
                    return
                }

                val mimeType = when {
                    resourcePath.endsWith(".html") -> "text/html; charset=utf-8"
                    resourcePath.endsWith(".js") -> "application/javascript; charset=utf-8"
                    resourcePath.endsWith(".css") -> "text/css; charset=utf-8"
                    resourcePath.endsWith(".glb") -> "model/gltf-binary"
                    resourcePath.endsWith(".json") -> "application/json; charset=utf-8"
                    else -> "application/octet-stream"
                }

                val bytes = inputStream.use { it.readBytes() }
                exchange.responseHeaders.set("Content-Type", mimeType)
                exchange.responseHeaders.set("Access-Control-Allow-Origin", allowedOrigin)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                exchange.close()
            }
        }

        private fun isUnsafeRawPath(rawPath: String): Boolean {
            if (rawPath.contains('\\')) return true
            for (segment in rawPath.split('/')) {
                if (segment == "..") return true
                if (segment.contains('\\')) return true
                // Absolute / drive-letter style segments (e.g. "C:")
                if (segment.length >= 2 && segment[1] == ':') return true
            }
            return false
        }

        private fun sendForbidden(exchange: HttpExchange, message: String) {
            val forbidden = message.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", allowedOrigin)
            exchange.sendResponseHeaders(403, forbidden.size.toLong())
            exchange.responseBody.use { it.write(forbidden) }
        }
    }

    private class ScenesHttpHandler(
        private val scenesRoot: File?,
        private val allowedOrigin: String,
    ) : HttpHandler {
        companion object {
            private const val MAX_GLB_BYTES = 100L * 1024 * 1024 // 100MB limit
        }

        override fun handle(exchange: HttpExchange) {
            try {
                val method = exchange.requestMethod.uppercase()
                if (method != "GET" && method != "HEAD") {
                    exchange.responseHeaders.set("Allow", "GET, HEAD")
                    exchange.sendResponseHeaders(405, -1)
                    return
                }

                if (scenesRoot == null || !scenesRoot.exists() || !scenesRoot.isDirectory) {
                    sendNotFound(exchange)
                    return
                }

                val rawPath = exchange.requestURI.path.orEmpty()
                val subPath = rawPath.removePrefix("/scenes").removePrefix("/").trim()
                if (subPath.isEmpty() || isUnsafePath(subPath)) {
                    sendForbidden(exchange)
                    return
                }

                // Strictly restrict to .glb files only
                if (!subPath.endsWith(".glb", ignoreCase = true)) {
                    sendForbidden(exchange)
                    return
                }

                val canonicalRoot = scenesRoot.canonicalFile
                val candidate = File(canonicalRoot, subPath)
                val canonicalTarget = candidate.canonicalFile

                // Path traversal check
                if (!canonicalTarget.toPath().startsWith(canonicalRoot.toPath())) {
                    sendForbidden(exchange)
                    return
                }

                // Check real path / symlink escapes
                val realPath = try {
                    canonicalTarget.toPath().toRealPath()
                } catch (_: Exception) {
                    null
                }
                if (realPath == null || !realPath.startsWith(canonicalRoot.toPath())) {
                    sendNotFound(exchange)
                    return
                }

                if (!canonicalTarget.exists() || !canonicalTarget.isFile) {
                    sendNotFound(exchange)
                    return
                }

                val fileLength = canonicalTarget.length()
                if (fileLength > MAX_GLB_BYTES) {
                    sendPayloadTooLarge(exchange)
                    return
                }

                exchange.responseHeaders.set("Content-Type", "model/gltf-binary")
                exchange.responseHeaders.set("Access-Control-Allow-Origin", allowedOrigin)

                if (method == "HEAD") {
                    exchange.sendResponseHeaders(200, -1)
                } else {
                    exchange.sendResponseHeaders(200, fileLength)
                    canonicalTarget.inputStream().use { input ->
                        exchange.responseBody.use { output ->
                            input.copyTo(output, bufferSize = 8192)
                        }
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                exchange.close()
            }
        }

        private fun isUnsafePath(path: String): Boolean {
            if (path.contains('\\') || path.contains(':')) return true
            for (seg in path.split('/')) {
                if (seg == ".." || seg == "." || seg.contains('\\') || seg.contains(':')) return true
            }
            return false
        }

        private fun sendForbidden(exchange: HttpExchange) {
            val msg = "403 Forbidden".toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", allowedOrigin)
            exchange.sendResponseHeaders(403, msg.size.toLong())
            exchange.responseBody.use { it.write(msg) }
        }

        private fun sendNotFound(exchange: HttpExchange) {
            val msg = "404 Not Found".toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", allowedOrigin)
            exchange.sendResponseHeaders(404, msg.size.toLong())
            exchange.responseBody.use { it.write(msg) }
        }

        private fun sendPayloadTooLarge(exchange: HttpExchange) {
            val msg = "413 Payload Too Large".toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", allowedOrigin)
            exchange.sendResponseHeaders(413, msg.size.toLong())
            exchange.responseBody.use { it.write(msg) }
        }
    }

    /**
     * Initializes JCEF and creates the initial CefBrowser.
     * Returns the AWT Component to be embedded in SwingPanel.
     */
    @Synchronized
    fun initializeBrowser(): Component {
        check(!isDisposed.get()) { "CefHostManager has already been disposed" }

        if (cefApp == null) {
            println("[CefHostManager] Initializing CefApp via CefAppBuilder...")
            val defaultBundle = `fun`.abbas.wps_adb.data.AppDataPaths.defaultJcefBundleDir()
            val localBundle = File("jcef-bundle")
            val installDir = when {
                defaultBundle.exists() && File(defaultBundle, "build_meta.json").exists() -> defaultBundle
                localBundle.exists() && File(localBundle, "build_meta.json").exists() -> localBundle
                else -> defaultBundle.apply { parentFile?.mkdirs() }
            }
            println("[CefHostManager] Using CEF install directory: ${installDir.absolutePath}")

            val builder = CefAppBuilder()
            builder.setInstallDir(installDir)
            builder.cefSettings.windowless_rendering_enabled = false
            builder.addJcefArgs(
                "--enable-webgl",
                "--enable-gpu-rasterization"
            )
            cefApp = builder.build()
        }

        if (cefClient == null) {
            val client = cefApp!!.createClient()
            val routerConfig = CefMessageRouterConfig("cefQuery", "cefQueryCancel")
            val msgRouter = CefMessageRouter.create(routerConfig)
            msgRouter.addHandler(object : CefMessageRouterHandlerAdapter() {
                override fun onQuery(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    queryId: Long,
                    request: String?,
                    persistent: Boolean,
                    callback: CefQueryCallback?
                ): Boolean {
                    if (request != null) {
                        try {
                            println("[CefHostManager] Received query from JS: $request")
                            onJsMessage(request)
                        } catch (e: Exception) {
                            System.err.println("[CefHostManager] Error in onJsMessage: ${e.message}")
                        }
                    }
                    callback?.success("ok")
                    return true
                }
            }, true)
            client.addMessageRouter(msgRouter)

            client.addDisplayHandler(object : org.cef.handler.CefDisplayHandlerAdapter() {
                override fun onConsoleMessage(
                    browser: CefBrowser?,
                    level: org.cef.CefSettings.LogSeverity?,
                    message: String?,
                    source: String?,
                    line: Int
                ): Boolean {
                    println("[BrowserConsole] $message ($source:$line)")
                    return false
                }
            })

            client.addLoadHandler(object : org.cef.handler.CefLoadHandlerAdapter() {
                override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    println("[CefHostManager] Page loaded: ${browser?.url} (status: $httpStatusCode)")
                }

                override fun onLoadError(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    errorCode: org.cef.handler.CefLoadHandler.ErrorCode?,
                    errorText: String?,
                    failedUrl: String?
                ) {
                    System.err.println("[CefHostManager] Page load error: $errorText ($errorCode) on $failedUrl")
                }
            })

            cefClient = client
        }

        if (cefBrowser != null) {
            cefBrowser!!.close(true)
            cefBrowser = null
        }

        val browser = cefClient!!.createBrowser(serverUrl, false, false)
        cefBrowser = browser
        println("[CefHostManager] Created CefBrowser navigating to $serverUrl")
        return browser.uiComponent
    }

    /**
     * Recreates the CefBrowser without re-initializing CefApp,
     * verifying dispose/recreate stability.
     */
    @Synchronized
    fun recreateBrowser(): Component {
        println("[CefHostManager] Recreating CefBrowser...")
        cefBrowser?.let {
            it.close(true)
            cefBrowser = null
        }
        val browser = cefClient!!.createBrowser(serverUrl, false, false)
        cefBrowser = browser
        return browser.uiComponent
    }

    /**
     * Executes JavaScript in the active CefBrowser (Kotlin -> JS).
     */
    fun executeJavaScript(code: String) {
        val browser = cefBrowser ?: return
        browser.executeJavaScript(code, browser.url, 0)
    }

    /**
     * Shuts down the browser, HTTP server, and resources.
     */
    @Synchronized
    fun dispose() {
        if (isDisposed.compareAndSet(false, true)) {
            println("[CefHostManager] Disposing CefHostManager...")
            try {
                cefBrowser?.close(true)
                cefBrowser = null
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            try {
                cefClient?.dispose()
                cefClient = null
            } catch (t: Throwable) {
                t.printStackTrace()
            }

            try {
                httpServer?.stop(0)
                httpServer = null
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }
}
