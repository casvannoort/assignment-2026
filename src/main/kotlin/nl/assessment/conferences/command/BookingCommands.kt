package nl.assessment.conferences.command

import nl.assessment.conferences.db.ConferenceEveningRepository
import nl.assessment.conferences.domain.Booking
import nl.assessment.conferences.domain.BookingId
import nl.assessment.conferences.domain.ChildDoubleBooked
import nl.assessment.conferences.domain.ChildId
import nl.assessment.conferences.domain.ConferenceEvening
import nl.assessment.conferences.domain.EveningId
import nl.assessment.conferences.domain.EveningNotFound
import nl.assessment.conferences.domain.Forbidden
import nl.assessment.conferences.domain.ParentId
import nl.assessment.conferences.domain.SlotId
import nl.assessment.conferences.query.UserDirectory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class BookingCommands(
    private val repository: ConferenceEveningRepository,
    private val users: UserDirectory,
    private val clock: Clock,
) {

    @Transactional
    fun book(parentId: ParentId, eveningId: EveningId, childId: ChildId, slotId: SlotId): Booking {
        val evening = repository.findById(eveningId) ?: throw EveningNotFound()
        ensureParentOf(parentId, childId)
        if (!users.isTeacherOf(evening.teacherId, childId)) throw Forbidden("this teacher does not teach the child")
        val booking = evening.book(childId, parentId, slotId, now())
        ensureNoOverlapElsewhere(evening, booking)
        repository.save(evening)
        return booking
    }

    @Transactional
    fun move(parentId: ParentId, eveningId: EveningId, bookingId: BookingId, toSlotId: SlotId): Booking {
        val evening = repository.findById(eveningId) ?: throw EveningNotFound()
        ensureParentOf(parentId, evening.booking(bookingId).childId)
        val moved = evening.move(bookingId, toSlotId, parentId, now())
        ensureNoOverlapElsewhere(evening, moved)
        repository.save(evening)
        return moved
    }

    @Transactional
    fun cancel(parentId: ParentId, eveningId: EveningId, bookingId: BookingId) {
        val evening = repository.findById(eveningId) ?: throw EveningNotFound()
        ensureParentOf(parentId, evening.booking(bookingId).childId)
        evening.cancel(bookingId, now())
        repository.save(evening)
    }

    private fun now() = LocalDateTime.now(clock)

    private fun ensureParentOf(parentId: ParentId, childId: ChildId) {
        if (!users.isParentOf(parentId, childId)) throw Forbidden("not your child")
    }

    /** Lock order: evening (already locked by findById), then child. Always the same, so commands cannot deadlock. */
    private fun ensureNoOverlapElsewhere(evening: ConferenceEvening, booking: Booking) {
        repository.lockChild(booking.childId)
        val time = evening.slots.first { it.id == booking.slotId }.time
        if (repository.hasOverlappingBooking(booking.childId, evening.date, time, otherThan = evening.id)) {
            throw ChildDoubleBooked()
        }
    }
}
