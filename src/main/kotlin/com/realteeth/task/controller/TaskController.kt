package com.realteeth.task.controller

import com.realteeth.task.dto.CreateTaskRequest
import com.realteeth.task.dto.CreateTaskResponse
import com.realteeth.task.dto.TaskResponse
import com.realteeth.task.entity.TaskStatus
import com.realteeth.task.service.TaskService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 작업 생성 / 조회용 REST API 컨트롤러다.
 *
 * "컨트롤러"는 HTTP 요청을 가장 먼저 받는 진입점이다.
 * 실제 비즈니스 로직은 서비스에 위임하고, 여기서는 요청/응답 형태를 맞추는 역할을 한다.
 */
@RestController
@RequestMapping("/api/tasks")
class TaskController(
    private val taskService: TaskService
) {
    @PostMapping
    fun createTask(@Valid @RequestBody request: CreateTaskRequest): ResponseEntity<CreateTaskResponse> {
        // `@RequestBody`는 JSON 본문을 Kotlin 객체로 바꿔 준다.
        // `@Valid`는 DTO에 붙은 검증 규칙을 실행하게 한다.
        val response = taskService.createTask(request)
        // 새로 만들어졌으면 201, 이미 있던 작업이면 200으로 구분한다.
        val status = if (response.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(response)
    }

    @GetMapping("/{id}")
    fun getTask(@PathVariable id: Long): ResponseEntity<TaskResponse> {
        // `@PathVariable`은 URL 경로의 `{id}` 값을 메서드 인자로 받는다.
        return ResponseEntity.ok(taskService.getTask(id))
    }

    @GetMapping
    fun getAllTasks(
        @RequestParam(required = false) status: TaskStatus?
    ): ResponseEntity<List<TaskResponse>> {
        // status 쿼리 파라미터가 있으면 해당 상태만, 없으면 전체를 조회한다.
        val tasks = if (status != null) {
            taskService.getTasksByStatus(status)
        } else {
            taskService.getAllTasks()
        }
        return ResponseEntity.ok(tasks)
    }
}
