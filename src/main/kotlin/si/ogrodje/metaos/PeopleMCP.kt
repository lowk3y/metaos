package si.ogrodje.metaos

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class PersonDto(val id: UUID?, val name: String, val email: String?)

private fun Person.toDto() = PersonDto(id, name, email)

@Component
class PeopleMCP(private val people: PeopleRepository) {

    @McpTool(name = "list_people", description = "List all people")
    fun list(): List<PersonDto> = people.findAll().map { it.toDto() }

    @Transactional
    @McpTool(name = "create_person", description = "Create a new person")
    fun create(
        @McpToolParam(description = "Full name") name: String,
        @McpToolParam(description = "Unique email address (case-insensitive)", required = false) email: String?,
    ): PersonDto {
        require(email == null || people.findByEmail(email) == null) { "Person with email $email already exists" }
        return people.save(Person(name = name, email = email)).toDto()
    }
}
