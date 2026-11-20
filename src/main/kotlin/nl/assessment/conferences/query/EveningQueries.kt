package nl.assessment.conferences.query

import nl.assessment.conferences.domain.User
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.ResultSet
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

data class EveningSummary(
    val id: UUID,
    val teacherName: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val conversationMinutes: Int,
)

data class Conversation(val bookingId: UUID, val childName: String, val parentName: String)

data class OverviewSlot(
    val slotId: UUID,
    val start: LocalTime,
    val end: LocalTime,
    val capacity: Int,
    val conversations: List<Conversation>,
)

@Service
class EveningQueries(private val jdbc: JdbcTemplate, private val clock: Clock) {

    private val select = "SELECT * FROM evening_summary"

    fun evenings(user: User): List<EveningSummary> = when (user) {
        is User.Teacher -> jdbc.query(
            "$select WHERE teacher_id = ? ORDER BY date, start_time",
            ::summary, user.id.value,
        )
        is User.Parent -> {
            val now = LocalDateTime.now(clock)
            jdbc.query(
                """
                $select
                WHERE teacher_id IN (
                    SELECT tc.teacher_id FROM teacher_child tc
                    JOIN parent_child pc ON pc.child_id = tc.child_id
                    WHERE pc.parent_id = ?
                )
                AND (date > ? OR (date = ? AND end_time > ?))
                ORDER BY date, start_time
                """,
                ::summary, user.id.value, now.toLocalDate(), now.toLocalDate(), now.toLocalTime(),
            )
        }
    }

    fun teacherOf(eveningId: UUID): UUID? = jdbc.query(
        "SELECT teacher_id FROM evening_summary WHERE id = ?",
        { rs, _ -> rs.getObject("teacher_id", UUID::class.java) },
        eveningId,
    ).singleOrNull()

    /** Every slot of the evening in time order, with its conversations (none for an empty slot). */
    fun overview(eveningId: UUID): List<OverviewSlot> = jdbc.query(
        "SELECT * FROM evening_overview WHERE evening_id = ? ORDER BY start_time",
        { rs, _ ->
            val conversation = rs.getObject("booking_id", UUID::class.java)?.let {
                Conversation(it, rs.getString("child_name"), rs.getString("parent_name"))
            }
            OverviewSlot(
                rs.getObject("slot_id", UUID::class.java),
                rs.getObject("start_time", LocalTime::class.java),
                rs.getObject("end_time", LocalTime::class.java),
                rs.getInt("capacity"),
                listOfNotNull(conversation),
            )
        },
        eveningId,
    ).groupBy { it.slotId }.map { (_, rows) -> rows.first().copy(conversations = rows.flatMap { it.conversations }) }

    private fun summary(rs: ResultSet, row: Int) = EveningSummary(
        rs.getObject("id", UUID::class.java),
        rs.getString("teacher_name"),
        rs.getObject("date", LocalDate::class.java),
        rs.getObject("start_time", LocalTime::class.java),
        rs.getObject("end_time", LocalTime::class.java),
        rs.getInt("conversation_minutes"),
    )
}
