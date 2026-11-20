package nl.assessment.conferences.command

import nl.assessment.conferences.db.ConferenceEveningRepository
import nl.assessment.conferences.domain.Booking
import nl.assessment.conferences.domain.ChildDoubleBooked
import nl.assessment.conferences.domain.ChildId
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
        if (!users.isParentOf(parentId, childId)) throw Forbidden("not your child")
        if (!users.isTeacherOf(evening.teacherId, childId)) throw Forbidden("this teacher does not teach the child")
        val booking = evening.book(childId, parentId, slotId, LocalDateTime.now(clock))

        // Lock order: evening (above), then child. Always the same, so two bookings cannot deadlock.
        repository.lockChild(childId)
        val time = evening.slots.first { it.id == slotId }.time
        if (repository.hasOverlappingBooking(childId, evening.date, time, otherThan = evening.id)) throw ChildDoubleBooked()

        repository.save(evening)
        return booking
    }
}
