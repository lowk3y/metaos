package si.ogrodje.metaos

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.OffsetDateTime
import java.util.UUID

data class ColumnChange(val from: Any?, val to: Any?)
data class ChangeDto(
    val at: OffsetDateTime,
    val table: String,
    val rowId: UUID,
    val operation: String,
    val changes: Map<String, ColumnChange>,
    val actor: String?,
    val actorId: String?,
    val client: String?,
    val tool: String?,
)

// Columns that change with every row and say nothing about what happened to it
private val BOOKKEEPING_COLUMNS = setOf("id", "created_at", "updated_at")

private fun diff(old: Map<String, Any?>, new: Map<String, Any?>): Map<String, ColumnChange> =
    (old.keys + new.keys).filter { it !in BOOKKEEPING_COLUMNS && old[it] != new[it] }
        .associateWith { ColumnChange(old[it], new[it]) }

@Component
class AuditMCP(
    private val jdbcTemplate: JdbcTemplate,
    private val jsonMapper: JsonMapper,
) {

    private val toChange = RowMapper { rs, _ ->
        fun row(column: String) = rs.getString(column)?.let { jsonMapper.readValue<Map<String, Any?>>(it) }.orEmpty()
        ChangeDto(
            at = rs.getObject("changed_at", OffsetDateTime::class.java),
            table = rs.getString("table_name"),
            rowId = rs.getObject("row_id", UUID::class.java),
            operation = rs.getString("operation"),
            changes = diff(row("old_row"), row("new_row")),
            actor = rs.getString("actor"),
            actorId = rs.getString("actor_id"),
            client = rs.getString("client"),
            tool = rs.getString("tool"),
        )
    }

    @McpTool(
        name = "get_history",
        description = "List every change to an episode, person, session or show, oldest first: what changed, when, " +
            "by whom, through which MCP client and tool. Includes people's roles on an episode and episodes " +
            "moving in and out of a session or show. Soft deletes show up as updates that set deleted_at"
    )
    fun history(@McpToolParam(description = "ID of the episode, person, session or show") id: UUID): List<ChangeDto> =
        jdbcTemplate.query(
            """
            select * from audit_log
            where row_id = ?
               or (table_name = 'episode_people'
                   and ? in (old_row ->> 'episode_id', new_row ->> 'episode_id', old_row ->> 'person_id', new_row ->> 'person_id'))
               or (table_name = 'episodes'
                   and ((old_row ->> 'session_id' is distinct from new_row ->> 'session_id'
                         and ? in (old_row ->> 'session_id', new_row ->> 'session_id'))
                     or (old_row ->> 'show_id' is distinct from new_row ->> 'show_id'
                         and ? in (old_row ->> 'show_id', new_row ->> 'show_id'))))
            order by id
            """.trimIndent(),
            toChange, id, id.toString(), id.toString(), id.toString(),
        )

    @McpTool(name = "list_recent_changes", description = "List the latest changes to all data, newest first")
    fun recentChanges(
        @McpToolParam(description = "How many changes to list, 20 by default", required = false) limit: Int?,
    ): List<ChangeDto> =
        jdbcTemplate.query("select * from audit_log order by id desc limit ?", toChange, (limit ?: 20).coerceIn(1, 200))
}
