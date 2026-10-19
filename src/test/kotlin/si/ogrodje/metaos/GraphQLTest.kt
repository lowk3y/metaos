package si.ogrodje.metaos

import org.hamcrest.Matchers.contains
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class)
class GraphQLTest @Autowired constructor(
    private val mvc: MockMvc,
    private val episodes: EpisodesRepository,
    private val people: PeopleRepository,
) {

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
    fun `lists the latest released episodes to anyone, the most recently aired first`() {
        // Aired in 2099, so they come before whatever else the tests have published
        released("gq-2", LocalDate.of(2099, 2, 1))
        released("gq-3", LocalDate.of(2099, 3, 1))
        released("gq-1", LocalDate.of(2099, 1, 1))
        released("gq-0", LocalDate.of(2098, 12, 1))
        episodes.save(Episode(title = "Not released yet", code = "gq-9").apply { airedDate = LocalDate.of(2099, 9, 1) })

        graphql("{ releasedEpisodes { code title airedDate youtubeUrl } }")
            .andExpect(jsonPath("$.errors").doesNotExist())
            .andExpect(jsonPath("$.data.releasedEpisodes[*].code", contains("gq-3", "gq-2", "gq-1")))
            .andExpect(jsonPath("$.data.releasedEpisodes[0].airedDate").value("2099-03-01"))
            .andExpect(jsonPath("$.data.releasedEpisodes[0].youtubeUrl").value("https://www.youtube.com/watch?v=gq-3"))

        graphql("{ releasedEpisodes(limit: 4) { code } }")
            .andExpect(jsonPath("$.data.releasedEpisodes[3].code").value("gq-0"))
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
