package si.ogrodje.metaos

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@Import(TestcontainersConfig::class)
class EpisodesTest @Autowired constructor(
    private val episodesRepository: EpisodesRepository,
    private val jdbcTemplate: JdbcTemplate,
) {

    @Test
    fun `can save and find episode by code`() {
        val episode = Episode(title = "Test Episode", code = "ep-101")
        val saved = episodesRepository.saveAndFlush(episode)
        println("Saved episode ID: ${saved.id}, code: ${saved.code}")

        val foundById = episodesRepository.findById(saved.id!!)
        println("Found by ID: $foundById")

        val found = episodesRepository.findByCode("ep-101")
        println("Found by lowercase code: $found")

        assertNotNull(found)
        assertEquals("Test Episode", found.title)
    }

    @Test
    fun `rejects duplicate code regardless of case`() {
        episodesRepository.saveAndFlush(Episode(title = "Original", code = "ep-201"))

        assertFailsWith<DataIntegrityViolationException> {
            episodesRepository.saveAndFlush(Episode(title = "Duplicate", code = "EP-201"))
        }
    }

    @Test
    fun `soft deletes episode and frees its code`() {
        val saved = episodesRepository.saveAndFlush(Episode(title = "To delete", code = "ep-301"))

        episodesRepository.delete(saved)

        assertNull(episodesRepository.findByCode("ep-301"))
        assertTrue(episodesRepository.findAll().none { it.id == saved.id })
        assertEquals(
            true,
            jdbcTemplate.queryForObject<Boolean>(
                "select deleted_at is not null from episodes where id = ?", saved.id
            )
        )

        episodesRepository.saveAndFlush(Episode(title = "Recreated", code = "ep-301"))
        assertEquals("Recreated", episodesRepository.findByCode("ep-301")?.title)
    }
}