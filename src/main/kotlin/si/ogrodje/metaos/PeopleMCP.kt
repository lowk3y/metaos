package si.ogrodje.metaos

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class PersonDto(val id: UUID?, val name: String, val email: String?)
data class PersonEpisodeDto(val episodeId: UUID?, val code: String?, val title: String, val roles: List<Role>)
data class CurrentUserDto(val username: String?, val email: String?, val emailVerified: Boolean, val person: PersonDto?)

private fun Person.toDto() = PersonDto(id, name, email)
// One entry per episode, with all of the person's roles on it
private fun List<EpisodePerson>.toPersonEpisodeDtos(): List<PersonEpisodeDto> =
    groupBy { it.episode.id }.values.map { assignments ->
        val episode = assignments.first().episode
        PersonEpisodeDto(episode.id, episode.code, episode.title, assignments.map { it.role }.sorted())
    }

@Component
class PeopleMCP(
    private val people: PeopleRepository,
    private val episodePeople: EpisodePeopleRepository,
) {

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

    @Transactional
    @McpTool(
        name = "set_person_email",
        description = "Set or clear a person's email. A signed-in user with the same verified email is linked to this person"
    )
    fun setEmail(
        @McpToolParam(description = "Person ID") personId: UUID,
        @McpToolParam(description = "Unique email address (case-insensitive); omit to clear", required = false) email: String?,
    ): PersonDto {
        val person = requireNotNull(people.findByIdOrNull(personId)) { "Person $personId not found" }
        require(email == null || people.findByEmail(email).let { it == null || it == person }) {
            "Person with email $email already exists"
        }
        person.email = email
        return person.toDto()
    }

    @Transactional
    @McpTool(name = "delete_person", description = "Soft-delete a person. They drop off their episodes until they are restored")
    fun delete(@McpToolParam(description = "Person ID") personId: UUID): PersonDto? =
        people.findByIdOrNull(personId)?.also { people.delete(it) }?.toDto()

    @Transactional
    @McpTool(name = "restore_person", description = "Bring back a soft-deleted person, with their roles on episodes")
    fun restore(@McpToolParam(description = "Person ID") personId: UUID): PersonDto {
        require(people.isDeleted(personId)) { "There is no deleted person $personId" }
        people.findDeletedEmail(personId)?.let { email ->
            require(people.findByEmail(email) == null) { "Person with email $email already exists" }
        }
        people.restore(personId)
        return requireNotNull(people.findByIdOrNull(personId)).toDto()
    }

    @Transactional(readOnly = true)
    @McpTool(name = "whoami", description = "Show the signed-in user and the person linked to them by verified email")
    fun whoami(): CurrentUserDto {
        val jwt = checkNotNull(currentJwt()) { "Not signed in" }
        return CurrentUserDto(
            username = jwt.getClaimAsString("preferred_username"),
            email = jwt.getClaimAsString("email"),
            emailVerified = jwt.getClaimAsBoolean("email_verified") == true,
            person = jwt.verifiedEmail?.let(people::findByEmail)?.toDto(),
        )
    }

    @Transactional(readOnly = true)
    @McpTool(name = "my_episodes", description = "List the episodes the signed-in user is on and their roles in each, latest first")
    fun myEpisodes(): List<PersonEpisodeDto> =
        episodePeople.findByPersonWithEpisode(signedInPerson()).toPersonEpisodeDtos()

    private fun signedInPerson(): Person {
        val jwt = checkNotNull(currentJwt()) { "Not signed in" }
        val email = checkNotNull(jwt.verifiedEmail) { "Signed-in user has no verified email" }
        return checkNotNull(people.findByEmail(email)) { "No person has email $email; link one with set_person_email" }
    }

    @Transactional(readOnly = true)
    @McpTool(name = "list_person_episodes", description = "List the episodes a person is on and their roles in each, latest first")
    fun listEpisodes(@McpToolParam(description = "Person ID") personId: UUID): List<PersonEpisodeDto> {
        val person = requireNotNull(people.findByIdOrNull(personId)) { "Person $personId not found" }
        return episodePeople.findByPersonWithEpisode(person).toPersonEpisodeDtos()
    }
}
