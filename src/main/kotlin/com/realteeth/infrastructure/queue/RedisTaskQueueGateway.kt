package com.realteeth.infrastructure.queue

import com.realteeth.config.TaskProcessingProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

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
        redisTemplate.opsForList().rightPush(taskProcessingProperties.queueName, taskId.toString())
    }

    override fun dequeue(): Long? {
        val value = redisTemplate.opsForList().leftPop(taskProcessingProperties.queueName) ?: return null
        return value.toLongOrNull()
    }
}
