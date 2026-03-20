package com.realteeth

import com.realteeth.config.MockWorkerProperties
import com.realteeth.config.TaskProcessingProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 애플리케이션의 시작점이다.
 *
 * - 스케줄러를 켜서 worker가 주기적으로 작업을 처리하게 한다.
 * - JPA auditing을 켜서 createdAt / updatedAt을 자동으로 채운다.
 * - 설정 클래스를 바인딩해서 application.yml 값을 코드에서 사용할 수 있게 한다.
 *
 * Kotlin 초보자 포인트:
 * - `class RealteethApplication` 자체는 거의 비어 있어도 된다.
 * - 실제 시작은 아래 `main()` 함수에서 `runApplication<...>()`를 호출하면서 이뤄진다.
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
@EnableConfigurationProperties(
    MockWorkerProperties::class,
    TaskProcessingProperties::class
)
class RealteethApplication

// JVM이 시작되면 이 main 함수가 실행되고, Spring Boot가 전체 애플리케이션을 띄운다.
fun main(args: Array<String>) {
    // 제네릭 `<RealteethApplication>`으로 "어느 Spring Boot 앱을 띄울지" 전달한다.
    runApplication<RealteethApplication>(*args)
}
