package nl.assessment.conferences.simulation

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals

/** After a wave, what the app confirmed must still be there: fewer bookings is always "valid", so invariants miss lost writes. */
@Tag("simulation")
class ConcurrentChecksTest {

    private val e = UUID.randomUUID()
    private val s1 = UUID.randomUUID()
    private val s2 = UUID.randomUUID()
    private val kept = UUID.randomUUID()
    private val moved = UUID.randomUUID()
    private val after = Snapshot(
        setOf(EveningRow(e, UUID.randomUUID(), LocalDate.parse("2030-02-01"), LocalTime.parse("19:00"), LocalTime.parse("19:30"), 15)),
        setOf(SlotRow(s1, e, LocalTime.parse("19:00"), LocalTime.parse("19:15"), 1), SlotRow(s2, e, LocalTime.parse("19:15"), LocalTime.parse("19:30"), 1)),
        setOf(BookingRow(kept, e, s1, UUID.randomUUID(), UUID.randomUUID()), BookingRow(moved, e, s2, UUID.randomUUID(), UUID.randomUUID())),
    )

    @Test
    fun `confirmed bookings and moves that stand give no findings`() {
        assertEquals(emptyList(), ConcurrentChecks.confirmedWritesStand(listOf(kept), listOf(moved to s2), after))
    }

    @Test
    fun `a confirmed booking that is gone, or a move that did not land, is a finding`() {
        assertEquals(1, ConcurrentChecks.confirmedWritesStand(listOf(UUID.randomUUID()), emptyList(), after).size)
        assertEquals(1, ConcurrentChecks.confirmedWritesStand(emptyList(), listOf(moved to s1), after).size)
    }
}
