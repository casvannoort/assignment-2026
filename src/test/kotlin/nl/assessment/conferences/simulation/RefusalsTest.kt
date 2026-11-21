package nl.assessment.conferences.simulation

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals

/** A refusal is only right when its stated reason is true in the database before the request. */
@Tag("simulation")
class RefusalsTest {

    private val json = JsonMapper.builder().build()
    private fun refused(code: String) = Reply(409, json.readTree("""{"code": "$code"}"""))
    private fun time(v: String) = LocalTime.parse(v)

    private val teacher = UUID.randomUUID()
    private val otherTeacher = UUID.randomUUID()
    private val parent = UUID.randomUUID()
    private val child = UUID.randomUUID()
    private val sibling = UUID.randomUUID()
    private val school = School(
        listOf(teacher, otherTeacher), listOf(parent), listOf(child, sibling),
        mapOf(child to listOf(parent), sibling to listOf(parent)),
        mapOf(child to listOf(teacher, otherTeacher), sibling to listOf(teacher)),
    )
    private val now = LocalDateTime.parse("2030-02-01T12:00")
    private val e = UUID.randomUUID()
    private val s1 = UUID.randomUUID()
    private val s2 = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val before = Snapshot(
        setOf(EveningRow(e, teacher, LocalDate.parse("2030-02-01"), time("19:00"), time("19:30"), 15)),
        setOf(SlotRow(s1, e, time("19:00"), time("19:15"), 1), SlotRow(s2, e, time("19:15"), time("19:30"), 1)),
        setOf(BookingRow(b, e, s1, child, parent)),
    )

    private fun check(action: Action, code: String) = Refusals.check(action, refused(code), before, now, school).size

    @Test
    fun `slot full only when the slot has no room`() {
        assertEquals(0, check(Action.Book(parent, e, sibling, s1), "SLOT_FULL"))
        assertEquals(1, check(Action.Book(parent, e, sibling, s2), "SLOT_FULL"))
    }

    @Test
    fun `child already booked only when the child has a booking that evening`() {
        assertEquals(0, check(Action.Book(parent, e, child, s2), "CHILD_ALREADY_BOOKED"))
        assertEquals(1, check(Action.Book(parent, e, sibling, s2), "CHILD_ALREADY_BOOKED"))
    }

    @Test
    fun `forbidden only for someone who may not do it`() {
        assertEquals(0, check(Action.Book(otherTeacher, e, sibling, s2), "FORBIDDEN"))
        assertEquals(1, check(Action.Book(parent, e, sibling, s2), "FORBIDDEN"))
        assertEquals(0, check(Action.RemoveSlot(otherTeacher, e, s2), "FORBIDDEN"))
        assertEquals(1, check(Action.RemoveSlot(teacher, e, s2), "FORBIDDEN"))
    }

    @Test
    fun `evening has ended only after its end time`() {
        assertEquals(1, check(Action.Cancel(parent, e, b), "EVENING_HAS_ENDED"))
        assertEquals(0, Refusals.check(Action.Cancel(parent, e, b), refused("EVENING_HAS_ENDED"), before, LocalDateTime.parse("2030-02-01T19:30"), school).size)
    }

    @Test
    fun `slot has bookings only when it has one`() {
        assertEquals(0, check(Action.RemoveSlot(teacher, e, s1), "SLOT_HAS_BOOKINGS"))
        assertEquals(1, check(Action.RemoveSlot(teacher, e, s2), "SLOT_HAS_BOOKINGS"))
    }

    @Test
    fun `evening overlaps only when the teacher has an overlapping evening`() {
        val overlapping = Action.CreateEvening(teacher, LocalDate.parse("2030-02-01"), time("19:15"), time("20:00"), 15, null)
        val touching = Action.CreateEvening(teacher, LocalDate.parse("2030-02-01"), time("19:30"), time("20:00"), 15, null)
        assertEquals(0, check(overlapping, "EVENING_OVERLAPS"))
        assertEquals(1, check(touching, "EVENING_OVERLAPS"))
    }

    @Test
    fun `not found only when it does not exist`() {
        assertEquals(0, check(Action.Cancel(parent, e, UUID.randomUUID()), "BOOKING_NOT_FOUND"))
        assertEquals(1, check(Action.Cancel(parent, e, b), "BOOKING_NOT_FOUND"))
        assertEquals(0, check(Action.Book(parent, e, sibling, UUID.randomUUID()), "SLOT_NOT_FOUND"))
        assertEquals(1, check(Action.Book(parent, e, sibling, s2), "SLOT_NOT_FOUND"))
    }

    @Test
    fun `slot has started only from its start time`() {
        val atQuarterPast = LocalDateTime.parse("2030-02-01T19:15")

        assertEquals(0, Refusals.check(Action.Book(parent, e, sibling, s2), refused("SLOT_HAS_STARTED"), before, atQuarterPast, school).size)
        assertEquals(1, check(Action.Book(parent, e, sibling, s2), "SLOT_HAS_STARTED"))
    }
}
