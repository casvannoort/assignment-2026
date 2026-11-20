package nl.assessment.conferences.db

import nl.assessment.conferences.TestcontainersConfiguration
import nl.assessment.conferences.domain.ParentId
import nl.assessment.conferences.domain.TeacherId
import nl.assessment.conferences.domain.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class UserDirectoryTest(@Autowired private val users: UserDirectory) {

    private val msJansen = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val anna = UUID.fromString("00000000-0000-0000-0000-000000000201")

    @Test
    fun `finds a teacher`() {
        assertEquals(User.Teacher(TeacherId(msJansen)), users.find(msJansen))
    }

    @Test
    fun `finds a parent`() {
        assertEquals(User.Parent(ParentId(anna)), users.find(anna))
    }

    @Test
    fun `finds nobody for an unknown id`() {
        assertNull(users.find(UUID.randomUUID()))
    }
}
