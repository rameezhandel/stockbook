package com.stockbook.app.feature.bills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockbook.app.AppRouter
import com.stockbook.app.design.EmptyStateBox
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.PeriodChoice
import com.stockbook.app.design.PeriodPicker
import com.stockbook.app.design.ScreenHeader
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.Timestamps
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings
import java.time.Instant

/**
 * The sales half of the book: every bill the shop has written over a span,
 * newest first.
 *
 * **The customers used to sit on top of this.** They have a tab of their own now
 * — see `PeopleScreen`. Finding a person and browsing records are two tasks with
 * two verbs, and stacking them made a scroll you had to go past to reach either.
 * The chip row above this pane was switching both halves at once, which is why
 * expenses, having no people, never fitted the pattern.
 *
 * Nothing on this list corrects anything. A bill entered wrong is opened first,
 * and edited or removed from inside the document — which is the only place the
 * owner can see what they are about to change.
 */
@Composable
fun BillsScreen(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    strings: Strings,
    /** False inside the book, which carries one header for both halves. */
    showHeader: Boolean = true,
    modifier: Modifier = Modifier
) {
    val currency = state.settings.currency

    // **This month by default.** The whole book is a year of rows before long,
    // and the reason to open this list is almost always something written
    // recently — so it opens on the span that answers that, and the other three
    // chips are there for the question it does not.
    //
    // `rememberSaveable` so the span survives opening a bill and coming back:
    // a list that quietly reset to this month every time a document was closed
    // would make a stretch of days impossible to read through.
    var choice by rememberSaveable { mutableStateOf(PeriodChoice.THIS_MONTH) }
    // Kept as epoch millis rather than `Instant`, which a `Bundle` cannot hold
    // without a saver of its own. The two picked days are the only state here
    // that is not already a plain value.
    var fromMillis by rememberSaveable { mutableStateOf(Timestamps.now().toEpochMilli()) }
    var toMillis by rememberSaveable { mutableStateOf(Timestamps.now().toEpochMilli()) }
    val from = Instant.ofEpochMilli(fromMillis)
    val to = Instant.ofEpochMilli(toMillis)

    // Keyed on `state` as well as the span: `billsIn` is a plain function over a
    // StateFlow snapshot, so a bill written while this is on screen moves the
    // list only if the read is keyed on what changed.
    val bills = remember(state, choice, fromMillis, toMillis) {
        store.billsIn(choice.period(from, to))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeader) ScreenHeader(title = strings.billsTitle, bottomPadding = 10.dp)

        LazyColumn(
            contentPadding = PaddingValues(
                start = Metrics.screenPadding,
                end = Metrics.screenPadding,
                bottom = 18.dp
            )
        ) {
            item {
                // The customers used to sit above this list. They have a tab of
                // their own now — finding a person and browsing records are two
                // tasks, and stacking them made one scroll you had to go past to
                // reach either.
                Kicker(strings.billsTitle, modifier = Modifier.padding(bottom = 8.dp))
                PeriodPicker(
                    choice = choice,
                    from = from,
                    to = to,
                    strings = strings,
                    onChoose = { choice = it },
                    onFrom = { fromMillis = it.toEpochMilli() },
                    onTo = { toMillis = it.toEpochMilli() },
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            // Two different nothings, and they need different words. A shop that
            // has never written a bill wants the button; a shop that wrote none
            // in August wants to be told that rather than invited to start one,
            // because the bills it is looking for are on another chip.
            if (bills.isEmpty()) {
                item {
                    if (state.bills.isEmpty()) {
                        EmptyStateBox(
                            icon = Icon.bills,
                            message = strings.noBillsEver,
                            actionTitle = strings.startABill,
                            onAction = { router.startBill() },
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        EmptyStateBox(
                            icon = Icon.bills,
                            message = strings.nothingInThisPeriod,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            items(bills, key = { it.number }) { bill ->
                BillRow(
                    bill = bill,
                    currency = currency,
                    strings = strings,
                    onClick = { router.openBill(bill) },
                    modifier = Modifier.padding(bottom = Metrics.rowGap)
                )
            }
        }
    }
}
