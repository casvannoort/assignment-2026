package nl.assessment.conferences.simulation

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** One thing a simulated person does. Ids are the real ids the app returned. */
sealed interface Action {
    data class CreateEvening(
        val teacher: UUID, val date: LocalDate, val start: LocalTime, val end: LocalTime,
        val minutes: Long, val capacity: Int?,
    ) : Action
    data class Book(val parent: UUID, val evening: UUID, val child: UUID, val slot: UUID) : Action
    data class Move(val parent: UUID, val evening: UUID, val booking: UUID, val slot: UUID) : Action
    data class Cancel(val parent: UUID, val evening: UUID, val booking: UUID) : Action
    data class RemoveSlot(val teacher: UUID, val evening: UUID, val slot: UUID) : Action
    data class Read(val user: UUID?, val path: String) : Action
    data class AdvanceClock(val minutes: Long) : Action
}
