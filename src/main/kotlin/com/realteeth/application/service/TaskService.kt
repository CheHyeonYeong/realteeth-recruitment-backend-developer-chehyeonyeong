package com.realteeth.application.service

import com.realteeth.application.dto.CreateTaskRequest
import com.realteeth.application.dto.CreateTaskResponse
import com.realteeth.application.dto.TaskResponse
import com.realteeth.domain.entity.ImageTask
import com.realteeth.domain.entity.TaskStatus
import com.realteeth.domain.repository.ImageTaskRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

@Service
class TaskService(
    private val imageTaskRepository: ImageTaskRepository
) {
    @Transactional
    fun createTask(request: CreateTaskRequest): CreateTaskResponse {
        val idempotencyKey = generateIdempotencyKey(request.imageUrl)

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
            imageUrl = request.imageUrl
        )
        val savedTask = imageTaskRepository.save(task)

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
        return imageTaskRepository.findAll().map { TaskResponse.from(it) }
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
