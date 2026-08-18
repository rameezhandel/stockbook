package com.stockbook.app.feature.customers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.ScreenHeader
import com.stockbook.app.design.SecondaryButton
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
import com.stockbook.core.text.Strings
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

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
    onShare: (String) -> Unit,
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
    var choice by remember { mutableStateOf(Choice.THIS_MONTH) }
    var from by remember {
        mutableStateOf(
            Timestamps.now().atZone(ZoneId.systemDefault()).toLocalDate()
                .minusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        )
    }
    var to by remember { mutableStateOf(Timestamps.now()) }

    /**
     * The payment the owner has tapped, waiting for a second tap to remove.
     *
     * A mistyped payment would otherwise misstate a customer's balance for good —
     * and unlike a bill, a payment has nothing to void: it is one number and one
     * date, so the honest correction is to delete it and enter it again.
     */
    var deleting by remember { mutableStateOf<String?>(null) }

    val period = when (choice) {
        Choice.THIS_MONTH -> StatementPeriod.thisMonth()
        Choice.LAST_MONTH -> StatementPeriod.lastMonth()
        Choice.THIS_YEAR -> StatementPeriod.thisYear()
        Choice.DATES -> StatementPeriod.Custom(from, to)
    }

    val statement = if (isSupplier) {
        store.statementForSupplier(partyKey, period)
    } else {
        store.statementForCustomer(partyKey, period)
    }

    // A period change re-draws the whole document; a row still armed for deletion
    // in the old one would be armed against a row that has moved.
    LaunchedEffect(choice) { deleting = null }

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
            PeriodChips(choice = choice, strings = strings, onChoose = { choice = it })

            if (choice == Choice.DATES) {
                Spacer(Modifier.height(10.dp))
                DateRangeCard(
                    from = from,
                    to = to,
                    strings = strings,
                    onFrom = { from = it },
                    onTo = { to = it }
                )
            }

            if (statement != null) {
                Spacer(Modifier.height(10.dp))
                ContactLine(statement.party)
                Document(
                    statement = statement,
                    currency = currency,
                    strings = strings,
                    deleting = deleting,
                    onArm = { deleting = it },
                    onDelete = { id ->
                        if (isSupplier) store.deleteSupplierPayment(id) else store.deletePayment(id)
                        deleting = null
                    }
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    strings.share,
                    onClick = { onShare(plainText(statement, store, currency, strings)) },
                    fullWidth = true,
                    height = 44.dp,
                    fontSize = 13.5,
                    leading = Icon.share
                )
            }
        }
    }
}

private enum class Choice { THIS_MONTH, LAST_MONTH, THIS_YEAR, DATES }

@Composable
private fun PeriodChips(choice: Choice, strings: Strings, onChoose: (Choice) -> Unit) {
    // Three taps that answer almost every question, and a fourth for the
    // month-end that does not start on the 1st.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip(strings.thisMonth, choice == Choice.THIS_MONTH, Modifier.weight(1f)) { onChoose(Choice.THIS_MONTH) }
        Chip(strings.lastMonth, choice == Choice.LAST_MONTH, Modifier.weight(1f)) { onChoose(Choice.LAST_MONTH) }
        Chip(strings.thisYear, choice == Choice.THIS_YEAR, Modifier.weight(1f)) { onChoose(Choice.THIS_YEAR) }
        Chip(strings.chooseDates, choice == Choice.DATES, Modifier.weight(1f)) { onChoose(Choice.DATES) }
    }
}

@Composable
private fun Chip(title: String, isOn: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (isOn) Nocturne.accent else Color.Transparent)
            .hairline(Nocturne.accent, 7.dp)
            .clickable(onClick = onClick)
    ) {
        Text(
            title,
            style = NocturneType.inter(11.5),
            color = if (isOn) Nocturne.bg else Nocturne.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeCard(
    from: Instant,
    to: Instant,
    strings: Strings,
    onFrom: (Instant) -> Unit,
    onTo: (Instant) -> Unit
) {
    /** Which of the two boxes opened the picker, or none. */
    var editing by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth().card().hairline(radius = Metrics.cardRadius).padding(12.dp)) {
        DateRow(strings.fromDate, from, strings) { editing = "from" }
        Spacer(Modifier.height(8.dp))
        DateRow(strings.toDate, to, strings) { editing = "to" }
    }

    val which = editing
    if (which != null) {
        val current = if (which == "from") from else to
        val picker = rememberDatePickerState(initialSelectedDateMillis = current.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { editing = null },
            confirmButton = {
                GhostButton(strings.done, onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        // Midnight UTC out of the picker, re-anchored to midday in
                        // the phone's own zone so the day cannot slip an offset.
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .atTime(12, 0)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                        if (which == "from") onFrom(picked) else onTo(picked)
                    }
                    editing = null
                })
            }
        ) {
            DatePicker(state = picker)
        }
    }
}

