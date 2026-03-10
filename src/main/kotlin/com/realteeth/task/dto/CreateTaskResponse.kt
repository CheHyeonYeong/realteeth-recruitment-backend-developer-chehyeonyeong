package com.realteeth.task.dto

import com.realteeth.task.entity.TaskStatus

data class CreateTaskResponse(
    val id: Long,
    val status: TaskStatus,
    val created: Boolean,
    val message: String
)
