package nl.assessment.conferences.query

import nl.assessment.conferences.domain.EveningId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalTime
import java.util.UUID

data class FreeSlot(val id: UUID, val start: LocalTime, val end: LocalTime, val capacity: Int, val freePlaces: Int)

@Service
class SlotQueries(private val jdbc: JdbcTemplate) {

    /** Slots with free places. Counts only, no names. */
    fun freeSlots(eveningId: EveningId): List<FreeSlot> = jdbc.query(
        "SELECT * FROM slot_availability WHERE evening_id = ? AND free_places > 0 ORDER BY start_time",
        { rs, _ ->
            FreeSlot(
                rs.getObject("id", UUID::class.java),
                rs.getObject("start_time", LocalTime::class.java),
                rs.getObject("end_time", LocalTime::class.java),
                rs.getInt("capacity"),
                rs.getInt("free_places"),
            )
        },
        eveningId.value,
    )
}
