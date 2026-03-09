package com.realteeth.task.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Duration
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "image_tasks")
class ImageTask(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 64)
    val idempotencyKey: String,

    @Column(nullable = false, length = 2048)
    val imageUrl: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: TaskStatus = TaskStatus.PENDING,

    @Column(length = 100)
    var externalJobId: String? = null,

    @Column(columnDefinition = "TEXT")
    var resultPayload: String? = null,

    @Column(nullable = false)
    var retryCount: Int = 0,

    @Column(nullable = false)
    var maxRetryCount: Int = 3,

    @Column(length = 100)
    var lastErrorCode: String? = null,

    @Column(columnDefinition = "TEXT")
    var lastErrorMessage: String? = null,

    @Column
    var nextRetryAt: LocalDateTime? = null,

    @Column
    var startedAt: LocalDateTime? = null,

    @Column
    var completedAt: LocalDateTime? = null,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime? = null
) {
    fun canStart(now: LocalDateTime): Boolean {
        if (status != TaskStatus.PENDING) {
            return false
        }
        return nextRetryAt?.let { !it.isAfter(now) } ?: true
    }

    fun start(jobId: String, now: LocalDateTime) {
        require(status == TaskStatus.PENDING) { "Task $id cannot move to PROCESSING from $status" }
        externalJobId = jobId
        status = TaskStatus.PROCESSING
        nextRetryAt = null
        lastErrorCode = null
        lastErrorMessage = null
        if (startedAt == null) {
            startedAt = now
        }
    }

    fun complete(result: String?, now: LocalDateTime) {
        require(status == TaskStatus.PROCESSING) { "Task $id cannot move to COMPLETED from $status" }
        resultPayload = result
        status = TaskStatus.COMPLETED
        lastErrorCode = null
        lastErrorMessage = null
        nextRetryAt = null
        completedAt = now
    }

    fun fail(
        errorCode: String,
        errorMessage: String,
        now: LocalDateTime,
        retryDelay: Duration?
    ) {
        require(status == TaskStatus.PENDING || status == TaskStatus.PROCESSING) {
            "Task $id cannot fail from $status"
        }

        lastErrorCode = errorCode
        lastErrorMessage = errorMessage
        resultPayload = null
        externalJobId = null

        if (retryDelay != null) {
            retryCount += 1
            status = TaskStatus.PENDING
            nextRetryAt = now.plus(retryDelay)
            completedAt = null
            return
        }

        status = TaskStatus.FAILED
        nextRetryAt = null
        completedAt = now
    }
}
