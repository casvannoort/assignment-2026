package nl.assessment.conferences.simulation

/** Something the simulation found wrong. `check` names the rule, so findings can be counted per rule. */
data class Finding(val check: String, val message: String)

/**
 * A broken state stays broken, so each check would report it again. This keeps only what is new since the last
 * check: a lasting violation is reported once, at the step that caused it, and again if it disappears and comes back.
 */
class NewFindings {
    private var last = emptySet<Finding>()

    fun of(found: List<Finding>): List<Finding> = found.filter { it !in last }.also { last = found.toSet() }
}
