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
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scenarios in src/test/resources/simulation/scenarios.md, written by an independent reviewer from the
 * assignment alone, implemented word for word. A failing scenario is reported, not edited to match the app.
 */
@Tag("simulation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class, SimulationConfig::class)
class AssignmentScenariosTest(@Autowired private val jdbc: JdbcTemplate, @LocalServerPort port: Int) {

    private val api = Api(port)
    private fun id(n: Int) = UUID.fromString("00000000-0000-0000-0000-000000000$n")
    private val jansen = id(101)
    private val anna = id(201)
    private val mark = id(202)
    private val sara = id(203)
    private val emma = id(301)
    private val daan = id(302)

    @BeforeEach
    fun clean() = SchoolGenerator.reset(jdbc)

    private class Evening(val id: UUID, val slots: Map<String, UUID>)

    private fun create(date: String = "2035-03-12", start: String = "19:00", end: String = "20:00", minutes: Long = 15, max: Int? = null): Evening {
        val reply = api.send(Action.CreateEvening(jansen, LocalDate.parse(date), LocalTime.parse(start), LocalTime.parse(end), minutes, max))
        assertTrue(reply.status in 200..299, "create gave ${reply.status}")
        return Evening(reply.id(), reply.body!!["slots"].values().associate { it["start"].asString().take(5) to UUID.fromString(it["id"].asString()) })
    }

    private fun book(user: UUID, e: Evening, child: UUID, at: String) = api.send(Action.Book(user, e.id, child, e.slots.getValue(at)))
    private fun read(user: UUID, path: String): List<JsonNode> = api.send(Action.Read(user, path)).also { assertEquals(200, it.status) }.body!!.values().toList()
    private fun freeSlots(user: UUID, e: Evening) = read(user, "/evenings/${e.id}/slots").map { it["start"].asString().take(5) }.toSet()
    private fun overview(e: Evening) = read(jansen, "/evenings/${e.id}/overview")
    private fun overviewTimes(e: Evening) = overview(e).map { it["start"].asString().take(5) to it["end"].asString().take(5) }
    private fun childrenAt(e: Evening) = overview(e).associate { row ->
        row["start"].asString().take(5) to row["conversations"].values().map { it["childName"].asString() to it["parentName"].asString() }
    }
    private fun bookingsSeenBy(user: UUID) = read(user, "/bookings").map { Triple(it["childName"].asString(), it["start"].asString().take(5), it["date"].asString()) }
    private fun is2xx(reply: Reply) = assertTrue(reply.status in 200..299, "expected 2xx, got ${reply.status} ${reply.code}")
    private fun is4xx(reply: Reply) = assertTrue(reply.status in 400..499, "expected 4xx, got ${reply.status}")

    @Test
    fun `S1 slots are generated from start, end and conversation length`() {
        val e = create()
        assertEquals(listOf("19:00" to "19:15", "19:15" to "19:30", "19:30" to "19:45", "19:45" to "20:00"), overviewTimes(e))
    }

    @Test
    fun `S2 a different length gives a different number of slots`() {
        val e = create(date = "2035-03-13", start = "18:00", end = "19:30", minutes = 10)
        val times = overviewTimes(e)
        assertEquals(9, times.size)
        assertEquals("18:00", times.first().first)
        assertEquals("19:30", times.last().second)
        assertTrue(times.zipWithNext().all { (a, b) -> a.second == b.first })
    }

    @Test
    fun `S3 the evening appears in the teacher's list`() {
        val e = create()
        val listed = read(jansen, "/evenings").single { it["id"].asString() == e.id.toString() }
        assertEquals("2035-03-12", listed["date"].asString())
        assertEquals("19:00", listed["start"].asString().take(5))
        assertEquals("20:00", listed["end"].asString().take(5))
        assertEquals(15, listed["conversationMinutes"].asInt())
    }

    @Test
    fun `S4 by default a slot holds one conversation`() {
        val e = create()
        is2xx(book(anna, e, emma, "19:00"))

        is4xx(book(sara, e, daan, "19:00"))
        assertTrue("19:00" !in freeSlots(sara, e))
        assertEquals(listOf("Emma Bakker" to "Anna Bakker"), childrenAt(e).getValue("19:00"))
    }

    @Test
    fun `S5 a larger maximum allows that many conversations, and no more`() {
        val e = create(date = "2035-03-14", max = 2)
        is2xx(book(anna, e, emma, "19:00"))
        assertTrue("19:00" in freeSlots(sara, e))

        is2xx(book(sara, e, daan, "19:00"))
        assertEquals(setOf("Emma Bakker", "Daan Visser"), childrenAt(e).getValue("19:00").map { it.first }.toSet())
        assertTrue("19:00" !in freeSlots(sara, e))
    }

    @Test
    fun `S6 a parent sees the free slots of an evening`() {
        val e = create()
        assertEquals(setOf("19:00", "19:15", "19:30", "19:45"), freeSlots(anna, e))
    }

    @Test
    fun `S7 a parent books a slot for a child`() {
        val e = create()
        val reply = book(anna, e, emma, "19:15")
        is2xx(reply)
        assertTrue(reply.idOrNull() != null)
        assertTrue(Triple("Emma Bakker", "19:15", "2035-03-12") in bookingsSeenBy(anna))
    }

    @Test
    fun `S8 free slots shrink as they fill`() {
        val e = create()
        is2xx(book(anna, e, emma, "19:00"))
        is2xx(book(sara, e, daan, "19:15"))

        assertEquals(setOf("19:30", "19:45"), freeSlots(sara, e))
        // "with another child": Jansen teaches only Emma and Daan, so Lisa is the only other test child.
        is4xx(book(id(204), e, id(303), "19:00"))
    }

    @Test
    fun `S9 a parent moves a booking to another slot`() {
        val e = create()
        val b = book(anna, e, emma, "19:00").id()

        is2xx(api.send(Action.Move(anna, e.id, b, e.slots.getValue("19:45"))))
        assertTrue(Triple("Emma Bakker", "19:45", "2035-03-12") in bookingsSeenBy(anna))
        assertTrue(Triple("Emma Bakker", "19:00", "2035-03-12") !in bookingsSeenBy(anna))
        assertTrue("19:00" in freeSlots(anna, e) && "19:45" !in freeSlots(anna, e))
        assertEquals(mapOf("19:45" to listOf("Emma Bakker" to "Anna Bakker")), childrenAt(e).filterValues { it.isNotEmpty() })
    }

    @Test
    fun `S10 moving into a full slot is refused and the booking stays put`() {
        val e = create()
        val b = book(anna, e, emma, "19:00").id()
        is2xx(book(sara, e, daan, "19:15"))

        is4xx(api.send(Action.Move(anna, e.id, b, e.slots.getValue("19:15"))))
        assertTrue(Triple("Emma Bakker", "19:00", "2035-03-12") in bookingsSeenBy(anna))
        assertEquals(listOf("Daan Visser" to "Sara Visser"), childrenAt(e).getValue("19:15"))
    }

    @Test
    fun `S11 a parent cancels a booking`() {
        val e = create()
        val b = book(anna, e, emma, "19:30").id()

        is2xx(api.send(Action.Cancel(anna, e.id, b)))
        assertTrue(bookingsSeenBy(anna).none { it.first == "Emma Bakker" })
        assertTrue("19:30" in freeSlots(anna, e))
        assertEquals(emptyList(), childrenAt(e).getValue("19:30"))
        is2xx(book(sara, e, daan, "19:30"))
    }

    @Test
    fun `S12 the other parent sees the appointment`() {
        val e = create()
        is2xx(book(anna, e, emma, "19:15"))
        assertTrue(Triple("Emma Bakker", "19:15", "2035-03-12") in bookingsSeenBy(mark))
    }

    @Test
    fun `S13 the other parent sees later changes too`() {
        val e = create()
        val b = book(anna, e, emma, "19:15").id()

        is2xx(api.send(Action.Move(anna, e.id, b, e.slots.getValue("19:45"))))
        assertTrue(Triple("Emma Bakker", "19:45", "2035-03-12") in bookingsSeenBy(mark))
        assertTrue(Triple("Emma Bakker", "19:15", "2035-03-12") !in bookingsSeenBy(mark))

        is2xx(api.send(Action.Cancel(anna, e.id, b)))
        assertTrue(bookingsSeenBy(mark).none { it.first == "Emma Bakker" })
    }

    @Test
    fun `S14 the teacher overview shows child, time and parent`() {
        val e = create()
        is2xx(book(anna, e, emma, "19:00"))
        is2xx(book(sara, e, daan, "19:30"))

        assertEquals(
            mapOf(
                "19:00" to listOf("Emma Bakker" to "Anna Bakker"), "19:15" to emptyList(),
                "19:30" to listOf("Daan Visser" to "Sara Visser"), "19:45" to emptyList(),
            ),
            childrenAt(e),
        )
    }

    @Test
    fun `S15 the overview reflects moves and cancellations`() {
        val e = create()
        val emmas = book(anna, e, emma, "19:00").id()
        val daans = book(sara, e, daan, "19:30").id()

        is2xx(api.send(Action.Move(anna, e.id, emmas, e.slots.getValue("19:45"))))
        is2xx(api.send(Action.Cancel(sara, e.id, daans)))
        assertEquals(mapOf("19:45" to listOf("Emma Bakker" to "Anna Bakker")), childrenAt(e).filterValues { it.isNotEmpty() })
    }

    @Test
    fun `S16 the teacher removes a slot for a break`() {
        val e = create()
        is2xx(api.send(Action.RemoveSlot(jansen, e.id, e.slots.getValue("19:30"))))

        assertEquals(listOf("19:00", "19:15", "19:45"), overviewTimes(e).map { it.first })
        assertTrue("19:30" !in freeSlots(anna, e))
    }

    @Test
    fun `S17 a removed slot cannot be booked`() {
        val e = create()
        is2xx(api.send(Action.RemoveSlot(jansen, e.id, e.slots.getValue("19:30"))))

        is4xx(book(anna, e, emma, "19:30"))
        assertTrue(bookingsSeenBy(anna).none { it.first == "Emma Bakker" })
        assertTrue("19:30" !in overviewTimes(e).map { it.first })
    }
}
