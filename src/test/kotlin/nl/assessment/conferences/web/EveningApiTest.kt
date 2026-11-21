package nl.assessment.conferences.web

import nl.assessment.conferences.TestDates
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class EveningApiTest(@Autowired private val mvc: MockMvc, @Autowired private val jdbc: JdbcTemplate) {

    private val msJansen = "00000000-0000-0000-0000-000000000101"
    private val mrDeVries = "00000000-0000-0000-0000-000000000102"
    private val anna = "00000000-0000-0000-0000-000000000201"
    private val kees = "00000000-0000-0000-0000-000000000204" // only Lisa's parent, Lisa only has Mr. de Vries

    private val date = TestDates.unique()

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
    fun `a conversation length too large for a duration gives 400, not 500`() {
        mvc.post("/evenings") {
            header("X-User-Id", msJansen)
            contentType = MediaType.APPLICATION_JSON
            content = """{"date": "$date", "start": "19:00", "end": "20:00", "conversationMinutes": 9223372036854775807}"""
        }.andExpect {
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

    @Test
    fun `a malformed body gives 400 with a code`() {
        listOf("""{"date": "not a date"}""", """{"start": "19:00"}""", "not json").forEach { body ->
            mvc.post("/evenings") {
                header("X-User-Id", msJansen)
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_REQUEST") }
            }
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

    @Test
    fun `a teacher removes a slot, but not one with a booking`() {
        val response = createEvening(msJansen).andReturn().response.contentAsString
        val evening = JsonPath.read<String>(response, "$.id")
        val (first, second) = JsonPath.read<List<String>>(response, "$.slots[*].id")
        mvc.post("/evenings/$evening/bookings") {
            header("X-User-Id", anna)
            contentType = MediaType.APPLICATION_JSON
            content = """{"childId": "00000000-0000-0000-0000-000000000301", "slotId": "$first"}"""
        }

        mvc.delete("/evenings/$evening/slots/$second") { header("X-User-Id", msJansen) }.andExpect {
            status { isNoContent() }
        }
        mvc.delete("/evenings/$evening/slots/$first") { header("X-User-Id", msJansen) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SLOT_HAS_BOOKINGS") }
        }
    }

    @Test
    fun `a teacher sees per slot which child comes with which parent, empty slots too`() {
        val response = createEvening(msJansen, start = "19:00", end = "19:30").andReturn().response.contentAsString
        val evening = JsonPath.read<String>(response, "$.id")
        mvc.post("/evenings/$evening/bookings") {
            header("X-User-Id", anna)
            contentType = MediaType.APPLICATION_JSON
            content = """{"childId": "00000000-0000-0000-0000-000000000301", "slotId": "${JsonPath.read<String>(response, "$.slots[1].id")}"}"""
        }

        mvc.get("/evenings/$evening/overview") { header("X-User-Id", msJansen) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].start") { value("19:00:00") }
            jsonPath("$[0].conversations.length()") { value(0) }
            jsonPath("$[1].start") { value("19:15:00") }
            jsonPath("$[1].conversations[0].childName") { value("Emma Bakker") }
            jsonPath("$[1].conversations[0].parentName") { value("Anna Bakker") }
        }
    }

    @Test
    fun `only the teacher of the evening sees its overview`() {
        val evening = createdId(msJansen)

        mvc.get("/evenings/$evening/overview") { header("X-User-Id", mrDeVries) }.andExpect { status { isForbidden() } }
        mvc.get("/evenings/$evening/overview") { header("X-User-Id", anna) }.andExpect { status { isForbidden() } }
        mvc.get("/evenings/${UUID.randomUUID()}/overview") { header("X-User-Id", msJansen) }.andExpect {
            status { isNotFound() }
        }
    }
}
