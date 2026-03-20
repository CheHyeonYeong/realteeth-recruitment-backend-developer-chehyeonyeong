package com.realteeth.task.dto

import com.realteeth.task.entity.ImageTask
import com.realteeth.task.entity.TaskStatus
import java.time.LocalDateTime

// 저장된 ImageTask 엔티티를 API 응답용으로 펼친 형태다.
// data class는 equals/hashCode/toString 등을 자동 생성해 주는 Kotlin 문법이다.
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
        // companion object는 Java의 static 메서드 비슷하게 생각하면 된다.
        // 엔티티를 그대로 외부에 노출하지 않고, 필요한 값만 응답 DTO로 옮긴다.
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