@Composable
private fun DateRow(label: String, value: Instant, strings: Strings, onTap: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap)
    ) {
        Text(label, style = NocturneType.inter(13.0), color = Nocturne.neutral500, modifier = Modifier.weight(1f))
        Text(strings.longDate(value), style = NocturneType.inter(13.0), color = Nocturne.accent)
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
    deleting: String?,
    onArm: (String?) -> Unit,
    onDelete: (String) -> Unit
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
                    deleting = deleting,
                    onArm = onArm,
                    onDelete = onDelete
                )
                if (index < statement.entries.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }

        FadedRule(modifier = Modifier.padding(vertical = 10.dp))

        Figure(chargedLabel(statement, strings), Money.text(statement.billed, currency))
        Spacer(Modifier.height(2.dp))
        Figure(settledLabel(statement, strings), Money.text(statement.received, currency))

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
    deleting: String?,
    onArm: (String?) -> Unit,
    onDelete: (String) -> Unit
) {
    // Either kind of payment can be deleted; a bill or a delivery is voided
    // instead, and that lives where the document itself is opened.
    val paymentId = when (entry) {
        is Statement.Entry.ForPayment -> entry.payment.id
        is Statement.Entry.ForSupplierPayment -> entry.payment.id
        else -> null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Only a payment. A bill is **voided**, never deleted, and voiding
            // lives inside the opened bill where it belongs — offering deletion
            // beside it here would be a second, worse route to the same history.
            .then(
                if (paymentId == null) Modifier
                else Modifier.clickable {
                    onArm(if (deleting == paymentId) null else paymentId)
                }
            )
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                when (entry) {
                    is Statement.Entry.ForBill -> {
                        Text(
                            entry.bill.reference(strings),
                            style = NocturneType.inter(13.0),
                            color = if (entry.bill.voided) Nocturne.neutral500 else Nocturne.text
                        )
                        Text(
                            if (entry.bill.voided) strings.voided else entry.bill.summary,
                            style = NocturneType.meta,
                            color = Nocturne.neutral500,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    is Statement.Entry.ForPayment -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(Icon.confirm, size = 10.dp, tint = Nocturne.accent400)
                            Spacer(Modifier.width(5.dp))
                            Text(strings.paymentLabel, style = NocturneType.inter(13.0), color = Nocturne.accent400)
                        }
                        entry.payment.note?.let {
                            Text(it, style = NocturneType.meta, color = Nocturne.neutral500)
                        }
                    }
                    is Statement.Entry.ForPurchase -> {
                        Text(
                            entry.purchase.reference(strings),
                            style = NocturneType.inter(13.0),
                            color = if (entry.purchase.voided) Nocturne.neutral500 else Nocturne.text
                        )
                        // The product and how many of it: a delivery note's whole
                        // content on one line, since a purchase carries one product.
                        Text(
                            if (entry.purchase.voided) strings.voided
                            else "${entry.purchase.name} × ${entry.purchase.qty}",
                            style = NocturneType.meta,
                            color = Nocturne.neutral500,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    is Statement.Entry.ForSupplierPayment -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(Icon.confirm, size = 10.dp, tint = Nocturne.accent400)
                            Spacer(Modifier.width(5.dp))
                            Text(strings.paymentLabel, style = NocturneType.inter(13.0), color = Nocturne.accent400)
                        }
                        entry.payment.note?.let {
                            Text(it, style = NocturneType.meta, color = Nocturne.neutral500)
                        }
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
                        is Statement.Entry.ForBill ->
                            if (entry.bill.voided) Nocturne.neutral500 else Nocturne.text
                        is Statement.Entry.ForPurchase ->
                            if (entry.purchase.voided) Nocturne.neutral500 else Nocturne.text
                        is Statement.Entry.ForPayment,
                        is Statement.Entry.ForSupplierPayment -> Nocturne.accent400
                    }
                )
                // The running balance beside every line: the column that turns a list
                // into a statement somebody can check.
                Text(Money.text(balance, currency), style = NocturneType.meta, color = Nocturne.neutral500)
            }
        }

        if (paymentId != null && deleting == paymentId) {
            Spacer(Modifier.height(7.dp))
            GhostButton(
                strings.deleteThisPayment,
                onClick = { onDelete(paymentId) },
                fontSize = 12.0,
                tint = Nocturne.neutral500
            )
        }
    }
}

