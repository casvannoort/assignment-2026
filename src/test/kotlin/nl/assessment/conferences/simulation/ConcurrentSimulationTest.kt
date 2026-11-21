package nl.assessment.conferences.simulation

import nl.assessment.conferences.TestcontainersConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertTrue

/** "Booking opens": hundreds of parents at once. Findings here are intermittent and get a hand-made test. */
@Tag("simulation")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.hikari.maximum-pool-size=50", "spring.datasource.hikari.connection-timeout=30000"],
)
@Import(TestcontainersConfiguration::class, SimulationConfig::class)
class ConcurrentSimulationTest(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val clock: MutableClock,
    @LocalServerPort port: Int,
) {
    private val simulator = ConcurrentSimulator(Api(port), jdbc, clock)
    private val perAction = Regex("(CreateEvening|Book|Move|Cancel) \\S+")

    @Test
    fun `bursts of parents booking at once keep every invariant`() {
        val seed = System.getProperty("seed", "1").toLong()
        val bursts = System.getProperty("bursts", "1").toInt()

        val run = simulator.run(seed, bursts, parentsPerBurst = 200)
        val report = Report.write("concurrent", listOf(run))

        assertTrue(run.findings.isEmpty(), "findings, see $report")
        assertTrue(run.outcomes.keys.all { it.matches(perAction) }, "outcomes not per action: ${run.outcomes.keys}")
        assertTrue(run.log.all { "parent#" in it || "teacher#" in it }, "log lines without a person: ${run.log.take(3)}")
        assertTrue(run.log.filter { " Book " in it || " Move " in it }.all { "child#" in it && "slot#" in it }, "book or move lines without child and slot")
        assertTrue(run.count("SLOT_FULL") > 0, "no contention on slots, see $report")
        assertTrue(run.count("CHILD_ALREADY_BOOKED") > 0, "no double clicks happened, see $report")
        assertTrue(run.count("CHILD_DOUBLE_BOOKED") > 0, "no child raced at two teachers, see $report")
        assertTrue(run.count("EVENING_OVERLAPS") > 0, "no teacher raced overlapping evenings, see $report")
        assertTrue(run.count("EVENING_HAS_ENDED") > 0, "no writes after an evening ended, see $report")
    }
}
