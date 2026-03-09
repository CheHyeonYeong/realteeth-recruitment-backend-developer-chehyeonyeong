package com.realteeth.task.dto

import com.realteeth.task.entity.ImageTask
import com.realteeth.task.entity.TaskStatus
import java.time.LocalDateTime

data class TaskResponse(
    val id: Long,
    val imageUrl: String,
    val status: TaskStatus,
    val externalJobId: String?,
    val result: String?,
    val retryCount: Int,
    val maxRetryCount: Int,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
    val nextRetryAt: LocalDateTime?,
    val startedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(task: ImageTask) = TaskResponse(
            id = task.id,
            imageUrl = task.imageUrl,
            status = task.status,
            externalJobId = task.externalJobId,
            result = task.resultPayload,
            retryCount = task.retryCount,
            maxRetryCount = task.maxRetryCount,
            lastErrorCode = task.lastErrorCode,
            lastErrorMessage = task.lastErrorMessage,
            nextRetryAt = task.nextRetryAt,
            startedAt = task.startedAt,
            completedAt = task.completedAt,
            createdAt = requireNotNull(task.createdAt),
            updatedAt = requireNotNull(task.updatedAt)
        )
    }
}
