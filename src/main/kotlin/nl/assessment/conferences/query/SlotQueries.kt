package nl.assessment.conferences.query

import nl.assessment.conferences.domain.EveningId
import nl.assessment.conferences.domain.EveningNotFound
import nl.assessment.conferences.domain.ParentId
import nl.assessment.conferences.domain.User
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

data class FreeSlot(val id: UUID, val start: LocalTime, val end: LocalTime, val capacity: Int, val freePlaces: Int)

@Service
class SlotQueries(private val jdbc: JdbcTemplate, private val evenings: EveningQueries, private val clock: Clock) {

    /**
     * Slots with free places that have not started yet. Counts only, no names.
     * A parent only gets slots of an evening that is in their own list: upcoming, at their children's teachers.
     */
    fun freeSlots(parentId: ParentId, eveningId: EveningId): List<FreeSlot> {
        if (evenings.evenings(User.Parent(parentId)).none { it.id == eveningId.value }) throw EveningNotFound()
        return freeSlotRows(eveningId)
    }

    private fun freeSlotRows(eveningId: EveningId): List<FreeSlot> {
        val now = LocalDateTime.now(clock)
        return jdbc.query(
            """
            SELECT * FROM slot_availability
            WHERE evening_id = ? AND free_places > 0 AND (date > ? OR start_time > ?)
            ORDER BY start_time
            """,
            { rs, _ ->
                FreeSlot(
                    rs.getObject("id", UUID::class.java),
                    rs.getObject("start_time", LocalTime::class.java),
                    rs.getObject("end_time", LocalTime::class.java),
                    rs.getInt("capacity"),
                    rs.getInt("free_places"),
                )
            },
            eveningId.value, now.toLocalDate(), now.toLocalTime(),
        )
    }
}
