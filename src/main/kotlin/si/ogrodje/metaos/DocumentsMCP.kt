package si.ogrodje.metaos

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

data class DriveFileDto(val id: String, val name: String, val type: String, val url: String?, val modifiedAt: OffsetDateTime?)
data class DocumentContentDto(val id: String, val name: String, val url: String?, val markdown: String)

enum class MatchResult { LINKED, WOULD_LINK, ALREADY_LINKED, NO_EPISODE_CODE, NO_SUCH_EPISODE }
data class DocumentMatchDto(val document: DriveFileDto, val episode: String?, val result: MatchResult)

// Google's own types lose their prefix (folder, document, spreadsheet, ...), others keep their MIME type
private fun DriveFile.toDto() =
    DriveFileDto(id, name, mimeType.removePrefix("application/vnd.google-apps."), webViewLink, modifiedTime)

// A linked doc that has been deleted, or moved where the bot can't see it
private fun missingDocument(fileId: String) = DriveFileDto(fileId, "(not in Google Drive anymore)", "missing", null, null)

const val DOCUMENT_REF = "Google Doc URL or Drive file ID"
const val FOLDER_REF = "Google Drive folder URL or ID; the Ogrodje folder when omitted"

private val FILE_ID_IN_URL = Regex("""/(?:d|folders)/([\w-]+)|[?&]id=([\w-]+)""")
private val FILE_ID = Regex("""[\w-]{10,}""")

/** The Drive file ID in a Google Docs or Drive URL, or the ID itself */
fun driveFileId(ref: String): String {
    FILE_ID_IN_URL.find(ref)?.let { match -> return match.groupValues.drop(1).first { it.isNotEmpty() } }
    return ref.trim().also { require(FILE_ID.matches(it)) { "$ref is neither a Google Drive URL nor a file ID" } }
}

private val EPISODE_CODE_IN_TITLE = Regex("""\bS(\d{1,2})E(\d{1,3})\b""", RegexOption.IGNORE_CASE)

/** The episode code in a doc's title, zero-padded like episode codes: "Ogrodje S05E7 - ..." is about S05E07 */
fun episodeCodeIn(title: String): String? =
    EPISODE_CODE_IN_TITLE.find(title)?.destructured?.let { (season, episode) ->
        "S%02dE%02d".format(season.toInt(), episode.toInt())
    }

// How far below the Ogrodje folder a file may be
private const val MAX_FOLDER_DEPTH = 10

// How many docs search_documents finds at most
private const val SEARCH_RESULTS = 25

