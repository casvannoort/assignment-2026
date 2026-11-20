package nl.assessment.conferences.query

import nl.assessment.conferences.TestcontainersConfiguration
import nl.assessment.conferences.domain.ChildId
import nl.assessment.conferences.domain.ParentId
import nl.assessment.conferences.domain.TeacherId
import nl.assessment.conferences.domain.User
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `knows which parents and teachers a child has`() {
        val emma = ChildId(UUID.fromString("00000000-0000-0000-0000-000000000301"))
        val daan = ChildId(UUID.fromString("00000000-0000-0000-0000-000000000302"))
        val mrDeVries = TeacherId(UUID.fromString("00000000-0000-0000-0000-000000000102"))

        assertTrue(users.isParentOf(ParentId(anna), emma))
        assertFalse(users.isParentOf(ParentId(anna), daan))
        assertTrue(users.isTeacherOf(mrDeVries, emma))
        assertFalse(users.isTeacherOf(mrDeVries, daan))
    }
}
