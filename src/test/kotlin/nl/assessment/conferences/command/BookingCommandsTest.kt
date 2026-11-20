package nl.assessment.conferences.command

import nl.assessment.conferences.TestDates
import nl.assessment.conferences.TestcontainersConfiguration
import nl.assessment.conferences.db.ConferenceEveningRepository
import nl.assessment.conferences.domain.ChildId
import nl.assessment.conferences.domain.EveningId
import nl.assessment.conferences.domain.EveningNotFound
import nl.assessment.conferences.domain.Forbidden
import nl.assessment.conferences.domain.ParentId
import nl.assessment.conferences.domain.SlotId
import nl.assessment.conferences.domain.TeacherId
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Duration
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class BookingCommandsTest(
    @Autowired private val evenings: EveningCommands,
    @Autowired private val bookings: BookingCommands,
    @Autowired private val repository: ConferenceEveningRepository,
) {

    private fun id(n: Int) = UUID.fromString("00000000-0000-0000-0000-000000000$n")
    private val msJansen = TeacherId(id(101))
    private val mrDeVries = TeacherId(id(102))
    private val anna = ParentId(id(201))
    private val sara = ParentId(id(203))
    private val kees = ParentId(id(204))
    private val emma = ChildId(id(301))
    private val daan = ChildId(id(302))

    private fun eveningOf(teacher: TeacherId) = evenings.create(
        teacher, TestDates.unique(), LocalTime.parse("19:00"), LocalTime.parse("20:00"), Duration.ofMinutes(15), 1,
    )

    @Test
    fun `a parent books a conversation for their child`() {
        val evening = eveningOf(msJansen)

        val booking = bookings.book(anna, evening.id, emma, evening.slots[0].id)

        assertEquals(listOf(booking), assertNotNull(repository.findById(evening.id)).bookings)
    }

    @Test
    fun `a parent cannot book for a child that is not theirs`() {
        val evening = eveningOf(msJansen)

        assertFailsWith<Forbidden> { bookings.book(kees, evening.id, emma, evening.slots[0].id) }
    }

    @Test
    fun `a child can only be booked at one of their own teachers`() {
        val evening = eveningOf(mrDeVries) // Daan only has Ms. Jansen

        assertFailsWith<Forbidden> { bookings.book(sara, evening.id, daan, evening.slots[0].id) }
    }

    @Test
    fun `booking at an unknown evening fails`() {
        assertFailsWith<EveningNotFound> { bookings.book(anna, EveningId.new(), emma, SlotId.new()) }
    }
}
