package nl.assessment.conferences.domain

import java.time.LocalTime

data class TimeRange(val start: LocalTime, val end: LocalTime) {
    init {
        require(end > start) { "end must be after start" }
    }

    fun overlaps(other: TimeRange) = start < other.end && other.start < end
}
