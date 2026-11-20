package nl.assessment.conferences

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import java.time.Clock
import java.time.ZoneId

@SpringBootApplication
class ConferencesApplication {

    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Europe/Amsterdam"))
}

fun main(args: Array<String>) {
    runApplication<ConferencesApplication>(*args)
}
