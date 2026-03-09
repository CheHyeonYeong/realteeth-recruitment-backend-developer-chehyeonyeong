package com.realteeth.task.service

import com.realteeth.infrastructure.queue.TaskQueueGateway
import com.realteeth.task.dto.CreateTaskRequest
import com.realteeth.task.entity.ImageTask
import com.realteeth.task.entity.TaskStatus
import com.realteeth.task.repository.ImageTaskRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.util.Optional

class TaskServiceTest {
    private val imageTaskRepository = mockk<ImageTaskRepository>()
    private val taskQueueGateway = mockk<TaskQueueGateway>(relaxed = true)
    private val taskService = TaskService(imageTaskRepository, taskQueueGateway)

    @Test
    fun `createTask returns existing task when idempotency key already exists`() {
        val request = CreateTaskRequest("https://example.com/image.png")
        val existingTask = ImageTask(
            id = 7,
            idempotencyKey = duplicateKey(),
            imageUrl = request.imageUrl,
            status = TaskStatus.PROCESSING
        )

        every { imageTaskRepository.findByIdempotencyKey(any()) } returns Optional.of(existingTask)

        val response = taskService.createTask(request)

        assertEquals(7, response.id)
        assertEquals(TaskStatus.PROCESSING, response.status)
        assertEquals("Task already exists", response.message)
        verify(exactly = 0) { taskQueueGateway.enqueue(any()) }
    }

    @Test
    fun `createTask saves and enqueues new task`() {
        val request = CreateTaskRequest("https://example.com/new-image.png")
        val savedTask = ImageTask(
            id = 11,
            idempotencyKey = "hash",
            imageUrl = request.imageUrl.trim(),
            status = TaskStatus.PENDING
        )

        every { imageTaskRepository.findByIdempotencyKey(any()) } returns Optional.empty()
        every { imageTaskRepository.saveAndFlush(any()) } returns savedTask
        every { taskQueueGateway.enqueue(savedTask.id) } just runs

        val response = taskService.createTask(request)

        assertEquals(11, response.id)
        assertEquals(TaskStatus.PENDING, response.status)
        assertEquals("Task created successfully", response.message)
        verify { taskQueueGateway.enqueue(11) }
    }

    @Test
    fun `createTask returns existing task when concurrent insert hits unique constraint`() {
        val request = CreateTaskRequest("https://example.com/race-image.png")
        val duplicatedTask = ImageTask(
            id = 15,
            idempotencyKey = duplicateKey(),
            imageUrl = request.imageUrl.trim(),
            status = TaskStatus.PENDING
        )

        every { imageTaskRepository.findByIdempotencyKey(any()) } returnsMany listOf(
            Optional.empty(),
            Optional.of(duplicatedTask)
        )
        every { imageTaskRepository.saveAndFlush(any()) } throws DataIntegrityViolationException("duplicate")

        val response = taskService.createTask(request)

        assertEquals(15, response.id)
        assertEquals(TaskStatus.PENDING, response.status)
        assertEquals("Task already exists", response.message)
        verify(exactly = 0) { taskQueueGateway.enqueue(any()) }
    }

    private fun duplicateKey(): String {
        return "d7d79d0b647bca6c776a95f3d09db7c35fc8b8974b9a96d7154b5708fefdfaf2"
    }
}
