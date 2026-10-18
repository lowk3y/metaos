package si.ogrodje.metaos

import jakarta.persistence.*
import org.hibernate.annotations.SoftDelete
import org.hibernate.annotations.SoftDeleteType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

@Entity
@Table(name = "episodes")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
class Episode(
    @Column(nullable = true, columnDefinition = "text") var title: String,
    @Column(nullable = true, columnDefinition = "citext") var code: String,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    override fun equals(other: Any?) = this === other || (other is Episode && id != null && id == other.id)
    override fun hashCode() = javaClass.hashCode()
    override fun toString() = "Episode(id=$id, title=$title)"


}

interface EpisodesRepository : JpaRepository<Episode, UUID> {
    fun findByCode(code: String): Episode?
}