package si.ogrodje.metaos

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A Dropbox folder that is about an episode, e.g. its artwork: by its Dropbox ID, its path when it was linked, and a link
 * to it for members
 */
@Entity
@Table(name = "episode_dropbox_folders")
class EpisodeDropboxFolder(
    // Eager, because Hibernate can't proxy a to-one whose target uses @SoftDelete.
    @ManyToOne(optional = false)
    @JoinColumn(name = "episode_id")
    val episode: Episode,

    // ID of the Dropbox folder shared with the bot
    @Column(nullable = false, columnDefinition = "text")
    val place: String,

    @Column(name = "folder_id", nullable = false, columnDefinition = "text")
    val folderId: String,

    @Column(nullable = false, columnDefinition = "text")
    val path: String,

    // A link that only those with access to the folder can open; none while Dropbox won't make one
    @Column(columnDefinition = "text")
    var url: String?,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    override fun equals(other: Any?) = this === other || (other is EpisodeDropboxFolder && id != null && id == other.id)
    override fun hashCode() = javaClass.hashCode()
    override fun toString() = "EpisodeDropboxFolder(id=$id, place=$place, folderId=$folderId, path=$path)"
}

/** A Dropbox folder that is about a recording session */
@Entity
@Table(name = "session_dropbox_folders")
class SessionDropboxFolder(
    // Eager, because Hibernate can't proxy a to-one whose target uses @SoftDelete.
    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id")
    val session: Session,

    // ID of the Dropbox folder shared with the bot
    @Column(nullable = false, columnDefinition = "text")
    val place: String,

    @Column(name = "folder_id", nullable = false, columnDefinition = "text")
    val folderId: String,

    @Column(nullable = false, columnDefinition = "text")
    val path: String,

    // A link that only those with access to the folder can open; none while Dropbox won't make one
    @Column(columnDefinition = "text")
    var url: String?,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    override fun equals(other: Any?) = this === other || (other is SessionDropboxFolder && id != null && id == other.id)
    override fun hashCode() = javaClass.hashCode()
    override fun toString() = "SessionDropboxFolder(id=$id, place=$place, folderId=$folderId, path=$path)"
}

interface EpisodeDropboxFoldersRepository : JpaRepository<EpisodeDropboxFolder, UUID> {
    fun findByEpisode(episode: Episode): List<EpisodeDropboxFolder>

    @Query("select * from episode_dropbox_folders where episode_id in :episodeIds order by path, place", nativeQuery = true)
    fun findByEpisodeIds(episodeIds: Collection<UUID>): List<EpisodeDropboxFolder>

    fun findByEpisodeAndFolderId(episode: Episode, folderId: String): EpisodeDropboxFolder?
    fun findByEpisodeAndPlaceAndPath(episode: Episode, place: String, path: String): EpisodeDropboxFolder?
    fun existsByPlaceAndFolderId(place: String, folderId: String): Boolean
}

interface SessionDropboxFoldersRepository : JpaRepository<SessionDropboxFolder, UUID> {
    fun findBySession(session: Session): List<SessionDropboxFolder>

    @Query("select * from session_dropbox_folders where session_id in :sessionIds order by path, place", nativeQuery = true)
    fun findBySessionIds(sessionIds: Collection<UUID>): List<SessionDropboxFolder>

    fun findBySessionAndFolderId(session: Session, folderId: String): SessionDropboxFolder?
    fun findBySessionAndPlaceAndPath(session: Session, place: String, path: String): SessionDropboxFolder?
    fun existsByPlaceAndFolderId(place: String, folderId: String): Boolean
}

// How long what Dropbox said stays good for lists
private val SEEN_FOR = Duration.ofMinutes(1)

/**
 * The shared folders and where the folders in them are now, by ID, for lists of episodes and sessions (DropboxGraphQL):
 * Dropbox is asked again after a minute, so a list asks it once per shared folder, rather than once per linked folder.
 */
@Component
@ConditionalOnDropbox
class DropboxFolderIndex(private val dropbox: Dropbox) {

    private class Seen<T>(val value: T, val at: Instant = Instant.now()) {
        val fresh get() = at.isAfter(Instant.now().minus(SEEN_FOR))
    }

    @Volatile
    private var places: Seen<List<DropboxPlace>>? = null
    private val folders = ConcurrentHashMap<String, Seen<Map<String, String>>>()

    fun places(): List<DropboxPlace> = places?.takeIf { it.fresh }?.value ?: dropbox.places().also { places = Seen(it) }

    /** Paths by folder ID */
    fun folders(place: String): Map<String, String> =
        folders[place]?.takeIf { it.fresh }?.value ?: dropbox.folders(place).also { folders[place] = Seen(it) }
}
