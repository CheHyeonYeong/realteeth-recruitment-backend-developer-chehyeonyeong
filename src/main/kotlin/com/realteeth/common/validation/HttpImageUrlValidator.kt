package com.realteeth.common.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.net.URI

class HttpImageUrlValidator : ConstraintValidator<HttpImageUrl, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value.isNullOrBlank()) {
            return true
        }

        val normalizedValue = value.trim()
        val uri = runCatching { URI(normalizedValue) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host ?: return false

        return (scheme == "http" || scheme == "https") && host.isNotBlank()
    }
}
