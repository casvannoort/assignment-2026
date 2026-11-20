package nl.assessment.conferences.web

import nl.assessment.conferences.command.EveningCommands
import nl.assessment.conferences.domain.ConferenceEvening
import nl.assessment.conferences.domain.EveningId
import nl.assessment.conferences.domain.Forbidden
import nl.assessment.conferences.domain.User
import nl.assessment.conferences.query.EveningQueries
import nl.assessment.conferences.query.FreeSlot
import nl.assessment.conferences.query.SlotQueries
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class CreateEveningRequest(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val conversationMinutes: Long,
    val capacity: Int = 1,
)

data class SlotResponse(val id: UUID, val start: LocalTime, val end: LocalTime, val capacity: Int)

data class EveningResponse(
    val id: UUID,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val conversationMinutes: Long,
    val slots: List<SlotResponse>,
) {
    constructor(evening: ConferenceEvening) : this(
        evening.id.value, evening.date, evening.time.start, evening.time.end, evening.conversationLength.toMinutes(),
        evening.slots.map { SlotResponse(it.id.value, it.time.start, it.time.end, it.capacity) },
    )
}

@RestController
@RequestMapping("/evenings")
class EveningController(
    private val commands: EveningCommands,
    private val queries: EveningQueries,
    private val slots: SlotQueries,
) {

    @GetMapping
    fun list(user: User) = queries.evenings(user)

    @GetMapping("/{id}/slots")
    fun freeSlots(user: User, @PathVariable id: UUID): List<FreeSlot> {
        val parent = user as? User.Parent ?: throw Forbidden("teachers use the overview")
        return slots.freeSlots(parent.id, EveningId(id))
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(user: User, @RequestBody request: CreateEveningRequest): EveningResponse {
        val teacher = user as? User.Teacher ?: throw Forbidden("only teachers can create evenings")
        val evening = commands.create(
            teacher.id, request.date, request.start, request.end,
            Duration.ofMinutes(request.conversationMinutes), request.capacity,
        )
        return EveningResponse(evening)
    }
}
