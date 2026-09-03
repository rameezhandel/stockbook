package com.stockbook.app.feature.customers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.FadedRule
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.PeriodChoice
import com.stockbook.app.design.PeriodPicker
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.ScreenHeader
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Statement
import com.stockbook.core.model.StatementParty
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.model.StatementRange
import com.stockbook.core.model.Timestamps
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.StatementDocument
import com.stockbook.core.text.Strings
import java.time.Instant
import java.time.ZoneId

/**
 * One customer's account over a period, as a document.
 *
 * Full screen rather than a sheet, for two reasons: it can run to a page, and it
 * is the one screen here the owner may well turn round and show the person it is
 * about. Everything on it comes from [Statement], which does the arithmetic and is
 * tested against literal figures.
 */
@Composable
fun StatementScreen(
    /** Whose account: a customer key, or a supplier key with [isSupplier] set. */
    partyKey: String,
    /**
     * Which side of the book. One screen for both, because a statement is a
     * statement — see `Statement.make`, where the same arithmetic serves both —
     * and two screens would drift the moment either was corrected.
     */
    isSupplier: Boolean = false,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    /** Hands the rendered document to the share sheet. */
    onSharePdf: (StatementDocument) -> Unit = {},
    /**
     * Opens a credit note for correction. A note is edited from here because
     * this is the document it appears on — the same place a bill is opened from.
     */
    onEditCreditNote: (com.stockbook.core.model.CreditNote) -> Unit = {},
    /**
     * Opens a payment for correction, by id. Same reasoning as the credit note
     * above it: this is the document the payment appears on.
     */
    onEditPayment: (String) -> Unit = {},
    onClose: () -> Unit
) {
    /**
     * Which chip is on.
     *
     * Held as the *choice* rather than as a `StatementPeriod`, because a period
     * carries an instant: one built for the chip would never equal the one built a
     * moment earlier and stored, so no chip would ever look selected. The period
     * is derived from this instead, which also means "this month" is still this
     * month if the app is left open past midnight on the 1st.
     */
    var choice by remember { mutableStateOf(PeriodChoice.THIS_MONTH) }
    var from by remember {
        mutableStateOf(
            Timestamps.now().atZone(ZoneId.systemDefault()).toLocalDate()
                .minusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        )
    }
    var to by remember { mutableStateOf(Timestamps.now()) }

    val period = choice.period(from, to)

    val statement = if (isSupplier) {
        store.statementForSupplier(partyKey, period)
    } else {
        store.statementForCustomer(partyKey, period)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Nocturne.bg)
            .statusBarsPadding()
    ) {
        ScreenHeader(title = strings.statement, subtitle = statement?.party?.name) {
            GhostButton(strings.done, onClick = onClose, fontSize = 12.5)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Metrics.screenPadding)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            PeriodPicker(
                choice = choice,
                from = from,
                to = to,
                strings = strings,
                onChoose = { choice = it },
                onFrom = { from = it },
                onTo = { to = it }
            )

            if (statement != null) {
                Spacer(Modifier.height(10.dp))
                ContactLine(statement.party)
                Document(
                    statement = statement,
                    currency = currency,
                    strings = strings,
                    onEditPayment = onEditPayment,
                    onEditCreditNote = onEditCreditNote
                )
                Spacer(Modifier.height(10.dp))
                // One way out, and it is the document. There was a plain-text
                // share beside this for a quick message, and it was a second
                // rendering of the same figures with none of the page's wording,
                // arithmetic or letterhead — a statement somebody could quote
                // back that the app would not recognise as its own.
                PrimaryButton(
                    strings.sharePdf,
                    onClick = { onSharePdf(StatementDocument.make(statement, store.settings, strings, currency)) },
                    fullWidth = true,
                    height = 44.dp,
                    fontSize = 13.5
                )
            }
        }
    }
}

