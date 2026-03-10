package com.realteeth.task.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class ImageTaskTest {
    @Test
    fun `claimForDispatch rejects non dispatchable task`() {
        val task = ImageTask(
            id = 1,
            idempotencyKey = "key-1",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PROCESSING
        )

        assertThrows(IllegalArgumentException::class.java) {
            task.claimForDispatch(LocalDateTime.now())
        }
    }

    @Test
    fun `scheduleRemoteRetry moves task to retry scheduled when retry is still available`() {
        val task = ImageTask(
            id = 2,
            idempotencyKey = "key-2",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PROCESSING,
            externalJobId = "job-2"
        )

        task.scheduleRemoteRetry(
            errorCode = "MOCK_WORKER_FAILED",
            errorMessage = "remote worker failed",
            now = LocalDateTime.now(),
            retryDelay = Duration.ofSeconds(10)
        )

        assertEquals(TaskStatus.RETRY_SCHEDULED, task.status)
        assertEquals(1, task.retryCount)
        assertNull(task.externalJobId)
        assertNotNull(task.nextRetryAt)
    }

    @Test
    fun `schedulePollingRetry keeps task processing and preserves external job`() {
        val task = ImageTask(
            id = 3,
            idempotencyKey = "key-3",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PROCESSING,
            externalJobId = "job-3",
            retryCount = 0,
            maxRetryCount = 3
        )

        task.schedulePollingRetry(
            errorCode = "MOCK_WORKER_POLL_FAILED",
            errorMessage = "temporary timeout",
            now = LocalDateTime.now(),
            retryDelay = Duration.ofSeconds(10)
        )

        assertEquals(TaskStatus.PROCESSING, task.status)
        assertEquals("job-3", task.externalJobId)
        assertEquals(1, task.retryCount)
        assertNotNull(task.nextRetryAt)
    }

    @Test
    fun `deadLetter marks task terminal while preserving external job for inspection`() {
        val task = ImageTask(
            id = 4,
            idempotencyKey = "key-4",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PROCESSING,
            externalJobId = "job-4"
        )

        task.deadLetter(
            errorCode = "FINAL_ERROR",
            errorMessage = "cannot recover",
            now = LocalDateTime.now()
        )

        assertEquals(TaskStatus.DEAD_LETTER, task.status)
        assertEquals("job-4", task.externalJobId)
    }
}
