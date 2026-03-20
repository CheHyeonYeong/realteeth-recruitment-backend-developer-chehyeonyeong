package com.realteeth.common.exception

// API 에러를 JSON으로 반환할 때 사용하는 가장 단순한 응답 형태다.
data class ApiErrorResponse(
    val error: String,
    val message: String
)
