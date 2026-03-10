package com.realteeth.task.service

import com.realteeth.config.TaskProcessingProperties
import com.realteeth.infrastructure.mockworker.JobStatus
import com.realteeth.infrastructure.mockworker.MockWorkerClient
import com.realteeth.infrastructure.mockworker.ProcessStartResponse
import com.realteeth.infrastructure.mockworker.ProcessStatusResponse
import com.realteeth.infrastructure.queue.TaskQueueAccessException
import com.realteeth.infrastructure.queue.TaskQueueGateway
import com.realteeth.task.entity.ImageTask
import com.realteeth.task.entity.TaskStatus
import com.realteeth.task.repository.ImageTaskRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import java.util.Optional

class TaskProcessingServiceTest {
    private val imageTaskRepository = mockk<ImageTaskRepository>(relaxed = true)
    private val taskQueueGateway = mockk<TaskQueueGateway>(relaxed = true)
    private val mockWorkerClient = mockk<MockWorkerClient>(relaxed = true)
    private val properties = TaskProcessingProperties(
        workerEnabled = true,
        queueName = "test:tasks",
        batchSize = 5,
        dispatchStaleAfter = Duration.ofSeconds(30),
        retryDelays = listOf(Duration.ofSeconds(10), Duration.ofSeconds(30))
    )
    private val taskProcessingService = TaskProcessingService(
        imageTaskRepository,
        taskQueueGateway,
        mockWorkerClient,
        properties
    )

    @Test
    fun `tryStart persists dispatching before calling worker`() {
        val task = ImageTask(
            id = 1,
            idempotencyKey = "key-1",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PENDING
        )

        every { imageTaskRepository.findById(1) } returns Optional.of(task)
        every { imageTaskRepository.saveAndFlush(task) } returns task
        every { mockWorkerClient.startProcessing(task.imageUrl) } returns ProcessStartResponse(
            jobId = "job-1",
            status = JobStatus.PROCESSING
        )
        every { imageTaskRepository.save(task) } returns task

        taskProcessingService.tryStart(1)

        assertEquals(TaskStatus.PROCESSING, task.status)
        assertEquals("job-1", task.externalJobId)
        verifySequence {
            imageTaskRepository.findById(1)
            imageTaskRepository.saveAndFlush(task)
            mockWorkerClient.startProcessing(task.imageUrl)
            imageTaskRepository.save(task)
        }
    }

    @Test
    fun `tryStart schedules retry when dispatch fails`() {
        val task = ImageTask(
            id = 2,
            idempotencyKey = "key-2",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PENDING,
            retryCount = 0,
            maxRetryCount = 3
        )

        every { imageTaskRepository.findById(2) } returns Optional.of(task)
        every { imageTaskRepository.saveAndFlush(task) } returns task
        every { mockWorkerClient.startProcessing(task.imageUrl) } throws IllegalStateException("worker down")
        every { imageTaskRepository.save(task) } returns task

        taskProcessingService.tryStart(2)

        assertEquals(TaskStatus.RETRY_SCHEDULED, task.status)
        assertEquals(1, task.retryCount)
        assertNotNull(task.nextRetryAt)
        assertEquals("MOCK_WORKER_DISPATCH_FAILED", task.lastErrorCode)
        verify { imageTaskRepository.save(task) }
    }

    @Test
    fun `tryStart completes task when worker already finished in start response`() {
        val task = ImageTask(
            id = 10,
            idempotencyKey = "key-10",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PENDING
        )

        every { imageTaskRepository.findById(10) } returns Optional.of(task)
        every { imageTaskRepository.saveAndFlush(task) } returns task
        every { mockWorkerClient.startProcessing(task.imageUrl) } returns ProcessStartResponse(
            jobId = "job-10",
            status = JobStatus.COMPLETED,
            result = "done"
        )
        every { imageTaskRepository.save(task) } returns task

        taskProcessingService.tryStart(10)

        assertEquals(TaskStatus.COMPLETED, task.status)
        assertEquals("done", task.resultPayload)
        verify { imageTaskRepository.save(task) }
    }

