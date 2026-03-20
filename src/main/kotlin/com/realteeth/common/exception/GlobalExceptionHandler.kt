package com.realteeth.common.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 컨트롤러 밖으로 던져진 예외를 공통 JSON 형식으로 바꿔 준다.
 *
 * 이 파일 덕분에 에러가 나도 Spring 기본 HTML 에러 페이지 대신
 * `{ "error": "...", "message": "..." }` 형태로 응답할 수 있다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(e: NoSuchElementException): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiErrorResponse("NOT_FOUND", e.message ?: "Resource not found"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiErrorResponse> {
        val error = e.bindingResult.fieldErrors.firstOrNull()
        val message = if (error != null) {
            "${error.field}: ${error.defaultMessage}"
        } else {
            // field error가 없는 경우를 대비한 기본 메시지다.
            "Request body is invalid"
        }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse("VALIDATION_ERROR", message))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class, IllegalArgumentException::class)
    fun handleBadRequest(e: Exception): ResponseEntity<ApiErrorResponse> {
        // 예: status=UNKNOWN 같은 잘못된 enum 값이 들어오면 여기로 올 수 있다.
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse("BAD_REQUEST", e.message ?: "Invalid request"))
    }

    // 예상하지 못한 예외가 와도 서버가 HTML 에러 페이지 대신 JSON을 주도록 한다.
    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiErrorResponse("INTERNAL_ERROR", e.message ?: "Unexpected error"))
    }
}
