package si.ogrodje.metaos

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/** A Koofr folder that is about an episode, e.g. its recordings. Koofr has no file IDs, so it's a place and a path */
@Entity
@Table(name = "episode_koofr_folders")
class EpisodeKoofrFolder(
    // Eager, because Hibernate can't proxy a to-one whose target uses @SoftDelete.
    @ManyToOne(optional = false)
    @JoinColumn(name = "episode_id")
    val episode: Episode,

    // ID of the Koofr mount shared with the bot
    @Column(nullable = false, columnDefinition = "text")
    val place: String,

    @Column(nullable = false, columnDefinition = "text")
    val path: String,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    override fun equals(other: Any?) = this === other || (other is EpisodeKoofrFolder && id != null && id == other.id)
    override fun hashCode() = javaClass.hashCode()
    override fun toString() = "EpisodeKoofrFolder(id=$id, place=$place, path=$path)"
}

/** A Koofr folder that is about a recording session, e.g. everything recorded that day */
@Entity
@Table(name = "session_koofr_folders")
class SessionKoofrFolder(
    // Eager, because Hibernate can't proxy a to-one whose target uses @SoftDelete.
    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id")
    val session: Session,

    // ID of the Koofr mount shared with the bot
    @Column(nullable = false, columnDefinition = "text")
    val place: String,

    @Column(nullable = false, columnDefinition = "text")
    val path: String,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    override fun equals(other: Any?) = this === other || (other is SessionKoofrFolder && id != null && id == other.id)
    override fun hashCode() = javaClass.hashCode()
    override fun toString() = "SessionKoofrFolder(id=$id, place=$place, path=$path)"
}

// A folder moved from one path to another takes the links to it and to the folders in it along
private const val MOVED = """
    set place = :toPlace, path = :toPath || substr(path, length(:fromPath) + 1)
    where place = :fromPlace and (path = :fromPath or starts_with(path, :fromPath || '/'))
"""

interface EpisodeKoofrFoldersRepository : JpaRepository<EpisodeKoofrFolder, UUID> {
    fun findByEpisode(episode: Episode): List<EpisodeKoofrFolder>

    @Query("select * from episode_koofr_folders where episode_id in :episodeIds order by path, place", nativeQuery = true)
    fun findByEpisodeIds(episodeIds: Collection<UUID>): List<EpisodeKoofrFolder>

    fun findByEpisodeAndPlaceAndPath(episode: Episode, place: String, path: String): EpisodeKoofrFolder?
    fun existsByPlaceAndPath(place: String, path: String): Boolean

    @Modifying(clearAutomatically = true)
    @Query("update episode_koofr_folders $MOVED", nativeQuery = true)
    fun moved(fromPlace: String, fromPath: String, toPlace: String, toPath: String): Int
}

interface SessionKoofrFoldersRepository : JpaRepository<SessionKoofrFolder, UUID> {
    fun findBySession(session: Session): List<SessionKoofrFolder>

    @Query("select * from session_koofr_folders where session_id in :sessionIds order by path, place", nativeQuery = true)
    fun findBySessionIds(sessionIds: Collection<UUID>): List<SessionKoofrFolder>

    fun findBySessionAndPlaceAndPath(session: Session, place: String, path: String): SessionKoofrFolder?
    fun existsByPlaceAndPath(place: String, path: String): Boolean

    @Modifying(clearAutomatically = true)
    @Query("update session_koofr_folders $MOVED", nativeQuery = true)
    fun moved(fromPlace: String, fromPath: String, toPlace: String, toPath: String): Int
}
