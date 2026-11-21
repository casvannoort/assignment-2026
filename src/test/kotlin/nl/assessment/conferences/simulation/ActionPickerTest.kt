package nl.assessment.conferences.simulation

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertTrue

@Tag("simulation")
class ActionPickerTest {

    private val school = SchoolGenerator.generate(seed = 1, teachers = 3, children = 12)
    private val now = LocalDateTime.parse("2030-02-01T12:00")

    /** Three evenings that ended yesterday and one that is still to come, each with one slot and one booking. */
    private val state: State = run {
        val days = listOf(-1L, -1L, -1L, 1L)
        val evenings = days.mapIndexed { i, day ->
            EveningRow(UUID.randomUUID(), school.teachers[i % 3], now.toLocalDate().plusDays(day), LocalTime.of(19, 0), LocalTime.of(20, 0), 60)
        }
        val slots = evenings.map { SlotRow(UUID.randomUUID(), it.id, LocalTime.of(19, 0), LocalTime.of(20, 0), 3) }
        val bookings = slots.mapIndexed { i, slot ->
            val child = school.children[i]
            BookingRow(UUID.randomUUID(), slot.evening, slot.id, child, school.parentsOf.getValue(child).first())
        }
        State(evenings.map { it.id }, Snapshot(evenings.toSet(), slots.toSet(), bookings.toSet()), now)
    }
    private val open = state.evenings.last()

    @Test
    fun `the picker mostly picks evenings that are still open, and sometimes an ended one`() {
        val picker = ActionPicker(Random(7), school)

        val picked = List(2000) { picker.next(state) }.mapNotNull { it.evening }
        val openShare = picked.count { it == open }.toDouble() / picked.size

        assertTrue(openShare > 0.6, "open evening picked ${"%.2f".format(openShare)} of the time")
        assertTrue(picked.any { it != open }, "ended evenings are never picked")
    }
}
