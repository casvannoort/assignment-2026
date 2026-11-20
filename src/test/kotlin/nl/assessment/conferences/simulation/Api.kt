package nl.assessment.conferences.simulation

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

data class Reply(val status: Int, val body: JsonNode?) {
    val code: String? get() = body?.get("code")?.asString()
    fun id(): UUID = UUID.fromString(body!!["id"].asString())
    fun idOrNull(): UUID? = body?.get("id")?.asString()?.let(UUID::fromString)
}

/** Talks to the running app over real HTTP, like a client would. */
class Api(port: Int) {
    private val base = "http://localhost:$port"
    private val client = HttpClient.newHttpClient()
    private val json = JsonMapper.builder().build()

    fun send(action: Action): Reply = when (action) {
        is Action.CreateEvening -> request(
            "POST", "/evenings", action.teacher,
            """{"date": "${action.date}", "start": "${action.start}", "end": "${action.end}",
               "conversationMinutes": ${action.minutes}${action.capacity?.let { """, "capacity": $it""" } ?: ""}}""",
        )
        is Action.Book -> request(
            "POST", "/evenings/${action.evening}/bookings", action.parent,
            """{"childId": "${action.child}", "slotId": "${action.slot}"}""",
        )
        is Action.Move -> request(
            "PUT", "/evenings/${action.evening}/bookings/${action.booking}", action.parent,
            """{"slotId": "${action.slot}"}""",
        )
        is Action.Cancel -> request("DELETE", "/evenings/${action.evening}/bookings/${action.booking}", action.parent)
        is Action.RemoveSlot -> request("DELETE", "/evenings/${action.evening}/slots/${action.slot}", action.teacher)
        is Action.Read -> request("GET", action.path, action.user)
        is Action.AdvanceClock -> error("the clock is not an API call")
    }

    private fun request(method: String, path: String, user: UUID?, body: String? = null): Reply {
        val request = HttpRequest.newBuilder(URI.create(base + path))
            .method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
            .header("Content-Type", "application/json")
            .timeout(java.time.Duration.ofSeconds(30))
            .apply { user?.let { header("X-User-Id", it.toString()) } }
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return Reply(response.statusCode(), response.body().takeIf { it.isNotBlank() }?.let(json::readTree))
    }
}
