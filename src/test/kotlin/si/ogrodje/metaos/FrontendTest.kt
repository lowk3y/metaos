package si.ogrodje.metaos

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

// src/test/resources/static stands in for the build of fe/ that bootJar puts into the jar
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class)
class FrontendTest @Autowired constructor(private val mvc: MockMvc) {

    @Test
    fun `answers the front-end's pages with index html, without a token`() {
        listOf("/episodes", "/episodes/S05E08").forEach { path ->
            mvc.get(path) { accept(MediaType.TEXT_HTML) }.andExpect {
                status { isOk() }
                content { string(containsString("<div id=\"app\">")) }
                header { string(HttpHeaders.CACHE_CONTROL, "no-cache") }
            }
        }
    }

    @Test
    fun `forwards the root to index html`() {
        mvc.get("/") { accept(MediaType.TEXT_HTML) }.andExpect {
            status { isOk() }
            forwardedUrl("/index.html")
        }
    }

    @Test
    fun `lets browsers keep assets for a year`() {
        mvc.get("/assets/index-Test1234.js").andExpect {
            status { isOk() }
            header { string(HttpHeaders.CACHE_CONTROL, containsString("max-age=31536000")) }
            header { string(HttpHeaders.CACHE_CONTROL, containsString("immutable")) }
        }
    }

    @Test
    fun `keeps missing files a 404`() {
        mvc.get("/assets/index-Missing.js").andExpect { status { isNotFound() } }
        mvc.get("/robots.txt") { accept(MediaType.TEXT_HTML) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `wants a token for anything that isn't a page`() {
        mvc.get("/episodes/S05E08") { accept(MediaType.APPLICATION_JSON) }.andExpect { status { isUnauthorized() } }
        mvc.get("/mcp") { accept(MediaType.TEXT_HTML) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `tells the front-end where users sign in`() {
        mvc.get("/config.json").andExpect {
            status { isOk() }
            jsonPath("$.issuer") { value("http://issuer.invalid/realms/metaos") }
            jsonPath("$.clientId") { value("metaos-ui") }
        }
    }
}
