package com.realteeth.infrastructure.queue

import com.realteeth.config.TaskProcessingProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * 실제 운영에 가까운 Redis 기반 큐다.
 *
 * Redis의 List 자료구조를 간단한 FIFO 큐처럼 사용한다.
 */
@Component
@ConditionalOnProperty(
    name = ["realteeth.tasks.queue-mode"],
    havingValue = "redis",
    matchIfMissing = true
)
class RedisTaskQueueGateway(
    private val redisTemplate: StringRedisTemplate,
    private val taskProcessingProperties: TaskProcessingProperties
) : TaskQueueGateway {
    override fun enqueue(taskId: Long) {
        try {
            // 오른쪽에 넣고 왼쪽에서 꺼내므로 FIFO 큐처럼 사용한다.
            redisTemplate.opsForList().rightPush(taskProcessingProperties.queueName, taskId.toString())
        } catch (exception: Exception) {
            throw TaskQueueAccessException("Failed to enqueue task $taskId", exception)
        }
    }

    override fun dequeue(): Long? {
        try {
            // 값이 없으면 null이 반환되고, worker는 그 경우 DB fallback scan으로 넘어갈 수 있다.
            val value = redisTemplate.opsForList().leftPop(taskProcessingProperties.queueName) ?: return null
            return value.toLongOrNull()
        } catch (exception: Exception) {
            throw TaskQueueAccessException("Failed to dequeue task", exception)
        }
    }
}
