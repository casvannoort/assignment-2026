package nl.assessment.conferences.simulation

import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDateTime
import java.util.UUID

/** Remembers each evening as it was when it ended, so later changes to it can be spotted. */
class EndedEvenings {
    private val frozen = mutableMapOf<UUID, Pair<Set<SlotRow>, Set<BookingRow>>>()

    fun update(snapshot: Snapshot, now: LocalDateTime) {
        snapshot.evenings.filter { !LocalDateTime.of(it.date, it.end).isAfter(now) && it.id !in frozen }.forEach { e ->
            frozen[e.id] = snapshot.slots.filter { it.evening == e.id }.toSet() to snapshot.bookings.filter { it.evening == e.id }.toSet()
        }
    }

    fun changed(snapshot: Snapshot): List<UUID> = frozen.filter { (id, state) ->
        state != (snapshot.slots.filter { it.evening == id }.toSet() to snapshot.bookings.filter { it.evening == id }.toSet())
    }.keys.toList()
}

/** Layer 2: our own rules, checked with SQL straight on the tables, not through the app. */
object Invariants {

    private val queries = mapOf(
        "L2.1 slots fit the evening" to """
            SELECT s.id FROM slot s JOIN evening e ON e.id = s.evening_id
            WHERE s.start_time < e.start_time OR s.end_time > e.end_time OR s.capacity < 1
               OR s.end_time - s.start_time <> make_interval(mins => e.conversation_minutes)
            UNION ALL
            SELECT a.id FROM slot a JOIN slot b ON a.evening_id = b.evening_id AND a.id < b.id
            WHERE a.start_time < b.end_time AND b.start_time < a.end_time
        """,
        "L2.2 capacity" to """
            SELECT s.id FROM slot s JOIN booking b ON b.slot_id = s.id
            GROUP BY s.id, s.capacity HAVING count(*) > s.capacity
        """,
        "L2.3 booking slot exists in its evening" to """
            SELECT b.id FROM booking b LEFT JOIN slot s ON s.id = b.slot_id
            WHERE s.id IS NULL OR s.evening_id <> b.evening_id
        """,
        "L2.4 child in one place at a time" to """
            SELECT a.id FROM booking a
            JOIN booking b ON a.child_id = b.child_id AND a.id < b.id
            JOIN slot sa ON sa.id = a.slot_id JOIN slot sb ON sb.id = b.slot_id
            JOIN evening ea ON ea.id = a.evening_id JOIN evening eb ON eb.id = b.evening_id
            WHERE a.evening_id = b.evening_id
               OR (ea.date = eb.date AND sa.start_time < sb.end_time AND sb.start_time < sa.end_time)
        """,
        "L2.5 parent and teacher belong to the child" to """
            SELECT b.id FROM booking b JOIN evening e ON e.id = b.evening_id
            WHERE NOT EXISTS (SELECT 1 FROM parent_child pc WHERE pc.parent_id = b.booked_by AND pc.child_id = b.child_id)
               OR NOT EXISTS (SELECT 1 FROM teacher_child tc WHERE tc.teacher_id = e.teacher_id AND tc.child_id = b.child_id)
        """,
        "L2.6 no overlapping evenings per teacher" to """
            SELECT a.id FROM evening a JOIN evening b ON a.teacher_id = b.teacher_id AND a.id < b.id
            WHERE a.date = b.date AND a.start_time < b.end_time AND b.start_time < a.end_time
        """,
    )

    fun check(jdbc: JdbcTemplate, now: LocalDateTime, ended: EndedEvenings): List<Finding> {
        val sql = queries.flatMap { (check, query) ->
            jdbc.queryForList(query, UUID::class.java).map { Finding(check, "row $it") }
        }
        val snapshot = Snapshot.take(jdbc)
        val afterEnd = ended.changed(snapshot).map { Finding("L2.7 nothing changes after the end", "evening $it") }
        ended.update(snapshot, now)
        return sql + afterEnd
    }
}
