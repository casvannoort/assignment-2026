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

class ActionPicker(private val random: Random, private val school: School) {

    private fun <T> List<T>.pick() = this[random.nextInt(size)]
    private fun chance(percent: Int) = random.nextInt(100) < percent
    private fun teacher(id: UUID) = "teacher#${school.teachers.indexOf(id)}"
    private fun parent(id: UUID) = school.parents.indexOf(id).let { if (it < 0) "unknown" else "parent#$it" }
    private fun child(id: UUID) = "child#${school.children.indexOf(id)}"

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

    private fun slotsOf(state: State, evening: UUID) =
        state.snapshot.slots.filter { it.evening == evening }.sortedBy { it.start }

    /** Bookings in a fixed order: evening position, slot time, child position. */
    private fun bookings(state: State) = state.snapshot.bookings.sortedWith(
        compareBy({ state.evenings.indexOf(it.evening) },
            { b -> state.snapshot.slots.first { it.id == b.slot }.start }, { school.children.indexOf(it.child) }),
    )

    private fun create(state: State): Pick {
        val teacher = school.teachers.pick()
        val day = if (chance(5)) -1L else random.nextLong(0, 3)
        val start = LocalTime.of(random.nextInt(17, 21), 0)
        var length = listOf(40L, 60L, 90L).pick()
        var minutes = listOf(10L, 15L, 20L).pick()
        if (chance(5)) length = -30
        if (chance(3)) minutes = 0
        val capacity = listOf(null, null, 1, 2, 3).pick()
        val action = Action.CreateEvening(
            teacher, state.now.toLocalDate().plusDays(day), start, start.plusMinutes(length), minutes, capacity,
        )
        return Pick(action, "create ${teacher(teacher)} day${if (day >= 0) "+" else ""}$day $start ${length}min by $minutes cap $capacity")
    }

    private fun book(state: State): Pick {
        val parent = if (chance(2)) UUID.randomUUID() else school.parents.pick()
        val own = school.childrenOf(parent)
        val child = if (own.isNotEmpty() && chance(85)) own.pick() else school.children.pick()
        val eveningIndex = state.evenings.indices.toList().pick()
        val evening = state.evenings[eveningIndex]
        val slots = slotsOf(state, evening)
        val (slot, slotName) = if (slots.isEmpty() || chance(3)) UUID.randomUUID() to "unknown" else
            slots.indices.toList().pick().let { slots[it].id to "slot#$it" }
        return Pick(
            Action.Book(parent, evening, child, slot),
            "book ${parent(parent)} ${child(child)} evening#$eveningIndex $slotName", evening,
        )
    }

    private fun move(state: State): Pick? {
        val all = bookings(state).ifEmpty { return null }
        val index = all.indices.toList().pick()
        val booking = all[index]
        val parent = if (chance(85)) school.parentsOf.getValue(booking.child).pick() else school.parents.pick()
        val slots = slotsOf(state, booking.evening)
        val target = slots.indices.toList().pick()
        return Pick(
            Action.Move(parent, booking.evening, booking.id, slots[target].id),
            "move ${parent(parent)} booking#$index slot#$target", booking.evening,
        )
    }

    private fun cancel(state: State): Pick? {
        val all = bookings(state).ifEmpty { return null }
        val index = all.indices.toList().pick()
        val booking = all[index]
        val parent = if (chance(85)) school.parentsOf.getValue(booking.child).pick() else school.parents.pick()
        return Pick(Action.Cancel(parent, booking.evening, booking.id), "cancel ${parent(parent)} booking#$index", booking.evening)
    }

    private fun removeSlot(state: State): Pick {
        val eveningIndex = state.evenings.indices.toList().pick()
        val evening = state.evenings[eveningIndex]
        val owner = state.snapshot.evenings.first { it.id == evening }.teacher
        val teacher = if (chance(80)) owner else school.teachers.pick()
        val slots = slotsOf(state, evening)
        if (slots.isEmpty()) return read(state)
        val index = slots.indices.toList().pick()
        return Pick(
            Action.RemoveSlot(teacher, evening, slots[index].id),
            "remove ${teacher(teacher)} evening#$eveningIndex slot#$index", evening,
        )
    }

    private fun read(state: State): Pick {
        val eveningIndex = state.evenings.indices.toList().pick()
        val evening = state.evenings[eveningIndex]
        val owner = state.snapshot.evenings.first { it.id == evening }.teacher
        return when (random.nextInt(4)) {
            0 -> {
                val user = if (chance(5)) null else (school.teachers + school.parents).pick()
                Pick(Action.Read(user, "/evenings"), "list evenings ${user?.let { if (it in school.teachers) teacher(it) else parent(it) } ?: "nobody"}")
            }
            1 -> school.parents.pick().let { Pick(Action.Read(it, "/bookings"), "list bookings ${parent(it)}") }
            2 -> school.parents.pick().let {
                Pick(Action.Read(it, "/evenings/$evening/slots"), "free slots ${parent(it)} evening#$eveningIndex", evening)
            }
            else -> (if (chance(70)) owner else school.teachers.pick()).let {
                Pick(Action.Read(it, "/evenings/$evening/overview"), "overview ${teacher(it)} evening#$eveningIndex", evening)
            }
        }
    }
}
