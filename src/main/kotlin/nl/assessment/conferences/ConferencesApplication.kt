package nl.assessment.conferences

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ConferencesApplication

fun main(args: Array<String>) {
    runApplication<ConferencesApplication>(*args)
}
