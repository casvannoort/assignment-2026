package nl.assessment.conferences.simulation

import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

data class RunResult(val seed: Long, val log: List<String>, val outcomes: Map<String, Int>, val findings: List<Finding>)

/** Runs random steps one after the other against the real app, and checks everything after every step. */
class SequentialSimulator(private val api: Api, private val jdbc: JdbcTemplate, private val clock: MutableClock) {

    fun run(seed: Long, steps: Int): RunResult {
        SchoolGenerator.reset(jdbc)
        val school = SchoolGenerator.generate(seed, teachers = 3, children = 12)
        SchoolGenerator.insert(jdbc, school)
        clock.reset(SimulationConfig.START)

        val picker = ActionPicker(Random(seed), school)
        val evenings = mutableListOf<UUID>()
        val ended = EndedEvenings()
        val log = mutableListOf<String>()
        val outcomes = mutableMapOf<String, Int>()
        val findings = mutableListOf<Finding>()
        val seen = mutableSetOf<Finding>()

        repeat(steps) { step ->
            val before = Snapshot.take(jdbc)
            val requestedAt = now()
            val pick = picker.next(State(evenings.filter { e -> before.evenings.any { it.id == e } }, before, requestedAt))
            val action = pick.action
            val reply = if (action is Action.AdvanceClock) null.also { clock.advance(Duration.ofMinutes(action.minutes)) }
            else api.send(action)
            if (action is Action.CreateEvening && reply?.status == 201) evenings += reply.id()
            val after = Snapshot.take(jdbc)

            val found = mutableListOf<Finding>()
            if (reply != null) {
                found += Consistency.check(action, reply, before, after)
                found += Refusals.check(action, reply, before, requestedAt, school)
                found += assignmentChecks(pick, reply, after, school)
            }
            found += Invariants.check(jdbc, now(), ended)
            // A broken state stays broken: report each violation once, at the step that caused it.
            findings += found.filter { seen.add(it) }.map { it.copy(message = "seed $seed step $step: ${it.message}") }

            val outcome = when {
                reply == null -> "advance clock"
                reply.code != null -> reply.code!!
                else -> "${action::class.simpleName} ${reply.status}"
            }
            outcomes.merge(outcome, 1, Int::plus)
            log += "$step ${pick.describe} -> ${reply?.status ?: "-"} ${reply?.code ?: ""}".trim()
        }
        return RunResult(seed, log, outcomes, findings)
    }

    private fun now() = LocalDateTime.now(clock)

    private fun assignmentChecks(pick: Pick, reply: Reply, after: Snapshot, school: School): List<Finding> {
        val action = pick.action
        return when (action) {
            is Action.CreateEvening ->
                AssignmentChecks.slotsFollowRequest(action, reply) + AssignmentChecks.capacityDefaultsToOne(action, reply)
            is Action.Book, is Action.Move -> {
                val child = after.bookings.firstOrNull { it.id == (if (action is Action.Move) action.booking else reply.idOrNull()) }?.child
                val moved = if (action is Action.Move) AssignmentChecks.moveOrCancelTookEffect(action, reply, after) else emptyList()
                moved + (child?.let { c ->
                    val parents = school.parentsOf.getValue(c)
                    AssignmentChecks.everyParentSeesBooking(
                        after.copy(bookings = after.bookings.filter { it.child == c }.toSet()),
                        mapOf(c to parents),
                        parents.associateWith { api.send(Action.Read(it, "/bookings")) },
                    )
                } ?: emptyList())
            }
            is Action.Cancel -> AssignmentChecks.moveOrCancelTookEffect(action, reply, after)
            is Action.RemoveSlot -> if (reply.status != 204) emptyList() else {
                val viewer = school.children.firstOrNull { action.teacher in school.teachersOf.getValue(it) }
                    ?.let { school.parentsOf.getValue(it).first() }
                AssignmentChecks.removedSlotIsGone(
                    action.slot,
                    api.send(Action.Read(action.teacher, "/evenings/${action.evening}/overview")),
                    viewer?.let { api.send(Action.Read(it, "/evenings/${action.evening}/slots")) } ?: Reply(404, null),
                )
            }
            is Action.Read -> when {
                action.path.endsWith("/slots") -> AssignmentChecks.freeSlotsHaveRoom(pick.evening!!, reply, after)
                action.path.endsWith("/overview") -> AssignmentChecks.overviewShowsEverything(pick.evening!!, reply, after)
                else -> emptyList()
            }
            is Action.AdvanceClock -> emptyList()
        }
    }
}
