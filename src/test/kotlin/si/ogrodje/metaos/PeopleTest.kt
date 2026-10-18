package si.ogrodje.metaos

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfig::class)
class PeopleTest @Autowired constructor(
    private val episodesRepository: EpisodesRepository,
    private val peopleRepository: PeopleRepository,
    private val episodesMCP: EpisodesMCP,
    private val peopleMCP: PeopleMCP,
) {

    @Test
    fun `rejects duplicate email regardless of case`() {
        peopleMCP.create(name = "Ana", email = "ana@ogrodje.si")

        assertFailsWith<IllegalArgumentException> { peopleMCP.create(name = "Ana Again", email = "ANA@ogrodje.si") }
        assertFailsWith<DataIntegrityViolationException> {
            peopleRepository.saveAndFlush(Person(name = "Ana Again", email = "ANA@ogrodje.si"))
        }
    }

    @Test
    fun `allows many people without email`() {
        peopleMCP.create(name = "First Guest", email = null)
        peopleMCP.create(name = "Second Guest", email = null)
    }

    @Test
    fun `assigns and replaces a person's role on an episode`() {
        episodesRepository.saveAndFlush(Episode(title = "Roles", code = "ep-401"))
        val person = peopleMCP.create(name = "Host Person", email = "host@ogrodje.si")

        episodesMCP.assignPerson(code = "ep-401", personId = person.id!!, role = Role.HOST)
        episodesMCP.assignPerson(code = "EP-401", personId = person.id, role = Role.CO_HOST)

        assertEquals(
            listOf(EpisodePersonDto(person.id, "Host Person", "host@ogrodje.si", Role.CO_HOST)),
            episodesMCP.listPeople("ep-401"),
        )
    }

    @Test
    fun `soft-deleted people drop off episodes`() {
        episodesRepository.saveAndFlush(Episode(title = "Guests", code = "ep-501"))
        val person = peopleMCP.create(name = "Guest Person", email = null)
        episodesMCP.assignPerson(code = "ep-501", personId = person.id!!, role = Role.GUEST)

        peopleRepository.deleteById(person.id)

        assertTrue(episodesMCP.listPeople("ep-501").isEmpty())
        assertTrue(peopleMCP.list().none { it.id == person.id })
    }

    @Test
    fun `rejects unknown episode or person`() {
        val person = peopleMCP.create(name = "Lost Person", email = null)

        assertFailsWith<IllegalArgumentException> {
            episodesMCP.assignPerson(code = "nope", personId = person.id!!, role = Role.GUEST)
        }
    }
}
