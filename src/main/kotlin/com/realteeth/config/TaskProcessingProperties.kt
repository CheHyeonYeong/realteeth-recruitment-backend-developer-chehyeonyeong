package com.realteeth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("realteeth.tasks")
data class TaskProcessingProperties(
    val workerEnabled: Boolean = true,
    val queueMode: String = "redis",
    val queueName: String = "realteeth:tasks",
    val batchSize: Int = 10,
    val retryDelays: List<Duration> = listOf(
        Duration.ofSeconds(10),
        Duration.ofSeconds(30),
        Duration.ofSeconds(60)
    )
) {
    fun nextRetryDelay(retryCount: Int, maxRetryCount: Int): Duration? {
        if (retryCount >= maxRetryCount) {
            return null
        }
        val index = retryCount.coerceAtMost(retryDelays.lastIndex)
        return retryDelays[index]
    }
}
