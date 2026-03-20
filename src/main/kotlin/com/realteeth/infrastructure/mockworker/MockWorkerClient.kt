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

/**
 * 외부 Mock Worker REST API를 감싸는 HTTP 클라이언트다.
 *
 * 이 파일은 "우리 서비스 밖에 있는 서버와 어떻게 통신하는지"를 보여 준다.
 *
 * Kotlin / Spring 초보자 포인트:
 * - `WebClient`는 HTTP 요청을 보내는 도구다.
 * - `bodyToMono(...)`는 비동기 응답 하나를 감싸는 Reactor 타입을 만든다.
 * - 이 프로젝트는 최종적으로 `.block()`해서 동기식처럼 사용한다.
 */
@Component
class MockWorkerClient(
    webClientBuilder: WebClient.Builder,
    private val mockWorkerProperties: MockWorkerProperties
) {
    // base URL을 한 번 정해 두고, 이후 요청마다 path만 붙인다.
    private val webClient = webClientBuilder
        .baseUrl(mockWorkerProperties.baseUrl)
        .build()

    // apiKeyRef는 이미 발급받은 API 키를 메모리에 캐시하기 위한 저장소다.
    // AtomicReference를 써서 스레드 안전하게 읽고 쓸 수 있게 했다.
    private val apiKeyRef = AtomicReference(mockWorkerProperties.apiKey?.takeIf { it.isNotBlank() })

    // API 키 최초 발급 구간만 직렬화하려고 쓰는 락 객체다.
    private val apiKeyLock = Any()

    // 외부 worker에 새 처리 작업을 등록한다.
    fun startProcessing(imageUrl: String): ProcessStartResponse {
        return webClient.post()
            // baseUrl 뒤에 붙는 path다.
            .uri("/process")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-API-KEY", resolveApiKey())
            .bodyValue(ProcessRequest(imageUrl))
            .retrieve()
            .onStatus(HttpStatusCode::isError, ::toMockWorkerException)
            .bodyToMono(ProcessStartResponse::class.java)
            .await("start response", "process request")
    }

    // 이미 발급받은 job id로 현재 처리 상태를 조회한다.
    fun getStatus(jobId: String): ProcessStatusResponse {
        return webClient.get()
            // `{jobId}` 부분은 아래 두 번째 인자로 치환된다.
            .uri("/process/{jobId}", jobId)
            .header("X-API-KEY", resolveApiKey())
            .retrieve()
            .onStatus(HttpStatusCode::isError, ::toMockWorkerException)
            .bodyToMono(ProcessStatusResponse::class.java)
            .await("status response for $jobId", "status request for $jobId")
    }

    private fun resolveApiKey(): String {
        // 이미 메모리에 캐시된 키가 있으면 재사용한다.
        apiKeyRef.get()?.let { return it }

        synchronized(apiKeyLock) {
            // 동시에 여러 요청이 와도 API key 발급 호출은 한 번만 하게 막는다.
            apiKeyRef.get()?.let { return it }

            // 실행 시 env에 API key가 없으면 여기서 외부 서버에 발급 요청을 보낸다.
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

    // 외부 서버가 4xx/5xx를 줄 때, 우리 서비스 내부에서 쓰기 쉬운 예외 형태로 바꾼다.
    private fun toMockWorkerException(response: ClientResponse): Mono<Throwable> {
        return response.bodyToMono(MockWorkerErrorResponse::class.java)
            .map { it.detail }
            .defaultIfEmpty("HTTP ${response.statusCode().value()}")
            .map { IllegalStateException("Mock Worker error: $it") }
    }

    private fun <T> Mono<T>.await(emptyMessage: String, operation: String): T {
        return timeout(mockWorkerProperties.requestTimeout)
            // timeout 예외를 좀 더 읽기 쉬운 도메인 메시지로 바꾼다.
            .onErrorMap(TimeoutException::class.java) {
                IllegalStateException(
                    "Mock Worker $operation timed out after ${mockWorkerProperties.requestTimeout}"
                )
            }
            // Reactor 비동기 타입을 여기서는 실제 값으로 꺼내 쓴다.
            .block()
            ?: throw IllegalStateException("Mock Worker returned an empty $emptyMessage")
    }
}

// API key 발급 요청 본문
data class IssueKeyRequest(
    val candidateName: String,
    val email: String
)

// API key 발급 응답 본문
data class IssueKeyResponse(
    val apiKey: String
)

// 작업 등록 요청 본문
data class ProcessRequest(
    val imageUrl: String
)

// 작업 등록 직후 응답 본문
data class ProcessStartResponse(
    val jobId: String,
    val status: JobStatus,
    val result: String? = null
)

// 작업 상태 조회 응답 본문
data class ProcessStatusResponse(
    val jobId: String,
    val status: JobStatus,
    val result: String?
)

// 외부 서버가 반환하는 에러 JSON
data class MockWorkerErrorResponse(
    val detail: String
)

// 외부 worker가 알려 주는 상태 값
enum class JobStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
