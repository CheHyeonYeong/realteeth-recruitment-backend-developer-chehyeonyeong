package com.realteeth.task.repository

import com.realteeth.task.entity.ImageTask
import com.realteeth.task.entity.TaskStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ImageTaskRepository : JpaRepository<ImageTask, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Optional<ImageTask>
    fun findByStatus(status: TaskStatus): List<ImageTask>
    fun findByStatusOrderByUpdatedAtAsc(status: TaskStatus): List<ImageTask>
    fun findAllByOrderByCreatedAtDesc(): List<ImageTask>
}
