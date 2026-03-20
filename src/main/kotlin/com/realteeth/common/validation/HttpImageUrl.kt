package com.realteeth.common.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * `http` 또는 `https` 이미지 URL만 허용하도록 만드는 커스텀 검증 어노테이션이다.
 */
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.PROPERTY_GETTER
)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [HttpImageUrlValidator::class])
annotation class HttpImageUrl(
    val message: String = "must be a valid http or https URL",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
