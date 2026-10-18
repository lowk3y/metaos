package si.ogrodje.metaos

import org.springframework.ai.mcp.annotation.McpResource
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class EpisodeDto(val id: UUID?, val code: String, val title: String)
data class EpisodePersonDto(val personId: UUID?, val name: String, val email: String?, val role: Role)

private fun Episode.toDto() = EpisodeDto(id, code, title)
private fun EpisodePerson.toDto() = EpisodePersonDto(person.id, person.name, person.email, role)

@Component
class EpisodesMCP(
    private val episodes: EpisodesRepository,
    private val people: PeopleRepository,
    private val episodePeople: EpisodePeopleRepository,
) {

    @McpTool(name = "list_episodes", description = "List all episodes")
    fun list(): List<EpisodeDto> = episodes.findAll().map { it.toDto() }

    @McpTool(name = "get_episode", description = "Find an episode by its code (case-insensitive)")
    fun get(@McpToolParam(description = "Episode code, e.g. S01E03") code: String): EpisodeDto? =
        episodes.findByCode(code)?.toDto()

    @Transactional
    @McpTool(name = "create_episode", description = "Create a new episode")
    fun create(
        @McpToolParam(description = "Unique episode code") code: String,
        @McpToolParam(description = "Episode title") title: String,
    ): EpisodeDto = episodes.save(Episode(title = title, code = code)).toDto()

    @Transactional
    @McpTool(name = "delete_episode", description = "Soft-delete an episode by its code (case-insensitive)")
    fun delete(@McpToolParam(description = "Episode code, e.g. S01E03") code: String): EpisodeDto? =
        episodes.findByCode(code)?.also { episodes.delete(it) }?.toDto()

    @Transactional(readOnly = true)
    @McpTool(name = "list_episode_people", description = "List the people on an episode and their roles")
    fun listPeople(@McpToolParam(description = "Episode code, e.g. S01E03") code: String): List<EpisodePersonDto> {
        val episode = requireNotNull(episodes.findByCode(code)) { "Episode $code not found" }
        return episodePeople.findByEpisodeWithPerson(episode).map { it.toDto() }
    }

    @Transactional
    @McpTool(
        name = "assign_person_to_episode",
        description = "Give a person a role on an episode, replacing the role they already have on it"
    )
    fun assignPerson(
        @McpToolParam(description = "Episode code, e.g. S01E03") code: String,
        @McpToolParam(description = "Person ID") personId: UUID,
        @McpToolParam(description = "Role on the episode") role: Role,
    ): EpisodePersonDto {
        val episode = requireNotNull(episodes.findByCode(code)) { "Episode $code not found" }
        val person = requireNotNull(people.findByIdOrNull(personId)) { "Person $personId not found" }
        val assignment = episodePeople.findByEpisodeAndPerson(episode, person)?.apply { this.role = role }
            ?: episodePeople.save(EpisodePerson(episode = episode, person = person, role = role))
        return assignment.toDto()
    }

    @McpResource(uri = "episode://{code}", name = "Episode", description = "Episode by code")
    fun resource(code: String): String =
        episodes.findByCode(code)?.let { "${it.code}: ${it.title}" } ?: "Episode $code not found"
}
