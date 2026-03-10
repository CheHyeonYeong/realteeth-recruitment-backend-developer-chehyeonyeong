package com.realteeth.infrastructure.mockworker

import com.realteeth.config.MockWorkerProperties
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.util.concurrent.TimeUnit

class MockWorkerClientTest {
    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getStatus sends api key header`() {
        server.start()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""{"jobId":"job-1","status":"PROCESSING","result":null}""")
        )

        val client = MockWorkerClient(
            WebClient.builder(),
            MockWorkerProperties(
                baseUrl = server.url("/").toString().removeSuffix("/"),
                apiKey = "test-key",
                requestTimeout = Duration.ofSeconds(5)
            )
        )

        val response = client.getStatus("job-1")

        val request = server.takeRequest()
        assertEquals("test-key", request.getHeader("X-API-KEY"))
        assertEquals("/process/job-1", request.path)
        assertEquals(JobStatus.PROCESSING, response.status)
    }

    @Test
    fun `startProcessing fails fast on timeout`() {
        server.start()
        server.enqueue(
            MockResponse()
                .setHeadersDelay(500, TimeUnit.MILLISECONDS)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"jobId":"job-1","status":"PROCESSING"}""")
        )

        val client = MockWorkerClient(
            WebClient.builder(),
            MockWorkerProperties(
                baseUrl = server.url("/").toString().removeSuffix("/"),
                apiKey = "test-key",
                requestTimeout = Duration.ofMillis(100)
            )
        )

        val exception = assertThrows<IllegalStateException> {
            client.startProcessing("https://example.com/image.png")
        }

        assertTrue(exception.message!!.contains("timed out"))
    }

    @Test
    fun `startProcessing parses immediate completed result`() {
        server.start()
        server.enqueue(
            MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""{"jobId":"job-2","status":"COMPLETED","result":"done"}""")
        )

        val client = MockWorkerClient(
            WebClient.builder(),
            MockWorkerProperties(
                baseUrl = server.url("/").toString().removeSuffix("/"),
                apiKey = "test-key",
                requestTimeout = Duration.ofSeconds(5)
            )
        )

        val response = client.startProcessing("https://example.com/image.png")

        assertEquals(JobStatus.COMPLETED, response.status)
        assertEquals("done", response.result)
    }
}
