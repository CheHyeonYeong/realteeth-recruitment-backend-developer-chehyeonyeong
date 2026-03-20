package com.realteeth.common.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.net.URI

/**
 * 문자열이 실제로 http/https URL 형태인지 검사한다.
 *
 * 이 검증기는 "정말 이미지 파일인지"까지 보지는 않고,
 * 최소한 형식이 http/https URL처럼 생겼는지만 확인한다.
 */
class HttpImageUrlValidator : ConstraintValidator<HttpImageUrl, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        // 비어 있는 값 검사는 NotBlank가 담당하므로 여기서는 통과시킨다.
        if (value.isNullOrBlank()) {
            return true
        }

        val normalizedValue = value.trim()
        // URI 파싱이 실패하면 애초에 URL 형식이 아니다.
        val uri = runCatching { URI(normalizedValue) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host ?: return false

        // scheme은 프로토콜(http/https), host는 도메인(example.com 같은 부분)다.
        return (scheme == "http" || scheme == "https") && host.isNotBlank()
    }
}
