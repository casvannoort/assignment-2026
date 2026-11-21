package nl.assessment.conferences.simulation

import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.random.Random

/** What the simulator knows before a step: the evenings it created (in order), the tables and the time. */
data class State(val evenings: List<UUID>, val snapshot: Snapshot, val now: LocalDateTime)

/**
 * One planned step. `describe` names people and things by position (teacher#1, slot#3), never by UUID,
 * so the same seed gives the same log even though the app makes random ids.
 */
data class Pick(val action: Action, val describe: String, val evening: UUID? = null)

/**
 * Picks random steps, mostly sensible ones, plus on purpose: the wrong role, an unknown user,
 * a slot or booking addressed through another evening, and slots that were removed earlier.
 */
class ActionPicker(private val random: Random, private val school: School) {

    private val removed = mutableListOf<Pair<UUID, UUID>>()

    /** The simulator reports removed slots, so later steps can try to use them. */
    fun slotRemoved(evening: UUID, slot: UUID) {
        removed += evening to slot
    }

    private fun <T> List<T>.pick() = this[random.nextInt(size)]
    private fun chance(percent: Int) = random.nextInt(100) < percent
    private fun name(id: UUID) = when (id) {
        in school.teachers -> "teacher#${school.teachers.indexOf(id)}"
        in school.parents -> "parent#${school.parents.indexOf(id)}"
        else -> "unknown"
    }

    /** Usually the intended person; sometimes someone of the other role, or a user the app does not know. */
    private fun actor(intended: UUID): Pair<UUID, String> {
        val roll = random.nextInt(100)
        return when {
            roll < 4 -> (if (intended in school.teachers) school.parents else school.teachers).pick().let { it to "${name(it)} wrong-role" }
            roll < 6 -> UUID.randomUUID() to "unknown"
            else -> intended to name(intended)
        }
    }

    fun next(state: State): Pick {
        val roll = random.nextInt(100)
        return when {
            state.evenings.isEmpty() || roll < 8 -> create(state)
            roll < 38 -> book(state)
            roll < 52 -> move(state) ?: book(state)
            roll < 59 -> cancel(state) ?: book(state)
            roll < 65 -> removeSlot(state)
            roll < 90 -> read(state)
            else -> (random.nextInt(1, 9) * 30L).let { Pick(Action.AdvanceClock(it), "advance clock $it min") }
        }
    }

    /** Mostly an evening that is still open, so most steps test live rules; sometimes any evening, ended ones too. */
    private fun eveningIndex(state: State): Int {
        val open = state.evenings.indices.filter { i ->
            state.snapshot.evenings.first { it.id == state.evenings[i] }.let { it.date.atTime(it.end) > state.now }
        }
        return if (open.isNotEmpty() && chance(80)) open.pick() else state.evenings.indices.toList().pick()
    }

    /** A booking, mostly on an evening that is still open; returns its position in the fixed order and the booking. */
    private fun booking(state: State): IndexedValue<BookingRow>? {
        val all = bookings(state).withIndex().toList().ifEmpty { return null }
        val open = all.filter { b -> state.snapshot.evenings.first { it.id == b.value.evening }.let { it.date.atTime(it.end) > state.now } }
        return if (open.isNotEmpty() && chance(80)) open.pick() else all.pick()
    }

    private fun slotsOf(state: State, evening: UUID) =
        state.snapshot.slots.filter { it.evening == evening }.sortedBy { it.start }

    /** Bookings in a fixed order: evening position, slot time, child position. */
    private fun bookings(state: State) = state.snapshot.bookings.sortedWith(
        compareBy({ state.evenings.indexOf(it.evening) },
            { b -> state.snapshot.slots.first { it.id == b.slot }.start }, { school.children.indexOf(it.child) }),
    )

    private fun otherEvening(state: State, than: UUID) = state.evenings.filter { it != than }.ifEmpty { null }?.pick()

    /** A slot for `evening`: mostly its own, sometimes one of another evening, a removed one, or one that never existed. */
    private fun slotFor(state: State, evening: UUID): Pair<UUID, String> {
        val roll = random.nextInt(100)
        val own = slotsOf(state, evening)
        val other = otherEvening(state, evening)?.let { slotsOf(state, it) }.orEmpty()
        return when {
            roll < 5 && other.isNotEmpty() -> other.pick().id to "other-evening slot"
            roll < 10 && removed.isNotEmpty() -> removed.pick().second to "removed-slot"
            roll < 13 || own.isEmpty() -> UUID.randomUUID() to "unknown slot"
            else -> own.indices.toList().pick().let { own[it].id to "slot#$it" }
        }
    }

