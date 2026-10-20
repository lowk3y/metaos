package si.ogrodje.metaos

import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

// At most 640 × 480, enough for four in a row on a sharp screen, whatever their shape
private const val THUMBNAIL_SIZE = "w640h480"

// What Dropbox makes thumbnails of
private val PICTURES = setOf("jpg", "jpeg", "png", "gif", "webp", "tiff", "tif", "bmp")

private fun isPicture(name: String) = name.substringAfterLast('.', "").lowercase() in PICTURES

private val DropboxFile.isPicture get() = !folder && isPicture(name)

/**
 * Pictures in the Dropbox folders linked to episodes and sessions, e.g. an episode's artwork, and thumbnails of them that
 * Dropbox makes. As with Koofr's photos (KoofrPhotos), they're those right in a folder and then those in the folders
 * right in it, by name; nothing deeper.
 */
@Component
@ConditionalOnDropbox
class DropboxPictures(
    private val dropbox: Dropbox,
    private val index: DropboxFolderIndex,
    private val episodeFolders: EpisodeDropboxFoldersRepository,
    private val sessionFolders: SessionDropboxFoldersRepository,
) {

    fun of(place: String, folder: String, limit: Int): List<DropboxFile> {
        val inFolder = dropbox.list(place, folder)
        // Sub-folders are listed only while there aren't enough pictures yet
        val inSubfolders = inFolder.filter { it.folder }.sortedBy { it.name }.asSequence()
            .flatMap { subfolder -> dropbox.list(place, subfolder.path).filter { it.isPicture }.sortedBy { it.name } }
        return (inFolder.filter { it.isPicture }.sortedBy { it.name }.asSequence() + inSubfolders).take(limit).toList()
    }

    /**
     * A thumbnail, only of a picture that [of] would list: everything else in Dropbox stays out of reach. Folders are
     * linked by their IDs, so those of the folder it's in and the one above that come from [DropboxFolderIndex], by path;
     * paths in Dropbox don't mind case.
     */
    fun thumbnail(place: String, path: String): DropboxImage {
        val normalised = dropboxPath(path)
        require(isPicture(normalised.substringAfterLast('/'))) { "$path isn't a picture" }
        require(index.places().any { it.id == place }) { "$place isn't a folder shared with the bot" }
        val ids = index.folders(place).entries.associate { (id, at) -> at.lowercase() to id }
        val folder = normalised.substringBeforeLast('/').ifEmpty { "/" }
        require(isLinked(place, ids[folder.lowercase()]) || isLinked(place, ids[folder.substringBeforeLast('/').lowercase()])) {
            "$path isn't in a folder of an episode or session"
        }
        return dropbox.thumbnail(place, normalised, THUMBNAIL_SIZE)
    }

    private fun isLinked(place: String, folderId: String?) = folderId != null &&
        (episodeFolders.existsByPlaceAndFolderId(place, folderId) || sessionFolders.existsByPlaceAndFolderId(place, folderId))
}

// Behind sign-in (SecurityConfig): the page fetches them with its token, as an <img> can't send one
@RestController
@ConditionalOnDropbox
class DropboxPicturesController(private val pictures: DropboxPictures) {

    @GetMapping("/dropbox/thumbnail")
    fun thumbnail(@RequestParam place: String, @RequestParam path: String): ResponseEntity<ByteArray> {
        val image = pictures.thumbnail(place, path)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.contentType))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
            .body(image.bytes)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound() = Unit
}
