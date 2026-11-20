package nl.assessment.conferences.query

import nl.assessment.conferences.domain.ParentId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class ChildBooking(
    val id: UUID,
    val eveningId: UUID,
    val slotId: UUID,
    val childId: UUID,
    val childName: String,
    val teacherName: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val bookedByName: String,
)

@Service
class BookingQueries(private val jdbc: JdbcTemplate) {

    /** All bookings of the parent's children, including those made by the other parent. */
    fun bookingsOfChildren(parentId: ParentId): List<ChildBooking> = jdbc.query(
        """
        SELECT * FROM child_bookings
        WHERE child_id IN (SELECT child_id FROM parent_child WHERE parent_id = ?)
        ORDER BY date, start_time
        """,
        { rs, _ ->
            ChildBooking(
                rs.getObject("id", UUID::class.java),
                rs.getObject("evening_id", UUID::class.java),
                rs.getObject("slot_id", UUID::class.java),
                rs.getObject("child_id", UUID::class.java),
                rs.getString("child_name"),
                rs.getString("teacher_name"),
                rs.getObject("date", LocalDate::class.java),
                rs.getObject("start_time", LocalTime::class.java),
                rs.getObject("end_time", LocalTime::class.java),
                rs.getString("booked_by_name"),
            )
        },
        parentId.value,
    )
}
