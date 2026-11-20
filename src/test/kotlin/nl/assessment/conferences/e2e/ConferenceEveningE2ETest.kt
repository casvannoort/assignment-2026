package nl.assessment.conferences.e2e

import nl.assessment.conferences.TestDates
import nl.assessment.conferences.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the real server on a random port and talks to it over HTTP, like a client would. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class ConferenceEveningE2ETest(@LocalServerPort port: Int) {

    private val http = RestClient.create("http://localhost:$port")
    private val msJansen = "00000000-0000-0000-0000-000000000101"
    private val anna = "00000000-0000-0000-0000-000000000201"
    private val mark = "00000000-0000-0000-0000-000000000202"
    private val emma = "00000000-0000-0000-0000-000000000301"
    private val date = TestDates.unique()

    @Test
    fun `a teacher creates an evening and sees it in the list`() {
        val created = http.post().uri("/evenings")
            .header("X-User-Id", msJansen)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"date": "$date", "start": "19:00", "end": "21:00", "conversationMinutes": 10}""")
            .retrieve().body<Map<String, Any>>()!!

        assertEquals(12, (created["slots"] as List<*>).size)

        val evenings = http.get().uri("/evenings")
            .header("X-User-Id", msJansen)
            .retrieve().body<List<Map<String, Any>>>()!!

        assertContains(evenings.map { it["id"] }, created["id"])
    }

    @Test
    fun `a parent books a conversation and the other parent sees it`() {
        val evening = http.post().uri("/evenings")
            .header("X-User-Id", msJansen)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"date": "$date", "start": "19:00", "end": "20:00", "conversationMinutes": 15}""")
            .retrieve().body<Map<String, Any>>()!!

        val freeSlots = http.get().uri("/evenings/${evening["id"]}/slots")
            .header("X-User-Id", anna)
            .retrieve().body<List<Map<String, Any>>>()!!

        val booking = http.post().uri("/evenings/${evening["id"]}/bookings")
            .header("X-User-Id", anna)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"childId": "$emma", "slotId": "${freeSlots[2]["id"]}"}""")
            .retrieve().body<Map<String, Any>>()!!

        val marksView = http.get().uri("/bookings")
            .header("X-User-Id", mark)
            .retrieve().body<List<Map<String, Any>>>()!!
            .single { it["id"] == booking["id"] }

        assertEquals("19:30:00", marksView["start"])
        assertEquals("Anna Bakker", marksView["bookedByName"])
    }

    @Test
    fun `the other parent moves the booking and then cancels it`() {
        val evening = http.post().uri("/evenings")
            .header("X-User-Id", msJansen)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"date": "${TestDates.unique()}", "start": "19:00", "end": "20:00", "conversationMinutes": 15}""")
            .retrieve().body<Map<String, Any>>()!!
        val slots = evening["slots"] as List<*>
        fun slotId(i: Int) = (slots[i] as Map<*, *>)["id"]

        val booking = http.post().uri("/evenings/${evening["id"]}/bookings")
            .header("X-User-Id", anna)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"childId": "$emma", "slotId": "${slotId(0)}"}""")
            .retrieve().body<Map<String, Any>>()!!
        val bookingUrl = "/evenings/${evening["id"]}/bookings/${booking["id"]}"

        http.put().uri(bookingUrl)
            .header("X-User-Id", mark)
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"slotId": "${slotId(3)}"}""")
            .retrieve().toBodilessEntity()

        val annasView = http.get().uri("/bookings").header("X-User-Id", anna)
            .retrieve().body<List<Map<String, Any>>>()!!
            .single { it["id"] == booking["id"] }
        assertEquals("19:45:00", annasView["start"])
        assertEquals("Mark Bakker", annasView["bookedByName"])

        http.delete().uri(bookingUrl).header("X-User-Id", mark).retrieve().toBodilessEntity()

        val bookingsAfter = http.get().uri("/bookings").header("X-User-Id", anna)
            .retrieve().body<List<Map<String, Any>>>()!!
        assertTrue(bookingsAfter.none { it["id"] == booking["id"] })
    }
}
