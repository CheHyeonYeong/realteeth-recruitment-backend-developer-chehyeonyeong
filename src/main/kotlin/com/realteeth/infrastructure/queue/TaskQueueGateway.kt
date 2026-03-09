package com.realteeth.infrastructure.queue

interface TaskQueueGateway {
    fun enqueue(taskId: Long)
    fun dequeue(): Long?
}
