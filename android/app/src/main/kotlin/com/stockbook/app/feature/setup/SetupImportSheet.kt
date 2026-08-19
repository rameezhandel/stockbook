package com.stockbook.app.feature.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.design.hairline
import com.stockbook.app.feature.settings.ImportFlow
import com.stockbook.app.photos.PhotoStore
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * The setup-time twin of the import card on the Settings backup screen.
 *
 * A shop moving to a new phone should not have to re-type its name, currency,
 * stock and bills by hand just because it happens to be starting fresh — the
 * file it already has is the fastest way through setup there is. Confirming
 * here calls the same [StockbookStore.replaceEverything] the Settings screen
 * does, which — as a fresh, never-set-up store — carries the owner straight
 * past the rest of these screens: `replaceEverything` marks setup complete as
 * part of rebuilding `Settings`, and the shell is watching that flag.
 *
 * No "this replaces what's here" warning, unlike the Settings version: at this
 * point in setup there is nothing yet to lose.
 */
@Composable
fun SetupImportSheet(
    importFlow: ImportFlow,
    store: StockbookStore,
    strings: Strings,
    onChooseFile: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(title = strings.importABackupFile, onClose = onClose)

        val current = importFlow.stage
        Text(
            when (current) {
                is ImportFlow.Stage.Imported -> strings.importNoteDone
                is ImportFlow.Stage.Failed -> strings.backupError(current.error)
                else -> strings.importNoteIdle
            },
            style = NocturneType.inter(12.0),
            color = if (current is ImportFlow.Stage.Failed) Nocturne.accent300 else Nocturne.neutral500,
            modifier = Modifier.padding(bottom = 11.dp)
        )

        if (current is ImportFlow.Stage.Picked) {
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
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    strings.cancel,
                    onClick = { importFlow.cancel() },
                    fullWidth = true,
                    height = 42.dp,
                    fontSize = 13.5,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                PrimaryButton(
                    strings.useThisBackup,
                    onClick = {
                        // Only ever acts on what confirm() hands back.
                        val confirmed = importFlow.confirm() ?: return@PrimaryButton
                        store.replaceEverything(confirmed)
                        // Nothing should be here on a fresh install, but this is
                        // also the path a reinstall takes over the top of an old
                        // container.
                        PhotoStore(context).sweep(store.photoIdsInUse())
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
                onClick = onChooseFile,
                fullWidth = true,
                height = 42.dp,
                fontSize = 13.5
            )
        }
    }
}
