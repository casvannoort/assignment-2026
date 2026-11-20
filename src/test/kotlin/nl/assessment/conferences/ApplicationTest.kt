package nl.assessment.conferences

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class ApplicationTest(@Autowired private val jdbc: JdbcTemplate) {

    @Test
    fun `starts against postgres with the seed data migrated`() {
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM teacher", Int::class.java))
    }
}
