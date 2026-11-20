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

    private fun summary(rs: ResultSet, row: Int) = EveningSummary(
        rs.getObject("id", UUID::class.java),
        rs.getString("teacher_name"),
        rs.getObject("date", LocalDate::class.java),
        rs.getObject("start_time", LocalTime::class.java),
        rs.getObject("end_time", LocalTime::class.java),
        rs.getInt("conversation_minutes"),
    )
}
