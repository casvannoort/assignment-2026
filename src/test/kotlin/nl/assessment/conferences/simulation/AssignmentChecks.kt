package nl.assessment.conferences.simulation

import tools.jackson.databind.JsonNode
import java.time.Duration
import java.time.LocalTime
import java.util.UUID

/**
 * Layer 1: the seven items of the original assignment. Expectations come from the request and the
 * assignment text, not from the app's own rules.
 */
object AssignmentChecks {

    private fun JsonNode.items(): List<JsonNode> = values().toList()
    private fun JsonNode.uuid(field: String): UUID = UUID.fromString(get(field).asString())
    private fun JsonNode.time(field: String): LocalTime = LocalTime.parse(get(field).asString())
    private fun finding(item: Int, name: String, message: String) = Finding("L1.$item $name", message)

    /** 1. A teacher creates an evening with a date, start, end and conversation length; the slots come from that. */
    fun slotsFollowRequest(create: Action.CreateEvening, reply: Reply): List<Finding> {
        if (reply.status != 201) return emptyList()
        val length = Duration.ofMinutes(create.minutes)
        val expected = (0 until Duration.between(create.start, create.end).dividedBy(length)).map {
            create.start.plus(length.multipliedBy(it)).let { start -> start to start.plus(length) }
        }
        val actual = reply.body!!["slots"].items().map { it.time("start") to it.time("end") }
        return listOfNotNull(
            finding(1, "slots follow the request", "$create gave $actual, expected $expected").takeIf { actual != expected },
        )
    }

    /** 2. A slot has a maximum number of conversations, by default one. */
    fun capacityDefaultsToOne(create: Action.CreateEvening, reply: Reply): List<Finding> {
        if (reply.status != 201) return emptyList()
        val expected = create.capacity ?: 1
        val actual = reply.body!!["slots"].items().map { it["capacity"].asInt() }.toSet()
        return listOfNotNull(
            finding(2, "capacity defaults to one", "$create gave capacities $actual").takeIf { actual.any { it != expected } },
        )
    }

    /** 3. A parent sees the free slots of an evening. */
    fun freeSlotsHaveRoom(evening: UUID, reply: Reply, snapshot: Snapshot): List<Finding> {
        if (reply.status != 200) return emptyList()
        val expected = snapshot.slots.filter { s -> s.evening == evening && snapshot.bookings.count { it.slot == s.id } < s.capacity }
            .map { it.id }.toSet()
        val actual = reply.body!!.items().map { it.uuid("id") }.toSet()
        return listOfNotNull(
            finding(3, "free slots have room", "evening $evening listed $actual, expected $expected").takeIf { actual != expected },
        )
    }

    /** 4. A parent can move a booking to another slot, or cancel it. */
    fun moveOrCancelTookEffect(action: Action, reply: Reply, after: Snapshot): List<Finding> {
        if (reply.status !in 200..299) return emptyList()
        val ok = when (action) {
            is Action.Move -> after.bookings.any { it.id == action.booking && it.slot == action.slot }
            is Action.Cancel -> after.bookings.none { it.id == action.booking }
            else -> true
        }
        return listOfNotNull(finding(4, "move or cancel took effect", "$action did not take effect").takeIf { !ok })
    }

    /** 5. A child can have more than one parent; every parent of the child sees the appointment, at its current time. */
    fun everyParentSeesBooking(snapshot: Snapshot, parentsOf: Map<UUID, List<UUID>>, bookingsOf: Map<UUID, Reply>): List<Finding> =
        snapshot.bookings.flatMap { booking ->
            val start = snapshot.slots.first { it.id == booking.slot }.start
            parentsOf[booking.child].orEmpty().mapNotNull { parent ->
                val seen = bookingsOf[parent]?.takeIf { it.status == 200 }?.body?.items()
                    ?.associate { it.uuid("id") to it.time("start") } ?: return@mapNotNull null
                finding(5, "every parent sees the booking", "parent $parent sees ${booking.id} at ${seen[booking.id]}, expected $start")
                    .takeIf { seen[booking.id] != start }
            }
        }

    /** 5. After a cancel, no parent of the child still sees the appointment. */
    fun cancelledIsGone(booking: UUID, bookingsOf: Map<UUID, Reply>): List<Finding> = bookingsOf.mapNotNull { (parent, reply) ->
        val seen = reply.takeIf { it.status == 200 }?.body?.items()?.map { it.uuid("id") } ?: return@mapNotNull null
        finding(5, "every parent sees the cancel", "parent $parent still sees cancelled $booking").takeIf { booking in seen }
    }

    /** 6. A teacher sees which child comes when, and with which parent: every slot, every booking, the right names, nothing extra. */
    fun overviewShowsEverything(evening: UUID, reply: Reply, snapshot: Snapshot, nameOf: (UUID) -> String): List<Finding> {
        if (reply.status != 200) return emptyList()
        val rows = reply.body!!.items()
        val slotIds = rows.map { it.uuid("slotId") }.toSet()
        val expectedSlots = snapshot.slots.filter { it.evening == evening }.map { it.id }.toSet()
        val bookings = snapshot.bookings.filter { it.evening == evening }
        val shown = rows.sumOf { it["conversations"].items().size }
        val wrong = bookings.filter { booking ->
            rows.none { row ->
                row.uuid("slotId") == booking.slot && row["conversations"].items().any {
                    it.uuid("bookingId") == booking.id && it["childName"].asString() == nameOf(booking.child) &&
                        it["parentName"].asString() == nameOf(booking.bookedBy)
                }
            }
        }
        return listOfNotNull(
            finding(6, "overview shows every slot", "evening $evening shows $slotIds, expected $expectedSlots")
                .takeIf { slotIds != expectedSlots },
            finding(6, "overview shows who comes when with which parent", "evening $evening wrong or missing ${wrong.map { it.id }}")
                .takeIf { wrong.isNotEmpty() },
            finding(6, "overview shows nothing extra", "evening $evening shows $shown conversations, expected ${bookings.size}")
                .takeIf { shown != bookings.size },
        )
    }

    /** Our rule: a teacher lists their own evenings, a parent the upcoming evenings of their children's teachers. */
    fun eveningListIsRight(user: UUID?, reply: Reply, snapshot: Snapshot, now: java.time.LocalDateTime, school: School): List<Finding> {
        if (reply.status != 200 || user == null) return emptyList()
        val expected = when (user) {
            in school.teachers -> snapshot.evenings.filter { it.teacher == user }
            in school.parents -> snapshot.evenings.filter { e ->
                java.time.LocalDateTime.of(e.date, e.end).isAfter(now) &&
                    school.childrenOf(user).any { e.teacher in school.teachersOf.getValue(it) }
            }
            else -> return emptyList()
        }.map { it.id }.toSet()
        val actual = reply.body!!.items().map { it.uuid("id") }.toSet()
        return listOfNotNull(
            Finding("L2.8 evening lists", "user $user sees $actual, expected $expected").takeIf { actual != expected },
        )
    }

    /** 7. A teacher can remove a slot, for example for a break. */
    fun removedSlotIsGone(slot: UUID, overview: Reply, freeSlots: Reply): List<Finding> {
        val inOverview = overview.status == 200 && overview.body!!.items().any { it.uuid("slotId") == slot }
        val inFreeSlots = freeSlots.status == 200 && freeSlots.body!!.items().any { it.uuid("id") == slot }
        return listOfNotNull(finding(7, "removed slot is gone", "slot $slot still listed").takeIf { inOverview || inFreeSlots })
    }
}
