package nl.assessment.conferences.db

import nl.assessment.conferences.TestcontainersConfiguration
import nl.assessment.conferences.domain.ChildId
import nl.assessment.conferences.domain.ConferenceEvening
import nl.assessment.conferences.domain.ParentId
import nl.assessment.conferences.domain.TeacherId
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@Import(TestcontainersConfiguration::class)
@Transactional
class ConferenceEveningRepositoryTest(@Autowired private val repository: ConferenceEveningRepository) {

    private val msJansen = TeacherId(UUID.fromString("00000000-0000-0000-0000-000000000101"))

    private fun newEvening() = ConferenceEvening.create(
        msJansen, LocalDate.parse("2030-03-12"), LocalTime.parse("19:00"), LocalTime.parse("20:00"),
        Duration.ofMinutes(15), 2, LocalDateTime.parse("2030-03-01T12:00"),
    )

    @Test
    fun `saves and loads an evening with its slots`() {
        val evening = newEvening()

        repository.save(evening)
        val loaded = assertNotNull(repository.findById(evening.id))

        assertEquals(evening.teacherId, loaded.teacherId)
        assertEquals(evening.date, loaded.date)
        assertEquals(evening.time, loaded.time)
        assertEquals(evening.conversationLength, loaded.conversationLength)
        assertEquals(evening.slots, loaded.slots)
    }

    @Test
    fun `saving an evening twice does not duplicate its slots`() {
        val evening = newEvening()

        repository.save(evening)
        repository.save(evening)

        assertEquals(4, assertNotNull(repository.findById(evening.id)).slots.size)
    }

    @Test
    fun `finds nothing for an unknown id`() {
        assertNull(repository.findById(newEvening().id))
    }

    @Test
    fun `saves and loads the bookings of an evening`() {
        val emma = ChildId(UUID.fromString("00000000-0000-0000-0000-000000000301"))
        val anna = ParentId(UUID.fromString("00000000-0000-0000-0000-000000000201"))
        val evening = newEvening()
        val booking = evening.book(emma, anna, evening.slots[2].id, LocalDateTime.parse("2030-03-01T12:00"))

        repository.save(evening)
        repository.save(evening)

        assertEquals(listOf(booking), assertNotNull(repository.findById(evening.id)).bookings)
    }

    @Test
    fun `saving deletes cancelled bookings and keeps moved ones`() {
        val now = LocalDateTime.parse("2030-03-01T12:00")
        val anna = ParentId(UUID.fromString("00000000-0000-0000-0000-000000000201"))
        val emma = ChildId(UUID.fromString("00000000-0000-0000-0000-000000000301"))
        val daan = ChildId(UUID.fromString("00000000-0000-0000-0000-000000000302"))
        val evening = newEvening()
        val cancelled = evening.book(emma, anna, evening.slots[0].id, now)
        val moved = evening.book(daan, anna, evening.slots[1].id, now)
        repository.save(evening)

        evening.cancel(cancelled.id, now)
        evening.move(moved.id, evening.slots[3].id, anna, now)
        repository.save(evening)

        assertEquals(evening.bookings, assertNotNull(repository.findById(evening.id)).bookings)
    }
}
