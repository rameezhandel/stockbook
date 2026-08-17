package com.stockbook.app.feature.customers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneField
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.PrimaryButton
import com.stockbook.app.design.SheetHeader
import com.stockbook.core.model.Currency
import com.stockbook.core.model.Customer
import com.stockbook.core.money.Money
import com.stockbook.core.store.StockbookStore
import com.stockbook.core.text.Strings

/**
 * Adding a customer, or correcting one.
 *
 * The same sheet for both, the way the product editor is, because the fields are
 * identical and the difference is one title and whether Remove is there.
 *
 * Only the name is required. A shop that knows a name and nothing else still has
 * a customer, and demanding a phone number would teach the owner to type nonsense
 * into the box.
 */
@Composable
fun CustomerEditorSheet(
    /** Null when adding. */
    existing: Customer?,
    store: StockbookStore,
    currency: Currency,
    strings: Strings,
    onClose: () -> Unit
) {
    var name by remember(existing?.key) { mutableStateOf(existing?.name ?: "") }
    var phone by remember(existing?.key) { mutableStateOf(existing?.phone ?: "") }
    var place by remember(existing?.key) { mutableStateOf(existing?.place ?: "") }
    var opening by remember(existing?.key) {
        mutableStateOf(
            // Blank rather than "0" for a customer who owes nothing from before:
            // a zero in the box reads as a figure somebody checked.
            existing?.openingBalance?.takeIf { it > 0 }?.let { Money.amount(it, currency) } ?: ""
        )
    }
    var confirmingRemoval by remember(existing?.key) { mutableStateOf(false) }

    val isEditing = existing?.isOnRoster == true
    val canSave = name.isNotBlank()

    Column(modifier = Modifier.fillMaxWidth()) {
        SheetHeader(
            title = if (isEditing) strings.editCustomer else strings.newCustomer,
            subtitle = existing?.let {
                if (it.hasHistory) it.meta(currency, strings) else strings.noBillsYet
            },
            onClose = onClose
        )

        NocturneField(
            value = name,
            onValueChange = { name = it },
            label = strings.customerName,
            placeholder = strings.customerNameExample,
            height = Metrics.tallInputHeight,
            isRequiredAndEmpty = name.isBlank(),
            fontSize = 15.0,
            imeAction = ImeAction.Next
        )
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            NocturneField(
                value = phone,
                onValueChange = { phone = it },
                label = strings.customerPhone,
                placeholder = strings.optionalField,
                numeric = true,
                imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            NocturneField(
                value = place,
                onValueChange = { place = it },
                label = strings.customerPlace,
                placeholder = strings.optionalField,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))

        NocturneField(
            value = opening,
            onValueChange = { opening = it },
            label = strings.openingBalanceField,
            placeholder = strings.optionalField,
            numeric = true,
            prefix = currency.symbol.trim()
        )
        Text(
            strings.openingBalanceNote,
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(16.dp))

        PrimaryButton(
            title = if (canSave) strings.saveCustomer else strings.enterCustomerNameFirst,
            onClick = {
                if (!canSave) return@PrimaryButton
                val openingValue = Money.parse(opening) ?: 0.0
                if (isEditing && existing != null) {
                    store.updateCustomer(existing.key, name, phone, place, openingValue)
                } else {
                    // A name that has only ever appeared on bills lands here too:
                    // adding it is what puts it on the roster, and `addCustomer`
                    // keys it the same way, so their history comes with them.
                    store.addCustomer(name, phone, place, openingValue)
                }
                onClose()
            },
            enabled = canSave,
            fullWidth = true,
            height = 48.dp,
            fontSize = 15.0
        )

        if (isEditing && existing != null) {
            // Removal is a second tap, and the note is why: "remove" beside
            // somebody's name reads like deleting them and their history, and it
            // does not do that.
            Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
                GhostButton(
                    if (confirmingRemoval) strings.tapAgainToRemove else strings.removeFromCustomers,
                    onClick = {
                        if (confirmingRemoval) {
                            store.removeCustomer(existing.key)
                            onClose()
                        } else {
                            confirmingRemoval = true
                        }
                    },
                    fontSize = 12.0,
                    tint = Nocturne.neutral500
                )
                Text(
                    strings.removeCustomerNote,
                    style = NocturneType.meta,
                    color = Nocturne.neutral500,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
