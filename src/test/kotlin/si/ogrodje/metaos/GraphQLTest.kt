package si.ogrodje.metaos

import com.jayway.jsonpath.JsonPath
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.RequestPostProcessor
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class)
class GraphQLTest @Autowired constructor(
    private val mvc: MockMvc,
    private val episodes: EpisodesRepository,
    private val people: PeopleRepository,
    private val episodePeople: EpisodePeopleRepository,
    private val sessions: SessionsRepository,
    private val episodeDocuments: EpisodeDocumentsRepository,
    private val sessionDocuments: SessionDocumentsRepository,
) {

    private val reader = jwt().jwt { it.claim("scope", API_READ_SCOPE) }

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

    private fun released(code: String, aired: LocalDate?) = episodes.save(
        Episode(title = "Released $code", code = code).apply {
            status = EpisodeStatus.PUBLISHED
            airedDate = aired
            youtubeUrl = "https://www.youtube.com/watch?v=$code"
        }
    )

    @Test
    fun `lists the latest released episodes to anyone, the most recently aired first, with their people`() {
        // Aired in 2099, so they come before whatever else the tests have published
        released("gq-2", LocalDate.of(2099, 2, 1))
        val latest = released("gq-3", LocalDate.of(2099, 3, 1))
        released("gq-1", LocalDate.of(2099, 1, 1))
        released("gq-0", LocalDate.of(2098, 12, 1))
        episodes.save(Episode(title = "Not released yet", code = "gq-9").apply { airedDate = LocalDate.of(2099, 9, 1) })

        val guest = people.save(Person(name = "Ana Guest", email = null))
        val host = people.save(Person(name = "Zala Host", email = "zala.host@example.com"))
        episodePeople.save(EpisodePerson(episode = latest, person = guest, role = Role.GUEST))
        episodePeople.save(EpisodePerson(episode = latest, person = host, role = Role.PRODUCER))
        episodePeople.save(EpisodePerson(episode = latest, person = host, role = Role.HOST))
        episodeDocuments.save(EpisodeDocument(latest, "doc-of-gq-3"))

        graphql("{ releasedEpisodes { code title airedDate youtubeUrl people { name roles } documents { url } } }")
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andExpect(jsonPath("$.data.releasedEpisodes[*].code", contains("gq-3", "gq-2", "gq-1")))
            .andExpect(jsonPath("$.data.releasedEpisodes[0].airedDate").value("2099-03-01"))
            .andExpect(jsonPath("$.data.releasedEpisodes[0].youtubeUrl").value("https://www.youtube.com/watch?v=gq-3"))
            // Each person once, with all of their roles; hosts first
            .andExpect(jsonPath("$.data.releasedEpisodes[0].people[*].name", contains("Zala Host", "Ana Guest")))
            .andExpect(jsonPath("$.data.releasedEpisodes[0].people[0].roles", contains("HOST", "PRODUCER")))
            .andExpect(jsonPath("$.data.releasedEpisodes[1].people", empty<Any>()))
            // Docs are the team's
            .andExpect(jsonPath("$.data.releasedEpisodes[0].documents", empty<Any>()))

        graphql("{ releasedEpisodes(limit: 4) { code } }")
            .andExpect(jsonPath("$.data.releasedEpisodes[3].code").value("gq-0"))
    }

    @Test
    fun `shows signed-in users the upcoming recordings and what's in production, by recording date`() {
        val session = sessions.save(Session(date = LocalDate.of(2099, 6, 1), location = "GraphQL Conf", address = null))
        fun episode(code: String, status: EpisodeStatus, init: Episode.() -> Unit = {}) =
            episodes.save(Episode(title = "Episode $code", code = code).apply { this.status = status; init() })
        episode("gq-u2", EpisodeStatus.IDEA) { this.session = session }
        episode("gq-u1", EpisodeStatus.SCHEDULED) { recordingDate = LocalDate.of(2099, 5, 1) }
        episode("gq-u9", EpisodeStatus.IDEA)
        episode("gq-p2", EpisodeStatus.EARLY_ACCESS) { recordingDate = LocalDate.of(2099, 4, 2) }
        episode("gq-p1", EpisodeStatus.IN_PRODUCTION) { recordingDate = LocalDate.of(2099, 4, 1); this.session = session }
        episodeDocuments.save(EpisodeDocument(episodes.findByCode("gq-u1")!!, "doc-of-gq-u1"))
        sessionDocuments.save(SessionDocument(session, "plan-of-graphql-conf"))

        val answer = graphql(
            "{ upcomingRecordings { code documents { url } session { location documents { fileId } } } inProduction { code status } }",
            reader,
        )
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andReturn().response.contentAsString

        // Other tests' episodes may be there too. An idea without a session or a date (gq-u9) isn't a recording yet.
        val upcoming = JsonPath.read<List<String>>(answer, "$.data.upcomingRecordings[*].code").filter { it.startsWith("gq-") }
        val inProduction = JsonPath.read<List<String>>(answer, "$.data.inProduction[*].code").filter { it.startsWith("gq-") }
        assertEquals(listOf("gq-u1", "gq-u2"), upcoming)
        assertEquals(listOf("gq-p1", "gq-p2"), inProduction)
        assertEquals(listOf("GraphQL Conf"), JsonPath.read(answer, "$.data.upcomingRecordings[?(@.code == 'gq-u2')].session.location"))
        assertEquals(listOf("EARLY_ACCESS"), JsonPath.read(answer, "$.data.inProduction[?(@.code == 'gq-p2')].status"))
        assertEquals(
            listOf("https://docs.google.com/document/d/doc-of-gq-u1/edit"),
            JsonPath.read(answer, "$.data.upcomingRecordings[?(@.code == 'gq-u1')].documents[*].url"),
        )
        assertEquals(
            listOf("plan-of-graphql-conf"),
            JsonPath.read(answer, "$.data.upcomingRecordings[?(@.code == 'gq-u2')].session.documents[*].fileId"),
        )
    }

    @Test
    fun `keeps what isn't public from anyone without metaos read`() {
        graphql("{ upcomingRecordings { code } }")
            .andExpect(jsonPath("$.errors[0].extensions.classification").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.data").value(null as Any?))
        graphql("{ inProduction { code } }", jwt().jwt { it.claim("scope", MCP_SCOPE) })
            .andExpect(jsonPath("$.errors[0].extensions.classification").value("FORBIDDEN"))
            .andExpect(jsonPath("$.data").value(null as Any?))
    }

    @Test
    fun `turns down a limit out of range`() {
        graphql("{ releasedEpisodes(limit: 500) { code } }")
            .andExpect(jsonPath("$.errors[0].message").value("limit must be between 1 and $MAX_RELEASED_EPISODES"))
            .andExpect(jsonPath("$.errors[0].extensions.classification").value("BAD_REQUEST"))
    }

    @Test
    fun `knows nobody without a token`() {
        graphql("{ me { username } }").andExpect(jsonPath("$.data.me").value(null as Any?))
    }

    @Test
    fun `tells the signed-in user who they are`() {
        people.save(Person(name = "GraphQL Tester", email = "graphql.tester@example.com"))
        val token = jwt().jwt {
            it.claim("preferred_username", "tester")
            it.claim("email", "graphql.tester@example.com")
            it.claim("email_verified", true)
        }

        graphql("{ me { username emailVerified person { name } } }", token)
            .andExpect(jsonPath("$.data.me.username").value("tester"))
            .andExpect(jsonPath("$.data.me.emailVerified").value(true))
            .andExpect(jsonPath("$.data.me.person.name").value("GraphQL Tester"))
    }
}
