package nl.assessment.conferences.web

import nl.assessment.conferences.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.MediaType
import com.jayway.jsonpath.JsonPath
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class EveningApiTest(@Autowired private val mvc: MockMvc, @Autowired private val jdbc: JdbcTemplate) {

    private val msJansen = "00000000-0000-0000-0000-000000000101"
    private val mrDeVries = "00000000-0000-0000-0000-000000000102"
    private val anna = "00000000-0000-0000-0000-000000000201"
    private val kees = "00000000-0000-0000-0000-000000000204" // only Lisa's parent, Lisa only has Mr. de Vries

    private val date = LocalDate.parse("2030-01-01").plusDays(Random.nextLong(10_000))

    private fun createEvening(user: String?, start: String = "19:00", end: String = "20:00") =
        mvc.post("/evenings") {
            user?.let { header("X-User-Id", it) }
            contentType = MediaType.APPLICATION_JSON
            content = """{"date": "$date", "start": "$start", "end": "$end", "conversationMinutes": 15}"""
        }

    @Test
    fun `a teacher creates an evening with generated slots`() {
        createEvening(msJansen).andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.slots.length()") { value(4) }
            jsonPath("$.slots[0].start") { value("19:00:00") }
            jsonPath("$.slots[0].capacity") { value(1) }
        }
    }

    @Test
    fun `an unknown user gets 401`() {
        createEvening(user = null).andExpect { status { isUnauthorized() } }
        createEvening(user = "00000000-0000-0000-0000-000000000999").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a parent cannot create an evening`() {
        createEvening(anna).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `an invalid evening gives 400 with a code`() {
        createEvening(msJansen, start = "20:00", end = "19:00").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_EVENING") }
        }
    }

    @Test
    fun `an overlapping evening gives 409 with a code`() {
        createEvening(msJansen)

        createEvening(msJansen).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("EVENING_OVERLAPS") }
        }
    }

    private fun createdId(user: String) =
        JsonPath.read<String>(createEvening(user).andReturn().response.contentAsString, "$.id")

    private fun listEvenings(user: String) = mvc.get("/evenings") { header("X-User-Id", user) }

    @Test
    fun `a teacher sees their own evenings, not those of another teacher`() {
        val own = createdId(msJansen)
        val other = createdId(mrDeVries)

        listEvenings(msJansen).andExpect {
            status { isOk() }
            jsonPath("$[*].id") { value(hasItem(own)) }
            jsonPath("$[*].id") { value(not(hasItem(other))) }
        }
    }

    @Test
    fun `a parent sees only evenings of their children's teachers`() {
        val ofLisasTeacher = createdId(mrDeVries)
        val ofAnotherTeacher = createdId(msJansen)

        listEvenings(kees).andExpect {
            status { isOk() }
            jsonPath("$[*].id") { value(hasItem(ofLisasTeacher)) }
            jsonPath("$[*].id") { value(not(hasItem(ofAnotherTeacher))) }
            jsonPath("$[0].teacherName") { exists() }
        }
    }

    @Test
    fun `a parent does not see evenings that have ended`() {
        val ended = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO evening VALUES (?, ?::uuid, DATE '2020-01-01', TIME '19:00', TIME '20:00', 15)",
            ended, mrDeVries,
        )

        listEvenings(kees).andExpect { jsonPath("$[*].id") { value(not(hasItem(ended.toString()))) } }
    }
}
