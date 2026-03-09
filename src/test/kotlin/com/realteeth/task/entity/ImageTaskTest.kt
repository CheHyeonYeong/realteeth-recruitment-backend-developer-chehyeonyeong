package com.realteeth.task.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

class ImageTaskTest {
    @Test
    fun `start rejects non pending task`() {
        val task = ImageTask(
            id = 1,
            idempotencyKey = "key-1",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PROCESSING
        )

        assertThrows(IllegalArgumentException::class.java) {
            task.start("job-1", LocalDateTime.now())
        }
    }

    @Test
    fun `fail keeps task pending when retry is still available`() {
        val task = ImageTask(
            id = 2,
            idempotencyKey = "key-2",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PENDING
        )

        task.fail(
            errorCode = "TEMPORARY_ERROR",
            errorMessage = "network issue",
            now = LocalDateTime.now(),
            retryDelay = Duration.ofSeconds(10)
        )

        assertEquals(TaskStatus.PENDING, task.status)
        assertEquals(1, task.retryCount)
    }

    @Test
    fun `fail marks task failed when retry budget is gone`() {
        val task = ImageTask(
            id = 3,
            idempotencyKey = "key-3",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PROCESSING,
            externalJobId = "job-3",
            retryCount = 3,
            maxRetryCount = 3
        )

        task.fail(
            errorCode = "FINAL_ERROR",
            errorMessage = "cannot recover",
            now = LocalDateTime.now(),
            retryDelay = null
        )

        assertEquals(TaskStatus.FAILED, task.status)
        assertNull(task.externalJobId)
    }
}
