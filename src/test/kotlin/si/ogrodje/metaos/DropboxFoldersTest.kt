package si.ogrodje.metaos

import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.everyItem
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(properties = ["metaos.dropbox.app-key=key", "metaos.dropbox.app-secret=secret", "metaos.dropbox.refresh-token=test"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class, DropboxFoldersTest.Config::class)
class DropboxFoldersTest @Autowired constructor(
    private val dropbox: FakeDropbox,
    private val episodesRepository: EpisodesRepository,
    private val sessionsRepository: SessionsRepository,
    private val dropboxMCP: DropboxMCP,
    private val foldersMCP: DropboxFoldersMCP,
    private val auditMCP: AuditMCP,
    private val history: History,
    private val pictures: DropboxPictures,
    private val mvc: MockMvc,
) {

    @TestConfiguration
    class Config {
        @Bean
        @Primary
        fun fakeDropbox() = FakeDropbox().apply {
            place("1001", "Artwork")
            place("1002", "Brand")
            // Pictures' own, as DropboxFolderIndex keeps the folders it saw in a place for a minute
            place("1003", "Pictures")
        }
    }

    @Test
    fun `shows the pictures in an episode's folders, then those in its folders, and thumbnails only of those to signed-in users`() {
        episodesRepository.saveAndFlush(Episode(title = "Pictures", code = "S09E44"))
        dropbox.put("1003", "/S09E44", null)
        dropbox.put("1003", "/S09E44/brief.md", "# Naslovnica".toByteArray())
        dropbox.put("1003", "/S09E44/podcast.png", byteArrayOf(1, 2, 3))
        dropbox.put("1003", "/S09E44/B variants", null)
        listOf("3.jpg", "1.jpg", "2.webp", "4.jpg").forEach { dropbox.put("1003", "/S09E44/B variants/$it", byteArrayOf(4)) }
        dropbox.put("1003", "/S09E44/B variants/deeper", null)
        dropbox.put("1003", "/S09E44/B variants/deeper/5.jpg", byteArrayOf(5))
        dropbox.put("1003", "/S09E44/A empty", null)
        dropbox.put("1003", "/elsewhere.png", byteArrayOf(6))
        val link = foldersMCP.linkEpisode("S09E44", "Pictures", "/S09E44").url

        // The folder's own first, then its folders' by name; no further than needed
        assertEquals(listOf("podcast.png"), pictures.of("1003", "/S09E44", limit = 1).map { it.name })
        val reader = jwt().jwt { it.claim("scope", API_READ_SCOPE) }
        val found = "$.data.episode.dropboxFolders[0].pictures"
        graphql("""{ episode(ref: "S09E44") { dropboxFolders { pictures(limit: 4) { name path thumbnail url } } } }""", reader)
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andExpect(jsonPath("$found[*].name", contains("podcast.png", "1.jpg", "2.webp", "3.jpg")))
            .andExpect(jsonPath("$found[1].path").value("/S09E44/B variants/1.jpg"))
            .andExpect(jsonPath("$found[1].thumbnail").value("/dropbox/thumbnail?place=1003&path=%2FS09E44%2FB%20variants%2F1.jpg"))
            .andExpect(jsonPath("$found[*].url", everyItem(equalTo(link))))
        // All of them without a limit
        graphql("""{ episode(ref: "S09E44") { dropboxFolders { pictures { name } } } }""", reader)
            .andExpect(jsonPath("$found[*].name", contains("podcast.png", "1.jpg", "2.webp", "3.jpg", "4.jpg")))
        graphql("""{ episode(ref: "S09E44") { dropboxFolders { pictures(limit: 0) { name } } } }""", reader)
            .andExpect(jsonPath("$.errors[0].message").value("limit must be at least 1"))

        fun thumbnail(path: String, place: String = "1003", auth: RequestPostProcessor? = reader) =
            mvc.get("/dropbox/thumbnail") { param("place", place); param("path", path); auth?.let { with(it) } }

        thumbnail("/S09E44/podcast.png", auth = null).andExpect { status { isUnauthorized() } }
        thumbnail("/S09E44/podcast.png", auth = jwt().jwt { it.claim("scope", MCP_SCOPE) }).andExpect { status { isForbidden() } }
        thumbnail("/S09E44/podcast.png").andExpect {
            status { isOk() }
            content { contentType("image/jpeg"); bytes(byteArrayOf(1, 2, 3)) }
            header { string(HttpHeaders.CACHE_CONTROL, "max-age=86400, private") }
        }
        thumbnail("/S09E44/B variants/4.jpg").andExpect { status { isOk() } }
        // Not a picture, deeper than a folder in it, not in a linked folder, a way out of it, gone, in another place
        // (not Artwork, whose folders the other tests need DropboxFolderIndex to see fresh) or in none shared with the bot
        listOf(
            "1003" to "/S09E44/brief.md", "1003" to "/S09E44/B variants/deeper/5.jpg", "1003" to "/elsewhere.png",
            "1003" to "/S09E44/../elsewhere.png", "1003" to "/S09E44/gone.png", "1002" to "/S09E44/podcast.png",
            "1004" to "/S09E44/podcast.png",
        ).forEach { (place, path) -> thumbnail(path, place).andExpect { status { isNotFound() } } }
    }

    @Test
    fun `links Dropbox folders to episodes and sessions by ID, so they stay linked wherever they're moved in their place`() {
        val recordingDay = sessionsRepository.saveAndFlush(Session(LocalDate.of(2026, 10, 22), location = "Four Points", address = null))
        val day = recordingDay.id!!
        val episode = episodesRepository.saveAndFlush(Episode(title = "Artwork", code = "S09E41").also { it.session = recordingDay }).id!!
        dropbox.put("1001", "/Season 9", null)
        dropbox.put("1001", "/Season 9/41 guest", null)
        dropbox.put("1001", "/Season 9/41 guest/thumb.png", ByteArray(4))
        dropbox.put("1001", "/Snemanja", null)

        foldersMCP.linkSession(day, "Artwork", "/Snemanja")
        val linked = foldersMCP.linkEpisode("s09e41", "1001", "Season 9/41 guest/")
        assertEquals(membersLink("/Season 9/41 guest"), linked.url)
        foldersMCP.linkEpisode("S09E41", "Artwork", "/Season 9/41 guest") // already linked
        assertFailsWith<IllegalArgumentException> { foldersMCP.linkEpisode("S09E41", "Artwork", "/Season 9/41 guest/thumb.png") }
        assertFailsWith<IllegalArgumentException> { foldersMCP.linkEpisode("S09E41", "Artwork", "/Nowhere") }
        assertFailsWith<IllegalArgumentException> { foldersMCP.linkEpisode("S09E41", "Artwork", "/") }
        assertFailsWith<IllegalArgumentException> { foldersMCP.linkEpisode("S09E41", "1003", "/Season 9") }

        fun folders() = foldersMCP.listEpisodeFolders("S09E41").map { Triple(it.path, it.linkedTo, it.missing) }
        assertEquals(listOf(Triple("/Season 9/41 guest", LinkedTo.EPISODE, false), Triple("/Snemanja", LinkedTo.SESSION, false)), folders())

        // Renamed through MetaOS, and moved in the Dropbox app
        dropboxMCP.move("Artwork", "/Season 9/41 guest", "/Season 9/S09E41 - guest", null)
        dropbox.move("1001", "/Snemanja", "1001", "/Season 9/Snemanja")
        assertEquals(
            listOf(Triple("/Season 9/S09E41 - guest", LinkedTo.EPISODE, false), Triple("/Season 9/Snemanja", LinkedTo.SESSION, false)),
            folders(),
        )

        // Out of its shared folder: missing, where it was linked
        dropbox.move("1001", "/Season 9/Snemanja", "1002", "/Snemanja")
        assertEquals(
            listOf(Triple("/Season 9/S09E41 - guest", LinkedTo.EPISODE, false), Triple("/Snemanja", LinkedTo.SESSION, true)),
            folders(),
        )

        // By where it is now, or for a missing one, where it was linked
        foldersMCP.unlinkEpisode("S09E41", "Artwork", "/Season 9/S09E41 - guest")
        assertEquals(listOf(LinkedTo.SESSION), foldersMCP.listEpisodeFolders("S09E41").map { it.linkedTo })
        assertFailsWith<IllegalArgumentException> { foldersMCP.unlinkEpisode("S09E41", "Artwork", "/Season 9/S09E41 - guest") }
        foldersMCP.unlinkSession(day, "Artwork", "/Snemanja")
        assertTrue(foldersMCP.listSessionFolders(day).isEmpty())

        assertEquals(listOf("INSERT", "DELETE"), auditMCP.history(episode).filter { it.table == "episode_dropbox_folders" }.map { it.operation })
        assertEquals(listOf("INSERT", "DELETE"), auditMCP.history(day).filter { it.table == "session_dropbox_folders" }.map { it.operation })
        assertEquals(
            listOf("Linked the Dropbox folder /Season 9/41 guest", "Unlinked the Dropbox folder /Season 9/41 guest"),
            history.of(episode).map { it.summary }.filter { "Dropbox" in it },
        )
    }

    @Test
    fun `lists episodes and their sessions with their Dropbox folders where they are now, only to signed-in users`() {
        val recordingDay = sessionsRepository.saveAndFlush(Session(LocalDate.of(2026, 10, 23), location = "Dropbox", address = null))
        val episode = Episode(title = "Released", code = "S09E42").apply {
            status = EpisodeStatus.PUBLISHED
            airedDate = LocalDate.of(2026, 10, 24)
            session = recordingDay
        }
        episodesRepository.saveAndFlush(episode)
        dropbox.put("1001", "/Season 9", null)
        dropbox.put("1001", "/Season 9/42 guest", null)
        dropbox.put("1001", "/Season 9/42 extra", null)
        dropbox.put("1001", "/Snemanja 42", null)
        val guest = membersLink("/Season 9/42 guest")
        foldersMCP.linkEpisode("S09E42", "Artwork", "/Season 9/42 guest")
        withoutMembersLinks { foldersMCP.linkEpisode("S09E42", "Artwork", "/Season 9/42 extra") }
        foldersMCP.linkSession(recordingDay.id!!, "Artwork", "/Snemanja 42")

        // Renamed in the Dropbox app, and moved out of its shared folder
        dropbox.move("1001", "/Season 9/42 guest", "1001", "/Season 9/S09E42 - guest")
        dropbox.move("1001", "/Season 9/42 extra", "1002", "/42 extra")

        val query = "{ episodes { code dropboxFolders { placeName path missing url } session { dropboxFolders { path } } } }"
        val reader = jwt().jwt { it.claim("scope", API_READ_SCOPE) }
        val folders = "$.data.episodes[?(@.code == 'S09E42')].dropboxFolders[*]"
        graphql(query, reader)
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andExpect(jsonPath("$folders.path", contains("/Season 9/42 extra", "/Season 9/S09E42 - guest")))
            .andExpect(jsonPath("$folders.missing", contains(true, false)))
            .andExpect(jsonPath("$folders.placeName", contains("Artwork", "Artwork")))
            // Its link for members, or else its path in the web app
            .andExpect(jsonPath("$folders.url", contains("https://www.dropbox.com/home/Artwork/Season%209/42%20extra", guest)))
            .andExpect(jsonPath("$.data.episodes[?(@.code == 'S09E42')].session.dropboxFolders[*].path", contains("/Snemanja 42")))

        graphql(query)
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andExpect(jsonPath(folders, empty<Any>()))
            .andExpect(jsonPath("$.data.episodes[?(@.code == 'S09E42')].session.dropboxFolders[*]", empty<Any>()))
    }

    @Test
    fun `links a folder without a link while Dropbox won't make one for members only, and makes it when it's linked again`() {
        val episode = episodesRepository.saveAndFlush(Episode(title = "Links", code = "S09E43")).id!!
        dropbox.put("1001", "/Season 9", null)
        dropbox.put("1001", "/Season 9/43", null)

        val without = withoutMembersLinks { foldersMCP.linkEpisode("S09E43", "Artwork", "/Season 9/43") }
        assertNull(without.url)
        assertEquals("Dropbox won't make the bot a link for members only", without.urlError)
        assertNull(foldersMCP.listEpisodeFolders("S09E43").single().url)

        assertEquals(membersLink("/Season 9/43"), foldersMCP.linkEpisode("S09E43", "Artwork", "/Season 9/43").url)
        assertEquals(membersLink("/Season 9/43"), foldersMCP.listEpisodeFolders("S09E43").single().url)
        assertEquals(
            listOf("Linked the Dropbox folder /Season 9/43", "Made a link to the Dropbox folder /Season 9/43"),
            history.of(episode).map { it.summary }.filter { "Dropbox" in it },
        )
    }

    private fun membersLink(path: String) = dropbox.membersLink(dropbox.file("1001", path)!!.id!!)

    private fun <T> withoutMembersLinks(block: () -> T): T {
        dropbox.membersLinks = false
        try {
            return block()
        } finally {
            dropbox.membersLinks = true
        }
    }

    private fun graphql(query: String, auth: RequestPostProcessor? = null): ResultActions {
        val request = post("/graphql").contentType(MediaType.APPLICATION_JSON)
            .content(JsonMapper.builder().build().writeValueAsString(mapOf("query" to query)))
        auth?.let(request::with)
        val actions = mvc.perform(request)
        // Answered right away, unless a resolver is asynchronous
        val result = actions.andReturn()
        return (if (result.request.isAsyncStarted) mvc.perform(asyncDispatch(result)) else actions)
            .andExpect(status().isOk)
    }
}
