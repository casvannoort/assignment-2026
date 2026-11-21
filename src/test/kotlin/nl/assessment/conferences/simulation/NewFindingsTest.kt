package nl.assessment.conferences.simulation

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** A broken state stays broken: report it when it appears, not at every later check, but again when it comes back. */
@Tag("simulation")
class NewFindingsTest {

    private val overbooked = Finding("L2.2 capacity", "slot 1 has 2 bookings")

    @Test
    fun `a violation that lasts is reported once`() {
        val new = NewFindings()

        assertEquals(listOf(overbooked), new.of(listOf(overbooked)))
        assertEquals(emptyList(), new.of(listOf(overbooked)))
    }

    @Test
    fun `a violation that disappears and comes back is reported again`() {
        val new = NewFindings()

        new.of(listOf(overbooked))
        new.of(emptyList())

        assertEquals(listOf(overbooked), new.of(listOf(overbooked)))
    }
}
