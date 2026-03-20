package com.realteeth.task.controller

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

/**
 * 서버 사이드 렌더링 화면을 반환하는 컨트롤러다.
 *
 * REST API와 달리 JSON이 아니라 HTML 템플릿 이름을 반환한다.
 */
@Controller
class TaskPageController {
    @GetMapping("/", "/tasks")
    fun tasksPage(model: Model): String {
        // `model`은 템플릿에 값(pageTitle 등)을 전달할 때 쓴다.
        model.addAttribute("pageTitle", "Realteeth Task Dashboard")
        return "tasks"
    }

    @GetMapping("/tasks/{id}/view")
    fun taskDetailPage(@PathVariable id: Long, model: Model): String {
        // 화면 안 JS가 taskId를 사용해 상세 API를 polling한다.
        model.addAttribute("taskId", id)
        return "task-detail"
    }
}
