package nl.assessment.conferences.simulation

/** Does the database change match what the reply says happened? */
object Consistency {

    val knownCodes = setOf(
        "INVALID_EVENING", "INVALID_REQUEST", "UNAUTHENTICATED", "FORBIDDEN",
        "EVENING_NOT_FOUND", "SLOT_NOT_FOUND", "BOOKING_NOT_FOUND",
        "EVENING_OVERLAPS", "SLOT_FULL", "CHILD_ALREADY_BOOKED", "CHILD_DOUBLE_BOOKED",
        "EVENING_HAS_ENDED", "SLOT_HAS_BOOKINGS",
    )

    fun check(action: Action, reply: Reply, before: Snapshot, after: Snapshot): List<Finding> {
        if (reply.status >= 500) return listOf(Finding("no-server-error", "$action gave ${reply.status}"))
        if (reply.status >= 400) {
            return listOfNotNull(
                Finding("error-code-known", "$action gave code ${reply.code}").takeIf { reply.code !in knownCodes },
                Finding("refusal-changes-nothing", "$action gave ${reply.status} but changed data").takeIf { before != after },
            )
        }
        val expected = when (action) {
            is Action.Read, is Action.AdvanceClock -> before
            is Action.CreateEvening -> before.copy(
                evenings = before.evenings + after.evenings.filter { it.id == reply.id() },
                slots = before.slots + after.slots.filter { it.evening == reply.id() },
            )
            is Action.Book -> before.copy(
                bookings = before.bookings + BookingRow(reply.id(), action.evening, action.slot, action.child, action.parent),
            )
            is Action.Move -> before.copy(
                bookings = before.bookings.map {
                    if (it.id == action.booking && it.slot != action.slot) it.copy(slot = action.slot, bookedBy = action.parent) else it
                }.toSet(),
            )
            is Action.Cancel -> before.copy(bookings = before.bookings.filter { it.id != action.booking }.toSet())
            is Action.RemoveSlot -> before.copy(slots = before.slots.filter { it.id != action.slot }.toSet())
        }
        return listOfNotNull(
            Finding("success-changes-what-it-says", "$action (${reply.status}) changed something else").takeIf { expected != after },
        )
    }
}