@Component
@ConditionalOnDrive
class DocumentsMCP(
    private val drive: Drive,
    private val episodes: EpisodesRepository,
    private val documents: EpisodeDocumentsRepository,
) {

    private fun findEpisode(ref: String) = requireNotNull(episodes.findByRef(ref)) { "Episode $ref not found" }

    // Only what's in the Ogrodje folder, not anything else someone shares with the bot
    private fun fileInOgrodje(ref: String): DriveFile {
        val file = requireNotNull(drive.file(driveFileId(ref))) { "$ref isn't in Google Drive, or MetaOS can't see it" }
        require(file.id == drive.rootFolderId || isBelow(drive.rootFolderId, file)) { "${file.name} isn't in the Ogrodje folder" }
        return file
    }

    // lookUp finds the folders on the way up, e.g. among those already looked up
    private fun isBelow(folderId: String, file: DriveFile, lookUp: (String) -> DriveFile? = drive::file): Boolean {
        var parents = file.parents
        repeat(MAX_FOLDER_DEPTH) {
            if (folderId in parents) return true
            parents = parents.mapNotNull(lookUp).flatMap { it.parents }
        }
        return false
    }

    private fun document(ref: String) =
        fileInOgrodje(ref).also { require(it.mimeType == DOCUMENT_TYPE) { "${it.name} isn't a Google Doc" } }

    private fun folderId(ref: String?): String =
        ref?.let { fileInOgrodje(it).also { folder -> require(folder.mimeType == FOLDER_TYPE) { "${folder.name} isn't a folder" } }.id }
            ?: drive.rootFolderId

    @McpTool(
        name = "list_drive_folder",
        description = "List the files and folders directly in a folder of Ogrodje's Google Drive folder, by name"
    )
    fun listFolder(@McpToolParam(description = FOLDER_REF, required = false) folder: String?): List<DriveFileDto> =
        drive.children(folderId(folder)).map { it.toDto() }

    @McpTool(name = "read_document", description = "Read a Google Doc in Ogrodje's Google Drive folder, as Markdown")
    fun read(@McpToolParam(description = DOCUMENT_REF) document: String): DocumentContentDto {
        val file = document(document)
        return DocumentContentDto(file.id, file.name, file.webViewLink, drive.markdown(file.id))
    }

    @McpTool(
        name = "search_documents",
        description = "Search the Google Docs in Ogrodje's Google Drive folder, or in a folder of it and its subfolders, " +
            "by the words in their title or text. Most relevant first, at most $SEARCH_RESULTS"
    )
    fun search(
        @McpToolParam(description = "Words to look for; \"in double quotes\" for an exact phrase") text: String,
        @McpToolParam(description = FOLDER_REF, required = false) folder: String?,
    ): List<DriveFileDto> {
        require(text.isNotBlank()) { "Nothing to search for" }
        val folderId = folderId(folder)
        // Docs found share folders, so each is looked up only once
        val folders = mutableMapOf<String, DriveFile?>()
        val lookUp = { id: String -> if (id in folders) folders[id] else drive.file(id).also { folders[id] = it } }
        return drive.searchDocuments(text).filter { isBelow(folderId, it, lookUp) }.take(SEARCH_RESULTS).map { it.toDto() }.toList()
    }

    @McpTool(
        name = "list_episode_documents",
        description = "List the Google Docs linked to an episode, by name. Docs deleted since are listed as missing"
    )
    fun listEpisodeDocuments(@McpToolParam(description = EPISODE_REF) code: String): List<DriveFileDto> =
        documents.findByEpisode(findEpisode(code))
            .map { drive.file(it.fileId)?.toDto() ?: missingDocument(it.fileId) }
            .sortedBy { it.name }

    @Transactional
    @McpTool(
        name = "link_episode_document",
        description = "Link a Google Doc in Ogrodje's Google Drive folder to an episode it is about, e.g. its " +
            "preparation doc. A doc can be linked to several episodes, e.g. the doc of a recording session"
    )
    fun link(
        @McpToolParam(description = EPISODE_REF) code: String,
        @McpToolParam(description = DOCUMENT_REF) document: String,
    ): DriveFileDto {
        val episode = findEpisode(code)
        val file = document(document)
        if (documents.findByEpisodeAndFileId(episode, file.id) == null) documents.save(EpisodeDocument(episode, file.id))
        return file.toDto()
    }

    @Transactional
    @McpTool(name = "unlink_episode_document", description = "Unlink a Google Doc from an episode. The doc stays in Drive")
    fun unlink(
        @McpToolParam(description = EPISODE_REF) code: String,
        @McpToolParam(description = DOCUMENT_REF) document: String,
    ): DriveFileDto {
        val episode = findEpisode(code)
        val fileId = driveFileId(document)
        documents.delete(
            requireNotNull(documents.findByEpisodeAndFileId(episode, fileId)) { "$document isn't linked to episode ${episode.label}" }
        )
        return drive.file(fileId)?.toDto() ?: missingDocument(fileId)
    }

    @Transactional
    @McpTool(
        name = "match_episode_documents",
        description = "Match the Google Docs directly in a folder to episodes by the episode code in their title, " +
            "e.g. \"S05E03 - Guest: Title\" or \"Ogrodje S05E7 - Title\" (S05E07). Only reports what it would link, " +
            "unless apply is true"
    )
    fun match(
        @McpToolParam(description = FOLDER_REF, required = false) folder: String?,
        @McpToolParam(description = "Link the matches, instead of only reporting them", required = false) apply: Boolean?,
    ): List<DocumentMatchDto> =
        drive.children(folderId(folder)).filter { it.mimeType == DOCUMENT_TYPE }.map { file ->
            val code = episodeCodeIn(file.name)
            val episode = code?.let(episodes::findByCode)
            val result = when {
                code == null -> MatchResult.NO_EPISODE_CODE
                episode == null -> MatchResult.NO_SUCH_EPISODE
                documents.findByEpisodeAndFileId(episode, file.id) != null -> MatchResult.ALREADY_LINKED
                apply == true -> MatchResult.LINKED.also { documents.save(EpisodeDocument(episode, file.id)) }
                else -> MatchResult.WOULD_LINK
            }
            DocumentMatchDto(file.toDto(), episode?.code ?: code, result)
        }
}
