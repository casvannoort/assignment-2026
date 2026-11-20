package nl.assessment.conferences.command

import nl.assessment.conferences.db.ConferenceEveningRepository
import nl.assessment.conferences.domain.ConferenceEvening
import nl.assessment.conferences.domain.EveningId
import nl.assessment.conferences.domain.EveningNotFound
import nl.assessment.conferences.domain.EveningOverlaps
import nl.assessment.conferences.domain.Forbidden
import nl.assessment.conferences.domain.SlotId
import nl.assessment.conferences.domain.TeacherId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class EveningCommands(private val repository: ConferenceEveningRepository, private val clock: Clock) {

    @Transactional
    fun create(
        teacherId: TeacherId,
        date: LocalDate,
        start: LocalTime,
        end: LocalTime,
        conversationLength: Duration,
        capacity: Int,
    ): ConferenceEvening {
        val evening = ConferenceEvening.create(
            teacherId, date, start, end, conversationLength, capacity, LocalDateTime.now(clock),
        )
        repository.lockTeacher(teacherId)
        if (repository.hasOverlappingEvening(teacherId, date, evening.time)) throw EveningOverlaps()
        repository.save(evening)
        return evening
    }

    @Transactional
    fun removeSlot(teacherId: TeacherId, eveningId: EveningId, slotId: SlotId) {
        val evening = repository.findById(eveningId) ?: throw EveningNotFound()
        if (evening.teacherId != teacherId) throw Forbidden("not your evening")
        evening.removeSlot(slotId)
        repository.save(evening)
    }
}
