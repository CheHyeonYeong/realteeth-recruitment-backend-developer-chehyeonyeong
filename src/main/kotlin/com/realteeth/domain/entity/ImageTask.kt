package com.realteeth.domain.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "image_tasks")
class ImageTask(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 64)
    val idempotencyKey: String,

    @Column(nullable = false, length = 2048)
    val imageUrl: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: TaskStatus = TaskStatus.PENDING,

    @Column(length = 100)
    var externalJobId: String? = null,

    @Column(columnDefinition = "TEXT")
    var result: String? = null,

    @Column(nullable = false)
    var retryCount: Int = 0,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
