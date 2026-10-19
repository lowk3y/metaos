package si.ogrodje.metaos

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest(properties = ["metaos.koofr.email=bot@example.com", "metaos.koofr.app-password=test"])
@Import(TestcontainersConfig::class, KoofrFoldersTest.Config::class)
class KoofrFoldersTest @Autowired constructor(
    private val koofr: FakeKoofr,
    private val episodesRepository: EpisodesRepository,
    private val sessionsRepository: SessionsRepository,
    private val koofrMCP: KoofrMCP,
    private val foldersMCP: KoofrFoldersMCP,
    private val auditMCP: AuditMCP,
) {

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun fakeKoofr() = FakeKoofr().apply {
            place("mount-media", "Ogrodje Media")
            place("mount-archive", "Arhiv")
        }
    }

    @Test
    fun `links Koofr folders to episodes and sessions, and they follow moves made through MetaOS`() {
        val recordingDay = sessionsRepository.saveAndFlush(Session(LocalDate.of(2026, 10, 22), location = "Four Points", address = null))
        val day = recordingDay.id!!
        val episode = episodesRepository.saveAndFlush(Episode(title = "Borderless", code = "S09E21").also { it.session = recordingDay }).id!!
        koofr.put("mount-media", "/Snemanja", null)
        koofr.put("mount-media", "/Snemanja/2026-10-22", null)
        koofr.put("mount-media", "/Snemanja/2026-10-22/S09E21", null)
        koofr.put("mount-media", "/Snemanja/2026-10-22/S09E21/raw.wav", ByteArray(8), "audio/wav")

        foldersMCP.linkSession(day, "Ogrodje Media", "/Snemanja/2026-10-22")
        foldersMCP.linkEpisode("s09e21", "mount-media", "Snemanja/2026-10-22/S09E21/")
        foldersMCP.linkEpisode("S09E21", "mount-media", "/Snemanja/2026-10-22/S09E21") // already linked
        assertFailsWith<IllegalArgumentException> { foldersMCP.linkEpisode("S09E21", "mount-media", "/Snemanja/2026-10-22/S09E21/raw.wav") }
        assertFailsWith<IllegalArgumentException> { foldersMCP.linkEpisode("S09E21", "mount-media", "/Nowhere") }
        assertFailsWith<IllegalArgumentException> { foldersMCP.linkEpisode("S09E21", "mount-own", "/") }

        fun folders() = foldersMCP.listEpisodeFolders("S09E21").map { Triple(it.placeName, it.path, it.linkedTo) }
        assertEquals(
            listOf(
                Triple("Ogrodje Media", "/Snemanja/2026-10-22", LinkedTo.SESSION),
                Triple("Ogrodje Media", "/Snemanja/2026-10-22/S09E21", LinkedTo.EPISODE),
            ),
            folders(),
        )

        // Into another place, with the episode's folder in it
        koofrMCP.move("Ogrodje Media", "/Snemanja/2026-10-22", "/2026-10-22", toPlace = "Arhiv")
        assertEquals(
            listOf(Triple("Arhiv", "/2026-10-22", LinkedTo.SESSION), Triple("Arhiv", "/2026-10-22/S09E21", LinkedTo.EPISODE)),
            folders(),
        )
        assertEquals(listOf(false, false), foldersMCP.listEpisodeFolders("S09E21").map { it.missing })

        // Moved in the Koofr app, not through MetaOS
        koofr.move("mount-archive", "/2026-10-22/S09E21", "mount-archive", "/S09E21")
        assertEquals(listOf(false, true), foldersMCP.listEpisodeFolders("S09E21").map { it.missing })

        foldersMCP.unlinkEpisode("S09E21", "Arhiv", "/2026-10-22/S09E21")
        assertEquals(listOf(LinkedTo.SESSION), foldersMCP.listEpisodeFolders("S09E21").map { it.linkedTo })
        assertFailsWith<IllegalArgumentException> { foldersMCP.unlinkEpisode("S09E21", "Arhiv", "/2026-10-22/S09E21") }
        foldersMCP.unlinkSession(day, "mount-archive", "/2026-10-22")
        assertTrue(foldersMCP.listSessionFolders(day).isEmpty())

        assertEquals(
            listOf("INSERT", "UPDATE", "DELETE"),
            auditMCP.history(episode).filter { it.table == "episode_koofr_folders" }.map { it.operation },
        )
        val sessionHistory = auditMCP.history(day).filter { it.table == "session_koofr_folders" }
        assertEquals(listOf("INSERT", "UPDATE", "DELETE"), sessionHistory.map { it.operation })
        assertEquals(ColumnChange("/Snemanja/2026-10-22", "/2026-10-22"), sessionHistory[1].changes["path"])
        assertEquals(ColumnChange("mount-media", "mount-archive"), sessionHistory[1].changes["place"])
    }
}
