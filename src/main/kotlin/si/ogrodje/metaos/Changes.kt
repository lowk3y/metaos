package si.ogrodje.metaos

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.postgresql.PGConnection
import org.postgresql.ds.PGSimpleDataSource
import org.slf4j.LoggerFactory
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails
import org.springframework.context.SmartLifecycle
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap

/** Where Postgres says which table changed (V22__change_notifications.sql) */
private const val CHANNEL = "metaos_changes"

/** The listening connection's name, e.g. in pg_stat_activity */
const val CHANGES_APPLICATION_NAME = "metaos-changes"

// How long the listener waits for a notification before it checks that its connection still works
private const val CHECK_AFTER_MILLIS = 10_000

// How long a browser waits before it connects again, after its stream ended
private const val RECONNECT_MILLIS = 1_000L

// After its connection fails, the listener tries again in a second, then twice as long each time, up to half a minute
private const val RETRY_MILLIS = 1_000L
private const val MAX_RETRY_MILLIS = 30_000L

/**
 * Tells the front-end's pages when MetaOS's data changes, so they show it right away (fe/src/changes.ts).
 *
 * Postgres says which table changed on channel metaos_changes, to every connection listening, so each pod listens on a
 * connection of its own and passes it on to the browsers connected to it, as Server-Sent Events at /events: `change`
 * with the table's name, and `resync` when it may have missed some, after its connection to Postgres broke. Pages load
 * their data again through /graphql, with their own token, so the events say no more than a table's name, to anyone.
 */
@Component
class Changes(private val database: JdbcConnectionDetails, registry: MeterRegistry) : SmartLifecycle {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val streams = ConcurrentHashMap.newKeySet<SseEmitter>()

    @Volatile
    private var listener: Listener? = null

    /** Whether it's listening to Postgres, rather than (re)connecting or stopped */
    val listening get() = listener?.listening == true

    init {
        Gauge.builder("metaos.changes.listening") { if (listening) 1 else 0 }
            .description("1 while MetaOS listens for changes in Postgres, for the front-end's pages")
            .register(registry)
        Gauge.builder("metaos.changes.streams") { streams.size }
            .description("Browsers connected to be told about changes")
            .register(registry)
    }

    /** A browser's stream of changes, until it goes away; it doesn't time out */
    fun stream(): SseEmitter {
        val stream = SseEmitter(0)
        // Right away, as the response's headers go with its first bytes, and EventSource is open only once they come.
        // It says how soon to connect again after the stream ends, e.g. while this pod stops.
        stream.send(SseEmitter.event().reconnectTime(RECONNECT_MILLIS))
        streams += stream
        stream.onCompletion { streams -= stream }
        stream.onError { streams -= stream }
        // Stopping, also since just now, when stop() may have missed it: the browser connects again, to another pod
        if (listener == null) stream.complete()
        return stream
    }

    // Cloudflare hangs up on a response that has sent nothing for 100 s
    @Scheduled(fixedDelayString = "\${metaos.changes.heartbeat:25s}")
    fun heartbeat() = send(SseEmitter.event().comment(""))

    private fun send(event: SseEmitter.SseEventBuilder) {
        val data = event.build()
        // A thread each, so a browser that doesn't read holds up neither the others nor the listener; one that's gone
        // fails, at the latest on the next heartbeat
        streams.forEach { stream ->
            Thread.ofVirtual().start {
                try {
                    stream.send(data)
                } catch (_: Exception) {
                    streams -= stream
                }
            }
        }
    }

    override fun start() {
        listener = Listener().also { it.start() }
    }

    override fun stop() {
        listener?.close()
        listener = null
        // Graceful shutdown waits for open requests, and a stream never ends by itself. Ended, the browsers connect again,
        // to a pod that isn't stopping.
        streams.forEach { stream -> Thread.ofVirtual().start { stream.complete() } }
    }

    override fun isRunning() = listener != null

    /** Listens on a connection of its own, outside the pool, as it's busy for as long as it listens */
    private inner class Listener : Thread("metaos-changes") {

        @Volatile
        var listening = false
            private set

        @Volatile
        private var closed = false

        @Volatile
        private var connection: Connection? = null

        init {
            isDaemon = true
        }

        override fun run() {
            var failures = 0
            while (!closed) {
                try {
                    connect().use { connection ->
                        connection.createStatement().use { it.execute("LISTEN $CHANNEL") }
                        listening = true
                        if (failures > 0) {
                            logger.info("Listening for changes again")
                            // EventSource drops an event without data
                            send(SseEmitter.event().name("resync").data("*"))
                            failures = 0
                        }
                        listen(connection)
                    }
                } catch (e: Exception) {
                    listening = false
                    if (closed) return
                    failures++
                    logger.warn("Not listening for changes: {}", e.message)
                    try {
                        sleep(minOf(RETRY_MILLIS shl minOf(failures - 1, 5), MAX_RETRY_MILLIS))
                    } catch (_: InterruptedException) {
                        return
                    }
                }
            }
        }

        private fun connect(): Connection = PGSimpleDataSource().apply {
            setUrl(database.jdbcUrl)
            user = database.username
            password = database.password
            applicationName = CHANGES_APPLICATION_NAME
            tcpKeepAlive = true
        }.connection.also { connection = it }

        private fun listen(connection: Connection) {
            val notifications = connection.unwrap(PGConnection::class.java)
            while (!closed) {
                val received = notifications.getNotifications(CHECK_AFTER_MILLIS)
                received.forEach { send(SseEmitter.event().name("change").data(it.parameter)) }
                // A connection that broke without a word shows only when it's used
                if (received.isEmpty() && !connection.isValid(5)) throw SQLException("The connection to Postgres broke")
            }
        }

        fun close() {
            closed = true
            // Unlike close(), abort() doesn't wait for getNotifications, which then fails
            connection?.abort { it.run() }
            interrupt()
        }
    }
}

// Public (SecurityConfig): it says only which tables changed, and EventSource can't send a token anyway
@RestController
class ChangesController(private val changes: Changes) {

    @GetMapping("/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun events(): ResponseEntity<SseEmitter> = ResponseEntity.ok()
        // Tells ingress-nginx to pass each event on as it comes, whatever the Ingress says
        .header("X-Accel-Buffering", "no")
        .body(changes.stream())
}
