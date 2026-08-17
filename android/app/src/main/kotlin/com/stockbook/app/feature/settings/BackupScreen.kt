package com.stockbook.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.ScreenHeader
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import com.stockbook.core.model.ShopState
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings
import com.stockbook.core.transfer.BackupDocument
import com.stockbook.core.transfer.BackupError
import com.stockbook.core.transfer.BackupService

/** What the owner has picked, and whether it can be trusted yet. */
private sealed interface ImportStage {
    data object Idle : ImportStage
    data class Picked(val document: BackupDocument, val filename: String) : ImportStage
    data class Failed(val error: BackupError) : ImportStage
    data object Imported : ImportStage
}

/**
 * The export/import handoff — the app's only route onto a new phone.
 *
 * Both directions go through the system document picker, so the file lands
 * wherever the owner keeps things and the app never asks for storage permission
 * to do it. Import **validates before asking anything**, and only then offers to
 * replace the database.
 */
@Composable
fun BackupScreen(
    state: ShopState,
    store: StockbookStore,
    strings: Strings,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var stage by remember { mutableStateOf<ImportStage>(ImportStage.Idle) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        // Only a real write counts. A cancelled save sheet must not claim a
        // backup exists — that is the one lie this screen must never tell.
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(BackupService.encode(store.makeBackupDocument()).toByteArray())
            }
        }.onSuccess { store.markExported() }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        stage = try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw BackupError.Unreadable
            ImportStage.Picked(BackupService.decode(text), uri.lastPathSegment ?: "backup.json")
        } catch (error: BackupError) {
            ImportStage.Failed(error)
        } catch (_: Exception) {
            ImportStage.Failed(BackupError.Unreadable)
        }
    }

    // Drawn as a sibling of the tab content inside the shell's Box, so without a
    // ground of its own the screen behind shows straight through it — and without
    // `statusBarsPadding` its header sits under the status bar.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Nocturne.bg)
            .statusBarsPadding()
            .imePadding()
    ) {
        ScreenHeader(title = strings.moveToAnotherPhone) {
            GhostButton(strings.done, onClick = onClose)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.screenPadding)
                .padding(bottom = 18.dp)
        ) {
            Text(
                strings.moveToAnotherPhoneNote,
                style = NocturneType.inter(12.5),
                color = Nocturne.neutral500,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            // Export
            Column(modifier = Modifier.fillMaxWidth().card().padding(13.dp)) {
                CardHeading(Icon.confirm, strings.exportEverything)
                Text(
                    if (state.settings.hasBackup) strings.exportNoteAfterBackup else strings.exportNoteFirstTime,
                    style = NocturneType.inter(12.0),
                    color = Nocturne.neutral500,
                    modifier = Modifier.padding(bottom = 11.dp)
                )
                PrimaryButton(
                    title = if (state.settings.hasBackup) strings.writeAFreshFile else strings.createBackupFile,
                    onClick = { exporter.launch(store.makeBackupDocument().suggestedFilename) },
                    fullWidth = true,
                    height = 42.dp,
                    fontSize = 13.5
                )
            }

            Spacer(Modifier.height(10.dp))

            // Import
            Column(modifier = Modifier.fillMaxWidth().card().padding(13.dp)) {
                CardHeading(Icon.openRow, strings.importABackupFile)

                val current = stage
                Text(
                    when (current) {
                        is ImportStage.Imported -> strings.importNoteDone
                        is ImportStage.Failed -> strings.backupError(current.error)
                        else -> strings.importNoteIdle
                    },
                    style = NocturneType.inter(12.0),
                    color = if (current is ImportStage.Failed) Nocturne.accent300 else Nocturne.neutral500,
                    modifier = Modifier.padding(bottom = 11.dp)
                )

                if (current is ImportStage.Picked) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hairline(Nocturne.accent, Metrics.controlRadius)
                            .padding(11.dp)
                    ) {
                        Text(
                            current.filename,
                            style = NocturneType.inter(13.0),
                            color = Nocturne.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            current.document.summaryLine(strings),
                            style = NocturneType.inter(11.5),
                            color = Nocturne.neutral500,
                            modifier = Modifier.padding(top = 7.dp)
                        )
                        // Naming what is about to be lost, in the owner's own
                        // numbers. The last thing between a tap and an
                        // unrecoverable swap.
                        Text(
                            strings.replaceWarning(state.products.size, state.bills.size),
                            style = NocturneType.inter(11.5),
                            color = Nocturne.accent300,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(
                            strings.cancel,
                            onClick = { stage = ImportStage.Idle },
                            fullWidth = true,
                            height = 42.dp,
                            fontSize = 13.5,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        PrimaryButton(
                            strings.replaceEverything,
                            onClick = {
                                store.replaceEverything(current.document)
                                stage = ImportStage.Imported
                            },
                            fullWidth = true,
                            height = 42.dp,
                            fontSize = 13.5,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    SecondaryButton(
                        strings.chooseAFile,
                        onClick = { importer.launch(arrayOf("application/json", "*/*")) },
                        fullWidth = true,
                        height = 42.dp,
                        fontSize = 13.5
                    )
                }
            }
        }
    }
}

@Composable
private fun CardHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Glyph(icon, size = 17.dp, tint = Nocturne.accent)
        Spacer(Modifier.width(9.dp))
        Text(title, style = NocturneType.rowPrimary, color = Nocturne.text)
    }
}
