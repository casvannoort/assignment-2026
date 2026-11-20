package nl.assessment.conferences.simulation

import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class EveningRow(val id: UUID, val teacher: UUID, val date: LocalDate, val start: LocalTime, val end: LocalTime, val minutes: Int)
data class SlotRow(val id: UUID, val evening: UUID, val start: LocalTime, val end: LocalTime, val capacity: Int)
data class BookingRow(val id: UUID, val evening: UUID, val slot: UUID, val child: UUID, val bookedBy: UUID)

/** Every row the app writes, read straight from the tables, not through the app. */
data class Snapshot(val evenings: Set<EveningRow>, val slots: Set<SlotRow>, val bookings: Set<BookingRow>) {
    companion object {
        fun take(jdbc: JdbcTemplate) = Snapshot(
            jdbc.query("SELECT * FROM evening") { rs, _ ->
                EveningRow(
                    rs.getObject("id", UUID::class.java), rs.getObject("teacher_id", UUID::class.java),
                    rs.getObject("date", LocalDate::class.java), rs.getObject("start_time", LocalTime::class.java),
                    rs.getObject("end_time", LocalTime::class.java), rs.getInt("conversation_minutes"),
                )
            }.toSet(),
            jdbc.query("SELECT * FROM slot") { rs, _ ->
                SlotRow(
                    rs.getObject("id", UUID::class.java), rs.getObject("evening_id", UUID::class.java),
                    rs.getObject("start_time", LocalTime::class.java), rs.getObject("end_time", LocalTime::class.java),
                    rs.getInt("capacity"),
                )
            }.toSet(),
            jdbc.query("SELECT * FROM booking") { rs, _ ->
                BookingRow(
                    rs.getObject("id", UUID::class.java), rs.getObject("evening_id", UUID::class.java),
                    rs.getObject("slot_id", UUID::class.java), rs.getObject("child_id", UUID::class.java),
                    rs.getObject("booked_by", UUID::class.java),
                )
            }.toSet(),
        )
    }
}
