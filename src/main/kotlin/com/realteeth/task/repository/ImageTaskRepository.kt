package com.realteeth.task.repository

import com.realteeth.task.entity.ImageTask
import com.realteeth.task.entity.TaskStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

/**
 * DB에서 ImageTask를 읽고 쓰는 인터페이스다.
 *
 * 메서드 이름만으로도 Spring Data JPA가 대부분의 쿼리를 자동 생성한다.
 * 예를 들어 `findByStatusOrderByUpdatedAtAsc`라는 이름만 써도
 * "status로 찾고 updatedAt 오름차순 정렬" 쿼리를 만들어 준다.
 */
interface ImageTaskRepository : JpaRepository<ImageTask, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Optional<ImageTask>
    fun findByStatus(status: TaskStatus): List<ImageTask>
    fun findByStatusInOrderByUpdatedAtAsc(statuses: Collection<TaskStatus>): List<ImageTask>
    fun findByStatusOrderByUpdatedAtAsc(status: TaskStatus): List<ImageTask>
    fun findAllByOrderByCreatedAtDesc(): List<ImageTask>
}
