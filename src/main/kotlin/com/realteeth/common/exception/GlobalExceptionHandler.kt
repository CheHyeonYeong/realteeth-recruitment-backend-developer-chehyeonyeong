package com.realteeth.common.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

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
            "Request body is invalid"
        }

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse("VALIDATION_ERROR", message))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class, IllegalArgumentException::class)
    fun handleBadRequest(e: Exception): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiErrorResponse("BAD_REQUEST", e.message ?: "Invalid request"))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(e: Exception): ResponseEntity<ApiErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiErrorResponse("INTERNAL_ERROR", e.message ?: "Unexpected error"))
    }
}
