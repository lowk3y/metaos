package si.ogrodje.metaos

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfig::class)
class EpisodesTest @Autowired constructor(
    private val episodesRepository: EpisodesRepository,
    private val episodesMCP: EpisodesMCP,
    private val peopleMCP: PeopleMCP,
    private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `restores a soft-deleted episode with its people and links`() {
        episodesRepository.saveAndFlush(Episode(title = "Comeback", code = "ep-1501"))
        val host = peopleMCP.create(name = "Comeback Host", email = null)
        episodesMCP.assignPerson(code = "ep-1501", personId = host.id!!, role = Role.HOST)
        episodesMCP.setLink("ep-1501", Platform.YOUTUBE, "https://youtu.be/abc")

        episodesMCP.delete("ep-1501")
        assertNull(episodesMCP.get("ep-1501"))

        val restored = episodesMCP.restore("EP-1501")

        assertEquals("ep-1501" to "https://youtu.be/abc", restored.code to restored.youtubeUrl)
        assertEquals(listOf("Comeback Host" to listOf(Role.HOST)), episodesMCP.listPeople("ep-1501").map { it.name to it.roles })
        assertFailsWith<IllegalArgumentException> { episodesMCP.restore("ep-1501") }
        assertFailsWith<IllegalArgumentException> { episodesMCP.restore("ep-1599") }
    }

    @Test
    fun `does not restore over a live episode with the same code`() {
        episodesRepository.saveAndFlush(Episode(title = "Old", code = "ep-1502"))
        episodesMCP.delete("ep-1502")
        episodesRepository.saveAndFlush(Episode(title = "New", code = "ep-1502"))

        assertFailsWith<IllegalArgumentException> { episodesMCP.restore("ep-1502") }
        assertEquals("New", episodesMCP.get("ep-1502")?.title)
    }

    @Test
    fun `can save and find episode by code`() {
        val episode = Episode(title = "Test Episode", code = "ep-101")
        val saved = episodesRepository.saveAndFlush(episode)
        println("Saved episode ID: ${saved.id}, code: ${saved.code}")

        val foundById = episodesRepository.findById(saved.id!!)
        println("Found by ID: $foundById")

        val found = episodesRepository.findByCode("ep-101")
        println("Found by lowercase code: $found")

        assertNotNull(found)
        assertEquals("Test Episode", found.title)
    }

    @Test
    fun `rejects duplicate code regardless of case`() {
        episodesRepository.saveAndFlush(Episode(title = "Original", code = "ep-201"))

        assertFailsWith<DataIntegrityViolationException> {
            episodesRepository.saveAndFlush(Episode(title = "Duplicate", code = "EP-201"))
        }
    }

    @Test
    fun `soft deletes episode and frees its code`() {
        val saved = episodesRepository.saveAndFlush(Episode(title = "To delete", code = "ep-301"))

        episodesRepository.delete(saved)

        assertNull(episodesRepository.findByCode("ep-301"))
        assertTrue(episodesRepository.findAll().none { it.id == saved.id })
        assertEquals(
            true,
            jdbcTemplate.queryForObject<Boolean>(
                "select deleted_at is not null from episodes where id = ?", saved.id
            )
        )

        episodesRepository.saveAndFlush(Episode(title = "Recreated", code = "ep-301"))
        assertEquals("Recreated", episodesRepository.findByCode("ep-301")?.title)
    }

    @Test
    fun `updates an episode's code and title, leaving omitted fields as they are`() {
        episodesRepository.saveAndFlush(Episode(title = "Old title", code = "ep-1203"))
        episodesRepository.saveAndFlush(Episode(title = "Taken", code = "ep-1205"))

        assertEquals("New title", episodesMCP.update("EP-1203", newCode = null, title = "  New title ").title)
        val moved = episodesMCP.update("ep-1203", newCode = "ep-1204", title = null)

        assertEquals("ep-1204" to "New title", moved.code to moved.title)
        assertNull(episodesMCP.get("ep-1203"))
        assertFailsWith<IllegalArgumentException> { episodesMCP.update("ep-1204", newCode = "EP-1205", title = null) }
        assertFailsWith<IllegalArgumentException> { episodesMCP.update("ep-1204", newCode = " ", title = null) }
        assertFailsWith<IllegalArgumentException> { episodesMCP.update("ep-1204", newCode = null, title = " ") }
        assertFailsWith<IllegalArgumentException> { episodesMCP.update("nope", newCode = null, title = "Title") }
    }

    @Test
    fun `sets and clears episode links`() {
        episodesRepository.saveAndFlush(Episode(title = "Linked", code = "ep-1201"))

        episodesMCP.setLink("ep-1201", Platform.YOUTUBE, "https://youtu.be/abc")
        episodesMCP.setLink("EP-1201", Platform.APPLE_PODCASTS, "https://podcasts.apple.com/si/podcast/x/id1?i=2")
        episodesMCP.setLink("ep-1201", Platform.PATREON, "https://www.patreon.com/posts/x-1")
        episodesMCP.setLink("ep-1201", Platform.YOUTUBE, null)

        val episode = assertNotNull(episodesMCP.get("ep-1201"))
        assertEquals(
            listOf(null, null, "https://podcasts.apple.com/si/podcast/x/id1?i=2", "https://www.patreon.com/posts/x-1"),
            listOf(episode.youtubeUrl, episode.spotifyUrl, episode.applePodcastsUrl, episode.patreonUrl),
        )
    }

    @Test
    fun `sets and clears summaries, which only get_episode returns`() {
        episodesRepository.saveAndFlush(Episode(title = "Summarized", code = "ep-1701"))

        val updated = episodesMCP.update("ep-1701", newCode = null, title = null, summary = " **Bold** intro ", machineSummary = "AI text")
        assertEquals(Triple("Summarized", "**Bold** intro", "AI text"), Triple(updated.title, updated.summary, updated.machineSummary))

        episodesMCP.update("ep-1701", newCode = null, title = null, summary = "", machineSummary = null)
        val episode = assertNotNull(episodesMCP.get("ep-1701"))
        assertEquals(null to "AI text", episode.summary to episode.machineSummary)
    }

    @Test
    fun `rejects links that are not https URLs of the platform`() {
        episodesRepository.saveAndFlush(Episode(title = "Wrong", code = "ep-1202"))

        assertFailsWith<IllegalArgumentException> { episodesMCP.setLink("ep-1202", Platform.SPOTIFY, "https://youtu.be/abc") }
        assertFailsWith<IllegalArgumentException> { episodesMCP.setLink("ep-1202", Platform.YOUTUBE, "http://youtu.be/abc") }
        assertFailsWith<IllegalArgumentException> { episodesMCP.setLink("ep-1202", Platform.YOUTUBE, "not a url") }
        assertFailsWith<IllegalArgumentException> { episodesMCP.setLink("ep-1202", Platform.PATREON, "https://patreon.example.com/x") }
    }

    @Test
    fun `lists the latest season and episode first`() {
        listOf("S91E02", "S92E01", "S91E10").forEach { episodesMCP.create(code = it, title = it) }

        assertEquals(
            listOf("S92E01", "S91E10", "S91E02"),
            episodesMCP.list().mapNotNull { it.code }.filter { it.startsWith("S9") },
        )
    }
}