package com.stockbook.app.feature.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stockbook.core.transfer.BackupDocument
import com.stockbook.core.transfer.BackupError
import com.stockbook.core.transfer.BackupService

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
            val text = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw BackupError.Unreadable
            Stage.Picked(BackupService.decode(text), uri.lastPathSegment ?: "backup.json")
        } catch (error: BackupError) {
            Stage.Failed(error)
        } catch (_: Exception) {
            Stage.Failed(BackupError.Unreadable)
        }
    }

    fun cancel() {
        stage = Stage.Idle
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
