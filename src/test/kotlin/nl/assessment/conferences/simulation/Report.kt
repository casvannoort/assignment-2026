package nl.assessment.conferences.simulation

import java.io.File

/** Writes what a simulation run saw to build/simulation, for SIMULATION.md. */
object Report {

    /** Outcomes a run must reach; otherwise "no findings" means nothing. */
    val keyOutcomes = setOf(
        "SLOT_FULL", "CHILD_ALREADY_BOOKED", "CHILD_DOUBLE_BOOKED", "FORBIDDEN", "EVENING_HAS_ENDED", "SLOT_HAS_BOOKINGS",
        "BOOKING_NOT_FOUND", "SLOT_NOT_FOUND", "UNAUTHENTICATED", "SLOT_HAS_STARTED",
    )

    fun write(mode: String, runs: List<RunResult>): File {
        val dir = File("build/simulation").apply { mkdirs() }
        runs.forEach { File(dir, "$mode-seed-${it.seed}.log").writeText(it.log.joinToString("\n")) }
        val outcomes = runs.flatMap { it.outcomes.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        val findings = runs.flatMap { it.findings }.groupBy { it.check }
        val report = File(dir, "$mode-report.md")
        report.writeText(buildString {
            appendLine("# $mode simulation: seeds ${runs.map { it.seed }}, ${runs.sumOf { it.log.size }} steps")
            appendLine()
            appendLine("## Outcomes")
            outcomes.toSortedMap().forEach { (k, v) -> appendLine("- $k: $v") }
            appendLine()
            appendLine("## Per result, over all actions")
            outcomes.entries.groupBy({ it.key.substringAfterLast(' ') }, { it.value }).toSortedMap()
                .forEach { (k, v) -> appendLine("- $k: ${v.sum()}") }
            appendLine()
            appendLine("## Findings (${findings.values.sumOf { it.size }})")
            findings.toSortedMap().forEach { (check, list) ->
                appendLine("- **$check**: ${list.size}")
                list.take(3).forEach { appendLine("  - ${it.message}") }
            }
        })
        return report
    }
}
