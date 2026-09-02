package com.stockbook.app.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.core.model.StatementPeriod
import com.stockbook.core.text.Strings
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Which span a screen is showing.
 *
 * Three taps that answer almost every question, and a fourth for the month-end
 * that does not start on the 1st.
 */
enum class PeriodChoice {
    THIS_MONTH, LAST_MONTH, THIS_YEAR, DATES;

    /**
     * The span itself, given whatever two dates the picker is holding.
     *
     * Here rather than in each screen, so a statement and a list of bills headed
     * "this month" are never two different months.
     */
    fun period(from: Instant, to: Instant): StatementPeriod = when (this) {
        THIS_MONTH -> StatementPeriod.thisMonth()
        LAST_MONTH -> StatementPeriod.lastMonth()
        THIS_YEAR -> StatementPeriod.thisYear()
        DATES -> StatementPeriod.Custom(from, to)
    }
}

/**
 * The row of spans, and the two dates underneath when the owner is choosing them.
 *
 * Shared rather than written per screen: the statement and the sales list both
 * ask the same question, and two pickers that must look and behave the same are
 * two pickers that will not, the first time either is corrected.
 */
@Composable
fun PeriodPicker(
    choice: PeriodChoice,
    from: Instant,
    to: Instant,
    strings: Strings,
    onChoose: (PeriodChoice) -> Unit,
    onFrom: (Instant) -> Unit,
    onTo: (Instant) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(strings.thisMonth, choice == PeriodChoice.THIS_MONTH, Modifier.weight(1f)) {
                onChoose(PeriodChoice.THIS_MONTH)
            }
            Chip(strings.lastMonth, choice == PeriodChoice.LAST_MONTH, Modifier.weight(1f)) {
                onChoose(PeriodChoice.LAST_MONTH)
            }
            Chip(strings.thisYear, choice == PeriodChoice.THIS_YEAR, Modifier.weight(1f)) {
                onChoose(PeriodChoice.THIS_YEAR)
            }
            Chip(strings.chooseDates, choice == PeriodChoice.DATES, Modifier.weight(1f)) {
                onChoose(PeriodChoice.DATES)
            }
        }

        if (choice == PeriodChoice.DATES) {
            Spacer(Modifier.height(10.dp))
            DateRangeCard(from = from, to = to, strings = strings, onFrom = onFrom, onTo = onTo)
        }
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
