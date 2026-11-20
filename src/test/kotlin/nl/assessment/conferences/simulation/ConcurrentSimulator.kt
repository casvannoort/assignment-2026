package nl.assessment.conferences.simulation

import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * "Booking opens": every teacher opens an evening, then hundreds of parents book at the same moment
 * (with double clicks), then many move at once. Only the invariants run here, after each wave:
 * comparing before and after per request would report false races.
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
        val outcomes = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val findings = mutableListOf<Finding>()

        fun record(kind: String, label: String, call: () -> Reply) {
            val outcome = runCatching(call).fold(
                { reply -> reply.code ?: if (reply.status >= 500) "SERVER_ERROR" else "$kind ${reply.status}" },
                { "TIMEOUT_OR_IO" },
            )
            outcomes.merge(outcome, 1, Int::plus)
            log += "$label -> $outcome"
        }

        fun wave(name: String, calls: List<Pair<String, () -> Reply>>) {
            val start = CountDownLatch(1)
            Executors.newVirtualThreadPerTaskExecutor().use { pool ->
                calls.forEach { (label, call) -> pool.submit { start.await(); record(label, name, call) } }
                start.countDown()
            }
            Invariants.check(jdbc, LocalDateTime.now(clock), ended)
                .forEach { findings += it.copy(message = "seed $seed $name (intermittent): ${it.message}") }
        }

        repeat(bursts) { burst ->
            val date = LocalDateTime.now(clock).toLocalDate().plusDays(1)
            val evenings = school.teachers.associateWith { teacher ->
                api.send(Action.CreateEvening(teacher, date, LocalTime.of(19, 0), LocalTime.of(21, 0), 15, random.nextInt(1, 3)))
            }.filterValues { it.status == 201 }.mapValues { (_, reply) ->
                reply.id() to reply.body!!["slots"].values().map { java.util.UUID.fromString(it["id"].asString()) }
            }

            val bookings = school.parents.shuffled(random).take(parentsPerBurst).flatMap { parent ->
                school.childrenOf(parent).mapNotNull { child ->
                    val teacher = school.teachersOf.getValue(child).shuffled(random).first()
                    val (evening, slots) = evenings[teacher] ?: return@mapNotNull null
                    val book = Action.Book(parent, evening, child, slots[random.nextInt(slots.size)])
                    "book" to { api.send(book) }
                }
            }
            val doubleClicks = bookings.filter { random.nextInt(100) < 20 }
            wave("burst $burst bookings", (bookings + doubleClicks).shuffled(random))

            val booked = Snapshot.take(jdbc).bookings.filter { b -> evenings.values.any { it.first == b.evening } }
            val moves = booked.shuffled(random).take(booked.size / 3).map { b ->
                val slots = evenings.values.first { it.first == b.evening }.second
                val move = Action.Move(school.parentsOf.getValue(b.child).first(), b.evening, b.id, slots[random.nextInt(slots.size)])
                "move" to { api.send(move) }
            }
            wave("burst $burst moves", moves)

            clock.advance(Duration.ofDays(1))
        }
        return RunResult(seed, log.toList(), outcomes.toMap(), findings)
    }
}
