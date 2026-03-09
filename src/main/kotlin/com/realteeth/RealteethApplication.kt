package com.realteeth

import com.realteeth.config.MockWorkerProperties
import com.realteeth.config.TaskProcessingProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
@EnableConfigurationProperties(
    MockWorkerProperties::class,
    TaskProcessingProperties::class
)
class RealteethApplication

fun main(args: Array<String>) {
    runApplication<RealteethApplication>(*args)
}
