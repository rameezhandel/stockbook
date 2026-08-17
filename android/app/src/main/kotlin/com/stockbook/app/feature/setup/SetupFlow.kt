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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.CurrencyDropdown
import com.stockbook.app.design.FieldEmphasis
import com.stockbook.app.design.ChoicePill
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
import com.stockbook.core.model.Customer
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
 * First-run setup: name and currency, then product names, then the regulars who
 * buy on account, then stock and prices.
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
    var draftCustomer by remember { mutableStateOf("") }
    var draftOpening by remember { mutableStateOf("") }
    /**
     * Name and what they already owe. Phone and place wait for the editor sheet —
     * but the carried-over balance belongs *here*, because this screen is where a
     * paper book gets migrated and going back to set twenty of them one at a time
     * is how an owner decides the app is not worth it.
     */
    val customerDrafts = remember { mutableStateListOf<CustomerDraft>() }
    val supplierDrafts = remember { mutableStateListOf<CustomerDraft>() }

    /** Which half of step 3 is showing. Customers first: every bill needs one. */
    var addingCustomers by remember { mutableStateOf(true) }

    // The exact set the owner asked for — a lock shop's four common lines.
    val suggestions = remember { listOf("Lever Handle Lock", "Cisa lock", "Padlock", "Deadbolt") }
    val available = suggestions.filter { s -> drafts.none { it.name.equals(s, ignoreCase = true) } }

    val incomplete = drafts.count { !it.isComplete }
    val isComplete = drafts.isNotEmpty() && incomplete == 0

    fun addSupplierDraft(name: String) {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) { draftCustomer = ""; return }
        // Same rule as the products list: a name already there is not a mistake
        // worth interrupting anybody for.
        if (supplierDrafts.none { Customer.key(it.name) == Customer.key(cleaned) }) {
            supplierDrafts.add(CustomerDraft(cleaned, Money.parse(draftOpening) ?: 0.0))
        }
        draftCustomer = ""
        draftOpening = ""
    }

    fun addCustomerDraft(name: String) {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) { draftCustomer = ""; return }
        // Same rule as the product list: a name already there is not a mistake
        // worth interrupting anybody for.
        if (customerDrafts.none { Customer.key(it.name) == Customer.key(cleaned) }) {
            customerDrafts.add(CustomerDraft(cleaned, Money.parse(draftOpening) ?: 0.0))
        }
        draftCustomer = ""
        draftOpening = ""
    }

    fun addDraftHere(name: String) {
        if (addingCustomers) addCustomerDraft(name) else addSupplierDraft(name)
    }

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
            .imePadding()
            .padding(horizontal = Metrics.screenPadding)
            .padding(top = 24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            repeat(TOTAL_STEPS) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index <= step) Nocturne.accent else Nocturne.neutral800)
                )
                if (index < TOTAL_STEPS - 1) Spacer(Modifier.width(6.dp))
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
                // Asked here rather than beside the prices, because by step 4
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

            // Step 3 — who buys on account. The only optional step, and it says
            // so: a setup screen that looks compulsory is where an owner gives up
            // and types nonsense.
            //
            // It sits before the prices because an optional step left until last —
            // between the owner and a finished setup — is one that gets skipped,
            // and this is the one the counter needs: a bill's customer is chosen
            // from this roster, not typed.
            2 -> Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // The two rosters share a step rather than taking one each. They
                // are the same form — a name and what was owed before — and a
                // fifth screen between the owner and an open shop is a screen that
                // gets skipped. The chips are the same ones the Book uses.
                val drafts = if (addingCustomers) customerDrafts else supplierDrafts

                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                    ChoicePill(
                        title = strings.customersTitle,
                        icon = Icon.customer,
                        selected = addingCustomers,
                        onClick = {
                            addingCustomers = true
                            draftCustomer = ""
                            draftOpening = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    ChoicePill(
                        title = strings.suppliersTitle,
                        icon = Icon.addStock,
                        selected = !addingCustomers,
                        onClick = {
                            addingCustomers = false
                            draftCustomer = ""
                            draftOpening = ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    if (addingCustomers) strings.whoDoYouSellTo else strings.whoDoYouBuyFrom,
                    style = NocturneType.setupTitle,
                    color = Nocturne.text
                )
                Text(
                    if (addingCustomers) strings.customersSetupBody else strings.suppliersSetupBody,
                    style = NocturneType.body,
                    color = Nocturne.neutral500,
                    modifier = Modifier.padding(top = 5.dp, bottom = 16.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    NocturneField(
                        value = draftCustomer,
                        onValueChange = { draftCustomer = it },
                        placeholder = if (addingCustomers) strings.customerNameExample
                        else strings.supplierNameExample,
                        height = Metrics.tallInputHeight,
                        fontSize = 15.0,
                        imeAction = ImeAction.Next,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    NocturneField(
                        value = draftOpening,
                        onValueChange = { draftOpening = it },
                        placeholder = strings.openingBalanceField,
                        height = Metrics.tallInputHeight,
                        numeric = true,
                        prefix = currency.symbol.trim(),
                        fontSize = 15.0,
                        onImeAction = { addDraftHere(draftCustomer) },
                        modifier = Modifier.width(120.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton("+", onClick = { addDraftHere(draftCustomer) }, height = Metrics.tallInputHeight)
                }
                Text(
                    if (addingCustomers) strings.openingBalanceNote else strings.supplierOpeningNote,
                    style = NocturneType.meta,
                    color = Nocturne.neutral500,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Kicker(
                    if (drafts.isEmpty()) {
                        if (addingCustomers) strings.noCustomersYetKicker else strings.noSuppliersYetKicker
                    } else {
                        strings.addedCount(drafts.size)
                    },
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                drafts.forEach { draft ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Metrics.rowGap)
                            .card(Metrics.controlRadius)
                            .padding(start = 13.dp, end = 4.dp)
                            .padding(vertical = 4.dp)
                    ) {
                        Glyph(Icon.customer, size = 13.dp, tint = Nocturne.neutral500)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            draft.name,
                            style = NocturneType.rowPrimary,
                            color = Nocturne.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (draft.openingBalance > 0) {
                            Text(
                                strings.owes(Money.text(draft.openingBalance, currency)),
                                style = NocturneType.meta,
                                color = Nocturne.accent400
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        IconButton(
                            Icon.close,
                            onClick = { drafts.remove(draft) },
                            size = 15.dp,
                            tint = Nocturne.neutral500,
                            contentDescription = strings.remove(draft.name)
                        )
                    }
                }
            }

            3 -> Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(strings.stockAndPrices, style = NocturneType.setupTitle, color = Nocturne.text)
                Text(
                    strings.stockAndPricesBody,
                    style = NocturneType.body,
                    color = Nocturne.neutral500,
                    modifier = Modifier.padding(top = 5.dp, bottom = 16.dp)
                )

                drafts.forEachIndexed { index, draft ->
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
                                // The last box of the last product has nowhere to send focus, so
                                // it offers Done and closes the keyboard instead of a Next
                                // that does nothing. `NocturneField` already hides the
                                // keyboard and drops focus on Done.
                                imeAction = if (index == drafts.lastIndex) ImeAction.Done else ImeAction.Next,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // The footer is a sibling of the content, never floating over it.
        Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            if (step == 3) {
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
                        strings.nextCustomers,
                        onClick = { if (drafts.isNotEmpty()) step = 2 },
                        enabled = drafts.isNotEmpty(),
                        fullWidth = true,
                        height = 48.dp,
                        fontSize = 15.0,
                        modifier = Modifier.weight(1f)
                    )
                    // Never disabled. Nobody is required here, and a dead button
                    // on an optional step reads as a wall.
                    2 -> PrimaryButton(
                        strings.nextStockAndPrices,
                        onClick = { step = 3 },
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
                            supplierDrafts.forEach {
                                store.addSupplier(it.name, openingBalance = it.openingBalance)
                            }
                            customerDrafts.forEach {
                                store.addCustomer(it.name, openingBalance = it.openingBalance)
                            }
                            store.completeSetup()
                        },
                        // Prices are the last thing between here and an open shop,
                        // and the line above says which product is still short.
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

/** Name, products, prices, customers. */
private const val TOTAL_STEPS = 4

/** One customer typed during setup: a name, and what they brought over. */
private data class CustomerDraft(val name: String, val openingBalance: Double)
