package com.realteeth.infrastructure.mockworker

import com.realteeth.config.MockWorkerProperties
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

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
            .block()
            ?: throw IllegalStateException("Mock Worker returned an empty start response")
    }

    fun getStatus(jobId: String): ProcessStatusResponse {
        return webClient.get()
            .uri("/process/{jobId}", jobId)
            .retrieve()
            .onStatus(HttpStatusCode::isError, ::toMockWorkerException)
            .bodyToMono(ProcessStatusResponse::class.java)
            .block()
            ?: throw IllegalStateException("Mock Worker returned an empty status response for $jobId")
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
                .block()
                ?.apiKey
                ?: throw IllegalStateException("Mock Worker returned an empty API key response")

            apiKeyRef.set(issuedKey)
            return issuedKey
        }
    }

    private fun toMockWorkerException(response: org.springframework.web.reactive.function.client.ClientResponse): Mono<Throwable> {
        return response.bodyToMono(MockWorkerErrorResponse::class.java)
            .map { IllegalStateException("Mock Worker error: ${it.detail}") }
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
    val status: JobStatus
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
