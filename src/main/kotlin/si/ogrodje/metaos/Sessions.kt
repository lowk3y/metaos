package si.ogrodje.metaos

import jakarta.persistence.*
import org.hibernate.annotations.SoftDelete
import org.hibernate.annotations.SoftDeleteType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "sessions")
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
class Session(
    @Column(nullable = false) var date: LocalDate,
    @Column(nullable = true, columnDefinition = "text") var location: String?,
    @Column(nullable = true, columnDefinition = "text") var address: String?,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    override fun equals(other: Any?) = this === other || (other is Session && id != null && id == other.id)
    override fun hashCode() = javaClass.hashCode()
    override fun toString() = "Session(id=$id, date=$date, location=$location)"
}

interface SessionsRepository : JpaRepository<Session, UUID> {
    fun findAllByOrderByDateDesc(): List<Session>

    // Soft-deleted sessions are hidden from JPA queries, so restoring one takes native SQL
    @Modifying(clearAutomatically = true)
    @Query("update sessions set deleted_at = null where id = :id and deleted_at is not null", nativeQuery = true)
    fun restore(id: UUID): Int
}
