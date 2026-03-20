package com.realteeth.task.entity

/**
 * 작업이 현재 어느 단계에 있는지 표현한다.
 *
 * enum class는 "정해진 값 후보 몇 개 중 하나"를 표현할 때 쓰는 Kotlin 문법이다.
 */
enum class TaskStatus {
    // 새로 만들어졌고, 아직 외부 worker로 보내지지 않았다.
    PENDING,

    // 바로 재시도하지 않고, nextRetryAt 시각까지 기다리는 상태다.
    RETRY_SCHEDULED,

    // 외부 worker 호출 직전 상태다. 가장 애매한 구간이라 별도 상태로 둔다.
    DISPATCHING,

    // 외부 job id를 받은 뒤 polling으로 상태를 확인하는 중이다.
    PROCESSING,

    // 최종 성공 상태다.
    COMPLETED,

    // 자동 복구를 멈추고 사람 확인이 필요한 최종 실패 상태다.
    DEAD_LETTER
}
