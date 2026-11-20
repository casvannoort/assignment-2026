package nl.assessment.conferences.command

import nl.assessment.conferences.TestDates
import nl.assessment.conferences.TestcontainersConfiguration
import nl.assessment.conferences.db.ConferenceEveningRepository
import nl.assessment.conferences.domain.EveningOverlaps
import nl.assessment.conferences.domain.TeacherId
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Duration
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class EveningCommandsTest(
    @Autowired private val commands: EveningCommands,
    @Autowired private val repository: ConferenceEveningRepository,
) {

    private val msJansen = TeacherId(UUID.fromString("00000000-0000-0000-0000-000000000101"))
    private val mrDeVries = TeacherId(UUID.fromString("00000000-0000-0000-0000-000000000102"))

    private val date = TestDates.unique()

    private fun create(teacher: TeacherId, start: String, end: String) = commands.create(
        teacher, date, LocalTime.parse(start), LocalTime.parse(end), Duration.ofMinutes(15), capacity = 1,
    )

    @Test
    fun `creates and stores an evening`() {
        val evening = create(msJansen, "19:00", "21:00")

        assertEquals(8, assertNotNull(repository.findById(evening.id)).slots.size)
    }

    @Test
    fun `rejects an evening that overlaps another evening of the same teacher`() {
        create(msJansen, "19:00", "21:00")

        assertFailsWith<EveningOverlaps> { create(msJansen, "20:00", "22:00") }
    }

    @Test
    fun `allows evenings at the same time for different teachers`() {
        create(msJansen, "19:00", "21:00")
        create(mrDeVries, "19:00", "21:00")
    }

    @Test
    fun `creating the same evening twice at the same time gives one evening`() {
        val pool = Executors.newFixedThreadPool(2)
        val attempt = Callable { runCatching { create(msJansen, "19:00", "21:00") } }

        val results = pool.invokeAll(listOf(attempt, attempt)).map { it.get() }
        pool.shutdown()

        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.exceptionOrNull() is EveningOverlaps })
    }
}
