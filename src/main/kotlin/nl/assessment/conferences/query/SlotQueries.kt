package nl.assessment.conferences.query

import nl.assessment.conferences.domain.EveningId
import nl.assessment.conferences.domain.ParentId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalTime
import java.util.UUID

data class FreeSlot(val id: UUID, val start: LocalTime, val end: LocalTime, val capacity: Int, val freePlaces: Int)

@Service
class SlotQueries(private val jdbc: JdbcTemplate) {

    /** Slots with free places, only for evenings of the parent's children's teachers. Counts only, no names. */
    fun freeSlots(parentId: ParentId, eveningId: EveningId): List<FreeSlot> = jdbc.query(
        """
        SELECT id, start_time, end_time, capacity, free_places FROM slot_availability
        WHERE evening_id = ? AND free_places > 0
        AND teacher_id IN (
            SELECT tc.teacher_id FROM teacher_child tc
            JOIN parent_child pc ON pc.child_id = tc.child_id
            WHERE pc.parent_id = ?
        )
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
        eveningId.value, parentId.value,
    )
}
