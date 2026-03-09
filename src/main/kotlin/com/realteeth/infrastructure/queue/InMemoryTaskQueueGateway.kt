package com.realteeth.infrastructure.queue

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedQueue

@Component
@ConditionalOnProperty(
    name = ["realteeth.tasks.queue-mode"],
    havingValue = "in-memory"
)
class InMemoryTaskQueueGateway : TaskQueueGateway {
    private val queue = ConcurrentLinkedQueue<Long>()

    override fun enqueue(taskId: Long) {
        queue.offer(taskId)
    }

    override fun dequeue(): Long? = queue.poll()
}
