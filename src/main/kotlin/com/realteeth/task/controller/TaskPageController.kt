package com.realteeth.task.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@Controller
class TaskPageController {
    @GetMapping("/", "/tasks")
    fun tasksPage(model: Model): String {
        model.addAttribute("pageTitle", "Realteeth Task Dashboard")
        return "tasks"
    }

    @GetMapping("/tasks/{id}/view")
    fun taskDetailPage(@PathVariable id: Long, model: Model): String {
        model.addAttribute("taskId", id)
        return "task-detail"
    }
}
