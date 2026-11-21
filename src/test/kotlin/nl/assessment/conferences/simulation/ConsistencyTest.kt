package nl.assessment.conferences.simulation

import nl.assessment.conferences.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("simulation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, SimulationConfig::class)
class ConsistencyTest(@Autowired private val jdbc: JdbcTemplate, @LocalServerPort port: Int) {

    private val api = Api(port)
    private fun id(n: Int) = UUID.fromString("00000000-0000-0000-0000-000000000$n")
    private val msJansen = id(101)
    private val anna = id(201)
    private val sara = id(203)
    private val emma = id(301)
    private val daan = id(302)

    @BeforeEach
    fun clean() = SchoolGenerator.reset(jdbc)

    private fun checked(action: Action, call: () -> Reply): List<Finding> {
        val before = Snapshot.take(jdbc)
        val reply = call()
        return Consistency.check(action, reply, before, Snapshot.take(jdbc))
    }

    private fun createEvening(): Pair<UUID, List<UUID>> {
        val create = Action.CreateEvening(msJansen, LocalDate.parse("2030-02-01"), LocalTime.parse("19:00"), LocalTime.parse("19:30"), 15, 1)
        val reply = api.send(create)
        assertEquals(emptyList(), Consistency.check(create, reply, Snapshot(emptySet(), emptySet(), emptySet()), Snapshot.take(jdbc)))
        val slots = reply.body!!["slots"].values().map { UUID.fromString(it["id"].asString()) }
        return reply.id() to slots
    }

    @Test
    fun `a correct booking and a refused booking give no findings`() {
        val (evening, slots) = createEvening()

        val book = Action.Book(anna, evening, emma, slots[0])
        assertEquals(emptyList(), checked(book) { api.send(book) })

        val full = Action.Book(sara, evening, daan, slots[0])
        val findings = checked(full) { api.send(full).also { assertEquals(409, it.status) } }
        assertEquals(emptyList(), findings)
    }

    @Test
    fun `a change the reply does not explain is a finding`() {
        val (evening, slots) = createEvening()
        val before = Snapshot.take(jdbc)
        val sneaky = BookingRow(UUID.randomUUID(), evening, slots[1], daan, sara)

        val findings = Consistency.check(
            Action.Book(anna, evening, emma, slots[0]),
            Reply(409, JsonMapper.builder().build().readTree("""{"code": "SLOT_FULL"}""")),
            before,
            before.copy(bookings = before.bookings + sneaky),
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun `stored slots that differ from the reply are a finding`() {
        val create = Action.CreateEvening(msJansen, LocalDate.parse("2030-02-02"), LocalTime.parse("19:00"), LocalTime.parse("19:30"), 15, null)
        val before = Snapshot.take(jdbc)
        val reply = api.send(create)
        val after = Snapshot.take(jdbc)
        val oneSlotLost = after.copy(slots = after.slots.drop(1).toSet())

        assertEquals(emptyList(), Consistency.check(create, reply, before, after))
        assertEquals(1, Consistency.check(create, reply, before, oneSlotLost).size)
    }

    @Test
    fun `a slot that has started is a known refusal`() {
        assertTrue("SLOT_HAS_STARTED" in Consistency.knownCodes)
    }
}
