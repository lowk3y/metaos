package si.ogrodje.metaos

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.transaction.autoconfigure.TransactionManagerCustomizers
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import java.util.concurrent.ConcurrentHashMap

/**
 * Tags every read-write transaction with who is making the change, for the audit_log trigger (V6__audit_log.sql):
 * the signed-in user, the OAuth client they came through and the MCP tool they called.
 */
class AuditingTransactionManager : JpaTransactionManager() {

    private val toolNames = ConcurrentHashMap<String, String>()

    override fun doBegin(transaction: Any, definition: TransactionDefinition) {
        super.doBegin(transaction, definition)
        if (definition.isReadOnly) return

        val jwt = currentJwt()
        // JdbcTemplate joins the transaction's connection; is_local = true scopes the settings to this transaction
        JdbcTemplate(requireNotNull(dataSource)).queryForList(
            """
            select set_config('metaos.actor_id', ?, true), set_config('metaos.actor', ?, true),
                   set_config('metaos.client', ?, true), set_config('metaos.tool', ?, true)
            """.trimIndent(),
            jwt?.subject.orEmpty(),
            jwt?.getClaimAsString("preferred_username").orEmpty(),
            jwt?.getClaimAsString("azp").orEmpty(),
            definition.name?.let(::toolName).orEmpty(),
        )
    }

    /** @Transactional names a transaction after the method that started it, e.g. si.ogrodje.metaos.SessionsMCP.update */
    private fun toolName(transactionName: String): String = toolNames.computeIfAbsent(transactionName) {
        val methodName = transactionName.substringAfterLast('.')
        runCatching { Class.forName(transactionName.substringBeforeLast('.')) }.getOrNull()?.methods
            ?.firstNotNullOfOrNull { method -> method.getAnnotation(McpTool::class.java)?.takeIf { method.name == methodName } }
            ?.name.orEmpty()
    }
}

@Configuration
class AuditConfig {

    // Takes the place of Boot's JpaTransactionManager
    @Bean
    fun transactionManager(customizers: ObjectProvider<TransactionManagerCustomizers>): PlatformTransactionManager =
        AuditingTransactionManager().also { manager -> customizers.ifAvailable { it.customize(manager) } }
}
