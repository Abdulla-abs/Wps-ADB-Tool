package `fun`.abbas.wps_adb.spike.renderer

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CefHostManagerTest {

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun test_sceneRuntime_servesFormalRuntimeIndexHtml() {
        val manager = CefHostManager(resourceRoot = "scene-runtime") { }
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(manager.serverUrl))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("Formal Scene Runtime"), "Expected official scene-runtime HTML content")
        } finally {
            manager.dispose()
        }
    }

    @Test
    fun test_spikeRenderer_servesLegacySpikeIndexHtml() {
        val manager = CefHostManager(resourceRoot = "spike-renderer") { }
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(manager.serverUrl))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("WpsAdbTool 3D Renderer Spike"), "Expected spike-renderer HTML content")
        } finally {
            manager.dispose()
        }
    }

    @Test
    fun test_pathTraversalAttempt_isBlockedWith403Forbidden() {
        val manager = CefHostManager(resourceRoot = "scene-runtime") { }
        try {
            val port = URI.create(manager.serverUrl).port
            // Send a raw request-target so intermediaries cannot normalize away ".." before the handler sees it.
            val statusAndBody = sendRawHttpGet(port, "/../spike-renderer/index.html")
            assertEquals(403, statusAndBody.first)
            assertTrue(statusAndBody.second.contains("403 Forbidden"), "Path traversal must return 403 Forbidden")

            val nested = sendRawHttpGet(port, "/assets/../../secret.txt")
            assertEquals(403, nested.first)
            assertTrue(nested.second.contains("403 Forbidden"), "Nested path traversal must return 403 Forbidden")
        } finally {
            manager.dispose()
        }
    }

    /**
     * Issues a minimal HTTP/1.1 GET with an unnormalized request-target.
     * java.net.http.HttpClient may resolve ".." in the URI before the bytes hit the wire.
     */
    private fun sendRawHttpGet(port: Int, requestTarget: String): Pair<Int, String> {
        java.net.Socket("127.0.0.1", port).use { socket ->
            val writer = socket.getOutputStream().bufferedWriter(Charsets.US_ASCII)
            writer.write("GET $requestTarget HTTP/1.1\r\n")
            writer.write("Host: 127.0.0.1:$port\r\n")
            writer.write("Connection: close\r\n")
            writer.write("\r\n")
            writer.flush()

            val text = socket.getInputStream().bufferedReader(Charsets.UTF_8).readText()
            val statusLine = text.lineSequence().firstOrNull().orEmpty()
            val status = Regex("""HTTP/\d\.\d\s+(\d+)""").find(statusLine)?.groupValues?.get(1)?.toIntOrNull()
                ?: error("Missing status line in response:\n$text")
            val body = text.substringAfter("\r\n\r\n", missingDelimiterValue = text.substringAfter("\n\n"))
            return status to body
        }
    }

    @Test
    fun test_missingResource_returns404WithClearError() {
        val manager = CefHostManager(resourceRoot = "scene-runtime") { }
        try {
            val missingUri = URI.create("http://127.0.0.1:${URI.create(manager.serverUrl).port}/does_not_exist.js")
            val request = HttpRequest.newBuilder()
                .uri(missingUri)
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(404, response.statusCode())
            assertTrue(response.body().contains("404 Not Found"), "Non-existent resource must return 404 Not Found")
        } finally {
            manager.dispose()
        }
    }

    @Test
    fun test_scenesEndpoint_servesGlbAndRejectsNonGlbAndTraversal() {
        val tempDir = java.nio.file.Files.createTempDirectory("scenes_test").toFile()
        try {
            val sceneDir = java.io.File(tempDir, "scene_01").apply { mkdirs() }
            val glbFile = java.io.File(sceneDir, "environment.glb").apply {
                writeBytes("GLB_DUMMY_HEADER_BYTES".toByteArray(Charsets.UTF_8))
            }
            val jsonFile = java.io.File(sceneDir, "scene.json").apply {
                writeText("{\"id\":\"scene_01\"}")
            }

            val manager = CefHostManager(
                resourceRoot = "scene-runtime",
                scenesRoot = tempDir,
            ) { }

            try {
                val port = URI.create(manager.serverUrl).port

                // 1. Valid .glb access -> 200 OK
                val glbUri = URI.create("http://127.0.0.1:$port/scenes/scene_01/environment.glb")
                val glbResp = httpClient.send(
                    HttpRequest.newBuilder().uri(glbUri).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray(),
                )
                assertEquals(200, glbResp.statusCode())
                assertEquals("model/gltf-binary", glbResp.headers().firstValue("Content-Type").orElse(""))
                assertEquals("GLB_DUMMY_HEADER_BYTES", String(glbResp.body(), Charsets.UTF_8))

                // 2. Non-.glb access -> 403 Forbidden
                val jsonUri = URI.create("http://127.0.0.1:$port/scenes/scene_01/scene.json")
                val jsonResp = httpClient.send(
                    HttpRequest.newBuilder().uri(jsonUri).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
                assertEquals(403, jsonResp.statusCode())

                // 3. Traversal attempt -> 403 Forbidden
                val statusAndBody = sendRawHttpGet(port, "/scenes/../secret.glb")
                assertEquals(403, statusAndBody.first)

                // 4. Missing glb -> 404 Not Found
                val missingUri = URI.create("http://127.0.0.1:$port/scenes/scene_01/missing.glb")
                val missingResp = httpClient.send(
                    HttpRequest.newBuilder().uri(missingUri).GET().build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
                assertEquals(404, missingResp.statusCode())

                // 5. HEAD method supported
                val headResp = httpClient.send(
                    HttpRequest.newBuilder().uri(glbUri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding(),
                )
                assertEquals(200, headResp.statusCode())

                // 6. POST method rejected -> 405
                val postResp = httpClient.send(
                    HttpRequest.newBuilder().uri(glbUri).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding(),
                )
                assertEquals(405, postResp.statusCode())
            } finally {
                manager.dispose()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

