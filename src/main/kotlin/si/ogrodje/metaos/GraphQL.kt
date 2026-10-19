package si.ogrodje.metaos

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.data.domain.PageRequest
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
