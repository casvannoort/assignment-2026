package nl.assessment.conferences.simulation

import org.springframework.jdbc.core.JdbcTemplate
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * "Booking opens". Per burst, each wave starts all its requests at the same moment:
 * 1. every teacher creates the same evening twice plus an overlapping one (teacher lock);
 * 2. both parents of a child book that child at two teachers at the same time (child lock);
 * 3. hundreds of parents book, with double clicks (evening lock);
 * 4. a third of the bookings move;
 * 5. the clock passes the end of those evenings, then parents book, move and cancel on them; all must be refused.
 * After each wave: the invariants, and whether every confirmed booking and move still stands.
 * Comparing before and after per request would report false races, so that only runs in sequential mode.
 */
class ConcurrentSimulator(private val api: Api, private val jdbc: JdbcTemplate, private val clock: MutableClock) {

    fun run(seed: Long, bursts: Int, parentsPerBurst: Int): RunResult {
        SchoolGenerator.reset(jdbc)
        val school = SchoolGenerator.generate(seed, teachers = 20, children = 300)
        SchoolGenerator.insert(jdbc, school)
        clock.reset(SimulationConfig.START)
        val random = Random(seed)
        val ended = EndedEvenings()
        val log = Collections.synchronizedList(mutableListOf<String>())
        val outcomes = ConcurrentHashMap<String, Int>()
        val findings = mutableListOf<Finding>()
        val seen = mutableSetOf<Finding>()

        fun wave(name: String, actions: List<Action>, check: (List<Pair<Action, Int>>) -> List<Finding> = { emptyList() }) {
            val results = Collections.synchronizedList(mutableListOf<Pair<Action, Int>>())
            val created = Collections.synchronizedList(mutableListOf<UUID>())
            val moved = Collections.synchronizedList(mutableListOf<Pair<UUID, UUID>>())
            val serverErrors = Collections.synchronizedList(mutableListOf<Finding>())
            val start = CountDownLatch(1)
            Executors.newVirtualThreadPerTaskExecutor().use { pool ->
                actions.forEach { action ->
                    pool.submit {
                        start.await()
                        val kind = action::class.simpleName
                        val outcome = try {
                            val reply = api.send(action)
                            results += action to reply.status
                            if (action is Action.Book && reply.status == 201) created += reply.id()
                            if (action is Action.Move && reply.status == 200) moved += action.booking to action.slot
                            if (reply.status >= 500) serverErrors += Finding("C.3 no server error", "$action gave ${reply.status}")
                            reply.code ?: if (reply.status >= 500) "SERVER_ERROR" else "$kind ${reply.status}"
                        } catch (e: HttpTimeoutException) {
                            "TIMEOUT"
                        }
                        outcomes.merge(outcome, 1, Int::plus)
                        log += "$name $kind -> $outcome"
                    }
                }
                start.countDown()
            }
            val after = Snapshot.take(jdbc)
            val found = Invariants.check(jdbc, LocalDateTime.now(clock), ended) +
                ConcurrentChecks.confirmedWritesStand(created, moved, after) + serverErrors + check(results.toList())
            findings += found.filter { seen.add(it) }.map { it.copy(message = "seed $seed $name (intermittent): ${it.message}") }
        }

        repeat(bursts) { burst ->
            val burstStart = LocalDateTime.now(clock)
            val today = burstStart.toLocalDate()

            val overlapDay = today.plusDays(2)
            wave("burst $burst overlapping evenings", school.teachers.flatMap { t ->
                val evening = Action.CreateEvening(t, overlapDay, LocalTime.of(19, 0), LocalTime.of(21, 0), 15, 1)
                listOf(evening, evening, evening.copy(start = LocalTime.of(20, 0), end = LocalTime.of(22, 0)))
            }.shuffled(random))

            val date = today.plusDays(1)
            val evenings = school.teachers.associateWith { teacher ->
                api.send(Action.CreateEvening(teacher, date, LocalTime.of(19, 0), LocalTime.of(21, 0), 15, random.nextInt(1, 3)))
            }.filterValues { it.status == 201 }.mapValues { (_, reply) ->
                reply.id() to reply.body!!["slots"].values().map { UUID.fromString(it["id"].asString()) }
            }

            wave("burst $burst one child at two teachers", school.children.filter {
                school.parentsOf.getValue(it).size == 2 && school.teachersOf.getValue(it).size >= 2
            }.flatMap { child ->
                val (first, second) = school.parentsOf.getValue(child)
                val (a, b) = school.teachersOf.getValue(child).shuffled(random)
                val (eveningA, slotsA) = evenings.getValue(a)
                val (eveningB, slotsB) = evenings.getValue(b)
                val k = random.nextInt(slotsA.size)
                listOf(Action.Book(first, eveningA, child, slotsA[k]), Action.Book(second, eveningB, child, slotsB[k]))
            }.shuffled(random))

            val bookings = school.parents.shuffled(random).take(parentsPerBurst).flatMap { parent ->
                school.childrenOf(parent).mapNotNull { child ->
                    val (evening, slots) = evenings[school.teachersOf.getValue(child).shuffled(random).first()] ?: return@mapNotNull null
                    Action.Book(parent, evening, child, slots[random.nextInt(slots.size)])
                }
            }
            wave("burst $burst bookings", (bookings + bookings.filter { random.nextInt(100) < 20 }).shuffled(random))

            val booked = Snapshot.take(jdbc).bookings.filter { b -> evenings.values.any { it.first == b.evening } }
            wave("burst $burst moves", booked.shuffled(random).take(booked.size / 3).map { b ->
                val slots = evenings.values.first { it.first == b.evening }.second
                Action.Move(school.parentsOf.getValue(b.child).first(), b.evening, b.id, slots[random.nextInt(slots.size)])
            })

            clock.advance(Duration.between(LocalDateTime.now(clock), date.atTime(21, 1)))
            val left = Snapshot.take(jdbc).bookings.filter { b -> evenings.values.any { it.first == b.evening } }.shuffled(random).take(100)
            wave("burst $burst after the end", (left.mapIndexed { i, b ->
                val parent = school.parentsOf.getValue(b.child).first()
                if (i % 2 == 0) Action.Cancel(parent, b.evening, b.id)
                else Action.Move(parent, b.evening, b.id, evenings.values.first { it.first == b.evening }.second.first())
            } + bookings.shuffled(random).take(100)).shuffled(random), ConcurrentChecks::refusedAfterEnd)

            clock.advance(Duration.between(LocalDateTime.now(clock), burstStart.plusDays(3)))
        }
        return RunResult(seed, log.toList(), outcomes.toMap(), findings)
    }
}
