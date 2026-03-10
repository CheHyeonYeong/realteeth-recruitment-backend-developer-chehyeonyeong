package com.realteeth.task.entity

enum class TaskStatus {
    PENDING,
    RETRY_SCHEDULED,
    DISPATCHING,
    PROCESSING,
    COMPLETED,
    DEAD_LETTER
}
