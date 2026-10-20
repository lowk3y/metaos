package si.ogrodje.metaos

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.scheduling.annotation.Scheduled
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Checks every hour that a connection to an outside service (Google Drive, Gmail, Cloud Storage, Koofr, Dropbox) still
 * works. That keeps its credentials in use - Google drops refresh tokens that go unused for six months - and broken ones
 * show up right away: in the health check, the metaos_<name>_connected metric and the log, not only when something next
 * needs them.
 */
abstract class ConnectionHealthIndicator(private val name: String, registry: MeterRegistry) : HealthIndicator {

    private val logger = LoggerFactory.getLogger(javaClass)

    private class Check(val at: Instant, val detail: Pair<String, String>?, val error: String?)

    @Volatile
    private var last: Check? = null

    init {
        Gauge.builder("metaos.$name.connected") { if (last?.detail != null) 1 else 0 }
            .description("1 while MetaOS can reach $name as the bot account")
            .register(registry)
    }

    /** What the connection sees, e.g. "folder" to "Ogrodje"; throws when it doesn't work */
    protected abstract fun probe(): Pair<String, String>

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    fun check() {
        last = try {
            Check(Instant.now(), probe(), null)
        } catch (e: Exception) {
            Check(Instant.now(), null, e.message ?: e.toString())
        }
        last?.error?.let { logger.error("{}: {}", name, it) }
    }

    override fun health(): Health {
        val check = last ?: return Health.unknown().build()
        val health = check.detail?.let { (key, value) -> Health.up().withDetail(key, value) }
            ?: Health.down().withDetail("error", check.error!!)
        return health.withDetail("checkedAt", check.at.toString()).build()
    }
}
