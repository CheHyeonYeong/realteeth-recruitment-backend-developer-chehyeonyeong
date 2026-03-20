package com.realteeth.task.dto

import com.realteeth.task.entity.TaskStatus

// 작업 생성 결과를 알려 주는 응답이다.
// created=true면 새로 만든 task, false면 기존 중복 task를 재사용한 것이다.
data class CreateTaskResponse(
    val id: Long,
    val status: TaskStatus,
    val created: Boolean,
    val message: String
)
