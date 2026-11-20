package nl.assessment.conferences.web

import nl.assessment.conferences.command.BookingCommands
import nl.assessment.conferences.domain.ChildId
import nl.assessment.conferences.domain.EveningId
import nl.assessment.conferences.domain.Forbidden
import nl.assessment.conferences.domain.SlotId
import nl.assessment.conferences.domain.User
import nl.assessment.conferences.query.BookingQueries
import nl.assessment.conferences.query.ChildBooking
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class BookRequest(val childId: UUID, val slotId: UUID)

data class BookingResponse(val id: UUID, val eveningId: UUID, val slotId: UUID, val childId: UUID)

@RestController
class BookingController(private val commands: BookingCommands, private val queries: BookingQueries) {

    @GetMapping("/bookings")
    fun list(user: User): List<ChildBooking> {
        val parent = user as? User.Parent ?: throw Forbidden("teachers use the overview")
        return queries.bookingsOfChildren(parent.id)
    }

    @PostMapping("/evenings/{eveningId}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    fun book(user: User, @PathVariable eveningId: UUID, @RequestBody request: BookRequest): BookingResponse {
        val parent = user as? User.Parent ?: throw Forbidden("only parents can book")
        val booking = commands.book(parent.id, EveningId(eveningId), ChildId(request.childId), SlotId(request.slotId))
        return BookingResponse(booking.id.value, eveningId, booking.slotId.value, booking.childId.value)
    }
}
