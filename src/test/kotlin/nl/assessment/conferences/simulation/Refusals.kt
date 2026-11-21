package nl.assessment.conferences.simulation

import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * A refusal is only right when the reason it gives is true in the database before the request.
 * Without this, an app that wrongly refuses valid requests would look perfectly healthy.
 * The check does not decide which reason should win when several are true; it only checks the one given.
 */
object Refusals {

    fun check(action: Action, reply: Reply, before: Snapshot, now: LocalDateTime, school: School): List<Finding> {
        if (reply.status !in 400..499) return emptyList()
        val code = reply.code ?: return emptyList()
        val reasonHolds = Reasons(action, before, now, school).holds(code)
        return listOfNotNull(Finding("R refusal has a true reason", "$action refused with $code").takeIf { !reasonHolds })
    }

    private class Reasons(val action: Action, val before: Snapshot, val now: LocalDateTime, val school: School) {
        val user: UUID? = when (action) {
            is Action.CreateEvening -> action.teacher
            is Action.Book -> action.parent
            is Action.Move -> action.parent
            is Action.Cancel -> action.parent
            is Action.RemoveSlot -> action.teacher
            is Action.Read -> action.user
            is Action.AdvanceClock -> null
        }
        val evening: UUID? = when (action) {
            is Action.Book -> action.evening
            is Action.Move -> action.evening
            is Action.Cancel -> action.evening
            is Action.RemoveSlot -> action.evening
            is Action.Read -> action.path.split("/").getOrNull(2)?.let(UUID::fromString)
            else -> null
        }
        val eveningRow = before.evenings.firstOrNull { it.id == evening }
        val booking = when (action) {
            is Action.Move -> before.bookings.firstOrNull { it.id == action.booking && it.evening == action.evening }
            is Action.Cancel -> before.bookings.firstOrNull { it.id == action.booking && it.evening == action.evening }
            else -> null
        }
        val slot: UUID? = when (action) {
            is Action.Book -> action.slot
            is Action.Move -> action.slot
            is Action.RemoveSlot -> action.slot
            else -> null
        }
        val slotRow = before.slots.firstOrNull { it.id == slot && it.evening == evening }
        val child: UUID? = if (action is Action.Book) action.child else booking?.child

        fun isTeacher(id: UUID?) = id in school.teachers
        fun isParent(id: UUID?) = id in school.parents
        fun ended(e: EveningRow) = !LocalDateTime.of(e.date, e.end).isAfter(now)
        fun visibleToParent(e: EveningRow) = !ended(e) &&
            school.childrenOf(user!!).any { e.teacher in school.teachersOf.getValue(it) }

        fun holds(code: String): Boolean = when (code) {
            "UNAUTHENTICATED" -> user == null || (!isTeacher(user) && !isParent(user))
            "INVALID_EVENING" -> (action as? Action.CreateEvening)?.let {
                it.end <= it.start || it.minutes <= 0 || Duration.ofMinutes(it.minutes) > Duration.between(it.start, it.end) ||
                    (it.capacity ?: 1) < 1 || !LocalDateTime.of(it.date, it.end).isAfter(now)
            } ?: false
            "EVENING_OVERLAPS" -> (action as? Action.CreateEvening)?.let { c ->
                before.evenings.any { it.teacher == c.teacher && it.date == c.date && it.start < c.end && c.start < it.end }
            } ?: false
            "FORBIDDEN" -> forbidden()
            "EVENING_NOT_FOUND" -> eveningRow == null ||
                (action is Action.Read && action.path.endsWith("/slots") && isParent(user) && !visibleToParent(eveningRow))
            "SLOT_NOT_FOUND" -> slot != null && slotRow == null
            "BOOKING_NOT_FOUND" -> (action is Action.Move || action is Action.Cancel) && booking == null
            "SLOT_FULL" -> slotRow != null && before.bookings.count { it.slot == slotRow.id && it.id != booking?.id } >= slotRow.capacity
            "CHILD_ALREADY_BOOKED" -> before.bookings.any { it.evening == evening && it.child == child }
            "CHILD_DOUBLE_BOOKED" -> slotRow != null && eveningRow != null && before.bookings.any { other ->
                val otherSlot = before.slots.first { it.id == other.slot }
                val otherEvening = before.evenings.first { it.id == other.evening }
                other.child == child && other.evening != evening && otherEvening.date == eveningRow.date &&
                    otherSlot.start < slotRow.end && slotRow.start < otherSlot.end
            }
            "EVENING_HAS_ENDED" -> eveningRow != null && ended(eveningRow)
            "SLOT_HAS_STARTED" -> slotRow != null && eveningRow != null && !LocalDateTime.of(eveningRow.date, slotRow.start).isAfter(now)
            "SLOT_HAS_BOOKINGS" -> slotRow != null && before.bookings.any { it.slot == slotRow.id }
            else -> false
        }

        private fun forbidden(): Boolean = when (action) {
            is Action.CreateEvening -> !isTeacher(user)
            is Action.Book -> !isParent(user) || child !in school.childrenOf(user!!) ||
                (eveningRow != null && eveningRow.teacher !in school.teachersOf[child].orEmpty())
            is Action.Move, is Action.Cancel -> !isParent(user) || (booking != null && booking.child !in school.childrenOf(user!!))
            is Action.RemoveSlot -> !isTeacher(user) || (eveningRow != null && eveningRow.teacher != user)
            is Action.Read -> when {
                action.path.endsWith("/overview") -> !isTeacher(user) || (eveningRow != null && eveningRow.teacher != user)
                action.path.endsWith("/slots") || action.path == "/bookings" -> !isParent(user)
                else -> false
            }
            is Action.AdvanceClock -> false
        }
    }
}
