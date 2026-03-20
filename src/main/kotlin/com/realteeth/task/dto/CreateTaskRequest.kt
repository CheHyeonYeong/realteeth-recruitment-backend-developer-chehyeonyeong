package com.realteeth.task.dto

import com.realteeth.common.validation.HttpImageUrl
import jakarta.validation.constraints.NotBlank

// 작업 생성 API에서 클라이언트가 보내는 요청 본문이다.
// field: 접두사는 Kotlin 프로퍼티가 아니라 실제 backing field에 검증 어노테이션을 붙이기 위해 사용한다.
data class CreateTaskRequest(
    @field:NotBlank(message = "imageUrl is required")
    @field:HttpImageUrl(message = "imageUrl must be a valid http or https URL")
    val imageUrl: String
)
