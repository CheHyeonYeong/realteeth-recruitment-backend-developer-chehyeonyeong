package com.realteeth.task.service

import com.realteeth.config.TaskProcessingProperties
import com.realteeth.infrastructure.mockworker.JobStatus
import com.realteeth.infrastructure.mockworker.MockWorkerClient
import com.realteeth.infrastructure.queue.TaskQueueGateway
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
        workPendingTasks()
        refreshProcessingTasks()
    }

    fun tryStart(taskId: Long) {
        val task = imageTaskRepository.findById(taskId).orElse(null) ?: return
        val now = LocalDateTime.now()
        if (!task.canStart(now)) {
            return
        }

        try {
            val response = mockWorkerClient.startProcessing(task.imageUrl)
            task.start(response.jobId, now)
        } catch (exception: Exception) {
            logger.warn("Dispatch error for task ${task.id}: ${exception.message}")
            task.fail(
                errorCode = "MOCK_WORKER_DISPATCH_FAILED",
                errorMessage = exception.message ?: "Dispatch failed",
                now = now,
                retryDelay = taskProcessingProperties.nextRetryDelay(task.retryCount, task.maxRetryCount)
            )
        }

        imageTaskRepository.save(task)
    }

    fun refresh(taskId: Long) {
        val task = imageTaskRepository.findById(taskId).orElse(null) ?: return
        if (task.status != TaskStatus.PROCESSING) {
            return
        }

        val jobId = task.externalJobId
        if (jobId.isNullOrBlank()) {
            task.fail(
                errorCode = "MISSING_EXTERNAL_JOB_ID",
                errorMessage = "Processing task lost its external job id",
                now = LocalDateTime.now(),
                retryDelay = null
            )
            imageTaskRepository.save(task)
            return
        }

        try {
            val remoteStatus = mockWorkerClient.getStatus(jobId)
            when (remoteStatus.status) {
                JobStatus.PROCESSING -> return
                JobStatus.COMPLETED -> task.complete(remoteStatus.result, LocalDateTime.now())
                JobStatus.FAILED -> task.fail(
                    errorCode = "MOCK_WORKER_FAILED",
                    errorMessage = "Mock Worker reported FAILED for job $jobId",
                    now = LocalDateTime.now(),
                    retryDelay = taskProcessingProperties.nextRetryDelay(task.retryCount, task.maxRetryCount)
                )
            }
        } catch (exception: Exception) {
            logger.warn("Polling error for task ${task.id}: ${exception.message}")
            task.fail(
                errorCode = "MOCK_WORKER_POLL_FAILED",
                errorMessage = exception.message ?: "Polling failed",
                now = LocalDateTime.now(),
                retryDelay = taskProcessingProperties.nextRetryDelay(task.retryCount, task.maxRetryCount)
            )
        }

        imageTaskRepository.save(task)
    }

    private fun workPendingTasks() {
        var remaining = taskProcessingProperties.batchSize
        while (remaining > 0) {
            val queuedTaskId = taskQueueGateway.dequeue()
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

    private fun refreshProcessingTasks() {
        imageTaskRepository.findByStatusOrderByUpdatedAtAsc(TaskStatus.PROCESSING)
            .take(taskProcessingProperties.batchSize)
            .forEach { refresh(it.id) }
    }

    private fun nextPendingTask(): ImageTask? {
        val now = LocalDateTime.now()
        return imageTaskRepository.findByStatusOrderByUpdatedAtAsc(TaskStatus.PENDING)
            .firstOrNull { it.canStart(now) }
    }
}
