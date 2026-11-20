package nl.assessment.conferences.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Slot(val id: SlotId, val time: TimeRange, val capacity: Int)

class ConferenceEvening(
    val id: EveningId,
    val teacherId: TeacherId,
    val date: LocalDate,
    val time: TimeRange,
    val conversationLength: Duration,
    val slots: List<Slot>,
) {
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
            if (!LocalDateTime.of(date, end).isAfter(now)) throw InvalidEvening("evening has already ended")

            val count = Duration.between(start, end).dividedBy(conversationLength)
            val slots = (0 until count).map { i ->
                val slotStart = start.plus(conversationLength.multipliedBy(i))
                Slot(SlotId.new(), TimeRange(slotStart, slotStart.plus(conversationLength)), capacity)
            }
            return ConferenceEvening(EveningId.new(), teacherId, date, TimeRange(start, end), conversationLength, slots)
        }
    }
}
