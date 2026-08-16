package com.stockbook.app.feature.sell

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
import com.stockbook.app.feature.bills.BillTemplate
import com.stockbook.core.model.Bill
import com.stockbook.core.model.ShopState
import com.stockbook.core.text.Strings

/**
 * What the owner turns to face the customer. Full-screen and opaque — the bill
 * is saved, the stock has moved, and there is nothing left to edit here.
 */
@Composable
fun ReceiptOverlay(
    bill: Bill,
    state: ShopState,
    strings: Strings,
    onSeeBills: () -> Unit,
    onNextCustomer: () -> Unit
) {
    // The check pops rather than fades: it is the one moment in the app worth a
    // flourish, and the overshoot is what makes it read as confirmation.
    var popped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (popped) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "check"
    )
    LaunchedEffect(bill.number) { popped = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Nocturne.bg)
            .systemBarsPadding()
            .padding(horizontal = Metrics.screenPadding)
            .padding(top = 24.dp, bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
            Text(
                strings.billSaved,
                style = NocturneType.inter(18.0, FontWeight.Medium),
                color = Nocturne.text
            )
        }

        Spacer(Modifier.height(18.dp))

        // The same document the Bills tab opens, so what is confirmed here and
        // what is looked up later cannot drift apart.
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            BillTemplate(
                bill = bill,
                currency = state.settings.currency,
                strings = strings,
                shopName = state.settings.ownerName
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(
                strings.seeBills,
                onClick = onSeeBills,
                fullWidth = true,
                height = 46.dp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            // The cart was already cleared on save, so this lands on an empty
            // new bill — the next customer is usually already waiting.
            PrimaryButton(
                strings.nextCustomer,
                onClick = onNextCustomer,
                fullWidth = true,
                height = 46.dp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
