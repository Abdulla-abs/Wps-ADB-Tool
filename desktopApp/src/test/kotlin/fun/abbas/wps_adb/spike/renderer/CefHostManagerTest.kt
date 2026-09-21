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
            val traversalUri = URI.create("http://127.0.0.1:${URI.create(manager.serverUrl).port}/../spike-renderer/index.html")
            val request = HttpRequest.newBuilder()
                .uri(traversalUri)
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            assertEquals(403, response.statusCode())
            assertTrue(response.body().contains("403 Forbidden"), "Path traversal must return 403 Forbidden")
        } finally {
            manager.dispose()
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
}
