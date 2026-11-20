package nl.assessment.conferences.domain

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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

    @Test
    fun `rejects an end that is not after the start`() {
        assertFailsWith<InvalidEvening> { evening("20:00", "20:00", 15) }
        assertFailsWith<InvalidEvening> { evening("20:00", "19:00", 15) }
    }

    @Test
    fun `rejects a conversation length that is zero or does not fit in the evening`() {
        assertFailsWith<InvalidEvening> { evening("19:00", "20:00", 0) }
        assertFailsWith<InvalidEvening> { evening("19:00", "20:00", 61) }
    }

    @Test
    fun `rejects a capacity below one`() {
        assertFailsWith<InvalidEvening> { evening("19:00", "20:00", 15, capacity = 0) }
    }

    @Test
    fun `rejects an evening that has already ended`() {
        assertFailsWith<InvalidEvening> {
            ConferenceEvening.create(
                teacher, date, LocalTime.parse("19:00"), LocalTime.parse("20:00"), Duration.ofMinutes(15), 1,
                now = LocalDateTime.parse("2030-03-12T20:00"),
            )
        }
    }

    private val anna = ParentId(UUID.randomUUID())
    private val emma = ChildId(UUID.randomUUID())
    private val daan = ChildId(UUID.randomUUID())
    private val lisa = ChildId(UUID.randomUUID())

    @Test
    fun `a parent books a conversation in a free slot`() {
        val evening = evening("19:00", "20:00", 15)
        val slot = evening.slots[1]

        val booking = evening.book(emma, anna, slot.id, now)

        assertEquals(Booking(booking.id, slot.id, emma, anna), booking)
        assertEquals(listOf(booking), evening.bookings)
    }

    @Test
    fun `a slot takes bookings up to its capacity`() {
        val evening = evening("19:00", "20:00", 15, capacity = 2)
        val slot = evening.slots[0].id

        evening.book(emma, anna, slot, now)
        evening.book(daan, anna, slot, now)

        assertFailsWith<SlotFull> { evening.book(lisa, anna, slot, now) }
        assertEquals(2, evening.bookings.size)
    }

    @Test
    fun `booking an unknown slot fails`() {
        val evening = evening("19:00", "20:00", 15)

        assertFailsWith<SlotNotFound> { evening.book(emma, anna, SlotId.new(), now) }
        assertTrue(evening.bookings.isEmpty())
    }

    @Test
    fun `a child has at most one booking per evening`() {
        val evening = evening("19:00", "20:00", 15)
        val mark = ParentId(UUID.randomUUID())
        evening.book(emma, anna, evening.slots[0].id, now)

        assertFailsWith<ChildAlreadyBooked> { evening.book(emma, mark, evening.slots[1].id, now) }
        assertEquals(1, evening.bookings.size)
    }
}
