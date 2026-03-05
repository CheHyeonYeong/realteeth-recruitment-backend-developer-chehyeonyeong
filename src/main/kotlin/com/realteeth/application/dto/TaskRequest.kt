package com.realteeth.application.dto

import jakarta.validation.constraints.NotBlank

data class CreateTaskRequest(
    @field:NotBlank(message = "imageUrl is required")
    val imageUrl: String
)
