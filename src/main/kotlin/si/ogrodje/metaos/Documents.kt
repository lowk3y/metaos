package si.ogrodje.metaos

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/** A Google Doc in the Ogrodje folder that is about an episode, e.g. its preparation doc */
@Entity
@Table(name = "episode_documents")
class EpisodeDocument(
    // Eager, because Hibernate can't proxy a to-one whose target uses @SoftDelete.
    @ManyToOne(optional = false)
    @JoinColumn(name = "episode_id")
    val episode: Episode,

    // Drive file ID - unlike the title and the URL, it stays the same when the doc is renamed or moved
    @Column(nullable = false, columnDefinition = "text")
    val fileId: String,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    override fun equals(other: Any?) = this === other || (other is EpisodeDocument && id != null && id == other.id)
    override fun hashCode() = javaClass.hashCode()
    override fun toString() = "EpisodeDocument(id=$id, fileId=$fileId)"
}

interface EpisodeDocumentsRepository : JpaRepository<EpisodeDocument, UUID> {
    fun findByEpisode(episode: Episode): List<EpisodeDocument>
    fun findByEpisodeAndFileId(episode: Episode, fileId: String): EpisodeDocument?

    // The first linked first
    @Query("select * from episode_documents where episode_id in :episodeIds order by created_at", nativeQuery = true)
    fun findByEpisodeIds(episodeIds: Collection<UUID>): List<EpisodeDocument>
}

/** A Google Doc in the Ogrodje folder that is about a recording session, e.g. its recording day plan */
@Entity
@Table(name = "session_documents")
class SessionDocument(
    // Eager, because Hibernate can't proxy a to-one whose target uses @SoftDelete.
    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id")
    val session: Session,

    // Drive file ID - unlike the title and the URL, it stays the same when the doc is renamed or moved
    @Column(nullable = false, columnDefinition = "text")
    val fileId: String,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
        protected set

    override fun equals(other: Any?) = this === other || (other is SessionDocument && id != null && id == other.id)
    override fun hashCode() = javaClass.hashCode()
    override fun toString() = "SessionDocument(id=$id, fileId=$fileId)"
}

interface SessionDocumentsRepository : JpaRepository<SessionDocument, UUID> {
    fun findBySession(session: Session): List<SessionDocument>
    fun findBySessionAndFileId(session: Session, fileId: String): SessionDocument?

    // The first linked first
    @Query("select * from session_documents where session_id in :sessionIds order by created_at", nativeQuery = true)
    fun findBySessionIds(sessionIds: Collection<UUID>): List<SessionDocument>
}
