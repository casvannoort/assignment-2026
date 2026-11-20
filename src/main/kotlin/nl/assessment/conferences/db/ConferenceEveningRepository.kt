package nl.assessment.conferences.db

import nl.assessment.conferences.domain.ConferenceEvening
import nl.assessment.conferences.domain.EveningId
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

    fun findById(id: EveningId): ConferenceEvening? {
        val slots = jdbc.query(
            "SELECT id, start_time, end_time, capacity FROM slot WHERE evening_id = ? ORDER BY start_time",
            { rs, _ -> Slot(SlotId(rs.uuid("id")), rs.timeRange(), rs.getInt("capacity")) },
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
                )
            },
            id.value,
        ).singleOrNull()
    }

    private fun ResultSet.uuid(column: String) = getObject(column, UUID::class.java)

    private fun ResultSet.timeRange() =
        TimeRange(getObject("start_time", LocalTime::class.java), getObject("end_time", LocalTime::class.java))
}
