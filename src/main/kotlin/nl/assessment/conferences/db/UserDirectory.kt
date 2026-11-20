package nl.assessment.conferences.db

import nl.assessment.conferences.domain.ParentId
import nl.assessment.conferences.domain.TeacherId
import nl.assessment.conferences.domain.User
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class UserDirectory(private val jdbc: JdbcTemplate) {

    fun find(id: UUID): User? = jdbc.query(
        "SELECT 'TEACHER' FROM teacher WHERE id = ? UNION ALL SELECT 'PARENT' FROM parent WHERE id = ?",
        { rs, _ -> if (rs.getString(1) == "TEACHER") User.Teacher(TeacherId(id)) else User.Parent(ParentId(id)) },
        id, id,
    ).singleOrNull()
}
