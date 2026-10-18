package si.ogrodje.metaos

import jakarta.persistence.*
import org.hibernate.annotations.SoftDelete
import org.hibernate.annotations.SoftDeleteType
import org.springframework.data.jpa.repository.JpaRepository
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

enum class Role { HOST, CO_HOST, GUEST }

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
}

interface EpisodePeopleRepository : JpaRepository<EpisodePerson, UUID> {
    fun findByEpisodeAndPerson(episode: Episode, person: Person): EpisodePerson?

    @Query("select ep from EpisodePerson ep join fetch ep.person where ep.episode = :episode")
    fun findByEpisodeWithPerson(episode: Episode): List<EpisodePerson>
}
