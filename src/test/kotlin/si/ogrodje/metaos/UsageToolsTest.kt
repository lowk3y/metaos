package si.ogrodje.metaos

import org.junit.jupiter.api.Test
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.type.classreading.CachingMetadataReaderFactory
import java.io.File
import kotlin.test.assertEquals

/** The front-end's Usage page lists MetaOS's MCP tools by hand (fe/src/tools.ts); this keeps the list complete */
class UsageToolsTest {

    // Read from the class files: a component scan would skip the integrations that are off without their credentials
    private val tools = CachingMetadataReaderFactory().let { readers ->
        PathMatchingResourcePatternResolver().getResources("classpath*:si/ogrodje/metaos/**/*.class")
            .flatMap { readers.getMetadataReader(it).annotationMetadata.getAnnotatedMethods(McpTool::class.java.name) }
            .map { it.getAnnotationAttributes(McpTool::class.java.name)!!["name"] as String }
    }

    @Test
    fun `the Usage page lists every MCP tool once, and only those`() {
        val listed = Regex("""name: '(\w+)'""").findAll(File("fe/src/tools.ts").readText()).map { it.groupValues[1] }.toList()
        assertEquals(listed.distinct(), listed, "Tools listed twice in fe/src/tools.ts")
        assertEquals(tools.sorted(), listed.sorted(), "The MCP tools, and those in fe/src/tools.ts")
    }
}
