package nl.assessment.conferences.simulation

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@TestConfiguration(proxyBeanMethods = false)
class SimulationConfig {

    @Bean
    @Primary
    fun simulationClock() = MutableClock(START, ZONE)

    companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Amsterdam")
        val START: Instant = LocalDateTime.parse("2030-01-06T08:00").atZone(ZONE).toInstant()
    }
}
