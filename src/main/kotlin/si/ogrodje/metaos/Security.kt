package si.ogrodje.metaos

import jakarta.servlet.DispatcherType
import org.springaicommunity.mcp.security.server.config.McpServerOAuth2Configurer.mcpServerOAuth2
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.web.SecurityFilterChain

const val MCP_SCOPE = "mcp:tools"

/** For reading what isn't public through /graphql; the front-end's tokens have it (keycloak/frontend_client.sh) */
const val API_READ_SCOPE = "metaos:read"

/** Keycloak access token of the user calling the MCP tools. */
fun currentJwt(): Jwt? = (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)?.token

/** Users are linked to a [Person] by email - only once Keycloak has verified it, so nobody can claim someone else's. */
val Jwt.verifiedEmail: String?
    get() = getClaimAsString("email")?.takeIf { getClaimAsBoolean("email_verified") == true }

/**
 * MCP endpoint is an OAuth2 resource server; Keycloak (see compose.yml) is the authorization server.
 * Unauthenticated clients get a 401 pointing at /.well-known/oauth-protected-resource/mcp,
 * from where they discover Keycloak, register themselves (DCR) and log the user in.
 */
@Configuration
// @PreAuthorize on what /graphql answers only signed-in users (GraphQL.kt)
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") issuerUri: String,
        // Boot's decoder: resolves Keycloak lazily and checks `aud` against ...resourceserver.jwt.audiences
        jwtDecoder: JwtDecoder,
    ): SecurityFilterChain = http
        .authorizeHttpRequests {
            // In k8s actuator is on its own port (MANAGEMENT_SERVER_PORT), which no Ingress routes
            it.requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
            // The k8s probes, on the server port - public through the Ingress, but they only say UP or DOWN
            it.requestMatchers("/livez", "/readyz").permitAll()
            it.requestMatchers("/mcp/**").hasAuthority("SCOPE_$MCP_SCOPE")
            // Anyone may ask for what's public, e.g. the released episodes; the rest needs a token (GraphQL.kt)
            it.requestMatchers("/graphql").permitAll()
            // The front-end (Frontend.kt): its files and pages are public - its data comes from the API, with a token
            it.requestMatchers(HttpMethod.GET, "/assets/**", "/favicon.png", "/silent-check-sso.html", "/config.json")
                .permitAll()
            it.requestMatchers(frontendPage).permitAll()
            // The error page of a request let in, e.g. a missing asset's 404, which would otherwise become a 401
            it.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            it.anyRequest().authenticated()
        }
        // Bearer tokens only - no cookies, so no CSRF and no HTTP sessions
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        // keycloak-js asks Keycloak whether the user is signed in through a hidden iframe of the front-end's
        // silent-check-sso.html, so MetaOS's pages may be framed - by MetaOS itself only
        .headers { headers -> headers.frameOptions { it.sameOrigin() } }
        .with(mcpServerOAuth2()) { mcp ->
            mcp.authorizationServer(issuerUri)
            mcp.jwtDecoder(jwtDecoder)
            mcp.protectedResourceMetadataCustomizer { metadata ->
                metadata.authorizationServer(issuerUri).resourceName("MetaOS").scope(MCP_SCOPE)
            }
        }
        .build()
}
