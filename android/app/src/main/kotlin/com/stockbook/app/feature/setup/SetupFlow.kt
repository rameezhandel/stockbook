package com.stockbook.app.feature.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.CurrencyDropdown
import com.stockbook.app.design.FieldEmphasis
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Kicker
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.RequiredMarking
import com.stockbook.app.design.SecondaryButton
import com.stockbook.app.design.card
import com.stockbook.core.model.Currency
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings
import com.stockbook.core.text.firstName

private class Draft(val name: String) {
    var stock by mutableStateOf("")
    var cost by mutableStateOf("")
    var price by mutableStateOf("")

    val isComplete: Boolean
        get() = StockbookStore.isProductDraftComplete(name, stock, cost, price)
}

/**
 * First-run setup: name and currency, then product names, then stock and prices.
 *
 * **Nothing here is persisted until "Open the shop."** The whole flow is a draft
 * held in this composable — a half-finished setup is not a shop, and abandoning
 * it mid-way should leave no trace to reconcile later.
 */
@Composable
fun SetupFlow(store: StockbookStore, strings: Strings) {
    var step by remember { mutableStateOf(0) }
    var ownerName by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(Currency.default) }
    var draftName by remember { mutableStateOf("") }
    val drafts = remember { mutableStateListOf<Draft>() }

    // The exact set the owner asked for — a lock shop's four common lines.
    val suggestions = remember { listOf("Lever Handle Lock", "Cisa lock", "Padlock", "Deadbolt") }
    val available = suggestions.filter { s -> drafts.none { it.name.equals(s, ignoreCase = true) } }

    val incomplete = drafts.count { !it.isComplete }
    val isComplete = drafts.isNotEmpty() && incomplete == 0

    fun addDraft(name: String) {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) { draftName = ""; return }
        // Duplicates are ignored in silence — tapping a capsule twice is not a
        // mistake worth interrupting somebody for.
        if (drafts.none { it.name.equals(cleaned, ignoreCase = true) }) drafts.add(Draft(cleaned))
        draftName = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Nocturne.bg)
            .statusBarsPadding()
            .padding(horizontal = Metrics.screenPadding)
            .padding(top = 24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index <= step) Nocturne.accent else Nocturne.neutral800)
                )
                if (index < 2) Spacer(Modifier.width(6.dp))
            }
        }

        when (step) {
            0 -> Column(modifier = Modifier.weight(1f)) {
                Text(strings.welcomeToStockbook, style = NocturneType.setupTitle, color = Nocturne.text)
                Text(
                    strings.welcomeBody,
                    style = NocturneType.body,
                    color = Nocturne.neutral500,
                    modifier = Modifier.padding(top = 5.dp, bottom = 18.dp)
                )
                NocturneField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = strings.yourName,
                    placeholder = strings.businessOwnerName,
                    height = Metrics.tallInputHeight,
                    isRequiredAndEmpty = ownerName.isBlank(),
                    fontSize = 15.0
                )
                Spacer(Modifier.height(14.dp))
                // Asked here rather than beside the prices, because by step 3
                // the owner is typing numbers and should know which ones.
                CurrencyDropdown(
                    selected = currency,
                    onSelect = { currency = it },
                    strings = strings,
                    label = strings.currencySection
                )
                Text(
                    strings.setupCurrencyNote,
                    style = NocturneType.meta,
                    color = Nocturne.neutral500,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            1 -> Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Kicker(
                    if (ownerName.firstName.isEmpty()) strings.yourShelves else strings.greeting(ownerName.firstName),
                    tint = Nocturne.accent,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(strings.whatDoYouStock, style = NocturneType.setupTitle, color = Nocturne.text)
                Text(
                    strings.stockNamesBody,
                    style = NocturneType.body,
                    color = Nocturne.neutral500,
                    modifier = Modifier.padding(top = 5.dp, bottom = 16.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    NocturneField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        placeholder = strings.productNameExample,
                        height = Metrics.tallInputHeight,
                        fontSize = 15.0,
                        onImeAction = { addDraft(draftName) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton("+", onClick = { addDraft(draftName) }, height = Metrics.tallInputHeight)
                }

                if (available.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Kicker(strings.commonHardwareLines, modifier = Modifier.padding(bottom = 8.dp))
                    available.forEach { name ->
                        SecondaryButton(
                            "+ $name",
                            onClick = { addDraft(name) },
                            height = 34.dp,
                            fontSize = 12.0,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Kicker(
                    if (drafts.isEmpty()) strings.nothingAddedYetKicker else strings.addedCount(drafts.size),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                drafts.toList().forEach { draft ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Metrics.rowGap)
                            .card(Metrics.controlRadius)
                            .padding(start = 13.dp, end = 4.dp)
                    ) {
                        Text(
                            draft.name,
                            style = NocturneType.rowPrimary,
                            color = Nocturne.text,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            Icon.close,
                            onClick = { drafts.remove(draft) },
                            size = 16.dp,
                            tint = Nocturne.neutral500,
                            contentDescription = strings.remove(draft.name)
                        )
                    }
                }
            }

            else -> Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(strings.stockAndPrices, style = NocturneType.setupTitle, color = Nocturne.text)
                Text(
                    strings.stockAndPricesBody,
                    style = NocturneType.body,
                    color = Nocturne.neutral500,
                    modifier = Modifier.padding(top = 5.dp, bottom = 16.dp)
                )

                drafts.forEach { draft ->
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).card().padding(12.dp)) {
                        Text(draft.name, style = NocturneType.rowPrimary, color = Nocturne.text)
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            NocturneField(
                                value = draft.stock,
                                onValueChange = { draft.stock = it },
                                label = strings.inStock,
                                numeric = true,
                                isRequiredAndEmpty = draft.stock.isBlank(),
                                requiredMarking = RequiredMarking.AFTER_TOUCH,
                                imeAction = ImeAction.Next,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            NocturneField(
                                value = draft.cost,
                                onValueChange = { draft.cost = it },
                                label = strings.youPay,
                                numeric = true,
                                isRequiredAndEmpty = draft.cost.isBlank(),
                                requiredMarking = RequiredMarking.AFTER_TOUCH,
                                imeAction = ImeAction.Next,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            NocturneField(
                                value = draft.price,
                                onValueChange = { draft.price = it },
                                label = strings.youSell,
                                numeric = true,
                                isRequiredAndEmpty = (Money.parse(draft.price) ?: 0.0) <= 0,
                                requiredMarking = RequiredMarking.AFTER_TOUCH,
                                emphasis = FieldEmphasis.SELLING_PRICE,
                                imeAction = ImeAction.Next,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // The footer is a sibling of the content, never floating over it.
        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            if (step == 2) {
                // The gate explains itself rather than leaving a dead button.
                Text(
                    if (isComplete) strings.allSet else strings.stillNeedPrices(incomplete),
                    style = NocturneType.inter(11.5),
                    color = if (isComplete) Nocturne.accent400 else Nocturne.neutral500,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                if (step > 0) {
                    SecondaryButton(strings.back, onClick = { step -= 1 }, height = 48.dp)
                    Spacer(Modifier.width(8.dp))
                }
                when (step) {
                    0 -> PrimaryButton(
                        strings.continueAction,
                        onClick = { if (ownerName.isNotBlank()) step = 1 },
                        enabled = ownerName.isNotBlank(),
                        fullWidth = true,
                        height = 48.dp,
                        fontSize = 15.0,
                        modifier = Modifier.weight(1f)
                    )
                    1 -> PrimaryButton(
                        strings.nextStockAndPrices,
                        onClick = { if (drafts.isNotEmpty()) step = 2 },
                        enabled = drafts.isNotEmpty(),
                        fullWidth = true,
                        height = 48.dp,
                        fontSize = 15.0,
                        modifier = Modifier.weight(1f)
                    )
                    else -> PrimaryButton(
                        strings.openTheShop,
                        onClick = {
                            if (!isComplete) return@PrimaryButton
                            store.setOwnerName(ownerName)
                            store.setCurrency(currency)
                            drafts.forEach { draft ->
                                store.addProduct(
                                    name = draft.name,
                                    stock = draft.stock.trim().toIntOrNull()
                                        ?: (Money.parse(draft.stock) ?: 0.0).toInt(),
                                    cost = Money.parse(draft.cost) ?: 0.0,
                                    price = Money.parse(draft.price) ?: 0.0
                                )
                            }
                            store.completeSetup()
                        },
                        enabled = isComplete,
                        fullWidth = true,
                        height = 48.dp,
                        fontSize = 15.0,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
