package com.stockbook.app.feature.bills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.app.photos.PhotoFailure
import com.stockbook.app.photos.PhotoStore
import com.stockbook.app.photos.PhotoStrip
import com.stockbook.app.photos.PhotoViewer
import com.stockbook.app.photos.rememberPhotoCapture
import com.stockbook.core.model.Bill
import com.stockbook.core.model.ShopState
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.BillText
import com.stockbook.core.text.Strings

/**
 * Opening a bill from history.
 *
 * The bill is looked up **live from the state** rather than rendered from the
 * value that opened the sheet, so a correction made elsewhere redraws the
 * document in place rather than leaving a copy of the old one on screen.
 *
 * Both ways of fixing a mistake live here rather than on the list row: having to
 * open the bill first is the cheapest possible confirmation step, and it puts the
 * document the owner is about to change in front of them while they change it.
 */
@Composable
fun BillSheet(
    bill: Bill,
    state: ShopState,
    store: StockbookStore,
    strings: Strings,
    onShare: (String) -> Unit,
    /** Sends one photograph out through the share sheet, as a file rather than text. */
    onSharePhoto: (String) -> Unit,
    /** Hands the bill to the form it was written on. The shell fills the cart. */
    onEdit: (Bill) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val photos = remember(context) { PhotoStore(context) }
    // Falls back to the value it was opened with, which matters after a database
    // replace has removed it from under the sheet.
    val live = state.bills.firstOrNull { it.number == bill.number } ?: bill

    // Keyed on the bill, so a sheet reopened on another one does not arrive with
    // the first tap already spent.
    var confirmingRemoval by remember(live.number) { mutableStateOf(false) }
    var viewing by remember(live.number) { mutableStateOf<String?>(null) }
    var trouble by remember(live.number) { mutableStateOf<String?>(null) }

    val capture = rememberPhotoCapture(
        onSaved = { id -> store.attachPhoto(live.number, id); trouble = null },
        onFailed = { reason ->
            trouble = when (reason) {
                PhotoFailure.NO_CAMERA -> strings.noCameraOnThisPhone
                else -> strings.couldNotReadThatPhoto
            }
        }
    )

    // One photograph, filling the sheet. Opening it replaces what is under it
    // rather than stacking a second sheet on top: there is one thing to look at,
    // and a way back.
    val open = viewing
    if (open != null) {
        PhotoViewer(
            id = open,
            strings = strings,
            onShare = { onSharePhoto(open) },
            onRemove = {
                // The book forgets it first, then the file goes. In that order a
                // crash in between leaves a picture nothing points at — which the
                // sweep collects — rather than a bill pointing at nothing.
                store.detachPhoto(live.number, open)
                photos.delete(open)
                viewing = null
            },
            onClose = { viewing = null }
        )
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = strings.billDetailTitle,
            // What it lists, or — where it lists nothing — what it came to. A
            // bill entered as a figure is not "0 items"; that reads as a document
            // whose contents went missing.
            subtitle = if (live.isItemised) {
                strings.items(live.lines.size)
            } else {
                Money.text(live.total, state.settings.currency)
            },
            onClose = onClose
        )

        BillTemplate(
            bill = live,
            currency = state.settings.currency,
            strings = strings,
            shopName = state.settings.ownerName
        )

        // The paper itself, where the owner photographed it. Under the bill
        // rather than beside it: the figures are what the sheet is for, and the
        // picture is the evidence behind them.
        if (live.photoIds.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Kicker(strings.billPhotos)
            Spacer(Modifier.height(8.dp))
            PhotoStrip(ids = live.photoIds, strings = strings, onOpen = { viewing = it })
        }

        Spacer(Modifier.height(if (live.photoIds.isEmpty()) 14.dp else 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            GhostButton(strings.takePhoto, onClick = capture.takePhoto, fontSize = 12.5)
            Spacer(Modifier.width(12.dp))
            GhostButton(strings.chooseFromPhotos, onClick = capture.chooseFromPhotos, fontSize = 12.5)
        }

        trouble?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = NocturneType.meta, color = Nocturne.accent400)
        }

        // The bill as something to send: the customer asking for "the invoice"
        // wants it on their phone, and plain text is what reaches them there.
        Spacer(Modifier.height(14.dp))
        SecondaryButton(
            strings.share,
            onClick = {
                onShare(
                    BillText.plainText(live, state.settings.ownerName, state.settings.currency, strings)
                )
            },
            fullWidth = true,
            height = 44.dp,
            fontSize = 13.5,
            leading = Icon.share
        )

        Spacer(Modifier.height(8.dp))
        SecondaryButton(
            strings.editBill,
            onClick = { onEdit(live) },
            fullWidth = true,
            height = 44.dp,
            fontSize = 13.5,
            leading = Icon.edit
        )

        // Removal is a second tap, and the note is why: this is the one action in
        // the app that takes a document out of history, and it moves the shelf on
        // the way past.
        Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
            GhostButton(
                if (confirmingRemoval) strings.tapAgainToRemove else strings.removeBill,
                onClick = {
                    if (confirmingRemoval) {
                        store.deleteBill(live.number)
                        // Its pictures go with it. Swept rather than deleted by
                        // name, so a photograph another bill also names — after
                        // a restore, say — is not taken away from that one.
                        photos.sweep(store.photoIdsInUse())
                        onClose()
                    } else {
                        confirmingRemoval = true
                    }
                },
                fontSize = 12.0,
                tint = Nocturne.neutral500
            )
            Text(
                strings.removeBillNote,
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
