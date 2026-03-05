package com.realteeth.application.dto

import com.realteeth.domain.entity.ImageTask
import com.realteeth.domain.entity.TaskStatus
import java.time.LocalDateTime

data class TaskResponse(
    val id: Long,
    val imageUrl: String,
    val status: TaskStatus,
    val result: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(task: ImageTask) = TaskResponse(
            id = task.id,
            imageUrl = task.imageUrl,
            status = task.status,
            result = task.result,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt
        )
    }
}

data class CreateTaskResponse(
    val id: Long,
    val status: TaskStatus,
    val message: String
)
