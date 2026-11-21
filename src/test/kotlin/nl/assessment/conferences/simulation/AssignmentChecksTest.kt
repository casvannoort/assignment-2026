package nl.assessment.conferences.simulation

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Each assignment item: one reply that follows the assignment, and one that does not. */
@Tag("simulation")
class AssignmentChecksTest {

    private val json = JsonMapper.builder().build()
    private fun reply(status: Int, body: String) = Reply(status, json.readTree(body))
    private val t = UUID.randomUUID()
    private val e = UUID.randomUUID()
    private val s1 = UUID.randomUUID()
    private val s2 = UUID.randomUUID()
    private val child = UUID.randomUUID()
    private val parent = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private fun time(v: String) = LocalTime.parse(v)

    private val create = Action.CreateEvening(t, LocalDate.parse("2030-02-01"), time("19:00"), time("19:40"), 15, null)
    private fun slotsJson(vararg times: Pair<String, String>, capacity: Int = 1) =
        times.joinToString(",", "[", "]") { (a, z) -> """{"id": "${UUID.randomUUID()}", "start": "$a:00", "end": "$z:00", "capacity": $capacity}""" }

    @Test
    fun `1 slots follow date, start, end and conversation length`() {
        val good = reply(201, """{"id": "$e", "slots": ${slotsJson("19:00" to "19:15", "19:15" to "19:30")}}""")
        val bad = reply(201, """{"id": "$e", "slots": ${slotsJson("19:00" to "19:15", "19:15" to "19:30", "19:30" to "19:40")}}""")

        assertEquals(emptyList(), AssignmentChecks.slotsFollowRequest(create, good))
        assertEquals(1, AssignmentChecks.slotsFollowRequest(create, bad).size)
    }

    @Test
    fun `2 a slot holds one conversation unless told otherwise`() {
        val good = reply(201, """{"id": "$e", "slots": ${slotsJson("19:00" to "19:15")}}""")
        val bad = reply(201, """{"id": "$e", "slots": ${slotsJson("19:00" to "19:15", capacity = 2)}}""")

        assertEquals(emptyList(), AssignmentChecks.capacityDefaultsToOne(create, good))
        assertEquals(1, AssignmentChecks.capacityDefaultsToOne(create, bad).size)
    }

    private val snapshot = Snapshot(
        setOf(EveningRow(e, t, LocalDate.parse("2030-02-01"), time("19:00"), time("19:30"), 15)),
        setOf(SlotRow(s1, e, time("19:00"), time("19:15"), 1), SlotRow(s2, e, time("19:15"), time("19:30"), 1)),
        setOf(BookingRow(b, e, s1, child, parent)),
    )

    @Test
    fun `3 a parent sees exactly the slots that still have room`() {
        assertEquals(emptyList(), AssignmentChecks.freeSlotsHaveRoom(e, reply(200, """[{"id": "$s2"}]"""), snapshot))
        assertEquals(1, AssignmentChecks.freeSlotsHaveRoom(e, reply(200, """[{"id": "$s1"}, {"id": "$s2"}]"""), snapshot).size)
    }

    @Test
    fun `4 a move or cancel that succeeds has taken effect`() {
        val moved = snapshot.copy(bookings = setOf(BookingRow(b, e, s2, child, parent)))

        assertEquals(emptyList(), AssignmentChecks.moveOrCancelTookEffect(Action.Move(parent, e, b, s2), Reply(200, null), moved))
        assertEquals(1, AssignmentChecks.moveOrCancelTookEffect(Action.Move(parent, e, b, s2), Reply(200, null), snapshot).size)
        assertEquals(1, AssignmentChecks.moveOrCancelTookEffect(Action.Cancel(parent, e, b), Reply(204, null), snapshot).size)
    }

