package com.realteeth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * worker의 동작 방식을 제어하는 설정 값이다.
 *
 * 예:
 * - queue를 어떤 구현으로 쓸지
 * - 한 번에 몇 개 작업을 볼지
 * - 재시도 간격을 얼마나 둘지
 *
 * 이 클래스는 application.yml의 `realteeth.tasks.*` 값을
 * Kotlin 객체로 옮겨 담는 역할을 한다.
 */
@ConfigurationProperties("realteeth.tasks")
data class TaskProcessingProperties(
    // TODO(kafka): queueMode에 "kafka"를 추가하면 구현체 분기 지점으로 쓸 수 있다.
    val workerEnabled: Boolean = true,
    val queueMode: String = "redis",
    // TODO(kafka): 지금은 Redis list 이름이지만, Kafka 도입 시 topic 이름으로 재해석하거나
    // topicName 필드를 별도로 두는 방향이 더 명확하다.
    val queueName: String = "realteeth:tasks",

    // worker가 한 번 돌 때 최대 몇 건까지 처리할지 정한다.
    val batchSize: Int = 10,

    // DISPATCHING이 이 시간보다 오래 유지되면 비정상 정지로 보고 복구 대상으로 본다.
    val dispatchStaleAfter: Duration = Duration.ofSeconds(30),

    // 재시도 간격 목록이다. retryCount가 올라갈수록 뒤 값을 사용한다.
    val retryDelays: List<Duration> = listOf(
        Duration.ofSeconds(10),
        Duration.ofSeconds(30),
        Duration.ofSeconds(60)
    )
) {
    // 현재 retryCount 기준으로 다음 재시도 대기 시간을 계산한다.
    fun nextRetryDelay(retryCount: Int, maxRetryCount: Int): Duration? {
        if (retryCount >= maxRetryCount) {
            return null
        }

        // retryCount가 목록 길이보다 커져도 마지막 delay를 재사용하도록 보정한다.
        val index = retryCount.coerceAtMost(retryDelays.lastIndex)
        return retryDelays[index]
    }
}
