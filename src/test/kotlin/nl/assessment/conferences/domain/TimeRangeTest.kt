package nl.assessment.conferences.domain

import org.junit.jupiter.api.Test
import java.time.LocalTime
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeRangeTest {

    private fun range(start: String, end: String) = TimeRange(LocalTime.parse(start), LocalTime.parse(end))

    @Test
    fun `end must be after start`() {
        assertFailsWith<IllegalArgumentException> { range("19:00", "19:00") }
        assertFailsWith<IllegalArgumentException> { range("19:00", "18:00") }
    }

    @Test
    fun `ranges that share time overlap`() {
        assertTrue(range("19:00", "19:15").overlaps(range("19:10", "19:30")))
        assertTrue(range("19:00", "21:00").overlaps(range("19:30", "19:45")))
    }

    @Test
    fun `ranges that only touch do not overlap`() {
        assertFalse(range("19:00", "19:15").overlaps(range("19:15", "19:30")))
    }
}
