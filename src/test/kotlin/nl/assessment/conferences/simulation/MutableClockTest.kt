package nl.assessment.conferences.simulation

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

@Tag("simulation")
class MutableClockTest {

    @Test
    fun `time moves only when the simulation advances it`() {
        val start = Instant.parse("2030-01-06T07:00:00Z")
        val clock = MutableClock(start, ZoneId.of("Europe/Amsterdam"))

        assertEquals(start, clock.instant())
        clock.advance(Duration.ofMinutes(90))

        assertEquals(start.plus(Duration.ofMinutes(90)), clock.instant())
        assertEquals(ZoneId.of("Europe/Amsterdam"), clock.zone)
    }
}
