package nl.assessment.conferences.simulation

import java.util.UUID

/** Checks for concurrent waves that the invariants cannot make: a lost write breaks no rule, it just disappears. */
object ConcurrentChecks {

    fun confirmedWritesStand(created: List<UUID>, moved: List<Pair<UUID, UUID>>, after: Snapshot): List<Finding> {
        val byId = after.bookings.associateBy { it.id }
        return created.filter { it !in byId }.map { Finding("C.1 confirmed booking exists", "booking $it got 201 but is gone") } +
            moved.filter { (booking, slot) -> byId[booking]?.slot != slot }
                .map { (booking, slot) -> Finding("C.2 confirmed move took effect", "booking $booking got 200 for slot $slot but is elsewhere") }
    }
}