@Composable
private fun ContactLine(party: StatementParty) {
    val details = listOfNotNull(party.phone, party.place)
    if (details.isNotEmpty()) {
        Text(
            details.joinToString(" · "),
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun Document(
    statement: Statement,
    currency: Currency,
    strings: Strings,
    onEditCreditNote: (com.stockbook.core.model.CreditNote) -> Unit = {},
    onEditPayment: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .card(Metrics.statRadius)
            .hairline(radius = Metrics.statRadius)
            .padding(16.dp)
    ) {
        Kicker(
            strings.dateSpan(
                from = strings.longDate(statement.range.start),
                to = strings.longDate(lastDay(statement.range))
            ),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Figure(strings.openingBalance, Money.text(statement.openingBalance, currency), muted = true)

        FadedRule(modifier = Modifier.padding(vertical = 10.dp))

        if (statement.isEmpty) {
            Text(
                strings.nothingInThisPeriod,
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
            )
        } else {
            statement.entries.forEachIndexed { index, entry ->
                EntryRow(
                    entry = entry,
                    balance = statement.runningBalances[index],
                    currency = currency,
                    strings = strings,
                    onEditPayment = onEditPayment,
                    onEditCreditNote = onEditCreditNote
                )
                if (index < statement.entries.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }

        FadedRule(modifier = Modifier.padding(vertical = 10.dp))

        Figure(chargedLabel(statement, strings), Money.text(statement.billed, currency))
        Spacer(Modifier.height(2.dp))
        Figure(settledLabel(statement, strings), Money.text(statement.received, currency))
        // Its own line, and only where there is one. Credit is not cash, and a
        // row saying "0.00 credited" on every statement teaches people to stop
        // reading the ones that are not zero.
        if (statement.credited > 0) {
            Spacer(Modifier.height(2.dp))
            Figure(strings.creditNotes, Money.text(statement.credited, currency))
        }

        FadedRule(modifier = Modifier.padding(vertical = 10.dp))

        // The number the whole document exists to state.
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                strings.closingBalance,
                style = NocturneType.inter(13.0, FontWeight.Medium),
                color = Nocturne.text,
                modifier = Modifier.weight(1f)
            )
            Text(
                closingText(statement, currency, strings),
                style = NocturneType.bigNumber(22.0),
                color = if (statement.closingBalance > 0) Nocturne.accent else Nocturne.text
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: String, muted: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = if (muted) NocturneType.meta else NocturneType.inter(13.0),
            color = if (muted) Nocturne.neutral500 else Nocturne.text,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = NocturneType.inter(13.0),
            color = if (muted) Nocturne.neutral500 else Nocturne.text
        )
    }
}

@Composable
private fun EntryRow(
    entry: Statement.Entry,
    balance: Double,
    currency: Currency,
    strings: Strings,
    onEditPayment: (String) -> Unit,
    onEditCreditNote: (com.stockbook.core.model.CreditNote) -> Unit = {}
) {
    // Either kind of payment can be deleted here; a bill or a delivery is changed
    // or taken out from inside the document itself, which is where the owner can
    // see what they are about to touch.
    val paymentId = when (entry) {
        is Statement.Entry.ForPayment -> entry.payment.id
        is Statement.Entry.ForSupplierPayment -> entry.payment.id
        else -> null
    }
    // Both of the things this screen can correct open the same way: tap the row,
    // get the sheet it was written on, with removal one button inside it. They
    // used to differ — a credit note opened, a payment armed a delete — which
    // meant the same gesture did two things depending on which row it landed on.
    //
    // A bill or a delivery is not corrected from here. Removing one puts stock
    // back on the shelf, and offering that from a row on a document somebody is
    // reading would be a second, worse route than the opened document itself.
    val creditNote = (entry as? Statement.Entry.ForCreditNote)?.note

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    creditNote != null -> Modifier.clickable { onEditCreditNote(creditNote) }
                    paymentId == null -> Modifier
                    else -> Modifier.clickable { onEditPayment(paymentId) }
                }
            )
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                when (entry) {
                    is Statement.Entry.ForBill -> {
                        Text(
                            reference(entry, strings),
                            style = NocturneType.inter(13.0),
                            color = Nocturne.text
                        )
                        // Drawn only where there is something to say. A bill
                        // entered as a figure lists nothing, and an empty second
                        // line leaves a gap in a document somebody is checking
                        // line by line.
                        Detail(entry.bill.summary)
                    }
                    is Statement.Entry.ForPayment -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(Icon.confirm, size = 10.dp, tint = Nocturne.accent400)
                            Spacer(Modifier.width(5.dp))
                            Text(reference(entry, strings), style = NocturneType.inter(13.0), color = Nocturne.accent400)
                        }
                        entry.payment.note?.let {
                            Text(it, style = NocturneType.meta, color = Nocturne.neutral500)
                        }
                    }
                    is Statement.Entry.ForCreditNote -> {
                        Text(
                            reference(entry, strings),
                            style = NocturneType.inter(13.0),
                            color = Nocturne.accent400
                        )
                        // Why it was written, where the owner said. On a document
                        // somebody is checking against their own paper, "returned,
                        // damaged" is the difference between a figure they
                        // recognise and one they have to go and ask about.
                        Detail(entry.note.reason)
                    }
                    is Statement.Entry.ForPurchase -> {
                        Text(
                            reference(entry, strings),
                            style = NocturneType.inter(13.0),
                            color = Nocturne.text
                        )
                        // The delivery note's whole content on one line, and
                        // nothing at all where the supplier's bill named none.
                        Detail(entry.purchase.described)
                    }
                    is Statement.Entry.ForSupplierPayment -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(Icon.confirm, size = 10.dp, tint = Nocturne.accent400)
                            Spacer(Modifier.width(5.dp))
                            Text(reference(entry, strings), style = NocturneType.inter(13.0), color = Nocturne.accent400)
                        }
                        entry.payment.note?.let {
                            Text(it, style = NocturneType.meta, color = Nocturne.neutral500)
                        }
                    }
                    is Statement.Entry.ForTransfer -> {
                        // Named by the account at the other end — see
                        // `reference` — because there is no number to show. The
                        // reason is drawn under it for the same purpose a credit
                        // note's is: a figure the customer cannot place is one
                        // they have to come and ask about.
                        Text(
                            reference(entry, strings),
                            style = NocturneType.inter(13.0),
                            color = Nocturne.text
                        )
                        Detail(entry.transfer.note)
                    }
                }
                Text(strings.longDate(entry.date), style = NocturneType.meta, color = Nocturne.neutral500)
            }

            Spacer(Modifier.width(6.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    amountText(entry, currency),
                    style = NocturneType.inter(13.0),
                    color = when (entry) {
                        is Statement.Entry.ForBill,
                        is Statement.Entry.ForPurchase -> Nocturne.text
                        is Statement.Entry.ForPayment,
                        is Statement.Entry.ForSupplierPayment,
                        is Statement.Entry.ForCreditNote -> Nocturne.accent400
                        // Whichever way this one moves the account, so it reads
                        // like the charge or the settlement it is.
                        is Statement.Entry.ForTransfer ->
                            if (entry.outgoing) Nocturne.accent400 else Nocturne.text
                    }
                )
                // The running balance beside every line: the column that turns a list
                // into a statement somebody can check.
                Text(Money.text(balance, currency), style = NocturneType.meta, color = Nocturne.neutral500)
            }
        }

    }
}

