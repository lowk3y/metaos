package si.ogrodje.metaos

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import java.time.LocalDate
import java.util.UUID

/*
 * GraphQL at /graphql (resources/graphql/schema.graphqls), for the front-end. It's open to anyone, as some of it is
 * public; the bearer token, when there is one, says who is asking.
 */

const val MAX_RELEASED_EPISODES = 20

private const val SIGNED_IN = "hasAuthority('SCOPE_$API_READ_SCOPE')"

// When an episode is or will be recorded: its own date, or else its session's
private val byRecordingDate = compareBy<Episode, LocalDate?>(nullsLast()) { it.recordingDate ?: it.session?.date }
    .thenBy(nullsLast()) { it.code }

@Controller
class EpisodesGraphQL(private val episodes: EpisodesRepository, private val episodePeople: EpisodePeopleRepository) {

    @QueryMapping
    fun releasedEpisodes(@Argument limit: Int): List<Episode> {
        require(limit in 1..MAX_RELEASED_EPISODES) { "limit must be between 1 and $MAX_RELEASED_EPISODES" }
        return episodes.findLatestAiredByStatus(EpisodeStatus.PUBLISHED, PageRequest.of(0, limit))
    }

    // An idea without a session or a date isn't a recording yet
    @QueryMapping
    @PreAuthorize(SIGNED_IN)
    fun upcomingRecordings(): List<Episode> =
        episodes.findByStatusIn(listOf(EpisodeStatus.IDEA, EpisodeStatus.PLANNED, EpisodeStatus.SCHEDULED))
            .filter { it.session != null || it.recordingDate != null }
            .sortedWith(byRecordingDate)

    @QueryMapping
    @PreAuthorize(SIGNED_IN)
    fun inProduction(): List<Episode> =
        episodes.findByStatusIn(
            listOf(EpisodeStatus.RECORDED, EpisodeStatus.IN_PRODUCTION, EpisodeStatus.READY, EpisodeStatus.EARLY_ACCESS)
        ).sortedWith(byRecordingDate)

    @SchemaMapping(typeName = "Episode")
    fun show(episode: Episode): String? = episode.show?.name

    // The people of all the episodes in an answer, in one query
    @BatchMapping(typeName = "Episode")
    fun people(episodes: List<Episode>): Map<Episode, List<EpisodePersonDto>> {
        val byEpisode = episodePeople.findByEpisodesWithPerson(episodes).groupBy { it.episode }
        return episodes.associateWith { byEpisode[it].orEmpty().toEpisodePersonDtos() }
    }
}

/** A Google Doc linked to an episode or a session (Documents.kt); linking takes only Google Docs */
data class DocumentLinkDto(val fileId: String) {
    val url = "https://docs.google.com/document/d/$fileId/edit"
}

// Whether the caller may see what isn't public - what @PreAuthorize(SIGNED_IN) checks, for fields others get empty
private fun signedIn() =
    SecurityContextHolder.getContext().authentication?.authorities.orEmpty().any { it.authority == "SCOPE_$API_READ_SCOPE" }

// Docs are the team's, so others get none
@Controller
class DocumentsGraphQL(
    private val episodeDocuments: EpisodeDocumentsRepository,
    private val sessionDocuments: SessionDocumentsRepository,
) {

    @BatchMapping(typeName = "Episode", field = "documents")
    fun episodeDocuments(episodes: List<Episode>): Map<Episode, List<DocumentLinkDto>> {
        val byEpisode = if (signedIn()) episodeDocuments.findByEpisodeIds(episodes.mapNotNull { it.id }).groupBy { it.episode } else emptyMap()
        return episodes.associateWith { episode -> byEpisode[episode].orEmpty().map { DocumentLinkDto(it.fileId) } }
    }

    @BatchMapping(typeName = "Session", field = "documents")
    fun sessionDocuments(sessions: List<Session>): Map<Session, List<DocumentLinkDto>> {
        val bySession = if (signedIn()) sessionDocuments.findBySessionIds(sessions.mapNotNull { it.id }).groupBy { it.session } else emptyMap()
        return sessions.associateWith { session -> bySession[session].orEmpty().map { DocumentLinkDto(it.fileId) } }
    }
}

