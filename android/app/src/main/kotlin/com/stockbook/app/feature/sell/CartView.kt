package com.stockbook.app.feature.sell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.FieldEmphasis
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.card
import com.stockbook.app.design.hairline
import com.stockbook.core.model.Currency
import com.stockbook.core.model.ShopState
import com.stockbook.core.money.Money
import com.stockbook.core.text.Strings

/**
 * The bill being built. The most important screen in the app: it is what the
 * owner is looking at while a customer waits.
 */
@Composable
fun CartView(
    cart: Cart,
    state: ShopState,
    currency: Currency,
    strings: Strings,
    onBrowse: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = Metrics.screenPadding,
                end = Metrics.screenPadding,
                bottom = 10.dp
            )
        ) {
            items(cart.lines, key = { it.productUid }) { line ->
                CartLineCard(
                    line = line,
                    stock = state.products.firstOrNull { it.uid == line.productUid }?.stock ?: 0,
                    currency = currency,
                    strings = strings,
                    cart = cart,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item {
                SecondaryButton(
                    strings.addAnotherItem,
                    onClick = onBrowse,
                    fullWidth = true,
                    height = 44.dp,
                    fontSize = 13.5,
                    leading = Icon.add,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Footer(cart = cart, currency = currency, strings = strings, onSave = onSave)
    }
}

@Composable
private fun Footer(cart: Cart, currency: Currency, strings: Strings, onSave: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Nocturne.surface)) {
        Box(Modifier.fillMaxWidth().height(Metrics.hairline).background(Nocturne.neutral800))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Metrics.screenPadding)
                .padding(top = 12.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            NocturneField(
                value = cart.customer,
                onValueChange = { cart.customer = it },
                placeholder = strings.customerName,
                height = 40.dp,
                isRequiredAndEmpty = cart.customer.isBlank()
            )
            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                PaymentPill(
                    strings.paidInFull,
                    Icon.confirm,
                    selected = cart.payMode == PayMode.FULL,
                    onClick = { cart.payMode = PayMode.FULL },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                PaymentPill(
                    strings.partPayment,
                    Icon.edit,
                    selected = cart.payMode == PayMode.PART,
                    onClick = { cart.payMode = PayMode.PART },
                    modifier = Modifier.weight(1f)
                )
            }

            if (cart.payMode == PayMode.PART) {
                Spacer(Modifier.height(10.dp))
                NocturneField(
                    value = cart.paidText,
                    onValueChange = { cart.paidText = it },
                    label = strings.paidNow,
                    height = 40.dp,
                    numeric = true
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                Text(
                    strings.total,
                    style = NocturneType.inter(13.0),
                    color = Nocturne.neutral500,
                    modifier = Modifier.weight(1f)
                )
                Text(Money.text(cart.total, currency), style = NocturneType.bigNumber(28.0), color = Nocturne.text)
            }

            if (cart.payMode == PayMode.PART) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(
                        strings.balance,
                        style = NocturneType.inter(12.5),
                        color = Nocturne.neutral500,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        Money.text(cart.balance, currency),
                        style = NocturneType.inter(15.0),
                        color = Nocturne.accent400
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            // Validation is the button's label, never a toast: it says what is
            // missing and stays disabled until it isn't.
            PrimaryButton(
                title = if (cart.canSave) strings.saveBill else strings.enterCustomerName,
                onClick = onSave,
                enabled = cart.canSave,
                fullWidth = true,
                height = 48.dp,
                fontSize = 15.0
            )
        }
    }
}

/** One product on the bill: quantity, live stock, and the editable price. */
@Composable
private fun CartLineCard(
    line: Cart.Line,
    stock: Int,
    currency: Currency,
    strings: Strings,
    cart: Cart,
    modifier: Modifier = Modifier
) {
    var priceText by remember(line.productUid, line.price) {
        mutableStateOf(Money.amount(line.price, currency))
    }

    Column(modifier = modifier.fillMaxWidth().card().padding(horizontal = 12.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                line.name,
                style = NocturneType.rowPrimary,
                color = Nocturne.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(Money.text(line.lineTotal, currency), style = NocturneType.inter(15.0), color = Nocturne.text)
            IconButton(
                Icon.delete,
                onClick = { cart.remove(line.productUid) },
                size = 15.dp,
                tint = Nocturne.neutral500,
                contentDescription = strings.remove(line.name)
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Stepper(
                quantity = line.qty,
                onChange = { cart.setQuantity(it, line.productUid) },
                strings = strings
            )
            Spacer(Modifier.width(8.dp))
            // Wraps rather than truncates: "only 3 in stock" is the warning that
            // stops a wrong bill going out.
            Text(
                if (line.qty > stock) strings.onlyInStock(stock) else strings.piecesInStock(stock),
                style = NocturneType.meta,
                color = Nocturne.neutral500,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            PriceBox(
                symbol = currency.symbol.trim(),
                text = priceText,
                overridden = line.isPriceOverridden,
                onChange = {
                    priceText = it
                    Money.parse(it)?.let { value -> cart.setPrice(value, line.productUid) }
                }
            )
        }

        if (line.isPriceOverridden) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Glyph(Icon.edit, size = 11.dp, tint = Nocturne.accent400)
                Spacer(Modifier.width(5.dp))
                Text(
                    strings.usualPriceNote(Money.text(line.basePrice, currency)),
                    style = NocturneType.inter(11.0),
                    color = Nocturne.accent400,
                    modifier = Modifier.weight(1f)
                )
                GhostButton(
                    strings.reset,
                    onClick = {
                        cart.resetPrice(line.productUid)
                        priceText = Money.amount(line.basePrice, currency)
                    },
                    fontSize = 11.0
                )
            }
        }
    }
}

@Composable
private fun Stepper(quantity: Int, onChange: (Int) -> Unit, strings: Strings) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(Metrics.compactControlHeight)
            .background(Nocturne.bg)
            .hairline(radius = Metrics.controlRadius)
    ) {
        Box(modifier = Modifier.size(34.dp).clickable { onChange(quantity - 1) }, contentAlignment = Alignment.Center) {
            Glyph(Icon.remove, size = 15.dp, tint = Nocturne.text)
        }
        Text(
            "$quantity",
            style = NocturneType.inter(14.0),
            color = Nocturne.text,
            modifier = Modifier.width(34.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Box(modifier = Modifier.size(34.dp).clickable { onChange(quantity + 1) }, contentAlignment = Alignment.Center) {
            Glyph(Icon.add, size = 15.dp, tint = Nocturne.text)
        }
    }
}

@Composable
private fun PriceBox(symbol: String, text: String, overridden: Boolean, onChange: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(112.dp)
    ) {
        NocturneField(
            value = text,
            onValueChange = onChange,
            height = Metrics.compactControlHeight,
            numeric = true,
            prefix = symbol,
            emphasis = if (overridden) FieldEmphasis.CHANGED else FieldEmphasis.NONE,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            fontSize = 13.5
        )
    }
}

@Composable
private fun PaymentPill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(38.dp)
            .hairline(if (selected) Nocturne.accent else Nocturne.neutral800, Metrics.controlRadius)
            .clickable(onClick = onClick)
    ) {
        Glyph(icon, size = 14.dp, tint = if (selected) Nocturne.accent else Nocturne.neutral500)
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = NocturneType.inter(13.0, FontWeight.Medium),
            color = if (selected) Nocturne.accent else Nocturne.neutral500
        )
    }
}
