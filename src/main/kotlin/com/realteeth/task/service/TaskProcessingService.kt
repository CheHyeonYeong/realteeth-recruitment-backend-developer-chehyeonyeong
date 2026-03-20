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

/**
 * 주기적으로 돌아가는 비동기 worker다.
 *
 * 한 번 실행될 때마다:
 * 1. 멈춘 DISPATCHING 작업을 복구하고
 * 2. 새 작업을 외부 worker에 보내고
 * 3. 이미 PROCESSING인 작업을 polling한다.
 *
 * TODO(kafka): Kafka를 붙이면 "2. 새 작업을 외부 worker에 보내는 진입점"은
 * 현재의 scheduled dequeue 대신 `@KafkaListener` 같은 consumer entrypoint로 옮길 수 있다.
 * 다만 "1. 복구"와 "3. polling"은 여전히 스케줄러로 남기는 편이 자연스럽다.
 *
 * 이 파일을 읽는 추천 순서:
 * - workOnce()
 * - tryStart()
 * - refresh()
 * - workPendingTasks()
 * - recoverStalledDispatches()
 * - refreshProcessingTasks()
 *
 * 전체 요약:
 * - queue는 "처리 신호"를 주는 장치다.
 * - DB는 "현재 상태를 믿는 기준"이다.
 * - 외부 worker는 실제 처리를 하는 별도 시스템이다.
 */
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

    // `@Scheduled(fixedDelay = 2_000L)`은 메서드가 끝난 뒤 2초 쉬고 다시 실행된다는 뜻이다.
    @Scheduled(fixedDelay = 2_000L)
    fun workOnce() {
        // 복구 -> 새 작업 시작 -> 진행 중 작업 확인 순서로 돈다.
        recoverStalledDispatches()
        workPendingTasks()
        refreshProcessingTasks()
    }

    // task id 하나를 받아 실제 외부 worker 시작까지 밀어 넣는 메서드다.
    fun tryStart(taskId: Long) {
        // `orElse(null) ?: return`은 "없으면 null, null이면 즉시 종료"라는 뜻이다.
        val task = imageTaskRepository.findById(taskId).orElse(null) ?: return
        val claimedAt = LocalDateTime.now()
        if (!task.canDispatch(claimedAt)) {
            // 이미 다른 worker가 선점했거나 아직 재시도 시각 전인 경우다.
            return
        }

        // 외부 API 호출 전에 DISPATCHING을 먼저 저장해 애매한 구간을 남긴다.
        task.claimForDispatch(claimedAt)
        imageTaskRepository.saveAndFlush(task)

        try {
            // 여기서부터는 네트워크를 통해 외부 Mock Worker를 호출한다.
            val response = mockWorkerClient.startProcessing(task.imageUrl)
            val now = LocalDateTime.now()

            // 외부에서 받은 job id를 task에 저장하고 PROCESSING으로 진입시킨다.
            task.startProcessing(response.jobId, now)
            when (response.status) {
                // 아직 진행 중이면 PROCESSING 상태를 유지한다.
                JobStatus.PROCESSING -> Unit
                JobStatus.COMPLETED -> task.complete(response.result, now)
                // 시작 응답에서 바로 실패가 오면 재시도 대기 상태로 보낸다.
                JobStatus.FAILED -> task.scheduleRemoteRetry(
                    errorCode = "MOCK_WORKER_FAILED",
                    errorMessage = "Mock Worker reported FAILED for job ${response.jobId}",
                    now = now,
                    retryDelay = taskProcessingProperties.nextRetryDelay(task.retryCount, task.maxRetryCount)
                )
            }
        } catch (exception: Exception) {
            // 외부 호출 실패는 서버 전체 실패가 아니라 "이 task 한 건의 실패"로 본다.
            logger.warn("Dispatch error for task ${task.id}: ${exception.message}")
            task.scheduleDispatchRetry(
                errorCode = "MOCK_WORKER_DISPATCH_FAILED",
                errorMessage = exception.message ?: "Dispatch failed",
                now = LocalDateTime.now(),
                retryDelay = taskProcessingProperties.nextRetryDelay(task.retryCount, task.maxRetryCount)
            )
        }

        // try-catch 안에서 바뀐 상태를 마지막에 한 번 더 저장한다.
        imageTaskRepository.save(task)
    }

    // 이미 시작된 외부 job의 최신 상태를 다시 조회하는 메서드다.
    fun refresh(taskId: Long) {
        val task = imageTaskRepository.findById(taskId).orElse(null) ?: return
        val now = LocalDateTime.now()
        if (!task.canPoll(now)) {
            // 아직 polling 시각이 안 되었거나 상태가 PROCESSING이 아니면 아무것도 하지 않는다.
            return
        }

        val jobId = task.externalJobId
        if (jobId.isNullOrBlank()) {
            // PROCESSING인데 job id가 없으면 외부 상태를 더는 추적할 수 없다.
            task.deadLetter(
                errorCode = "MISSING_EXTERNAL_JOB_ID",
                errorMessage = "Processing task lost its external job id",
                now = now
            )
            imageTaskRepository.save(task)
            return
        }

        try {
            // 외부 worker에 "이 job 아직 살아 있니?"를 묻는 구간이다.
            val remoteStatus = mockWorkerClient.getStatus(jobId)
            when (remoteStatus.status) {
                JobStatus.PROCESSING -> {
                    // 아직 끝나지 않았으면 heartbeat만 기록하고 다음 주기에 다시 본다.
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
            // polling 실패는 두 가지로 나눈다:
            // 1. 아직 재시도 가능 -> 같은 externalJobId로 다시 조회
            // 2. 한계 초과 -> DEAD_LETTER
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

    // queue에서 새 작업을 꺼내거나, 필요하면 DB를 직접 뒤져서 시작 후보를 찾는다.
    private fun workPendingTasks() {
        var remaining = taskProcessingProperties.batchSize

        // queue에 문제가 생기면 while 루프 안에서 false로 바꿔 DB fallback 모드로 전환한다.
        var queueAvailable = true
        while (remaining > 0) {
            val queuedTaskId = if (queueAvailable) {
                try {
                    // TODO(kafka): Kafka consumer를 쓰면 이 dequeue 루프 대신
                    // listener가 받은 task id로 tryStart(taskId)를 직접 호출하게 된다.
                    taskQueueGateway.dequeue()
                } catch (exception: TaskQueueAccessException) {
                    queueAvailable = false
                    // queue 접근이 깨지면 이번 주기부터는 DB 직접 조회 방식으로 전환한다.
                    logger.warn("Queue unavailable while dequeuing tasks. Falling back to DB scan.", exception)
                    null
                }
            } else {
                null
            }
            if (queuedTaskId != null) {
                // queue에서 꺼낸 task id가 있으면 그걸 우선 처리한다.
                tryStart(queuedTaskId)
                remaining--
                continue
            }

            // queue가 비었거나 장애면 DB에서 아직 처리 가능한 작업을 고른다.
            val nextPendingTask = nextPendingTask() ?: break
            tryStart(nextPendingTask.id)
            remaining--
        }
    }

    // 오래된 DISPATCHING은 "외부 작업 생성 여부가 불확실한 상태"로 보고 보수적으로 dead letter 처리한다.
    private fun recoverStalledDispatches() {
        val now = LocalDateTime.now()
        val cutoff = now.minus(taskProcessingProperties.dispatchStaleAfter)
        imageTaskRepository.findByStatusOrderByUpdatedAtAsc(TaskStatus.DISPATCHING)
            .asSequence()
            // Sequence는 큰 컬렉션을 중간 리스트 없이 순차 처리할 때 유용하다.
            .filter { dispatchingTask ->
                val updatedAt = dispatchingTask.updatedAt
                updatedAt == null || !updatedAt.isAfter(cutoff)
            }
            .take(taskProcessingProperties.batchSize)
            .forEach { dispatchingTask ->
                // 이 상태는 외부 job이 생성됐는지 확신할 수 없으므로 자동 재전송하지 않는다.
                dispatchingTask.deadLetter(
                    errorCode = "DISPATCH_RECOVERY_REQUIRED",
                    errorMessage = "Dispatch outcome is unknown after interruption; moved to dead letter",
                    now = now
                )
                imageTaskRepository.save(dispatchingTask)
            }
    }

    // 이미 PROCESSING인 작업들 중 지금 polling 시각이 된 것만 다시 조회한다.
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
        // 새 작업과 재시도 작업을 함께 보되, 실제 dispatch 가능한 시각인지도 확인한다.
        // TODO(kafka): Kafka를 도입해도 이 DB fallback 조회는 남겨 두는 편이 좋다.
        // 이유는 consumer 중단, rebalance, 중복 소비, 재기동 이후 복구를 DB 기준으로 다시 잡기 위해서다.
        // listOf(...)는 Kotlin에서 읽기 전용 리스트를 만드는 표준 함수다.
        val candidateStatuses = listOf(TaskStatus.PENDING, TaskStatus.RETRY_SCHEDULED)

        return imageTaskRepository.findByStatusInOrderByUpdatedAtAsc(candidateStatuses)
            .firstOrNull { it.canDispatch(now) }
    }
}
