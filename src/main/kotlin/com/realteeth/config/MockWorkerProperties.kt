package com.realteeth.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("mock-worker")
data class MockWorkerProperties(
    val baseUrl: String,
    val apiKey: String? = null,
    val candidateName: String = "Realteeth Candidate",
    val candidateEmail: String = "candidate@example.com"
)
