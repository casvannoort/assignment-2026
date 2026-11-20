package nl.assessment.conferences.web

import nl.assessment.conferences.TestDates
import nl.assessment.conferences.TestcontainersConfiguration
import nl.assessment.conferences.command.BookingCommands
import nl.assessment.conferences.command.EveningCommands
import nl.assessment.conferences.domain.ChildId
import nl.assessment.conferences.domain.ParentId
import nl.assessment.conferences.domain.TeacherId
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Duration
import java.time.LocalTime
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class BookingApiTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val evenings: EveningCommands,
    @Autowired private val bookings: BookingCommands,
) {

    private fun id(n: Int) = UUID.fromString("00000000-0000-0000-0000-000000000$n")
    private val msJansen = id(101)
    private val anna = id(201)
    private val sara = id(203)
    private val emma = id(301)

    private fun eveningAtJansen(capacity: Int = 1) = evenings.create(
        TeacherId(msJansen), TestDates.unique(), LocalTime.parse("19:00"), LocalTime.parse("19:30"),
        Duration.ofMinutes(15), capacity,
    )

    @Test
    fun `a parent sees the free slots of an evening, with free places`() {
        val evening = eveningAtJansen(capacity = 2)
        bookings.book(ParentId(anna), evening.id, ChildId(emma), evening.slots[0].id)

        mvc.get("/evenings/${evening.id.value}/slots") { header("X-User-Id", sara) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].start") { value("19:00:00") }
            jsonPath("$[0].freePlaces") { value(1) }
            jsonPath("$[1].freePlaces") { value(2) }
        }
    }

    @Test
    fun `a full slot is not listed`() {
        val evening = eveningAtJansen(capacity = 1)
        bookings.book(ParentId(anna), evening.id, ChildId(emma), evening.slots[0].id)

        mvc.get("/evenings/${evening.id.value}/slots") { header("X-User-Id", sara) }.andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].start") { value("19:15:00") }
        }
    }

    @Test
    fun `a teacher uses the overview instead of the slot list`() {
        val evening = eveningAtJansen()

        mvc.get("/evenings/${evening.id.value}/slots") { header("X-User-Id", msJansen) }.andExpect {
            status { isForbidden() }
        }
    }
}
