package com.stockbook.app.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stockbook.app.photos.PhotoStore
import com.stockbook.core.transfer.BackupDocument
import com.stockbook.core.transfer.BackupError
import com.stockbook.core.transfer.BackupArchive

/**
 * The state machine behind importing a backup.
 *
 * Lifted out of the screens that use it because it is the most destructive path
 * in the app — it replaces every product and bill on the phone — and logic that
 * dangerous should be assertable without a device. The two call sites (the
 * Settings backup screen, and first-run setup) now only render the stage and
 * forward taps.
 *
 * The safety property this type exists to guarantee: **`confirm()` returns a
 * document only from `Picked`.** Nothing else can produce one, so a corrupt
 * file, a cancelled pick, or a double tap after importing cannot reach
 * `replaceEverything`.
 */
class ImportFlow {

    sealed interface Stage {
        data object Idle : Stage
        /** Decoded and validated. The owner has not agreed to anything yet. */
        data class Picked(val document: BackupDocument, val filename: String) : Stage
        data class Failed(val error: BackupError) : Stage
        data object Imported : Stage
    }

    var stage: Stage by mutableStateOf(Stage.Idle)
        private set

    /**
     * Handles the document picker's result. A file that does not decode, or
     * that fails validation, lands in [Stage.Failed] and never in [Stage.Picked].
     */
    fun pick(uri: Uri?, resolver: ContentResolver) {
        if (uri == null) return
        stage = try {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw BackupError.Unreadable
            // The document only. An archive's photographs are not touched until
            // the owner has agreed to the swap — a book they look at and cancel
            // must not leave pictures behind that nothing refers to.
            val document = BackupArchive.document(bytes)
            source = uri
            Stage.Picked(document, uri.lastPathSegment ?: "backup")
        } catch (error: BackupError) {
            source = null
            Stage.Failed(error)
        } catch (_: Exception) {
            source = null
            Stage.Failed(BackupError.Unreadable)
        }
    }

    /**
     * What was picked, kept so the photographs can be read after the owner
     * agrees rather than before.
     *
     * The `Uri` rather than the bytes: an archive of two hundred photographs is
     * tens of megabytes, and holding it across a decision the owner may take a
     * minute over is memory this phone has better uses for. A document picker's
     * Uri stays readable for as long as the activity does.
     */
    private var source: Uri? = null

    /**
     * Writes the archive's photographs to disk. Call **after**
     * `replaceEverything`, so the book that names them exists first.
     *
     * Silent about failure on purpose. The book is already in; a picture that
     * will not come out of the archive costs that picture, and the bill keeps its
     * id either way so the same archive can be tried again later.
     */
    fun restorePhotos(resolver: ContentResolver, photos: PhotoStore) {
        val uri = source ?: return
        runCatching {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return
            BackupArchive.photos(bytes) { id, data -> photos.write(id, data) }
        }
    }

    fun cancel() {
        stage = Stage.Idle
        source = null
    }

    /**
     * The document to apply, or null when there is nothing the owner has agreed
     * to. Callers must treat null as "do nothing" — it is the only thing
     * standing between a stray tap and an unrecoverable swap.
     */
    fun confirm(): BackupDocument? {
        val picked = stage as? Stage.Picked ?: return null
        stage = Stage.Imported
        return picked.document
    }
}
