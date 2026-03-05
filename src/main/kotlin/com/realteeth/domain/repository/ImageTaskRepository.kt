package com.realteeth.domain.repository

import com.realteeth.domain.entity.ImageTask
import com.realteeth.domain.entity.TaskStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ImageTaskRepository : JpaRepository<ImageTask, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Optional<ImageTask>
    fun findByStatus(status: TaskStatus): List<ImageTask>
    fun findByStatusIn(statuses: List<TaskStatus>): List<ImageTask>
}
