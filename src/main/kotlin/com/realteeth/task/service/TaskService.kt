package com.realteeth.task.service

import com.realteeth.infrastructure.queue.TaskQueueGateway
import com.realteeth.infrastructure.queue.TaskQueueAccessException
import com.realteeth.task.dto.CreateTaskRequest
import com.realteeth.task.dto.CreateTaskResponse
import com.realteeth.task.dto.TaskResponse
import com.realteeth.task.entity.ImageTask
import com.realteeth.task.entity.TaskStatus
import com.realteeth.task.repository.ImageTaskRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

@Service
class TaskService(
    private val imageTaskRepository: ImageTaskRepository,
    private val taskQueueGateway: TaskQueueGateway
) {
    private val logger = LoggerFactory.getLogger(javaClass)

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
                created = false,
                message = "Task already exists"
            )
        }

        val task = ImageTask(
            idempotencyKey = idempotencyKey,
            imageUrl = normalizedImageUrl
        )
        val savedTask = try {
            imageTaskRepository.saveAndFlush(task)
        } catch (exception: DataIntegrityViolationException) {
            val duplicatedTask = imageTaskRepository.findByIdempotencyKey(idempotencyKey).orElse(null)
                ?: throw exception

            return CreateTaskResponse(
                id = duplicatedTask.id,
                status = duplicatedTask.status,
                created = false,
                message = "Task already exists"
            )
        }

        try {
            taskQueueGateway.enqueue(savedTask.id)
        } catch (exception: TaskQueueAccessException) {
            logger.warn(
                "Queue unavailable while enqueuing task {}. It will be recovered from DB polling.",
                savedTask.id,
                exception
            )
        }

        return CreateTaskResponse(
            id = savedTask.id,
            status = savedTask.status,
            created = true,
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