    @Test
    fun `tryStart schedules retry when worker immediately fails in start response`() {
        val task = ImageTask(
            id = 11,
            idempotencyKey = "key-11",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PENDING
        )

        every { imageTaskRepository.findById(11) } returns Optional.of(task)
        every { imageTaskRepository.saveAndFlush(task) } returns task
        every { mockWorkerClient.startProcessing(task.imageUrl) } returns ProcessStartResponse(
            jobId = "job-11",
            status = JobStatus.FAILED
        )
        every { imageTaskRepository.save(task) } returns task

        taskProcessingService.tryStart(11)

        assertEquals(TaskStatus.RETRY_SCHEDULED, task.status)
        assertEquals(1, task.retryCount)
        assertNull(task.externalJobId)
        assertEquals("MOCK_WORKER_FAILED", task.lastErrorCode)
        verify { imageTaskRepository.save(task) }
    }

    @Test
    fun `refresh marks task as completed when worker returns completed`() {
        val task = ImageTask(
            id = 3,
            idempotencyKey = "key-3",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PROCESSING,
            externalJobId = "job-3"
        )

        every { imageTaskRepository.findById(3) } returns Optional.of(task)
        every { mockWorkerClient.getStatus("job-3") } returns ProcessStatusResponse(
            jobId = "job-3",
            status = JobStatus.COMPLETED,
            result = "done"
        )
        every { imageTaskRepository.save(task) } returns task

        taskProcessingService.refresh(3)

        assertEquals(TaskStatus.COMPLETED, task.status)
        assertEquals("done", task.resultPayload)
        verify { imageTaskRepository.save(task) }
    }

    @Test
    fun `refresh marks task failed after retry budget is exhausted`() {
        val task = ImageTask(
            id = 4,
            idempotencyKey = "key-4",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PROCESSING,
            externalJobId = "job-4",
            retryCount = 3,
            maxRetryCount = 3
        )

        every { imageTaskRepository.findById(4) } returns Optional.of(task)
        every { mockWorkerClient.getStatus("job-4") } returns ProcessStatusResponse(
            jobId = "job-4",
            status = JobStatus.FAILED,
            result = null
        )
        every { imageTaskRepository.save(task) } returns task

        taskProcessingService.refresh(4)

        assertEquals(TaskStatus.DEAD_LETTER, task.status)
        assertEquals("MOCK_WORKER_FAILED", task.lastErrorCode)
        verify { imageTaskRepository.save(task) }
    }

    @Test
    fun `refresh records heartbeat when worker is still processing`() {
        val previousUpdatedAt = LocalDateTime.now().minusMinutes(1)
        val task = ImageTask(
            id = 6,
            idempotencyKey = "key-6",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PROCESSING,
            externalJobId = "job-6",
            updatedAt = previousUpdatedAt
        )

        every { imageTaskRepository.findById(6) } returns Optional.of(task)
        every { mockWorkerClient.getStatus("job-6") } returns ProcessStatusResponse(
            jobId = "job-6",
            status = JobStatus.PROCESSING,
            result = null
        )
        every { imageTaskRepository.save(task) } returns task

        taskProcessingService.refresh(6)

        assertEquals(TaskStatus.PROCESSING, task.status)
        assertTrue(requireNotNull(task.updatedAt).isAfter(previousUpdatedAt))
        verify { imageTaskRepository.save(task) }
    }

    @Test
    fun `refresh keeps task processing when polling temporarily fails`() {
        val task = ImageTask(
            id = 7,
            idempotencyKey = "key-7",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PROCESSING,
            externalJobId = "job-7"
        )

        every { imageTaskRepository.findById(7) } returns Optional.of(task)
        every { mockWorkerClient.getStatus("job-7") } throws IllegalStateException("timeout")
        every { imageTaskRepository.save(task) } returns task

        taskProcessingService.refresh(7)

        assertEquals(TaskStatus.PROCESSING, task.status)
        assertEquals("job-7", task.externalJobId)
        assertEquals(1, task.retryCount)
        assertNotNull(task.nextRetryAt)
        assertEquals("MOCK_WORKER_POLL_FAILED", task.lastErrorCode)
    }

