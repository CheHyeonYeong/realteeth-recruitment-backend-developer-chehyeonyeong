package com.realteeth.infrastructure.queue

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 로컬 개발용 큐다.
 *
 * 서버 프로세스 안 메모리에만 저장되므로, 재시작하면 내용이 사라진다.
 * 즉, 개발 편의를 위한 구현이지 운영용 내구성은 없다.
 */
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

    // 비어 있으면 null을 반환한다.
    override fun dequeue(): Long? = queue.poll()
}
