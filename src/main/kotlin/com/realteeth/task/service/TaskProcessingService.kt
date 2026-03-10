package com.realteeth.task.service

import com.realteeth.config.TaskProcessingProperties
import com.realteeth.infrastructure.mockworker.JobStatus
import com.realteeth.infrastructure.mockworker.MockWorkerClient
import com.realteeth.infrastructure.queue.TaskQueueGateway
import com.realteeth.infrastructure.queue.TaskQueueAccessException
import com.realteeth.task.entity.ImageTask
import com.realteeth.task.entity.TaskStatus
import com.realteeth.task.repository.ImageTaskRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@ConditionalOnProperty(
    name = ["realteeth.tasks.worker-enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class TaskProcessingService(
    private val imageTaskRepository: ImageTaskRepository,
    private val taskQueueGateway: TaskQueueGateway,
    private val mockWorkerClient: MockWorkerClient,
    private val taskProcessingProperties: TaskProcessingProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 2_000L)
    fun workOnce() {
        recoverStalledDispatches()
        workPendingTasks()
        refreshProcessingTasks()
    }

    fun tryStart(taskId: Long) {
        val task = imageTaskRepository.findById(taskId).orElse(null) ?: return
        val claimedAt = LocalDateTime.now()
        if (!task.canDispatch(claimedAt)) {
            return
        }

        task.claimForDispatch(claimedAt)
        imageTaskRepository.saveAndFlush(task)

        try {
            val response = mockWorkerClient.startProcessing(task.imageUrl)
            val now = LocalDateTime.now()
            task.startProcessing(response.jobId, now)
            when (response.status) {
                JobStatus.PROCESSING -> Unit
                JobStatus.COMPLETED -> task.complete(response.result, now)
                JobStatus.FAILED -> task.scheduleRemoteRetry(
                    errorCode = "MOCK_WORKER_FAILED",
                    errorMessage = "Mock Worker reported FAILED for job ${response.jobId}",
                    now = now,
                    retryDelay = taskProcessingProperties.nextRetryDelay(task.retryCount, task.maxRetryCount)
                )
            }
        } catch (exception: Exception) {
            logger.warn("Dispatch error for task ${task.id}: ${exception.message}")
            task.scheduleDispatchRetry(
                errorCode = "MOCK_WORKER_DISPATCH_FAILED",
                errorMessage = exception.message ?: "Dispatch failed",
                now = LocalDateTime.now(),
                retryDelay = taskProcessingProperties.nextRetryDelay(task.retryCount, task.maxRetryCount)
            )
        }

        imageTaskRepository.save(task)
    }

    fun refresh(taskId: Long) {
        val task = imageTaskRepository.findById(taskId).orElse(null) ?: return
        val now = LocalDateTime.now()
        if (!task.canPoll(now)) {
            return
        }

        val jobId = task.externalJobId
        if (jobId.isNullOrBlank()) {
            task.deadLetter(
                errorCode = "MISSING_EXTERNAL_JOB_ID",
                errorMessage = "Processing task lost its external job id",
                now = now
            )
            imageTaskRepository.save(task)
            return
        }

        try {
            val remoteStatus = mockWorkerClient.getStatus(jobId)
            when (remoteStatus.status) {
                JobStatus.PROCESSING -> {
                    task.recordProcessingHeartbeat(LocalDateTime.now())
                    imageTaskRepository.save(task)
                    return
                }
                JobStatus.COMPLETED -> task.complete(remoteStatus.result, LocalDateTime.now())
                JobStatus.FAILED -> task.scheduleRemoteRetry(
                    errorCode = "MOCK_WORKER_FAILED",
                    errorMessage = "Mock Worker reported FAILED for job $jobId",
                    now = LocalDateTime.now(),
                    retryDelay = taskProcessingProperties.nextRetryDelay(task.retryCount, task.maxRetryCount)
                )
            }
        } catch (exception: Exception) {
            logger.warn("Polling error for task ${task.id}: ${exception.message}")
            val retryDelay = taskProcessingProperties.nextRetryDelay(task.retryCount, task.maxRetryCount)
            if (retryDelay != null) {
                task.schedulePollingRetry(
                    errorCode = "MOCK_WORKER_POLL_FAILED",
                    errorMessage = exception.message ?: "Polling failed",
                    now = LocalDateTime.now(),
                    retryDelay = retryDelay
                )
            } else {
                task.deadLetter(
                    errorCode = "MOCK_WORKER_POLL_FAILED",
                    errorMessage = exception.message ?: "Polling failed",
                    now = LocalDateTime.now()
                )
            }
        }

        imageTaskRepository.save(task)
    }

    private fun workPendingTasks() {
        var remaining = taskProcessingProperties.batchSize
        var queueAvailable = true
        while (remaining > 0) {
            val queuedTaskId = if (queueAvailable) {
                try {
                    taskQueueGateway.dequeue()
                } catch (exception: TaskQueueAccessException) {
                    queueAvailable = false
                    logger.warn("Queue unavailable while dequeuing tasks. Falling back to DB scan.", exception)
                    null
                }
            } else {
                null
            }
            if (queuedTaskId != null) {
                tryStart(queuedTaskId)
                remaining--
                continue
            }

            val nextPendingTask = nextPendingTask() ?: break
            tryStart(nextPendingTask.id)
            remaining--
        }
    }

    private fun recoverStalledDispatches() {
        val now = LocalDateTime.now()
        val cutoff = now.minus(taskProcessingProperties.dispatchStaleAfter)
        imageTaskRepository.findByStatusOrderByUpdatedAtAsc(TaskStatus.DISPATCHING)
            .asSequence()
            .filter { dispatchingTask ->
                val updatedAt = dispatchingTask.updatedAt
                updatedAt == null || !updatedAt.isAfter(cutoff)
            }
            .take(taskProcessingProperties.batchSize)
            .forEach { dispatchingTask ->
                dispatchingTask.deadLetter(
                    errorCode = "DISPATCH_RECOVERY_REQUIRED",
                    errorMessage = "Dispatch outcome is unknown after interruption; moved to dead letter",
                    now = now
                )
                imageTaskRepository.save(dispatchingTask)
            }
    }

    private fun refreshProcessingTasks() {
        val now = LocalDateTime.now()
        imageTaskRepository.findByStatusOrderByUpdatedAtAsc(TaskStatus.PROCESSING)
            .asSequence()
            .filter { it.canPoll(now) }
            .take(taskProcessingProperties.batchSize)
            .forEach { refresh(it.id) }
    }

    private fun nextPendingTask(): ImageTask? {
        val now = LocalDateTime.now()
        return imageTaskRepository.findByStatusInOrderByUpdatedAtAsc(
            listOf(TaskStatus.PENDING, TaskStatus.RETRY_SCHEDULED)
        ).firstOrNull { it.canDispatch(now) }
    }
}
