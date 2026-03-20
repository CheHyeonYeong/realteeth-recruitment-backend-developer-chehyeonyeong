package com.realteeth.task.service

import com.realteeth.infrastructure.queue.TaskQueueGateway
import com.realteeth.infrastructure.queue.TaskQueueAccessException
import com.realteeth.task.dto.CreateTaskRequest
import com.realteeth.task.dto.CreateTaskResponse
import com.realteeth.task.dto.TaskResponse
import com.realteeth.task.entity.ImageTask
import com.realteeth.task.entity.TaskStatus
import com.realteeth.task.repository.ImageTaskRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest

/**
 * 작업 생성과 단순 조회를 담당하는 서비스다.
 *
 * 핵심 책임은 "중복 요청 방지"와 "새 작업 큐 적재"다.
 *
 * 이 파일을 읽을 때 순서는 이렇게 보면 된다.
 * 1. createTask(): 새 작업 생성 흐름
 * 2. getTask() / getAllTasks() / getTasksByStatus(): 조회 흐름
 * 3. generateIdempotencyKey(): 중복 요청 판별 기준
 *
 * Kotlin 초보자 포인트:
 * - `val`은 다시 대입하지 않는 참조다.
 * - `?:` 는 "왼쪽이 null이면 오른쪽을 사용"하는 Elvis 연산자다.
 * - `map { ... }` 는 컬렉션 각 원소를 다른 형태로 바꿀 때 쓴다.
 */
@Service
class TaskService(
    // 생성자에 `private val`로 받은 값은 이 클래스의 필드가 된다.
    // Spring이 실행 시점에 적절한 구현체를 자동으로 넣어 준다.
    private val imageTaskRepository: ImageTaskRepository,
    private val taskQueueGateway: TaskQueueGateway
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // `@Transactional`은 이 메서드 안 DB 작업을 하나의 트랜잭션으로 묶는다.
    @Transactional
    fun createTask(request: CreateTaskRequest): CreateTaskResponse {
        // 사용자가 공백을 넣어도 같은 URL이면 같은 요청으로 보려고 trim() 한다.
        val normalizedImageUrl = request.imageUrl.trim()

        // 이 해시값이 "중복 요청 판별 키" 역할을 한다.
        val idempotencyKey = generateIdempotencyKey(normalizedImageUrl)

        // 같은 이미지 URL이 이미 들어온 적 있으면 기존 작업을 그대로 돌려준다.
        val existingTask = imageTaskRepository.findByIdempotencyKey(idempotencyKey)
        if (existingTask.isPresent) {
            // Java Optional이라 get()으로 실제 값을 꺼낸다.
            val task = existingTask.get()
            return CreateTaskResponse(
                id = task.id,
                status = task.status,
                created = false,
                message = "Task already exists"
            )
        }

        val task = ImageTask(
            idempotencyKey = idempotencyKey,
            imageUrl = normalizedImageUrl
        )
        val savedTask = try {
            // saveAndFlush로 unique constraint 충돌을 바로 드러내게 한다.
            // save()만 쓰면 SQL 실행 시점이 늦어질 수 있어서 경쟁 상태 파악이 늦어질 수 있다.
            imageTaskRepository.saveAndFlush(task)
        } catch (exception: DataIntegrityViolationException) {
            // 동시에 같은 요청이 들어온 레이스 상황을 DB unique key로 막는다.
            // `?: throw exception`은 "중복 task를 못 찾으면 원래 예외를 다시 던진다"는 뜻이다.
            val duplicatedTask = imageTaskRepository.findByIdempotencyKey(idempotencyKey).orElse(null)
                ?: throw exception

            return CreateTaskResponse(
                id = duplicatedTask.id,
                status = duplicatedTask.status,
                created = false,
                message = "Task already exists"
            )
        }

        try {
            // TODO(kafka): Kafka 도입 시 이 줄이 producer 발행 지점이다.
            // 지금은 taskId만 큐에 넣지만, Kafka에서는 taskId / idempotencyKey / createdAt 등을 담은
            // 이벤트 객체를 topic에 publish하는 쪽이 운영상 더 낫다.
            taskQueueGateway.enqueue(savedTask.id)
        } catch (exception: TaskQueueAccessException) {
            // 큐 장애가 나도 DB에는 작업이 있으므로 생성 자체는 성공 처리한다.
            // 즉, 이 시스템에서 source of truth는 queue가 아니라 DB다.
            logger.warn(
                "Queue unavailable while enqueuing task {}. It will be recovered from DB polling.",
                savedTask.id,
                exception
            )
        }

        return CreateTaskResponse(
            id = savedTask.id,
            status = savedTask.status,
            created = true,
            message = "Task created successfully"
        )
    }

    // `readOnly = true`는 "이 메서드는 조회 전용"이라는 의도를 더 분명하게 만든다.
    @Transactional(readOnly = true)
    fun getTask(id: Long): TaskResponse {
        // 없으면 예외를 던지고, 있으면 DTO로 바꿔서 반환한다.
        val task = imageTaskRepository.findById(id)
            .orElseThrow { NoSuchElementException("Task not found: $id") }
        return TaskResponse.from(task)
    }

    @Transactional(readOnly = true)
    fun getAllTasks(): List<TaskResponse> {
        // DB 엔티티 그대로 내보내지 않고 응답 전용 DTO로 변환한다.
        return imageTaskRepository.findAllByOrderByCreatedAtDesc().map { TaskResponse.from(it) }
    }

    @Transactional(readOnly = true)
    fun getTasksByStatus(status: TaskStatus): List<TaskResponse> {
        return imageTaskRepository.findByStatus(status).map { TaskResponse.from(it) }
    }

    // 같은 URL이면 항상 같은 해시를 만들기 위해 SHA-256을 사용한다.
    private fun generateIdempotencyKey(imageUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(imageUrl.toByteArray())
        // 바이트 배열을 사람이 보기 쉬운 16진수 문자열로 바꾼다.
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
