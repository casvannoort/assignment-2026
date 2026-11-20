package nl.assessment.conferences.domain

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals

class ConferenceEveningTest {

    private val teacher = TeacherId(UUID.randomUUID())
    private val date = LocalDate.parse("2030-03-12")
    private val now = LocalDateTime.parse("2030-03-01T12:00")

    private fun evening(start: String, end: String, minutes: Long, capacity: Int = 1) =
        ConferenceEvening.create(
            teacher, date, LocalTime.parse(start), LocalTime.parse(end), Duration.ofMinutes(minutes), capacity, now,
        )

    private fun ConferenceEvening.slotTimes() = slots.map { "${it.time.start}-${it.time.end}" }

    @Test
    fun `generates slots of one conversation length between start and end`() {
        val evening = evening("19:00", "20:00", 15)

        assertEquals(listOf("19:00-19:15", "19:15-19:30", "19:30-19:45", "19:45-20:00"), evening.slotTimes())
    }

    @Test
    fun `drops a trailing part that is too short for a conversation`() {
        val evening = evening("19:00", "19:50", 15)

        assertEquals(listOf("19:00-19:15", "19:15-19:30", "19:30-19:45"), evening.slotTimes())
    }

    @Test
    fun `gives every slot the capacity of the evening`() {
        val evening = evening("19:00", "20:00", 30, capacity = 2)

        assertEquals(listOf(2, 2), evening.slots.map { it.capacity })
    }
}
