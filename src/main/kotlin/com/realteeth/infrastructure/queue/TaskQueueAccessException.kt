package com.realteeth.infrastructure.queue

class TaskQueueAccessException(
    message: String,
    cause: Throwable
) : RuntimeException(message, cause)
