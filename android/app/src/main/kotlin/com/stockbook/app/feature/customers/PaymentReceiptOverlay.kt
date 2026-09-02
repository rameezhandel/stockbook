package com.stockbook.app.feature.customers

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.SecondaryButton
import com.stockbook.core.text.PaymentReceiptDocument
import com.stockbook.core.text.Strings

/**
 * The slip for one payment: what was taken, from whom, and where the account
 * stands now.
 *
 * Full-screen and opaque, like the bill's receipt, and for the same reason: the
 * payment is written and there is nothing left to edit here. It is the page the
 * owner turns to face the customer.
 *
 * **Drawn from [PaymentReceiptDocument] — the same structure the PDF draws.**
 * Not a screen that happens to show the same figures: the same wording, the same
 * order, the same formatting, decided once in shared code and tested there. What
 * the customer is shown on the phone and what comes out of the printer cannot
 * disagree, because there is nothing for them to disagree about.
 */
@Composable
fun PaymentReceiptOverlay(
    document: PaymentReceiptDocument,
    /**
     * Whether this is confirming something that just happened.
     *
     * The tick and "Payment saved" belong to the moment the money was taken. The
     * same page reached from a correction is a document being looked up, and a
     * confirmation on it would be claiming something happened that did not.
     */
    justSaved: Boolean,
    strings: Strings,
    onSharePdf: () -> Unit,
    onClose: () -> Unit
) {
    // The check pops rather than fades, exactly as the bill's does: it is the
    // same moment, and the overshoot is what makes it read as confirmation.
    var popped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (popped) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "check"
    )
    LaunchedEffect(document) { popped = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Nocturne.bg)
            .systemBarsPadding()
            .padding(horizontal = Metrics.screenPadding)
            .padding(top = 24.dp, bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (justSaved) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .scale(scale)
                        .border(1.dp, Nocturne.accent, CircleShape)
                ) {
                    Glyph(Icon.confirm, size = 18.dp, tint = Nocturne.accent)
                }
                Spacer(Modifier.width(11.dp))
            }
            Text(
                if (justSaved) strings.paymentSaved else document.docType,
                style = NocturneType.inter(18.0, FontWeight.Medium),
                color = Nocturne.text
            )
        }

        Spacer(Modifier.height(18.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {

            // The letterhead, as it prints: the shop, then what the paper is.
            Text(document.shopName, style = NocturneType.inter(15.0, FontWeight.Medium), color = Nocturne.text)
            if (document.shopAddressLines.isNotEmpty()) {
                Text(
                    document.shopAddressLines.joinToString(", "),
                    style = NocturneType.meta,
                    color = Nocturne.neutral500
                )
            }
            Spacer(Modifier.height(16.dp))

            Fact(document.addressedToLabel, document.partyName, document.partyLines.joinToString(" · "))
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { Fact(document.receiptLabel, document.receiptValue) }
                Box(Modifier.weight(1f)) { Fact(document.dateLabel, document.dateValue) }
            }

            Spacer(Modifier.height(16.dp))

            // The figure the page exists to state, set alone so it is the one
            // thing read across a counter.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Metrics.cardRadius))
                    .background(Nocturne.surface)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    document.amountLabel.uppercase(),
                    style = NocturneType.meta,
                    color = Nocturne.accent400
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    document.amountValue,
                    style = NocturneType.inter(26.0, FontWeight.SemiBold),
                    color = Nocturne.accent
                )
            }

            document.noteLabel?.let { label ->
                Spacer(Modifier.height(14.dp))
                Text(label.uppercase(), style = NocturneType.meta, color = Nocturne.neutral500)
                Text(
                    document.noteValue.orEmpty(),
                    style = NocturneType.inter(13.5),
                    color = Nocturne.text
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                document.summaryTitle.uppercase(),
                style = NocturneType.meta,
                color = Nocturne.neutral500
            )
            Spacer(Modifier.height(8.dp))
            for (row in document.summaryRows) {
                SummaryLine(row.label, if (row.deduction) "(${row.value})" else row.value)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    document.closingLabel,
                    style = NocturneType.inter(13.5, FontWeight.Medium),
                    color = Nocturne.text,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    document.closingValue,
                    style = NocturneType.inter(17.0, FontWeight.SemiBold),
                    color = Nocturne.accent
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(document.footnote, style = NocturneType.meta, color = Nocturne.neutral500)
        }

        // Print it now or never: the customer is still at the counter, and this
        // is the moment they want the slip.
        Spacer(Modifier.height(14.dp))
        SecondaryButton(
            strings.sharePdf,
            onClick = onSharePdf,
            fullWidth = true,
            height = 44.dp,
            fontSize = 13.5,
            leading = Icon.share
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(
            strings.done,
            onClick = onClose,
            fullWidth = true,
            height = 46.dp
        )
    }
}

/** A label with the fact under it, as the printed page sets its two boxed facts. */
@Composable
private fun Fact(label: String, value: String, detail: String? = null) {
    Column {
        Text(label.uppercase(), style = NocturneType.meta, color = Nocturne.accent400)
        Spacer(Modifier.height(2.dp))
        Text(value, style = NocturneType.inter(14.0, FontWeight.Medium), color = Nocturne.text)
        if (!detail.isNullOrBlank()) {
            Text(detail, style = NocturneType.meta, color = Nocturne.neutral500)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = NocturneType.inter(13.0),
            color = Nocturne.neutral400,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = NocturneType.inter(13.0), color = Nocturne.text)
    }
}
