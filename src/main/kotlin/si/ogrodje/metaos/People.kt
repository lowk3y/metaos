package si.ogrodje.metaos

import jakarta.persistence.*
import org.hibernate.annotations.SoftDelete
import org.hibernate.annotations.SoftDeleteType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

@Entity
@Table(name = "people")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
class Person(
    @Column(nullable = false, columnDefinition = "text") var name: String,
    @Column(nullable = true, columnDefinition = "citext") var email: String?,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    override fun equals(other: Any?) = this === other || (other is Person && id != null && id == other.id)
    override fun hashCode() = javaClass.hashCode()
    override fun toString() = "Person(id=$id, name=$name)"
}

enum class Role { HOST, CO_HOST, GUEST, PRODUCER, CONSULTANT, DESIGNER }

@Entity
@Table(name = "episode_people")
class EpisodePerson(
    // Eager, because Hibernate can't proxy a to-one whose target uses @SoftDelete.
    @ManyToOne(optional = false)
    @JoinColumn(name = "episode_id")
    val episode: Episode,

    @ManyToOne(optional = false)
    @JoinColumn(name = "person_id")
    val person: Person,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    var role: Role,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    override fun equals(other: Any?) = this === other || (other is EpisodePerson && id != null && id == other.id)
    override fun hashCode() = javaClass.hashCode()
    override fun toString() = "EpisodePerson(id=$id, role=$role)"
}

interface PeopleRepository : JpaRepository<Person, UUID> {
    fun findByEmail(email: String): Person?

    // Soft-deleted people are hidden from JPA queries, so finding and restoring one takes native SQL
    @Query("select count(*) > 0 from people where id = :id and deleted_at is not null", nativeQuery = true)
    fun isDeleted(id: UUID): Boolean

    @Query("select cast(email as text) from people where id = :id and deleted_at is not null", nativeQuery = true)
    fun findDeletedEmail(id: UUID): String?

    @Modifying(clearAutomatically = true)
    @Query("update people set deleted_at = null where id = :id", nativeQuery = true)
    fun restore(id: UUID): Int
}

interface EpisodePeopleRepository : JpaRepository<EpisodePerson, UUID> {
    fun findByEpisodeAndPerson(episode: Episode, person: Person): List<EpisodePerson>
    fun findByEpisodeAndPersonAndRole(episode: Episode, person: Person, role: Role): EpisodePerson?

    @Query("select ep from EpisodePerson ep join fetch ep.person where ep.episode = :episode")
    fun findByEpisodeWithPerson(episode: Episode): List<EpisodePerson>

    @Query("select ep from EpisodePerson ep join fetch ep.episode e where ep.person = :person order by e.code desc")
    fun findByPersonWithEpisode(person: Person): List<EpisodePerson>
}
