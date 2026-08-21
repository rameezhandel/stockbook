package com.stockbook.app.feature.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.AppRouter
import com.stockbook.app.design.EmptyStateBox
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Expense
import com.stockbook.core.model.ShopState
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * What the owner spent, and nothing else.
 *
 * The third side of the book, and the one joined to nobody: no customer, no
 * supplier, no bill. The line under the total says so out loud, because a
 * shopkeeper writing down their petrol deserves to know at a glance that it will
 * not turn up on a customer's statement.
 *
 * Same shape as the other two panes — a figure on top, a list underneath, and
 * every row a way in to correcting it.
 */
@Composable
fun ExpensesPane(
    state: ShopState,
    store: StockbookStore,
    router: AppRouter,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    val currency = state.settings.currency

    /**
     * Which span the total covers. Screen-local and not remembered across
     * launches, for the reason Home's is not: the useful answer on opening the
     * app is almost always this month.
     */
    var span by rememberSaveable { mutableStateOf(Span.THIS_MONTH) }

    // Keyed on `state`: `spentIn` is a plain function over a StateFlow snapshot,
    // so read bare it would subscribe to nothing and the total would sit still
    // while the list below it grew.
    val spent = remember(state, span) { store.spentIn(span.period()) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Metrics.screenPadding,
            end = Metrics.screenPadding,
            bottom = 18.dp
        )
    ) {
        item {
            TotalCard(
                label = strings.expenseInPeriod,
                value = Money.text(spent, currency),
                note = strings.expensesArePrivate,
                span = span,
                strings = strings,
                onChoose = { span = it }
            )
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Kicker(strings.expensesTitle, modifier = Modifier.weight(1f))
                GhostButton(strings.addAnExpense, onClick = { router.openNewExpense() }, fontSize = 12.0)
            }
            Spacer(Modifier.height(8.dp))
        }

        if (state.expenses.isEmpty()) {
            item {
                EmptyStateBox(
                    icon = Icon.expenses,
                    message = strings.noExpensesYet,
                    actionTitle = strings.addAnExpense,
                    onAction = { router.openNewExpense() }
                )
            }
        }

        items(state.expenses, key = { it.id }) { expense ->
            ExpenseRow(
                expense = expense,
                currency = currency,
                strings = strings,
                onClick = { router.openExpense(expense) },
                modifier = Modifier.padding(bottom = Metrics.rowGap)
            )
        }
    }
}

/** The three spans, the same three Home offers. */
private enum class Span {
    THIS_MONTH, LAST_MONTH, THIS_YEAR;

    fun period(): StatementPeriod = when (this) {
        THIS_MONTH -> StatementPeriod.thisMonth()
        LAST_MONTH -> StatementPeriod.lastMonth()
        THIS_YEAR -> StatementPeriod.thisYear()
    }

    fun label(strings: Strings): String = when (this) {
        THIS_MONTH -> strings.thisMonth
        LAST_MONTH -> strings.lastMonth
        THIS_YEAR -> strings.thisYear
    }
}

@Composable
private fun TotalCard(
    label: String,
    value: String,
    note: String,
    span: Span,
    strings: Strings,
    onChoose: (Span) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().card().hairline(radius = Metrics.cardRadius).padding(14.dp)) {
        Text(label, style = NocturneType.inter(11.0), color = Nocturne.neutral500)
        Text(
            value,
            style = NocturneType.fittedNumber(value),
            color = Nocturne.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp)
        )
        Text(
            note,
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            for (candidate in Span.entries) {
                SpanChip(
                    title = candidate.label(strings),
                    selected = candidate == span,
                    onClick = { onChoose(candidate) },
                    modifier = Modifier.weight(1f)
                )
                if (candidate != Span.entries.last()) Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun SpanChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        title,
        style = NocturneType.inter(11.5),
        color = if (selected) Nocturne.accent else Nocturne.neutral500,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .card(Metrics.controlRadius)
            .then(
                if (selected) {
                    Modifier.hairline(Nocturne.accent, Metrics.controlRadius)
                } else {
                    Modifier.hairline(Nocturne.divider, Metrics.controlRadius)
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 6.dp)
    )
}

/**
 * One expense. Tapping it opens the sheet it was written on, which is where it
 * is corrected or removed — the same rule a bill and a delivery follow.
 */
@Composable
private fun ExpenseRow(
    expense: Expense,
    currency: Currency,
    strings: Strings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .card(Metrics.rowRadius)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                expense.note,
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                strings.pickedDate(expense.spentAt),
                style = NocturneType.meta,
                color = Nocturne.neutral500
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            Money.text(expense.amount, currency),
            style = NocturneType.inter(14.0),
            color = Nocturne.text,
            maxLines = 1
        )
    }
}
