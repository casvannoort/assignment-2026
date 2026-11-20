package nl.assessment.conferences.command

import nl.assessment.conferences.db.ConferenceEveningRepository
import nl.assessment.conferences.domain.Booking
import nl.assessment.conferences.domain.ChildId
import nl.assessment.conferences.domain.EveningId
import nl.assessment.conferences.domain.EveningNotFound
import nl.assessment.conferences.domain.Forbidden
import nl.assessment.conferences.domain.ParentId
import nl.assessment.conferences.domain.SlotId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class BookingCommands(private val repository: ConferenceEveningRepository, private val clock: Clock) {

    @Transactional
    fun book(parentId: ParentId, eveningId: EveningId, childId: ChildId, slotId: SlotId): Booking {
        val evening = repository.findById(eveningId) ?: throw EveningNotFound()
        if (!repository.isParentOf(parentId, childId)) throw Forbidden("not your child")
        if (!repository.isTeacherOf(evening.teacherId, childId)) throw Forbidden("this teacher does not teach the child")
        val booking = evening.book(childId, parentId, slotId, LocalDateTime.now(clock))
        repository.save(evening)
        return booking
    }
}
