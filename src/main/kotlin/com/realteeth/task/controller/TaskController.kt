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

@RestController
@RequestMapping("/api/tasks")
class TaskController(
    private val taskService: TaskService
) {
    @PostMapping
    fun createTask(@Valid @RequestBody request: CreateTaskRequest): ResponseEntity<CreateTaskResponse> {
        val response = taskService.createTask(request)
        val status = if (response.message == "Task already exists") HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(response)
    }

    @GetMapping("/{id}")
    fun getTask(@PathVariable id: Long): ResponseEntity<TaskResponse> {
        return ResponseEntity.ok(taskService.getTask(id))
    }

    @GetMapping
    fun getAllTasks(
        @RequestParam(required = false) status: TaskStatus?
    ): ResponseEntity<List<TaskResponse>> {
        val tasks = if (status != null) {
            taskService.getTasksByStatus(status)
        } else {
            taskService.getAllTasks()
        }
        return ResponseEntity.ok(tasks)
    }
}
