package nl.assessment.conferences.simulation

import nl.assessment.conferences.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

/** Every invariant must fire on a database that breaks it on purpose; a checker that never fires proves nothing. */
@Tag("simulation")
@SpringBootTest
@Import(TestcontainersConfiguration::class, SimulationConfig::class)
class InvariantsTest(@Autowired private val jdbc: JdbcTemplate) {

    private fun id(n: Int) = UUID.fromString("00000000-0000-0000-0000-000000000$n")
    private val msJansen = id(101)
    private val mrDeVries = id(102)
    private val anna = id(201)
    private val sara = id(203)
    private val emma = id(301)
    private val daan = id(302)
    private val lisa = id(303)
    private val now = LocalDateTime.parse("2030-01-06T08:00")

    @BeforeEach
    fun clean() = SchoolGenerator.reset(jdbc)

    private fun evening(teacher: UUID = msJansen, start: String = "19:00", end: String = "20:00", date: String = "2030-02-01") =
        UUID.randomUUID().also {
            jdbc.update("INSERT INTO evening VALUES (?, ?, ?::date, ?::time, ?::time, 15)", it, teacher, date, start, end)
        }

    private fun slot(evening: UUID, start: String, end: String, capacity: Int = 1) = UUID.randomUUID().also {
        jdbc.update("INSERT INTO slot VALUES (?, ?, ?::time, ?::time, ?)", it, evening, start, end, capacity)
    }

    private fun booking(evening: UUID, slot: UUID, child: UUID, parent: UUID) = UUID.randomUUID().also {
        jdbc.update("INSERT INTO booking VALUES (?, ?, ?, ?, ?)", it, evening, slot, child, parent)
    }

    private fun reported(ended: EndedEvenings = EndedEvenings()) =
        Invariants.check(jdbc, now, ended).map { it.check }.toSet()

    @Test
    fun `a valid database breaks no invariant`() {
        val e = evening()
        val s = slot(e, "19:00", "19:15")
        slot(e, "19:15", "19:30")
        booking(e, s, emma, anna)

        assertEquals(emptySet(), reported())
    }

    @Test
    fun `slots outside the evening, of the wrong length or overlapping are reported`() {
        val e = evening()
        slot(e, "18:45", "19:00")
        assertEquals(setOf("L2.1 slots fit the evening"), reported())

        clean()
        val e2 = evening()
        slot(e2, "19:00", "19:15")
        slot(e2, "19:10", "19:25")
        assertEquals(setOf("L2.1 slots fit the evening"), reported())
    }

    @Test
    fun `more bookings than capacity is reported`() {
        val e = evening()
        val s = slot(e, "19:00", "19:15", capacity = 1)
        booking(e, s, emma, anna)
        booking(e, s, daan, sara)

        assertEquals(setOf("L2.2 capacity"), reported())
    }

    @Test
    fun `a booking on a slot of another evening is reported`() {
        val e = evening()
        val other = evening(start = "20:00", end = "21:00")
        val foreign = slot(other, "20:00", "20:15")
        slot(e, "19:00", "19:15")
        booking(e, foreign, emma, anna)

        assertEquals(setOf("L2.3 booking slot exists in its evening"), reported())
    }

    @Test
    fun `a child in two conversations at the same time is reported`() {
        val atJansen = evening(msJansen)
        val atDeVries = evening(mrDeVries)
        booking(atJansen, slot(atJansen, "19:00", "19:15"), emma, anna)
        booking(atDeVries, slot(atDeVries, "19:00", "19:15"), emma, anna)

        assertEquals(setOf("L2.4 child in one place at a time"), reported())
    }

    @Test
    fun `a booking by someone who is not the parent, or at a teacher who does not teach the child, is reported`() {
        val e = evening(msJansen)
        booking(e, slot(e, "19:00", "19:15"), lisa, sara) // Lisa does not have Ms. Jansen

        assertEquals(setOf("L2.5 parent and teacher belong to the child"), reported())
    }

    @Test
    fun `overlapping evenings of one teacher are reported`() {
        evening(msJansen, "19:00", "20:00")
        evening(msJansen, "19:30", "21:00")

        assertEquals(setOf("L2.6 no overlapping evenings per teacher"), reported())
    }

    @Test
    fun `a change to an evening after it ended is reported`() {
        val e = evening(date = "2030-01-05")
        val s = slot(e, "19:00", "19:15")
        val ended = EndedEvenings()
        ended.update(Snapshot.take(jdbc), now)

        booking(e, s, emma, anna)

        assertEquals(setOf("L2.7 nothing changes after the end"), reported(ended))
    }
}
