package com.realteeth.task.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Duration
import java.time.LocalDateTime

/**
 * 이미지 처리 작업 한 건을 저장하는 엔티티다.
 *
 * 상태 전이 규칙을 이 클래스 안에 모아 둬서,
 * 서비스 레이어가 status 값을 제멋대로 바꾸지 않게 한다.
 *
 * 이 파일은 "DB 테이블 한 줄"이 어떻게 생겼는지와
 * "상태가 어떤 규칙으로 바뀌는지"를 같이 보여 주는 파일이다.
 *
 * Kotlin 초보자 포인트:
 * - `val`: 한 번 넣고 다시 바꾸지 않는 필드
 * - `var`: 상태가 바뀔 수 있는 필드
 * - 생성자 괄호 안에 선언된 `val/var`는 곧바로 클래스 필드가 된다
 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "image_tasks")
class ImageTask(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    // 같은 요청인지 판별하기 위한 멱등 키다.
    @Column(nullable = false, unique = true, length = 64)
    val idempotencyKey: String,

    // 사용자가 실제로 보내온 이미지 URL이다.
    @Column(nullable = false, length = 2048)
    val imageUrl: String,

    // 현재 이 task가 어느 단계에 있는지 나타낸다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: TaskStatus = TaskStatus.PENDING,

    // 외부 Mock Worker가 발급한 job id다. 아직 시작 전이면 null일 수 있다.
    @Column(length = 100)
    var externalJobId: String? = null,

    // 외부 worker의 결과 payload를 저장한다.
    @Column(columnDefinition = "TEXT")
    var resultPayload: String? = null,

    // 지금까지 몇 번 재시도했는지 센다.
    @Column(nullable = false)
    var retryCount: Int = 0,

    // 최대 몇 번까지 재시도할지 저장한다.
    @Column(nullable = false)
    var maxRetryCount: Int = 3,

    // 마지막 실패의 종류를 코드 형태로 남긴다.
    @Column(length = 100)
    var lastErrorCode: String? = null,

    // 마지막 실패 메시지를 좀 더 사람이 읽기 쉽게 남긴다.
    @Column(columnDefinition = "TEXT")
    var lastErrorMessage: String? = null,

    // 재시도나 polling을 "언제 다시 할지"를 의미한다.
    @Column
    var nextRetryAt: LocalDateTime? = null,

    // 실제 외부 처리 시작 시각
    @Column
    var startedAt: LocalDateTime? = null,

    // 성공이든 실패든 종료 시각
    @Column
    var completedAt: LocalDateTime? = null,

    // JPA auditing이 자동으로 넣어 주는 생성 시각
    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    // JPA auditing이 자동으로 넣어 주는 수정 시각
    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime? = null
) {
    // 새 작업 또는 재시도 대기 작업이 지금 외부 worker로 나갈 수 있는지 판단한다.
    fun canDispatch(now: LocalDateTime): Boolean {
        if (status != TaskStatus.PENDING && status != TaskStatus.RETRY_SCHEDULED) {
            return false
        }

        // nextRetryAt이 null이면 바로 가능, 값이 있으면 그 시각이 지났을 때만 가능하다.
        // `?.let { ... } ?: true`는 "null이 아니면 계산하고, null이면 true"라는 뜻이다.
        return nextRetryAt?.let { !it.isAfter(now) } ?: true
    }

    // 현재 작업을 지금 시점에 다시 polling해도 되는지 확인한다.
    fun canPoll(now: LocalDateTime): Boolean {
        if (status != TaskStatus.PROCESSING) {
            return false
        }
        return nextRetryAt?.let { !it.isAfter(now) } ?: true
    }

    // 외부 `/process` 호출 직전 상태를 명시적으로 남긴다.
    fun claimForDispatch(now: LocalDateTime) {
        // require(...)는 조건이 거짓이면 IllegalArgumentException을 던진다.
        // 즉, 허용되지 않은 상태 전이를 코드 수준에서 막는 역할이다.
        require(canDispatch(now)) { "Task $id cannot move to DISPATCHING from $status" }
        status = TaskStatus.DISPATCHING
        nextRetryAt = null
        lastErrorCode = null
        lastErrorMessage = null
        completedAt = null
    }

    // 외부 worker가 job id를 발급하면 PROCESSING으로 진입한다.
    fun startProcessing(jobId: String, now: LocalDateTime) {
        require(status == TaskStatus.DISPATCHING) { "Task $id cannot move to PROCESSING from $status" }
        externalJobId = jobId
        status = TaskStatus.PROCESSING
        nextRetryAt = null
        lastErrorCode = null
        lastErrorMessage = null

        // 최초 시작 시각만 기록하고, 이후 재poll로는 덮어쓰지 않는다.
        if (startedAt == null) {
            startedAt = now
        }
    }

    // 최종 성공 시 결과 payload를 저장하고 COMPLETED로 종료한다.
    fun complete(result: String?, now: LocalDateTime) {
        require(status == TaskStatus.PROCESSING) { "Task $id cannot move to COMPLETED from $status" }
        resultPayload = result
        status = TaskStatus.COMPLETED
        lastErrorCode = null
        lastErrorMessage = null
        nextRetryAt = null
        completedAt = now
    }

    // 아직 처리 중이라는 사실만 확인됐을 때 heartbeat처럼 최신 시각만 갱신한다.
    fun recordProcessingHeartbeat(now: LocalDateTime) {
        require(status == TaskStatus.PROCESSING) { "Task $id cannot stay in PROCESSING from $status" }
        nextRetryAt = null
        lastErrorCode = null
        lastErrorMessage = null

        // 보통 updatedAt은 auditing이 바꾸지만, heartbeat 성격의 갱신을 분명히 드러내려고 직접 넣는다.
        updatedAt = now
    }

    // 외부 작업 시작 자체가 실패했을 때 재시도 예약 또는 dead letter로 보낸다.
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
        // 외부 job이 만들어졌는지 확실하지 않으므로 기존 job id는 비운다.
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

    // 외부 worker가 FAILED를 알려 주면 새 작업으로 다시 보내기 위해 재시도 상태로 돌린다.
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
        // 이미 실패한 외부 job id는 더 이상 추적 가치가 없으므로 제거한다.
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

    // polling만 일시 실패한 경우에는 같은 externalJobId를 유지한 채 나중에 다시 조회한다.
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

    // 자동 복구를 중단하고 수동 확인 대상으로 남기는 최종 실패 상태다.
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

        // DEAD_LETTER도 "종료"이기 때문에 completedAt을 남긴다.
        completedAt = now
    }
}
