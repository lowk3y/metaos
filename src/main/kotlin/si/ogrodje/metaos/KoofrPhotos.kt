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

// 512 px wide, enough for four in a row on a sharp screen
private const val THUMBNAIL_SIZE = "large"

private val KoofrFile.isPhoto get() = !folder && contentType?.startsWith("image/") == true

/**
 * Photos in the Koofr folders linked to episodes and sessions, and thumbnails of them that Koofr makes. A shoot is
 * usually a folder of its own in an episode's folder, so the photos are those right in a folder and then those in the
 * folders right in it, by name; nothing deeper.
 */
@Component
@ConditionalOnKoofr
class KoofrPhotos(
    private val koofr: Koofr,
    private val episodeFolders: EpisodeKoofrFoldersRepository,
    private val sessionFolders: SessionKoofrFoldersRepository,
) {

    fun of(place: String, folder: String, limit: Int): List<KoofrFile> {
        val inFolder = koofr.list(place, folder)
        // Sub-folders are listed only while there aren't enough photos yet
        val inSubfolders = inFolder.filter { it.folder }.sortedBy { it.name }.asSequence()
            .flatMap { subfolder -> koofr.list(place, subfolder.path).filter { it.isPhoto }.sortedBy { it.name } }
        return (inFolder.filter { it.isPhoto }.sortedBy { it.name }.asSequence() + inSubfolders).take(limit).toList()
    }

    /** A thumbnail, only of a photo that [of] would list: everything else in Koofr stays out of reach */
    fun thumbnail(place: String, path: String): KoofrImage {
        val normalised = koofrPath(path)
        val folder = normalised.substringBeforeLast('/').ifEmpty { "/" }
        require(isLinked(place, folder) || isLinked(place, folder.substringBeforeLast('/').ifEmpty { "/" })) {
            "$path isn't in a folder of an episode or session"
        }
        require(koofr.file(place, normalised)?.isPhoto == true) { "$path isn't a photo" }
        return koofr.thumbnail(place, normalised, THUMBNAIL_SIZE)
    }

    private fun isLinked(place: String, folder: String) =
        episodeFolders.existsByPlaceAndPath(place, folder) || sessionFolders.existsByPlaceAndPath(place, folder)
}

// Behind sign-in (SecurityConfig): the page fetches them with its token, as an <img> can't send one
@RestController
@ConditionalOnKoofr
class KoofrPhotosController(private val photos: KoofrPhotos) {

    @GetMapping("/koofr/thumbnail")
    fun thumbnail(@RequestParam place: String, @RequestParam path: String): ResponseEntity<ByteArray> {
        val image = photos.thumbnail(place, path)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.contentType))
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
            .body(image.bytes)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound() = Unit
}