    /** The evening in the URL: mostly the right one, sometimes another. */
    private fun urlEvening(state: State, right: UUID): Pair<UUID, String> =
        if (chance(10)) otherEvening(state, right)?.let { it to "via other-evening" } ?: (right to "") else right to ""

    private fun create(state: State): Pick {
        val (teacher, who) = actor(school.teachers.pick())
        val day = if (chance(5)) -1L else random.nextLong(0, 3)
        val start = LocalTime.of(random.nextInt(17, 21), 0)
        var length = listOf(40L, 60L, 90L).pick()
        var minutes = listOf(10L, 15L, 20L).pick()
        if (chance(5)) length = -30
        if (chance(3)) minutes = 0
        val capacity = if (chance(3)) listOf(0, -1).pick() else listOf(null, null, 1, 2, 3).pick()
        val action = Action.CreateEvening(
            teacher, state.now.toLocalDate().plusDays(day), start, start.plusMinutes(length), minutes, capacity,
        )
        return Pick(action, "create $who day${if (day >= 0) "+" else ""}$day $start ${length}min by $minutes cap $capacity")
    }

    private fun book(state: State): Pick {
        val (parent, who) = actor(school.parents.pick())
        val own = if (parent in school.parents) school.childrenOf(parent) else emptyList()
        val child = if (own.isNotEmpty() && chance(85)) own.pick() else school.children.pick()
        val eveningIndex = eveningIndex(state)
        val evening = state.evenings[eveningIndex]
        val (slot, slotName) = slotFor(state, evening)
        return Pick(
            Action.Book(parent, evening, child, slot),
            "book $who child#${school.children.indexOf(child)} evening#$eveningIndex $slotName", evening,
        )
    }

    private fun move(state: State): Pick? {
        val (index, booking) = booking(state) ?: return null
        val (parent, who) = actor(if (chance(85)) school.parentsOf.getValue(booking.child).pick() else school.parents.pick())
        val (slot, slotName) = slotFor(state, booking.evening)
        val (evening, via) = urlEvening(state, booking.evening)
        return Pick(Action.Move(parent, evening, booking.id, slot), "move $who booking#$index $slotName $via".trim(), evening)
    }

    private fun cancel(state: State): Pick? {
        val (index, booking) = booking(state) ?: return null
        val (parent, who) = actor(if (chance(85)) school.parentsOf.getValue(booking.child).pick() else school.parents.pick())
        val (evening, via) = urlEvening(state, booking.evening)
        return Pick(Action.Cancel(parent, evening, booking.id), "cancel $who booking#$index $via".trim(), evening)
    }

    private fun removeSlot(state: State): Pick {
        val eveningIndex = eveningIndex(state)
        val evening = state.evenings[eveningIndex]
        val owner = state.snapshot.evenings.first { it.id == evening }.teacher
        val (teacher, who) = actor(if (chance(80)) owner else school.teachers.pick())
        val (slot, slotName) = slotFor(state, evening)
        return Pick(Action.RemoveSlot(teacher, evening, slot), "remove $who evening#$eveningIndex $slotName", evening)
    }

    private fun read(state: State): Pick {
        val eveningIndex = eveningIndex(state)
        val evening = state.evenings[eveningIndex]
        val owner = state.snapshot.evenings.first { it.id == evening }.teacher
        return when (random.nextInt(4)) {
            0 -> {
                val user = if (chance(5)) null else (school.teachers + school.parents).pick()
                Pick(Action.Read(user, "/evenings"), "list evenings ${user?.let(::name) ?: "nobody"}")
            }
            1 -> actor(school.parents.pick()).let { (u, who) -> Pick(Action.Read(u, "/bookings"), "list bookings $who") }
            2 -> actor(school.parents.pick()).let { (u, who) ->
                Pick(Action.Read(u, "/evenings/$evening/slots"), "free slots $who evening#$eveningIndex", evening)
            }
            else -> actor(if (chance(70)) owner else school.teachers.pick()).let { (u, who) ->
                Pick(Action.Read(u, "/evenings/$evening/overview"), "overview $who evening#$eveningIndex", evening)
            }
        }
    }
}
