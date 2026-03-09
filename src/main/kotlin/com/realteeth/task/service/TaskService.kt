package com.realteeth.task.service

import com.realteeth.infrastructure.queue.TaskQueueGateway
import com.realteeth.task.dto.CreateTaskRequest
import com.realteeth.task.dto.CreateTaskResponse
import com.realteeth.task.dto.TaskResponse
import com.realteeth.task.entity.ImageTask
import com.realteeth.task.entity.TaskStatus
import com.realteeth.task.repository.ImageTaskRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

@Service
class TaskService(
    private val imageTaskRepository: ImageTaskRepository,
    private val taskQueueGateway: TaskQueueGateway
) {
    @Transactional
    fun createTask(request: CreateTaskRequest): CreateTaskResponse {
        val normalizedImageUrl = request.imageUrl.trim()
        val idempotencyKey = generateIdempotencyKey(normalizedImageUrl)

        val existingTask = imageTaskRepository.findByIdempotencyKey(idempotencyKey)
        if (existingTask.isPresent) {
            val task = existingTask.get()
            return CreateTaskResponse(
                id = task.id,
                status = task.status,
                message = "Task already exists"
            )
        }

        val task = ImageTask(
            idempotencyKey = idempotencyKey,
            imageUrl = normalizedImageUrl
        )
        val savedTask = try {
            imageTaskRepository.saveAndFlush(task)
        } catch (_: DataIntegrityViolationException) {
            val duplicatedTask = imageTaskRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow { IllegalStateException("Task creation conflicted but existing task was not found") }

            return CreateTaskResponse(
                id = duplicatedTask.id,
                status = duplicatedTask.status,
                message = "Task already exists"
            )
        }

        taskQueueGateway.enqueue(savedTask.id)

        return CreateTaskResponse(
            id = savedTask.id,
            status = savedTask.status,
            message = "Task created successfully"
        )
    }

    @Transactional(readOnly = true)
    fun getTask(id: Long): TaskResponse {
        val task = imageTaskRepository.findById(id)
            .orElseThrow { NoSuchElementException("Task not found: $id") }
        return TaskResponse.from(task)
    }

    @Transactional(readOnly = true)
    fun getAllTasks(): List<TaskResponse> {
        return imageTaskRepository.findAllByOrderByCreatedAtDesc().map { TaskResponse.from(it) }
    }

    @Transactional(readOnly = true)
    fun getTasksByStatus(status: TaskStatus): List<TaskResponse> {
        return imageTaskRepository.findByStatus(status).map { TaskResponse.from(it) }
    }

    private fun generateIdempotencyKey(imageUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(imageUrl.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