    @Test
    fun `5 every parent of the child sees the booking`() {
        val other = UUID.randomUUID()
        val parentsOf = mapOf(child to listOf(parent, other))
        val sees = reply(200, """[{"id": "$b", "start": "19:00:00"}]""")
        val blind = reply(200, "[]")
        val staleTime = reply(200, """[{"id": "$b", "start": "19:15:00"}]""")

        assertEquals(emptyList(), AssignmentChecks.everyParentSeesBooking(snapshot, parentsOf, mapOf(parent to sees, other to sees)))
        assertEquals(1, AssignmentChecks.everyParentSeesBooking(snapshot, parentsOf, mapOf(parent to sees, other to blind)).size)
        assertEquals(1, AssignmentChecks.everyParentSeesBooking(snapshot, parentsOf, mapOf(parent to sees, other to staleTime)).size)
        assertEquals(emptyList(), AssignmentChecks.cancelledIsGone(b, mapOf(parent to blind, other to blind)))
        assertEquals(1, AssignmentChecks.cancelledIsGone(b, mapOf(parent to blind, other to sees)).size)
    }

    @Test
    fun `6 the overview shows every slot and who comes when with which parent`() {
        val good = reply(200, """[
            {"slotId": "$s1", "start": "19:00:00", "conversations": [{"bookingId": "$b", "childName": "Emma", "parentName": "Anna"}]},
            {"slotId": "$s2", "start": "19:15:00", "conversations": []}]""")
        val missingSlot = reply(200, """[
            {"slotId": "$s1", "start": "19:00:00", "conversations": [{"bookingId": "$b", "childName": "Emma", "parentName": "Anna"}]}]""")
        val noParent = reply(200, """[
            {"slotId": "$s1", "start": "19:00:00", "conversations": [{"bookingId": "$b", "childName": "Emma", "parentName": ""}]},
            {"slotId": "$s2", "start": "19:15:00", "conversations": []}]""")

        val wrongChild = reply(200, """[
            {"slotId": "$s1", "start": "19:00:00", "conversations": [{"bookingId": "$b", "childName": "Daan", "parentName": "Anna"}]},
            {"slotId": "$s2", "start": "19:15:00", "conversations": []}]""")
        val extra = reply(200, """[
            {"slotId": "$s1", "start": "19:00:00", "conversations": [{"bookingId": "$b", "childName": "Emma", "parentName": "Anna"}]},
            {"slotId": "$s2", "start": "19:15:00", "conversations": [{"bookingId": "${UUID.randomUUID()}", "childName": "Emma", "parentName": "Anna"}]}]""")
        val names = mapOf(child to "Emma", parent to "Anna")::getValue

        assertEquals(emptyList(), AssignmentChecks.overviewShowsEverything(e, good, snapshot, names))
        assertTrue(AssignmentChecks.overviewShowsEverything(e, missingSlot, snapshot, names).isNotEmpty())
        assertTrue(AssignmentChecks.overviewShowsEverything(e, noParent, snapshot, names).isNotEmpty())
        assertTrue(AssignmentChecks.overviewShowsEverything(e, wrongChild, snapshot, names).isNotEmpty())
        assertTrue(AssignmentChecks.overviewShowsEverything(e, extra, snapshot, names).isNotEmpty())
    }

    @Test
    fun `7 a removed slot is gone from the overview and the free slots`() {
        val overview = reply(200, """[{"slotId": "$s1", "conversations": []}]""")

        assertEquals(emptyList(), AssignmentChecks.removedSlotIsGone(s2, overview, reply(200, "[]")))
        assertEquals(1, AssignmentChecks.removedSlotIsGone(s2, overview, reply(200, """[{"id": "$s2"}]""")).size)
    }

    @Test
    fun `the evening list shows a teacher their own evenings and a parent the upcoming ones of their children's teachers`() {
        val school = School(listOf(t), listOf(parent), listOf(child), mapOf(child to listOf(parent)), mapOf(child to listOf(t)))
        val now = java.time.LocalDateTime.parse("2030-02-01T12:00")
        val right = reply(200, """[{"id": "$e"}]""")
        val empty = reply(200, "[]")

        assertEquals(emptyList(), AssignmentChecks.eveningListIsRight(t, right, snapshot, now, school))
        assertEquals(emptyList(), AssignmentChecks.eveningListIsRight(parent, right, snapshot, now, school))
        assertEquals(1, AssignmentChecks.eveningListIsRight(parent, empty, snapshot, now, school).size)
        assertEquals(1, AssignmentChecks.eveningListIsRight(parent, right, snapshot, now.plusDays(1), school).size)
    }
}
