package nl.assessment.conferences.simulation

import nl.assessment.conferences.TestcontainersConfiguration
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("simulation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, SimulationConfig::class)
class SequentialSimulationTest(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val clock: MutableClock,
    @LocalServerPort port: Int,
) {
    private val simulator = SequentialSimulator(Api(port), jdbc, clock)

    @Test
    fun `the same seed gives the same run`() {
        val first = simulator.run(seed = 3, steps = 40)
        val second = simulator.run(seed = 3, steps = 40)

        assertEquals(first.log, second.log)
    }

    @Test
    fun `a short run completes and counts its outcomes`() {
        val run = simulator.run(seed = 1, steps = 50)

        assertEquals(50, run.log.size)
        assertEquals(50, run.outcomes.values.sum())
    }

    @Test
    fun `full run finds no logic errors and reaches every key outcome`() {
        val seed = System.getProperty("seed", "1").toLong()
        val seeds = System.getProperty("seeds", "1").toInt()
        val steps = System.getProperty("steps", "300").toInt()

        val runs = (seed until seed + seeds).map { simulator.run(it, steps) }
        val report = Report.write("sequential", runs)

        assertTrue(runs.all { it.findings.isEmpty() }, "findings, see $report")
        val missing = Report.keyOutcomes - runs.flatMap { it.outcomes.keys }.toSet()
        assertTrue(missing.isEmpty(), "never reached $missing, see $report")
    }

    @Test
    fun `the picker sends requests across evenings, to removed slots and with the wrong role`() {
        val run = simulator.run(seed = 2, steps = 600)

        assertTrue(run.log.any { "wrong-role" in it }, "no wrong-role requests")
        assertTrue(run.log.any { "removed-slot" in it }, "no requests to removed slots")
        assertTrue(run.log.any { "other-evening" in it }, "no cross-evening requests")
        assertTrue((run.outcomes["BOOKING_NOT_FOUND"] ?: 0) > 0, "BOOKING_NOT_FOUND never reached")
    }

    @Test
    fun `a step that throws is recorded as a finding and the run goes on`() {
        val unreachable = SequentialSimulator(Api(port = 1), jdbc, clock)

        val run = unreachable.run(seed = 1, steps = 5)

        assertEquals(5, run.log.size)
        assertTrue(run.findings.isNotEmpty() && run.findings.all { it.check == "crash" })
    }
}
