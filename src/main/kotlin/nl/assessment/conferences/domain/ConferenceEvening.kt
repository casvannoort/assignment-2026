package nl.assessment.conferences.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Slot(val id: SlotId, val time: TimeRange, val capacity: Int)

data class Booking(val id: BookingId, val slotId: SlotId, val childId: ChildId, val bookedBy: ParentId)

class ConferenceEvening(
    val id: EveningId,
    val teacherId: TeacherId,
    val date: LocalDate,
    val time: TimeRange,
    val conversationLength: Duration,
    val slots: List<Slot>,
    bookings: List<Booking> = emptyList(),
) {
    private val _bookings = bookings.toMutableList()
    val bookings: List<Booking> get() = _bookings.toList()

    fun hasEnded(now: LocalDateTime) = !LocalDateTime.of(date, time.end).isAfter(now)

    fun book(childId: ChildId, parentId: ParentId, slotId: SlotId, now: LocalDateTime): Booking {
        if (hasEnded(now)) throw EveningHasEnded()
        if (_bookings.any { it.childId == childId }) throw ChildAlreadyBooked()
        ensureRoomIn(slotId)
        return Booking(BookingId.new(), slotId, childId, parentId).also { _bookings += it }
    }

    fun move(bookingId: BookingId, toSlotId: SlotId, parentId: ParentId, now: LocalDateTime): Booking {
        if (hasEnded(now)) throw EveningHasEnded()
        val booking = booking(bookingId)
        if (booking.slotId == toSlotId) return booking
        ensureRoomIn(toSlotId)
        val moved = booking.copy(slotId = toSlotId, bookedBy = parentId)
        _bookings[_bookings.indexOf(booking)] = moved
        return moved
    }

    fun booking(id: BookingId) = _bookings.find { it.id == id } ?: throw BookingNotFound()

    private fun ensureRoomIn(slotId: SlotId) {
        val slot = slots.find { it.id == slotId } ?: throw SlotNotFound()
        if (_bookings.count { it.slotId == slotId } >= slot.capacity) throw SlotFull()
    }

    companion object {
        fun create(
            teacherId: TeacherId,
            date: LocalDate,
            start: LocalTime,
            end: LocalTime,
            conversationLength: Duration,
            capacity: Int,
            now: LocalDateTime,
        ): ConferenceEvening {
            if (end <= start) throw InvalidEvening("end must be after start")
            if (conversationLength <= Duration.ZERO) throw InvalidEvening("conversation length must be positive")
            if (conversationLength > Duration.between(start, end)) throw InvalidEvening("conversation length does not fit in the evening")
            if (capacity < 1) throw InvalidEvening("capacity must be at least 1")

            val count = Duration.between(start, end).dividedBy(conversationLength)
            val slots = (0 until count).map { i ->
                val slotStart = start.plus(conversationLength.multipliedBy(i))
                Slot(SlotId.new(), TimeRange(slotStart, slotStart.plus(conversationLength)), capacity)
            }
            return ConferenceEvening(EveningId.new(), teacherId, date, TimeRange(start, end), conversationLength, slots)
                .also { if (it.hasEnded(now)) throw InvalidEvening("evening has already ended") }
        }
    }
}
