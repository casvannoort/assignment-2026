package nl.assessment.conferences.simulation

import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import kotlin.random.Random

data class School(
    val teachers: List<UUID>,
    val parents: List<UUID>,
    val children: List<UUID>,
    val parentsOf: Map<UUID, List<UUID>>,
    val teachersOf: Map<UUID, List<UUID>>,
) {
    fun childrenOf(parent: UUID) = children.filter { parent in parentsOf.getValue(it) }
}

/** Generates a school from a seed. Families have 1 to 3 children and 1 or 2 parents; each child has 1 to 4 teachers. */
object SchoolGenerator {

    fun generate(seed: Long, teachers: Int, children: Int): School {
        val random = Random(seed)
        fun id() = UUID(random.nextLong(), random.nextLong())

        val teacherIds = List(teachers) { id() }
        val childIds = mutableListOf<UUID>()
        val parentIds = mutableListOf<UUID>()
        val parentsOf = mutableMapOf<UUID, List<UUID>>()
        while (childIds.size < children) {
            val familyParents = List(if (random.nextInt(10) < 6) 2 else 1) { id() }
            parentIds += familyParents
            repeat(minOf(random.nextInt(1, 4), children - childIds.size)) {
                val child = id()
                childIds += child
                parentsOf[child] = familyParents
            }
        }
        val teachersOf = childIds.associateWith {
            teacherIds.shuffled(random).take(random.nextInt(1, minOf(4, teachers) + 1))
        }
        return School(teacherIds, parentIds, childIds, parentsOf, teachersOf)
    }

    fun insert(jdbc: JdbcTemplate, school: School) {
        school.teachers.forEachIndexed { i, id -> jdbc.update("INSERT INTO teacher VALUES (?, ?)", id, "Teacher $i") }
        school.parents.forEachIndexed { i, id -> jdbc.update("INSERT INTO parent VALUES (?, ?)", id, "Parent $i") }
        school.children.forEachIndexed { i, id -> jdbc.update("INSERT INTO child VALUES (?, ?)", id, "Child $i") }
        school.parentsOf.forEach { (child, parents) ->
            parents.forEach { jdbc.update("INSERT INTO parent_child VALUES (?, ?)", it, child) }
        }
        school.teachersOf.forEach { (child, teachers) ->
            teachers.forEach { jdbc.update("INSERT INTO teacher_child VALUES (?, ?)", it, child) }
        }
    }

    /** Removes all evenings and everything generated; keeps the fixed seed data (ids 00000000-0000-0000-0000-…). */
    fun reset(jdbc: JdbcTemplate) {
        listOf("booking", "slot", "evening").forEach { jdbc.update("DELETE FROM $it") }
        val generated = "NOT LIKE '00000000-0000-0000-0000-%'"
        jdbc.update("DELETE FROM parent_child WHERE parent_id::text $generated")
        jdbc.update("DELETE FROM teacher_child WHERE teacher_id::text $generated OR child_id::text $generated")
        listOf("parent", "child", "teacher").forEach { jdbc.update("DELETE FROM $it WHERE id::text $generated") }
    }
}