    @Test
    fun `refresh dead letters task after polling retry budget is exhausted`() {
        val task = ImageTask(
            id = 8,
            idempotencyKey = "key-8",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PROCESSING,
            externalJobId = "job-8",
            retryCount = 3,
            maxRetryCount = 3
        )

        every { imageTaskRepository.findById(8) } returns Optional.of(task)
        every { mockWorkerClient.getStatus("job-8") } throws IllegalStateException("timeout")
        every { imageTaskRepository.save(task) } returns task

        taskProcessingService.refresh(8)

        assertEquals(TaskStatus.DEAD_LETTER, task.status)
        assertEquals("job-8", task.externalJobId)
        assertEquals("MOCK_WORKER_POLL_FAILED", task.lastErrorCode)
    }

    @Test
    fun `workOnce can pick up retry scheduled task even when queue is empty`() {
        val task = ImageTask(
            id = 5,
            idempotencyKey = "key-5",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.RETRY_SCHEDULED,
            nextRetryAt = LocalDateTime.now().minusSeconds(1)
        )

        every { imageTaskRepository.findByStatusOrderByUpdatedAtAsc(TaskStatus.DISPATCHING) } returns emptyList()
        every { taskQueueGateway.dequeue() } returns null
        every { imageTaskRepository.findByStatusInOrderByUpdatedAtAsc(any()) } returns listOf(task)
        every { imageTaskRepository.findById(5) } returns Optional.of(task)
        every { imageTaskRepository.findByStatusOrderByUpdatedAtAsc(TaskStatus.PROCESSING) } returns emptyList()
        every { imageTaskRepository.saveAndFlush(task) } returns task
        every { mockWorkerClient.startProcessing(task.imageUrl) } returns ProcessStartResponse(
            jobId = "job-5",
            status = JobStatus.PROCESSING
        )
        every { imageTaskRepository.save(task) } returns task

        taskProcessingService.workOnce()

        assertEquals(TaskStatus.PROCESSING, task.status)
        verify { mockWorkerClient.startProcessing(task.imageUrl) }
    }

    @Test
    fun `workOnce falls back to DB scan when queue dequeue fails`() {
        val task = ImageTask(
            id = 13,
            idempotencyKey = "key-13",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PENDING
        )

        every { imageTaskRepository.findByStatusOrderByUpdatedAtAsc(TaskStatus.DISPATCHING) } returns emptyList()
        every { taskQueueGateway.dequeue() } throws TaskQueueAccessException(
            "Failed to dequeue task",
            IllegalStateException("redis down")
        )
        every { imageTaskRepository.findByStatusInOrderByUpdatedAtAsc(any()) } returns listOf(task)
        every { imageTaskRepository.findById(13) } returns Optional.of(task)
        every { imageTaskRepository.findByStatusOrderByUpdatedAtAsc(TaskStatus.PROCESSING) } returns emptyList()
        every { imageTaskRepository.saveAndFlush(task) } returns task
        every { mockWorkerClient.startProcessing(task.imageUrl) } returns ProcessStartResponse(
            jobId = "job-13",
            status = JobStatus.PROCESSING
        )
        every { imageTaskRepository.save(task) } returns task

        taskProcessingService.workOnce()

        assertEquals(TaskStatus.PROCESSING, task.status)
        verify { mockWorkerClient.startProcessing(task.imageUrl) }
    }

    @Test
    fun `workOnce dead letters stale dispatching task`() {
        val task = ImageTask(
            id = 9,
            idempotencyKey = "key-9",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.DISPATCHING,
            updatedAt = LocalDateTime.now().minusMinutes(1)
        )

        every { imageTaskRepository.findByStatusOrderByUpdatedAtAsc(TaskStatus.DISPATCHING) } returns listOf(task)
        every { imageTaskRepository.save(task) } returns task
        every { taskQueueGateway.dequeue() } returns null
        every { imageTaskRepository.findByStatusInOrderByUpdatedAtAsc(any()) } returns emptyList()
        every { imageTaskRepository.findByStatusOrderByUpdatedAtAsc(TaskStatus.PROCESSING) } returns emptyList()

        taskProcessingService.workOnce()

        assertEquals(TaskStatus.DEAD_LETTER, task.status)
        assertEquals("DISPATCH_RECOVERY_REQUIRED", task.lastErrorCode)
        assertNull(task.nextRetryAt)
    }
}
