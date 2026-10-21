package si.ogrodje.metaos

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.security.web.util.matcher.AndRequestMatcher
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver
import java.time.Duration

/*
 * The front-end (fe/). bootJar builds it into the jar's static/, from where MetaOS serves it; in development Vite's dev
 * server serves it instead, and proxies the API to MetaOS. Files in fe/public need a rule in SecurityConfig.
 */

/** A browser loading a page - answered with index.html, as Vue Router keeps the page in the path (createWebHistory) */
val frontendPage: RequestMatcher = AndRequestMatcher(
    RequestMatcher { it.method == "GET" },
    // Browsers ask for text/html when they load a page; */* is what everything else accepts
    MediaTypeRequestMatcher(MediaType.TEXT_HTML).apply { setIgnoredMediaTypes(setOf(MediaType.ALL)) },
)

/**
 * Serves the front-end's files, and index.html for every other path. Resource handlers come after the controllers and
 * the MCP endpoint, so those keep their paths. A path whose last segment has a dot is a file, so a missing one stays a
 * 404 rather than becoming a page.
 */
@Configuration
class FrontendConfig : WebMvcConfigurer {

    // The resource handler takes "/" for an empty path, which it doesn't serve
    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addViewController("/").setViewName("forward:/index.html")
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        // Vite names these after a hash of their content, so a name never gets other content
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/static/assets/")
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
        // index.html names the current assets, so browsers and Cloudflare check it again on every load
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/")
            .setCacheControl(CacheControl.noCache())
            // Uncached, as the cache would keep an entry for every path anyone asks for
            .resourceChain(false)
            .addResolver(PageResolver())
    }

    private class PageResolver : PathResourceResolver() {
        override fun getResource(resourcePath: String, location: Resource): Resource? =
            super.getResource(resourcePath, location) ?: when {
                '.' in resourcePath.substringAfterLast('/') -> null
                resourcePath.substringBefore('/') in SERVER_PATHS -> null
                else -> super.getResource("index.html", location)
            }
    }

    private companion object {
        // MetaOS's own paths are never pages, e.g. /actuator/prometheus in k8s, where actuator is on another port
        val SERVER_PATHS = setOf("actuator", "mcp", "graphql", "events", "koofr", "dropbox", ".well-known")
    }
}

/**
 * What the front-end needs at runtime, so one build works everywhere: where users sign in and as which client, and
 * where MCP clients connect (the Usage page)
 */
data class FrontendConfigDto(val issuer: String, val clientId: String, val mcpUrl: String)

@RestController
class FrontendController(
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") private val issuer: String,
    @Value("\${metaos.frontend.client-id}") private val clientId: String,
    @Value("\${metaos.frontend.mcp-url}") private val mcpUrl: String,
) {

    @GetMapping("/config.json")
    fun config() = FrontendConfigDto(issuer, clientId, mcpUrl)
}
