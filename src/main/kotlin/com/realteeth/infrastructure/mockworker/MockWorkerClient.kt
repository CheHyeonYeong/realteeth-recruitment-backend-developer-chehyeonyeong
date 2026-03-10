package com.realteeth.infrastructure.mockworker

import com.realteeth.config.MockWorkerProperties
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeoutException

@Component
class MockWorkerClient(
    webClientBuilder: WebClient.Builder,
    private val mockWorkerProperties: MockWorkerProperties
) {
    private val webClient = webClientBuilder
        .baseUrl(mockWorkerProperties.baseUrl)
        .build()

    private val apiKeyRef = AtomicReference(mockWorkerProperties.apiKey?.takeIf { it.isNotBlank() })
    private val apiKeyLock = Any()

    fun startProcessing(imageUrl: String): ProcessStartResponse {
        return webClient.post()
            .uri("/process")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-API-KEY", resolveApiKey())
            .bodyValue(ProcessRequest(imageUrl))
            .retrieve()
            .onStatus(HttpStatusCode::isError, ::toMockWorkerException)
            .bodyToMono(ProcessStartResponse::class.java)
            .await("start response", "process request")
    }

    fun getStatus(jobId: String): ProcessStatusResponse {
        return webClient.get()
            .uri("/process/{jobId}", jobId)
            .header("X-API-KEY", resolveApiKey())
            .retrieve()
            .onStatus(HttpStatusCode::isError, ::toMockWorkerException)
            .bodyToMono(ProcessStatusResponse::class.java)
            .await("status response for $jobId", "status request for $jobId")
    }

    private fun resolveApiKey(): String {
        apiKeyRef.get()?.let { return it }

        synchronized(apiKeyLock) {
            apiKeyRef.get()?.let { return it }

            val issuedKey = webClient.post()
                .uri("/auth/issue-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    IssueKeyRequest(
                        candidateName = mockWorkerProperties.candidateName,
                        email = mockWorkerProperties.candidateEmail
                    )
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError, ::toMockWorkerException)
                .bodyToMono(IssueKeyResponse::class.java)
                .await("API key response", "API key issue request")
                .apiKey

            apiKeyRef.set(issuedKey)
            return issuedKey
        }
    }

    private fun toMockWorkerException(response: ClientResponse): Mono<Throwable> {
        return response.bodyToMono(MockWorkerErrorResponse::class.java)
            .map { it.detail }
            .defaultIfEmpty("HTTP ${response.statusCode().value()}")
            .map { IllegalStateException("Mock Worker error: $it") }
    }

    private fun <T> Mono<T>.await(emptyMessage: String, operation: String): T {
        return timeout(mockWorkerProperties.requestTimeout)
            .onErrorMap(TimeoutException::class.java) {
                IllegalStateException(
                    "Mock Worker $operation timed out after ${mockWorkerProperties.requestTimeout}"
                )
            }
            .block()
            ?: throw IllegalStateException("Mock Worker returned an empty $emptyMessage")
    }
}

data class IssueKeyRequest(
    val candidateName: String,
    val email: String
)

data class IssueKeyResponse(
    val apiKey: String
)

data class ProcessRequest(
    val imageUrl: String
)

data class ProcessStartResponse(
    val jobId: String,
    val status: JobStatus,
    val result: String? = null
)

data class ProcessStatusResponse(
    val jobId: String,
    val status: JobStatus,
    val result: String?
)

data class MockWorkerErrorResponse(
    val detail: String
)

enum class JobStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
