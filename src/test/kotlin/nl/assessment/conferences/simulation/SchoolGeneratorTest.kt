package nl.assessment.conferences.simulation

import nl.assessment.conferences.TestcontainersConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("simulation")
@SpringBootTest
@Import(TestcontainersConfiguration::class, SimulationConfig::class)
class SchoolGeneratorTest(@Autowired private val jdbc: JdbcTemplate) {

    @Test
    fun `the same seed gives the same school`() {
        assertEquals(SchoolGenerator.generate(7, teachers = 3, children = 12), SchoolGenerator.generate(7, 3, 12))
    }

    @Test
    fun `the school has the family shapes the rules care about`() {
        val school = SchoolGenerator.generate(7, teachers = 5, children = 60)

        assertEquals(60, school.children.size)
        assertTrue(school.children.any { school.parentsOf.getValue(it).size == 2 }, "a child with two parents")
        assertTrue(school.parents.any { p -> school.childrenOf(p).size >= 2 }, "a parent with several children")
        assertTrue(school.children.all { school.teachersOf.getValue(it).size in 1..4 }, "1 to 4 teachers per child")
    }

    @Test
    fun `insert puts the school in the database and reset removes it again`() {
        val school = SchoolGenerator.generate(11, teachers = 3, children = 12)

        SchoolGenerator.insert(jdbc, school)
        assertEquals(2 + 3, count("teacher"))
        assertEquals(3 + 12, count("child"))
        assertEquals(4 + school.parents.size, count("parent"))

        SchoolGenerator.reset(jdbc)
        assertEquals(2, count("teacher"))
        assertEquals(3, count("child"))
    }

    private fun count(table: String) = jdbc.queryForObject("SELECT count(*) FROM $table", Int::class.java)
}
