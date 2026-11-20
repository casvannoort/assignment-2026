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

@Service
class EveningQueries(private val jdbc: JdbcTemplate, private val clock: Clock) {

    private val select = """
        SELECT e.id, t.name AS teacher_name, e.date, e.start_time, e.end_time, e.conversation_minutes
        FROM evening e JOIN teacher t ON t.id = e.teacher_id
    """

    fun evenings(user: User): List<EveningSummary> = when (user) {
        is User.Teacher -> jdbc.query(
            "$select WHERE e.teacher_id = ? ORDER BY e.date, e.start_time",
            ::summary, user.id.value,
        )
        is User.Parent -> {
            val now = LocalDateTime.now(clock)
            jdbc.query(
                """
                $select
                WHERE e.teacher_id IN (
                    SELECT tc.teacher_id FROM teacher_child tc
                    JOIN parent_child pc ON pc.child_id = tc.child_id
                    WHERE pc.parent_id = ?
                )
                AND (e.date > ? OR (e.date = ? AND e.end_time > ?))
                ORDER BY e.date, e.start_time
                """,
                ::summary, user.id.value, now.toLocalDate(), now.toLocalDate(), now.toLocalTime(),
            )
        }
    }

    private fun summary(rs: ResultSet, row: Int) = EveningSummary(
        rs.getObject("id", UUID::class.java),
        rs.getString("teacher_name"),
        rs.getObject("date", LocalDate::class.java),
        rs.getObject("start_time", LocalTime::class.java),
        rs.getObject("end_time", LocalTime::class.java),
        rs.getInt("conversation_minutes"),
    )
}
