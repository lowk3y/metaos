package si.ogrodje.metaos

import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.UserCredentials
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.scheduling.annotation.Scheduled
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/*
 * MetaOS uses Google as Ogrodje's bot account, through the "Desktop app" OAuth client "MetaOS" of Google Cloud project
 * ogrodje-metaos. Drive and Gmail have a refresh token each (google/refresh_token.sh): Google revokes tokens with Gmail
 * access when the account's password changes, which shouldn't take Drive down with it.
 */

val googleTransport = NetHttpTransport()
val googleJson: GsonFactory = GsonFactory.getDefaultInstance()

/**
 * A refresh token of the bot account. The library turns it into access tokens; [call] says what to do when Google stops
 * accepting it - the bot removed MetaOS's access, or the token went unused for six months.
 */
class GoogleAccount(clientId: String, clientSecret: String, refreshToken: String, private val renew: String) {

    private val credentials = UserCredentials.newBuilder()
        .setClientId(clientId).setClientSecret(clientSecret).setRefreshToken(refreshToken)
        .build()

    /** Signs a Google API client's requests, and gives them timeouts */
    val requests = HttpRequestInitializer { request ->
        HttpCredentialsAdapter(credentials).initialize(request)
        request.connectTimeout = 10_000
        request.readTimeout = 30_000
    }

    fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        if ("invalid_grant" in e.message.orEmpty()) throw IllegalStateException("Google no longer accepts the bot's refresh token. $renew", e)
        throw e
    }
}

/**
 * Checks every hour that a Google connection still works. That keeps its refresh token in use - Google drops refresh
 * tokens that go unused for six months - and a revoked one shows up right away: in the health check, the
 * metaos_<name>_connected metric and the log, not only when something next needs it.
 */
abstract class GoogleHealthIndicator(private val name: String, registry: MeterRegistry) : HealthIndicator {

    private val logger = LoggerFactory.getLogger(javaClass)

    private class Check(val at: Instant, val detail: Pair<String, String>?, val error: String?)

    @Volatile
    private var last: Check? = null

    init {
        Gauge.builder("metaos.$name.connected") { if (last?.detail != null) 1 else 0 }
            .description("1 while MetaOS can reach Google ($name) as the bot account")
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
        last?.error?.let { logger.error("Google ({}): {}", name, it) }
    }

    override fun health(): Health {
        val check = last ?: return Health.unknown().build()
        val health = check.detail?.let { (key, value) -> Health.up().withDetail(key, value) }
            ?: Health.down().withDetail("error", check.error!!)
        return health.withDetail("checkedAt", check.at.toString()).build()
    }
}