/**
 * The line under an entry's reference, where the entry has one.
 *
 * Null and blank are the same answer here — a bill with no lines on it and a
 * delivery with no product on it both have nothing to add — and both draw
 * nothing rather than an empty row the eye has to account for.
 */
@Composable
private fun Detail(text: String?) {
    if (text.isNullOrBlank()) return
    Text(
        text,
        style = NocturneType.meta,
        color = Nocturne.neutral500,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * What a row is called on screen — the printed statement's own rule, borrowed
 * rather than restated.
 *
 * The two are read side by side when somebody checks a PDF against the app, and
 * a row named two different ways is a row they reconcile by eye.
 */
private fun reference(entry: Statement.Entry, strings: Strings): String =
    StatementDocument.reference(entry, strings)

private fun amountText(entry: Statement.Entry, currency: Currency): String = when (entry) {
    is Statement.Entry.ForBill -> Money.text(entry.bill.total, currency)
    is Statement.Entry.ForPurchase -> Money.text(entry.purchase.total, currency)
    // A minus sign on both kinds of payment: it is what the account moves by, and
    // on a supplier's statement that is money leaving rather than arriving.
    is Statement.Entry.ForCreditNote -> "− ${Money.text(entry.note.total, currency)}"
    is Statement.Entry.ForPayment -> "− ${Money.text(entry.payment.amount, currency)}"
    is Statement.Entry.ForSupplierPayment -> "− ${Money.text(entry.payment.amount, currency)}"
    // The sign says which way this account moved, not which way the money went —
    // no money went anywhere. Out of this account is a reduction like a payment;
    // into it is a charge like a bill.
    is Statement.Entry.ForTransfer ->
        if (entry.outgoing) "− ${Money.text(entry.transfer.amount, currency)}"
        else Money.text(entry.transfer.amount, currency)
}

/**
 * "Billed" and "Received" are the customer's words. On a supplier's account they
 * read backwards — the shop is the one being billed — so the two figures are
 * named by which way the account runs.
 */
private fun chargedLabel(statement: Statement, strings: Strings): String =
    if (statement.party.isSupplier) strings.purchasedInPeriod else strings.billedInPeriod

private fun settledLabel(statement: Statement, strings: Strings): String =
    if (statement.party.isSupplier) strings.paidOutInPeriod else strings.receivedInPeriod

private fun closingText(statement: Statement, currency: Currency, strings: Strings): String =
    if (statement.closingBalance < 0) {
        strings.inAdvance(Money.text(-statement.closingBalance, currency))
    } else {
        Money.text(statement.closingBalance, currency)
    }

/**
 * The last day this statement can honestly say it covers — the range's own rule,
 * so the screen and the PDF are headed with the same date.
 */
private fun lastDay(range: StatementRange): Instant = range.asOf()
