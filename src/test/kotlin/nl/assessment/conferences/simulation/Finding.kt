package nl.assessment.conferences.simulation

/** Something the simulation found wrong. `check` names the rule, so findings can be counted per rule. */
data class Finding(val check: String, val message: String)
