package nl.assessment.conferences.db

import nl.assessment.conferences.domain.Booking
import nl.assessment.conferences.domain.BookingId
import nl.assessment.conferences.domain.ChildId
import nl.assessment.conferences.domain.ConferenceEvening
import nl.assessment.conferences.domain.EveningId
import nl.assessment.conferences.domain.ParentId
import nl.assessment.conferences.domain.Slot
import nl.assessment.conferences.domain.SlotId
import nl.assessment.conferences.domain.TeacherId
import nl.assessment.conferences.domain.TimeRange
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Repository
class ConferenceEveningRepository(private val jdbc: JdbcTemplate) {

    fun save(evening: ConferenceEvening) {
        jdbc.update(
            """
            INSERT INTO evening (id, teacher_id, date, start_time, end_time, conversation_minutes)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """,
            evening.id.value, evening.teacherId.value, evening.date,
            evening.time.start, evening.time.end, evening.conversationLength.toMinutes(),
        )
        evening.slots.forEach { slot ->
            jdbc.update(
                """
                INSERT INTO slot (id, evening_id, start_time, end_time, capacity)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                slot.id.value, evening.id.value, slot.time.start, slot.time.end, slot.capacity,
            )
        }
        // Delete before inserting: a child can cancel and book again, and the unique (evening_id, child_id) must not trip.
        deleteRowsNoLongerIn("booking", evening.id, evening.bookings.map { it.id.value })
        deleteRowsNoLongerIn("slot", evening.id, evening.slots.map { it.id.value })
        evening.bookings.forEach { booking ->
            jdbc.update(
                """
                INSERT INTO booking (id, evening_id, slot_id, child_id, booked_by)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET slot_id = excluded.slot_id, booked_by = excluded.booked_by
                """,
                booking.id.value, evening.id.value, booking.slotId.value, booking.childId.value, booking.bookedBy.value,
            )
        }
    }

    private fun deleteRowsNoLongerIn(table: String, eveningId: EveningId, keep: List<UUID>) {
        jdbc.update(
            "DELETE FROM $table WHERE evening_id = ? AND id <> ALL (?::uuid[])",
            eveningId.value, keep.joinToString(",", "{", "}"),
        )
    }

    /** Locks the teacher until the transaction ends, so their evenings can be checked and changed safely. */
    fun lockTeacher(teacherId: TeacherId) {
        jdbc.query("SELECT id FROM teacher WHERE id = ? FOR UPDATE", { _ -> }, teacherId.value)
    }

    fun hasOverlappingEvening(teacherId: TeacherId, date: LocalDate, time: TimeRange): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM evening WHERE teacher_id = ? AND date = ? AND start_time < ? AND ? < end_time)",
            Boolean::class.java,
            teacherId.value, date, time.end, time.start,
        )!!

    /** Locks the child until the transaction ends, so their bookings across evenings can be checked safely. */
    fun lockChild(childId: ChildId) {
        jdbc.query("SELECT id FROM child WHERE id = ? FOR UPDATE", { _ -> }, childId.value)
    }

    fun hasOverlappingBooking(childId: ChildId, date: LocalDate, time: TimeRange, otherThan: EveningId): Boolean =
        jdbc.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1 FROM booking b
                JOIN slot s ON s.id = b.slot_id
                JOIN evening e ON e.id = b.evening_id
                WHERE b.child_id = ? AND e.id <> ? AND e.date = ? AND s.start_time < ? AND ? < s.end_time
            )
            """,
            Boolean::class.java,
            childId.value, otherThan.value, date, time.end, time.start,
        )!!

    /** Loads the evening and locks its row until the transaction ends (pessimistic lock). */
    fun findById(id: EveningId): ConferenceEvening? {
        // Lock first, so the slots and bookings read below cannot change until the transaction ends.
        val exists = jdbc.query("SELECT id FROM evening WHERE id = ? FOR UPDATE", { _, _ -> }, id.value).isNotEmpty()
        if (!exists) return null
        val slots = jdbc.query(
            "SELECT id, start_time, end_time, capacity FROM slot WHERE evening_id = ? ORDER BY start_time",
            { rs, _ -> Slot(SlotId(rs.uuid("id")), rs.timeRange(), rs.getInt("capacity")) },
            id.value,
        )
        val bookings = jdbc.query(
            "SELECT id, slot_id, child_id, booked_by FROM booking WHERE evening_id = ?",
            { rs, _ ->
                Booking(
                    BookingId(rs.uuid("id")), SlotId(rs.uuid("slot_id")), ChildId(rs.uuid("child_id")),
                    ParentId(rs.uuid("booked_by")),
                )
            },
            id.value,
        )
        return jdbc.query(
            "SELECT teacher_id, date, start_time, end_time, conversation_minutes FROM evening WHERE id = ?",
            { rs, _ ->
                ConferenceEvening(
                    id = id,
                    teacherId = TeacherId(rs.uuid("teacher_id")),
                    date = rs.getObject("date", LocalDate::class.java),
                    time = rs.timeRange(),
                    conversationLength = Duration.ofMinutes(rs.getLong("conversation_minutes")),
                    slots = slots,
                    bookings = bookings,
                )
            },
            id.value,
        ).singleOrNull()
    }

    private fun ResultSet.uuid(column: String) = getObject(column, UUID::class.java)

    private fun ResultSet.timeRange() =
        TimeRange(getObject("start_time", LocalTime::class.java), getObject("end_time", LocalTime::class.java))
}
