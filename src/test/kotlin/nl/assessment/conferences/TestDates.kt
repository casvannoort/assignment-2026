package nl.assessment.conferences

import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/** Tests commit their data, so each one takes its own future date to stay independent. */
object TestDates {
    private val next = AtomicLong()

    fun unique(): LocalDate = LocalDate.parse("2030-01-01").plusDays(next.getAndIncrement())
}
