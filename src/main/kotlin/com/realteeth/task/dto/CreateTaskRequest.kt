package com.realteeth.task.dto

import com.realteeth.common.validation.HttpImageUrl
import jakarta.validation.constraints.NotBlank

data class CreateTaskRequest(
    @field:NotBlank(message = "imageUrl is required")
    @field:HttpImageUrl(message = "imageUrl must be a valid http or https URL")
    val imageUrl: String
)
