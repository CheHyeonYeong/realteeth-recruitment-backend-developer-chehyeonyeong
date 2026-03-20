package com.realteeth.infrastructure.queue

// 큐가 잠시 죽었을 때 상위 계층에서 별도로 복구 전략을 선택할 수 있게 감싸는 예외다.
class TaskQueueAccessException(
    message: String,
    cause: Throwable
) : RuntimeException(message, cause)
