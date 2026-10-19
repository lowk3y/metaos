package si.ogrodje.metaos

import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class)
class SecurityTest @Autowired constructor(private val mvc: MockMvc) {

    private fun MockHttpServletRequestDsl.initialize() {
        contentType = MediaType.APPLICATION_JSON
        accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
        content = """
            {"jsonrpc":"2.0","id":1,"method":"initialize",
             "params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
        """.trimIndent()
    }

    @Test
    fun `publishes protected resource metadata pointing at Keycloak`() {
        mvc.get("/.well-known/oauth-protected-resource/mcp").andExpect {
            status { isOk() }
            jsonPath("$.resource") { value("http://localhost/mcp") }
            jsonPath("$.authorization_servers[0]") { value("http://issuer.invalid/realms/metaos") }
            jsonPath("$.scopes_supported[0]") { value(MCP_SCOPE) }
        }
    }

    @Test
    fun `challenges anonymous MCP requests with resource metadata location`() {
        mvc.post("/mcp") { initialize() }.andExpect {
            status { isUnauthorized() }
            header {
                string(
                    HttpHeaders.WWW_AUTHENTICATE,
                    containsString("resource_metadata=http://localhost/.well-known/oauth-protected-resource/mcp")
                )
            }
        }
    }

    @Test
    fun `rejects tokens without the MCP scope`() {
        mvc.post("/mcp") {
            initialize()
            with(jwt().jwt { it.claim("scope", "profile email") })
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `accepts tokens with the MCP scope`() {
        mvc.post("/mcp") {
            initialize()
            with(jwt().jwt { it.claim("scope", "profile $MCP_SCOPE") })
        }.andExpect {
            status { isOk() }
            header { exists("Mcp-Session-Id") }
        }
    }

    @Test
    fun `keeps health endpoint public`() {
        mvc.get("/actuator/health").andExpect { status { isOk() } }
    }

    @Test
    fun `keeps probe endpoints public without details`() {
        listOf("/livez", "/readyz").forEach { path ->
            mvc.get(path).andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
                jsonPath("$.components") { doesNotExist() }
            }
        }
    }

    @Test
    fun `keeps prometheus endpoint public`() {
        mvc.get("/actuator/prometheus").andExpect {
            status { isOk() }
            content { string(containsString("jvm_memory_used_bytes")) }
        }
    }
}
