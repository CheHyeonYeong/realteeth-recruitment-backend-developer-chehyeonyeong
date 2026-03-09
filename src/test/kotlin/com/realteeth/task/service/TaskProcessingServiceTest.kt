package com.realteeth.task.service

import com.realteeth.config.TaskProcessingProperties
import com.realteeth.infrastructure.mockworker.JobStatus
import com.realteeth.infrastructure.mockworker.MockWorkerClient
import com.realteeth.infrastructure.mockworker.ProcessStartResponse
import com.realteeth.infrastructure.mockworker.ProcessStatusResponse
import com.realteeth.infrastructure.queue.TaskQueueGateway
import com.realteeth.task.entity.ImageTask
import com.realteeth.task.entity.TaskStatus
import com.realteeth.task.repository.ImageTaskRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Optional

class TaskProcessingServiceTest {
    private val imageTaskRepository = mockk<ImageTaskRepository>(relaxed = true)
    private val taskQueueGateway = mockk<TaskQueueGateway>(relaxed = true)
    private val mockWorkerClient = mockk<MockWorkerClient>(relaxed = true)
    private val properties = TaskProcessingProperties(
        workerEnabled = true,
        queueName = "test:tasks",
        batchSize = 5,
        retryDelays = listOf(Duration.ofSeconds(10), Duration.ofSeconds(30))
    )
    private val taskProcessingService = TaskProcessingService(
        imageTaskRepository,
        taskQueueGateway,
        mockWorkerClient,
        properties
    )

    @Test
    fun `tryStart marks task as processing when dispatch succeeds`() {
        val task = ImageTask(
            id = 1,
            idempotencyKey = "key-1",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PENDING
        )

        every { imageTaskRepository.findById(1) } returns Optional.of(task)
        every { mockWorkerClient.startProcessing(task.imageUrl) } returns ProcessStartResponse(
            jobId = "job-1",
            status = JobStatus.PROCESSING
        )
        every { imageTaskRepository.save(task) } returns task

        taskProcessingService.tryStart(1)

        assertEquals(TaskStatus.PROCESSING, task.status)
        assertEquals("job-1", task.externalJobId)
        verify { imageTaskRepository.save(task) }
    }

    @Test
    fun `tryStart keeps task pending and schedules retry when dispatch fails`() {
        val task = ImageTask(
            id = 2,
            idempotencyKey = "key-2",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PENDING,
            retryCount = 0,
            maxRetryCount = 3
        )

        every { imageTaskRepository.findById(2) } returns Optional.of(task)
        every { mockWorkerClient.startProcessing(task.imageUrl) } throws IllegalStateException("worker down")
        every { imageTaskRepository.save(task) } returns task

        taskProcessingService.tryStart(2)

        assertEquals(TaskStatus.PENDING, task.status)
        assertEquals(1, task.retryCount)
        assertNotNull(task.nextRetryAt)
        assertEquals("MOCK_WORKER_DISPATCH_FAILED", task.lastErrorCode)
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

        assertEquals(TaskStatus.FAILED, task.status)
        assertEquals("MOCK_WORKER_FAILED", task.lastErrorCode)
        verify { imageTaskRepository.save(task) }
    }

    @Test
    fun `workOnce can pick up pending task even when queue is empty`() {
        val task = ImageTask(
            id = 5,
            idempotencyKey = "key-5",
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.PENDING
        )

        every { taskQueueGateway.dequeue() } returns null
        every { imageTaskRepository.findByStatusOrderByUpdatedAtAsc(TaskStatus.PENDING) } returns listOf(task)
        every { imageTaskRepository.findById(5) } returns Optional.of(task)
        every { imageTaskRepository.findByStatusOrderByUpdatedAtAsc(TaskStatus.PROCESSING) } returns emptyList()
        every { mockWorkerClient.startProcessing(task.imageUrl) } returns ProcessStartResponse(
            jobId = "job-5",
            status = JobStatus.PROCESSING
        )
        every { imageTaskRepository.save(task) } returns task

        taskProcessingService.workOnce()

        assertEquals(TaskStatus.PROCESSING, task.status)
        verify { mockWorkerClient.startProcessing(task.imageUrl) }
    }
}
