package com.realteeth.task.controller

import com.ninjasquad.springmockk.MockkBean
import com.realteeth.task.dto.CreateTaskResponse
import com.realteeth.task.dto.TaskResponse
import com.realteeth.task.entity.TaskStatus
import com.realteeth.task.service.TaskService
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var taskService: TaskService

    @Test
    fun `createTask returns created for new task`() {
        every { taskService.createTask(any()) } returns CreateTaskResponse(
            id = 1,
            status = TaskStatus.PENDING,
            created = true,
            message = "Queued"
        )

        mockMvc.perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"imageUrl":"https://example.com/image.png"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.created").value(true))
    }

    @Test
    fun `createTask returns ok for duplicate task`() {
        every { taskService.createTask(any()) } returns CreateTaskResponse(
            id = 7,
            status = TaskStatus.PROCESSING,
            created = false,
            message = "Deduplicated task"
        )

        mockMvc.perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"imageUrl":"https://example.com/image.png"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.status").value("PROCESSING"))
            .andExpect(jsonPath("$.created").value(false))
            .andExpect(jsonPath("$.message").value("Deduplicated task"))
    }

    @Test
    fun `createTask returns validation error when imageUrl is blank`() {
        mockMvc.perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"imageUrl":"   "}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("imageUrl: imageUrl is required"))
    }

    @Test
    fun `createTask returns validation error when imageUrl is not a valid URL`() {
        mockMvc.perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"imageUrl":"not-a-url"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("imageUrl: imageUrl must be a valid http or https URL"))
    }

    @Test
    fun `getTask returns not found payload when task is missing`() {
        every { taskService.getTask(404) } throws NoSuchElementException("Task not found: 404")

        mockMvc.perform(get("/api/tasks/404"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Task not found: 404"))
    }

    @Test
    fun `getAllTasks returns bad request when status is invalid`() {
        mockMvc.perform(get("/api/tasks").param("status", "UNKNOWN"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `getTask returns task payload`() {
        every { taskService.getTask(9) } returns TaskResponse(
            id = 9,
            imageUrl = "https://example.com/image.png",
            status = TaskStatus.RETRY_SCHEDULED,
            externalJobId = null,
            result = null,
            retryCount = 1,
            maxRetryCount = 3,
            lastErrorCode = "MOCK_WORKER_FAILED",
            lastErrorMessage = "worker failed",
            nextRetryAt = LocalDateTime.of(2026, 3, 10, 12, 0),
            startedAt = LocalDateTime.of(2026, 3, 10, 11, 59),
            completedAt = null,
            createdAt = LocalDateTime.of(2026, 3, 10, 11, 58),
            updatedAt = LocalDateTime.of(2026, 3, 10, 11, 59)
        )

        mockMvc.perform(get("/api/tasks/9"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(9))
            .andExpect(jsonPath("$.status").value("RETRY_SCHEDULED"))
            .andExpect(jsonPath("$.retryCount").value(1))
    }
}