private fun amountText(entry: Statement.Entry, currency: Currency): String = when (entry) {
    is Statement.Entry.ForBill -> Money.text(entry.bill.total, currency)
    is Statement.Entry.ForPurchase -> Money.text(entry.purchase.total, currency)
    // A minus sign on both kinds of payment: it is what the account moves by, and
    // on a supplier's statement that is money leaving rather than arriving.
    is Statement.Entry.ForPayment -> "− ${Money.text(entry.payment.amount, currency)}"
    is Statement.Entry.ForSupplierPayment -> "− ${Money.text(entry.payment.amount, currency)}"
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
 * The last day the range covers, for display. `range.end` is exclusive, so showing
 * it would claim a day the statement does not include.
 */
private fun lastDay(range: StatementRange): Instant = range.end.minusSeconds(1)

/**
 * Plain text for the share sheet, which is how a statement actually reaches a
 * customer here — a photo of a screen or a message, not an emailed PDF. The app
 * makes no network call either way; the OS does whatever the owner picks.
 */
private fun plainText(
    statement: Statement,
    store: StockbookStore,
    currency: Currency,
    strings: Strings
): String {
    val lines = mutableListOf<String>()
    if (store.settings.ownerName.isNotBlank()) lines.add(store.settings.ownerName)
    lines.add("${strings.statement} — ${statement.party.name}")
    lines.add(
        strings.dateSpan(
            from = strings.longDate(statement.range.start),
            to = strings.longDate(lastDay(statement.range))
        )
    )
    lines.add("")
    lines.add("${strings.openingBalance}: ${Money.text(statement.openingBalance, currency)}")

    statement.entries.forEachIndexed { index, entry ->
        val balance = Money.text(statement.runningBalances[index], currency)
        when (entry) {
            is Statement.Entry.ForBill -> {
                val marker = if (entry.bill.voided) " (${strings.voided})" else ""
                lines.add(
                    "${strings.longDate(entry.bill.createdAt)}  ${entry.bill.reference(strings)}$marker  " +
                        "${Money.text(entry.bill.total, currency)}  →  $balance"
                )
            }
            is Statement.Entry.ForPayment -> lines.add(
                "${strings.longDate(entry.payment.receivedAt)}  ${strings.paymentLabel}  " +
                    "− ${Money.text(entry.payment.amount, currency)}  →  $balance"
            )
            is Statement.Entry.ForPurchase -> {
                val marker = if (entry.purchase.voided) " (${strings.voided})" else ""
                lines.add(
                    "${strings.longDate(entry.purchase.createdAt)}  " +
                        "${entry.purchase.reference(strings)}  " +
                        "${entry.purchase.name} × ${entry.purchase.qty}$marker  " +
                        "${Money.text(entry.purchase.total, currency)}  →  $balance"
                )
            }
            is Statement.Entry.ForSupplierPayment -> lines.add(
                "${strings.longDate(entry.payment.paidAt)}  ${strings.paymentLabel}  " +
                    "− ${Money.text(entry.payment.amount, currency)}  →  $balance"
            )
        }
    }

    lines.add("")
    lines.add("${chargedLabel(statement, strings)}: ${Money.text(statement.billed, currency)}")
    lines.add("${settledLabel(statement, strings)}: ${Money.text(statement.received, currency)}")
    lines.add("${strings.closingBalance}: ${closingText(statement, currency, strings)}")
    return lines.joinToString("\n")
}
