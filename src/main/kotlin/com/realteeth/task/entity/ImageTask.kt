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
    fun canDispatch(now: LocalDateTime): Boolean {
        if (status != TaskStatus.PENDING && status != TaskStatus.RETRY_SCHEDULED) {
            return false
        }
        return nextRetryAt?.let { !it.isAfter(now) } ?: true
    }

    fun canPoll(now: LocalDateTime): Boolean {
        if (status != TaskStatus.PROCESSING) {
            return false
        }
        return nextRetryAt?.let { !it.isAfter(now) } ?: true
    }

    fun claimForDispatch(now: LocalDateTime) {
        require(canDispatch(now)) { "Task $id cannot move to DISPATCHING from $status" }
        status = TaskStatus.DISPATCHING
        nextRetryAt = null
        lastErrorCode = null
        lastErrorMessage = null
        completedAt = null
    }

    fun startProcessing(jobId: String, now: LocalDateTime) {
        require(status == TaskStatus.DISPATCHING) { "Task $id cannot move to PROCESSING from $status" }
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

    fun recordProcessingHeartbeat(now: LocalDateTime) {
        require(status == TaskStatus.PROCESSING) { "Task $id cannot stay in PROCESSING from $status" }
        nextRetryAt = null
        lastErrorCode = null
        lastErrorMessage = null
        updatedAt = now
    }

    fun scheduleDispatchRetry(
        errorCode: String,
        errorMessage: String,
        now: LocalDateTime,
        retryDelay: Duration?
    ) {
        require(status == TaskStatus.DISPATCHING) {
            "Task $id cannot schedule dispatch retry from $status"
        }

        lastErrorCode = errorCode
        lastErrorMessage = errorMessage
        resultPayload = null
        externalJobId = null

        if (retryDelay != null) {
            retryCount += 1
            status = TaskStatus.RETRY_SCHEDULED
            nextRetryAt = now.plus(retryDelay)
            completedAt = null
            return
        }

        deadLetter(errorCode, errorMessage, now)
    }

    fun scheduleRemoteRetry(
        errorCode: String,
        errorMessage: String,
        now: LocalDateTime,
        retryDelay: Duration?
    ) {
        require(status == TaskStatus.PROCESSING) {
            "Task $id cannot schedule remote retry from $status"
        }

        lastErrorCode = errorCode
        lastErrorMessage = errorMessage
        resultPayload = null
        externalJobId = null

        if (retryDelay != null) {
            retryCount += 1
            status = TaskStatus.RETRY_SCHEDULED
            nextRetryAt = now.plus(retryDelay)
            completedAt = null
            return
        }

        deadLetter(errorCode, errorMessage, now)
    }

    fun schedulePollingRetry(
        errorCode: String,
        errorMessage: String,
        now: LocalDateTime,
        retryDelay: Duration?
    ) {
        require(status == TaskStatus.PROCESSING) {
            "Task $id cannot schedule polling retry from $status"
        }

        lastErrorCode = errorCode
        lastErrorMessage = errorMessage

        if (retryDelay != null) {
            retryCount += 1
            nextRetryAt = now.plus(retryDelay)
            completedAt = null
            return
        }

        deadLetter(errorCode, errorMessage, now)
    }

    fun deadLetter(
        errorCode: String,
        errorMessage: String,
        now: LocalDateTime
    ) {
        require(
            status == TaskStatus.PENDING ||
                status == TaskStatus.RETRY_SCHEDULED ||
                status == TaskStatus.DISPATCHING ||
                status == TaskStatus.PROCESSING
        ) {
            "Task $id cannot move to DEAD_LETTER from $status"
        }

        lastErrorCode = errorCode
        lastErrorMessage = errorMessage
        status = TaskStatus.DEAD_LETTER
        nextRetryAt = null
        completedAt = now
    }
}
