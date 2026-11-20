package nl.assessment.conferences.query

import nl.assessment.conferences.domain.ParentId
import nl.assessment.conferences.domain.TeacherId
import nl.assessment.conferences.domain.User
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserDirectory(private val jdbc: JdbcTemplate) {

    fun find(id: UUID): User? = jdbc.query(
        "SELECT role FROM app_user WHERE id = ?",
        { rs, _ -> if (rs.getString("role") == "TEACHER") User.Teacher(TeacherId(id)) else User.Parent(ParentId(id)) },
        id,
    ).singleOrNull()
}
