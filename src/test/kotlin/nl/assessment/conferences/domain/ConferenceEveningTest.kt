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

    @Test
    fun `no booking once the evening has ended`() {
        val evening = evening("19:00", "20:00", 15)
        val atTheEnd = LocalDateTime.of(date, LocalTime.parse("20:00"))

        assertFailsWith<EveningHasEnded> { evening.book(emma, anna, evening.slots[0].id, atTheEnd) }
    }

    @Test
    fun `booking is still possible during the evening`() {
        val evening = evening("19:00", "20:00", 15)
        val duringTheEvening = LocalDateTime.of(date, LocalTime.parse("19:30"))

        evening.book(emma, anna, evening.slots[3].id, duringTheEvening)
    }

    @Test
    fun `no booking into a slot that has started`() {
        val evening = evening("19:00", "20:00", 15)
        val atHalfPast = LocalDateTime.of(date, LocalTime.parse("19:30"))

        assertFailsWith<SlotHasStarted> { evening.book(emma, anna, evening.slots[1].id, atHalfPast) }
        assertFailsWith<SlotHasStarted> { evening.book(emma, anna, evening.slots[2].id, atHalfPast) }
        assertTrue(evening.bookings.isEmpty())
    }

    @Test
    fun `no moving into a slot that has started, but cancelling is still possible`() {
        val evening = evening("19:00", "20:00", 15)
        val booking = evening.book(emma, anna, evening.slots[3].id, now)
        val atHalfPast = LocalDateTime.of(date, LocalTime.parse("19:30"))

        assertFailsWith<SlotHasStarted> { evening.move(booking.id, evening.slots[2].id, anna, atHalfPast) }
        assertEquals(evening.slots[3].id, evening.booking(booking.id).slotId)
        evening.cancel(booking.id, atHalfPast)
        assertTrue(evening.bookings.isEmpty())
    }

    @Test
    fun `a booking moves to another slot`() {
        val evening = evening("19:00", "20:00", 15)
        val booking = evening.book(emma, anna, evening.slots[0].id, now)

        val moved = evening.move(booking.id, evening.slots[2].id, anna, now)

        assertEquals(booking.copy(slotId = evening.slots[2].id), moved)
        assertEquals(listOf(moved), evening.bookings)
    }

    @Test
    fun `moving to a full slot fails and keeps the original booking`() {
        val evening = evening("19:00", "20:00", 15)
        val emmasBooking = evening.book(emma, anna, evening.slots[0].id, now)
        evening.book(daan, anna, evening.slots[1].id, now)

        assertFailsWith<SlotFull> { evening.move(emmasBooking.id, evening.slots[1].id, anna, now) }
        assertEquals(evening.slots[0].id, evening.bookings.single { it.childId == emma }.slotId)
    }

    @Test
    fun `moving an unknown booking fails`() {
        val evening = evening("19:00", "20:00", 15)

        assertFailsWith<BookingNotFound> { evening.move(BookingId.new(), evening.slots[0].id, anna, now) }
    }

    @Test
    fun `moving to the slot it is already in changes nothing`() {
        val evening = evening("19:00", "20:00", 15)
        val booking = evening.book(emma, anna, evening.slots[0].id, now)

        assertEquals(booking, evening.move(booking.id, evening.slots[0].id, anna, now))
        assertEquals(listOf(booking), evening.bookings)
    }

    @Test
    fun `moving records the parent who moved it`() {
        val evening = evening("19:00", "20:00", 15)
        val mark = ParentId(UUID.randomUUID())
        val booking = evening.book(emma, anna, evening.slots[0].id, now)

        assertEquals(mark, evening.move(booking.id, evening.slots[1].id, mark, now).bookedBy)
    }

    @Test
    fun `a cancelled booking frees its place`() {
        val evening = evening("19:00", "20:00", 15)
        val slot = evening.slots[0].id
        val booking = evening.book(emma, anna, slot, now)

        evening.cancel(booking.id, now)

        assertTrue(evening.bookings.isEmpty())
        evening.book(daan, anna, slot, now)
    }

    @Test
    fun `no moving or cancelling once the evening has ended`() {
        val evening = evening("19:00", "20:00", 15)
        val booking = evening.book(emma, anna, evening.slots[0].id, now)
        val afterwards = LocalDateTime.of(date, LocalTime.parse("21:00"))

        assertFailsWith<EveningHasEnded> { evening.move(booking.id, evening.slots[1].id, anna, afterwards) }
        assertFailsWith<EveningHasEnded> { evening.cancel(booking.id, afterwards) }
    }

    @Test
    fun `a teacher removes an empty slot, for example for a break`() {
        val evening = evening("19:00", "20:00", 15)
        val brk = evening.slots[2].id

        evening.removeSlot(brk, now)

        assertEquals(listOf("19:00-19:15", "19:15-19:30", "19:45-20:00"), evening.slotTimes())
        assertFailsWith<SlotNotFound> { evening.book(emma, anna, brk, now) }
    }

    @Test
    fun `a slot with bookings cannot be removed`() {
        val evening = evening("19:00", "20:00", 15)
        evening.book(emma, anna, evening.slots[0].id, now)

        assertFailsWith<SlotHasBookings> { evening.removeSlot(evening.slots[0].id, now) }
        assertEquals(4, evening.slots.size)
    }

    @Test
    fun `removing an unknown slot fails`() {
        assertFailsWith<SlotNotFound> { evening("19:00", "20:00", 15).removeSlot(SlotId.new(), now) }
    }

    @Test
    fun `no removing slots once the evening has ended`() {
        val evening = evening("19:00", "20:00", 15)
        val afterwards = LocalDateTime.of(date, LocalTime.parse("21:00"))

        assertFailsWith<EveningHasEnded> { evening.removeSlot(evening.slots[0].id, afterwards) }
    }
}
