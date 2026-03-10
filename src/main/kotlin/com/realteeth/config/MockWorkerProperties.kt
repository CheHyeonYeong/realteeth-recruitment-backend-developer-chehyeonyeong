package com.realteeth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("mock-worker")
data class MockWorkerProperties(
    val baseUrl: String,
    val apiKey: String? = null,
    val candidateName: String = "Realteeth Candidate",
    val candidateEmail: String = "candidate@example.com",
    val requestTimeout: Duration = Duration.ofSeconds(10)
)
