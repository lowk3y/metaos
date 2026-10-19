package si.ogrodje.metaos

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Status
import kotlin.test.assertEquals

class DriveHealthIndicatorTest {

    @Test
    fun `reports whether the bot can still reach the Ogrodje folder`() {
        val drive = FakeDrive()
        val registry = SimpleMeterRegistry()
        val indicator = DriveHealthIndicator(drive, registry)
        fun connected() = registry.get("metaos.drive.connected").gauge().value()

        assertEquals(Status.UNKNOWN, indicator.health().status)

        indicator.check()
        assertEquals(Status.UP, indicator.health().status)
        assertEquals("Ogrodje", indicator.health().details["folder"])
        assertEquals(1.0, connected())

        drive.failure = IllegalStateException("Google no longer accepts the Drive bot's refresh token")
        indicator.check()
        assertEquals(Status.DOWN, indicator.health().status)
        assertEquals("Google no longer accepts the Drive bot's refresh token", indicator.health().details["error"])
        assertEquals(0.0, connected())

        drive.failure = null
        indicator.check()
        assertEquals(1.0, connected())
    }
}
