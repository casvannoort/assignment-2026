package nl.assessment.conferences.simulation

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A clock that only moves when the simulation says so, to reach "after the evening" on purpose. */
class MutableClock(@Volatile private var now: Instant, private val zone: ZoneId) : Clock() {

    fun reset(to: Instant) {
        now = to
    }

    fun advance(by: Duration) {
        now = now.plus(by)
    }

    override fun instant(): Instant = now
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
}
