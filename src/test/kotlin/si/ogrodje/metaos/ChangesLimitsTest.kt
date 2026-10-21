package si.ogrodje.metaos

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// /events is open to anyone (Changes.kt): a pod takes only so many streams, and each ends after its lifetime
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["metaos.changes.max-streams=2", "metaos.changes.lifetime=3s", "metaos.changes.heartbeat=1s"],
)
@Import(TestcontainersConfig::class)
class ChangesLimitsTest(@Value("\${local.server.port}") private val port: Int) {

    private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
    private val open = mutableListOf<HttpResponse<Stream<String>>>()

    private fun connect(): HttpResponse<Stream<String>> = client.send(
        HttpRequest.newBuilder(URI("http://localhost:$port/events")).header("Accept", "text/event-stream").build(),
        BodyHandlers.ofLines(),
    ).also { open += it }

    @AfterEach
    fun close() = open.forEach { it.body().close() }

    @Test
    fun `turns away a stream past the limit, and takes one again once a stream has ended`() {
        val first = connect()
        assertEquals(200, connect().statusCode())
        val third = connect()
        assertEquals(503, third.statusCode())
        assertEquals("30", third.headers().firstValue("Retry-After").orElse(null))

        // Ends within its lifetime, give or take a sixth, like a stream the pod closed: the browser connects again
        val started = Instant.now()
        CompletableFuture.runAsync { first.body().forEach { } }.get(10, TimeUnit.SECONDS)
        val took = Duration.between(started, Instant.now())
        assertTrue(took < Duration.ofMillis(3500 + 1000), "ended after $took")

        // The stream that ended frees its place, once the pod has noticed
        val until = Instant.now() + Duration.ofSeconds(5)
        var again = connect()
        while (again.statusCode() == 503 && Instant.now() < until) {
            Thread.sleep(100)
            again = connect()
        }
        assertEquals(200, again.statusCode())
    }
}
