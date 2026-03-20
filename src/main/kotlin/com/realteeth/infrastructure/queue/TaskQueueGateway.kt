package com.realteeth.infrastructure.queue

/**
 * 작업 큐 접근을 추상화한 인터페이스다.
 *
 * 구현체는 Redis일 수도 있고, 로컬 개발용 메모리 큐일 수도 있다.
 * TODO(kafka): Kafka를 도입하면 이 인터페이스의 구현체로
 * `KafkaTaskQueueGateway`를 추가하는 지점이다.
 * - enqueue(taskId): Kafka producer send
 * - dequeue(): polling consumer 대신 별도 listener 진입점으로 대체 가능
 *
 * 중요한 점은 서비스 레이어가 Redis/Kafka/In-memory 중 무엇을 쓰는지 모르게 만든다는 점이다.
 */
interface TaskQueueGateway {
    fun enqueue(taskId: Long)
    fun dequeue(): Long?
}
