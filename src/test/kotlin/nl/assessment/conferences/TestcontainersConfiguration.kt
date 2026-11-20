package nl.assessment.conferences

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun postgresContainer() = postgres

    companion object {
        /** One Postgres for the whole test run, shared by every Spring context. */
        private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18"))
    }
}
