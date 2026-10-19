package si.ogrodje.metaos

import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.data.domain.PageRequest
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller

/*
 * GraphQL at /graphql (resources/graphql/schema.graphqls), for the front-end. It's open to anyone, as some of it is
 * public; the bearer token, when there is one, says who is asking.
 */

const val MAX_RELEASED_EPISODES = 20

@Controller
class EpisodesGraphQL(private val episodes: EpisodesRepository) {

    @QueryMapping
    fun releasedEpisodes(@Argument limit: Int): List<Episode> {
        require(limit in 1..MAX_RELEASED_EPISODES) { "limit must be between 1 and $MAX_RELEASED_EPISODES" }
        return episodes.findLatestAiredByStatus(EpisodeStatus.PUBLISHED, PageRequest.of(0, limit))
    }

    @SchemaMapping(typeName = "Episode")
    fun show(episode: Episode): String? = episode.show?.name
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
