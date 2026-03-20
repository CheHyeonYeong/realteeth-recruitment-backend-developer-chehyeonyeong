package com.realteeth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 외부 Mock Worker 서버에 접속할 때 필요한 설정 값 모음이다.
 *
 * application.yml의 `mock-worker.*` 값을 이 클래스에 주입한다.
 *
 * 즉, base URL / API key / 후보자 정보 / timeout을 코드에서 쉽게 쓰도록 모아 둔 객체다.
 */
@ConfigurationProperties("mock-worker")
data class MockWorkerProperties(
    val baseUrl: String,
    val apiKey: String? = null,
    val candidateName: String = "Realteeth Candidate",
    val candidateEmail: String = "candidate@example.com",
    val requestTimeout: Duration = Duration.ofSeconds(10)
)