/** A Koofr folder linked to an episode or session: a place shared with the bot, named when Koofr answers, and a path */
data class KoofrFolderLinkDto(val place: String, val placeName: String?, val path: String)

// Everything about one episode or session, for its page. What isn't public is empty, or null, for anyone else, as an
// episode or session can also be reached through the released episodes.
@Controller
class DetailsGraphQL(
    private val episodeRepository: EpisodesRepository,
    private val sessionRepository: SessionsRepository,
    private val episodeFolders: EpisodeKoofrFoldersRepository,
    private val sessionFolders: SessionKoofrFoldersRepository,
    private val history: History,
    private val koofr: ObjectProvider<Koofr>,
) {

    @QueryMapping
    @PreAuthorize(SIGNED_IN)
    fun episode(@Argument ref: String): Episode? = episodeRepository.findByRef(ref)

    @QueryMapping
    @PreAuthorize(SIGNED_IN)
    fun session(@Argument id: UUID): Session? = sessionRepository.findByIdOrNull(id)

    @SchemaMapping(typeName = "Episode")
    fun machineSummary(episode: Episode): String? = episode.machineSummary.takeIf { signedIn() }

    @SchemaMapping(typeName = "Episode", field = "koofrFolders")
    fun episodeKoofrFolders(episode: Episode): List<KoofrFolderLinkDto> =
        if (signedIn()) folders(episodeFolders.findByEpisode(episode).map { it.place to it.path }) else emptyList()

    @SchemaMapping(typeName = "Episode", field = "history")
    fun episodeHistory(episode: Episode): List<HistoryEntryDto> =
        if (signedIn()) history.of(checkNotNull(episode.id)) else emptyList()

    @SchemaMapping(typeName = "Session")
    fun address(session: Session): String? = session.address.takeIf { signedIn() }

    @SchemaMapping(typeName = "Session", field = "episodes")
    fun sessionEpisodes(session: Session): List<Episode> =
        if (signedIn()) episodeRepository.findBySessionOrderByCodeDesc(session).sortedWith(compareBy(nullsLast()) { it.code })
        else emptyList()

    @SchemaMapping(typeName = "Session", field = "koofrFolders")
    fun sessionKoofrFolders(session: Session): List<KoofrFolderLinkDto> =
        if (signedIn()) folders(sessionFolders.findBySession(session).map { it.place to it.path }) else emptyList()

    @SchemaMapping(typeName = "Session", field = "history")
    fun sessionHistory(session: Session): List<HistoryEntryDto> =
        if (signedIn()) history.of(checkNotNull(session.id)) else emptyList()

    // Places by name when Koofr is on and answers; a page doesn't fail for want of a name
    private fun folders(links: List<Pair<String, String>>): List<KoofrFolderLinkDto> {
        if (links.isEmpty()) return emptyList()
        val names = runCatching { koofr.ifAvailable?.places()?.associate { it.id to it.name } }.getOrNull().orEmpty()
        return links.map { (place, path) -> KoofrFolderLinkDto(place, names[place], path) }
            .sortedWith(compareBy({ it.placeName }, { it.path }))
    }
}

@Controller
class CurrentUserGraphQL(private val people: PeopleRepository) {

    @QueryMapping
    fun me(): CurrentUserDto? = currentJwt()?.toCurrentUserDto(people)
}

/** require(...) failing, e.g. on an argument out of range, is the caller's mistake: its message goes back to them */
@Component
class BadRequestExceptionResolver : DataFetcherExceptionResolverAdapter() {

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? =
        (ex as? IllegalArgumentException)?.let {
            GraphqlErrorBuilder.newError(env).errorType(ErrorType.BAD_REQUEST).message(it.message.orEmpty()).build()
        }
}
