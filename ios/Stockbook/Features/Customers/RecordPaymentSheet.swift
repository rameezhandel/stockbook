import SwiftUI

/// Money a customer has just handed over against what they owe.
///
/// Deliberately not attached to a bill. A shop like this is settled by somebody
/// putting cash on the counter against their account, not against invoice #7, and
/// making the owner pick a bill would be asking them to maintain a fiction.
struct RecordPaymentSheet: View {
    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

    let customer: Customer
    let onClose: () -> Void

    var body: some View {
        PaymentSheet(
            name: customer.name,
            key: customer.key,
            owed: customer.owed,
            dateLabel: Loc.receivedOn,
            footnote: Loc.paymentNotAgainstOneBill,
            onSave: { amount, at, note in
                store.recordPayment(customerKey: customer.key, amount: amount, receivedAt: at, note: note)
            },
            onClose: onClose
        )
    }
}

/// The same sheet, for money going the other way.
///
/// One body, two entry points, exactly as with the editor: what a payment *is*
/// does not change with its direction — an amount, a date, a note, and a balance
/// that has to come down by it.
struct PaySupplierSheet: View {
    @Environment(StockbookStore.self) private var store

    let supplier: Supplier
    let onClose: () -> Void

    var body: some View {
        PaymentSheet(
            name: supplier.name,
            key: supplier.key,
            owed: supplier.owed,
            dateLabel: Loc.paidOn,
            footnote: Loc.paymentNotAgainstOnePurchase,
            onSave: { amount, at, note in
                store.recordSupplierPayment(supplierKey: supplier.key, amount: amount, paidAt: at, note: note)
            },
            onClose: onClose
        )
    }
}

private struct PaymentSheet: View {
    @Environment(\.currency) private var currency

    let name: String
    let key: String
    let owed: Double
    let dateLabel: String
    let footnote: String
    let onSave: (Double, Date, String) -> Void
    let onClose: () -> Void

    @State private var amount = ""
    @State private var receivedAt = Date.now
    @State private var note = ""

    private var typed: Double { Money.parse(amount) ?? 0 }
    private var canSave: Bool { typed > 0 }

    /// What will still be owed once this is saved. Shown live, because it is the
    /// number the owner is actually trying to reach — usually zero.
    private var remaining: Double { owed - typed }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(
                title: Loc.recordAPayment,
                subtitle: name,
                onClose: onClose
            )

            NocturneField.number(
                label: Loc.amountReceived,
                text: $amount,
                height: Metrics.tallInputHeight,
                isRequiredAndEmpty: amount.isBlank,
                emphasis: .sellingPrice,
                prefix: currency.symbol.trimmed,
                fontSize: 17,
                identifier: "payment.amount"
            )
            .padding(.bottom, 6)

            // Owed before, and what is left after. A running total the owner can
            // check against the cash in their hand before committing.
            HStack(spacing: 6) {
                Text(Loc.closingBalance).nocturneText(.meta)
                Spacer(minLength: 8)
                Text(remainingText)
                    .font(NocturneType.inter(13, .medium))
                    .foregroundStyle(remaining > 0 ? Nocturne.accent400 : Nocturne.neutral400)
                    .contentTransition(.numericText())
            }
            .motion(Motion.numbers, value: remaining)
            .padding(.bottom, 14)

            DatePicker(dateLabel, selection: $receivedAt, displayedComponents: .date)
                .datePickerStyle(.compact)
                .font(NocturneType.inter(13))
                .tint(Nocturne.accent)
                .padding(.bottom, 14)

            NocturneField(
                label: Loc.paymentNote,
                placeholder: Loc.paymentNoteExample,
                text: $note,
                identifier: "payment.note"
            )
            .padding(.bottom, 8)

            Text(footnote)
                .nocturneText(.meta)
                .padding(.bottom, 16)

            Button(canSave ? Loc.savePayment : Loc.enterAnAmount) { save() }
                .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 48, fontSize: 15))
                .disabled(!canSave)
        }
        .keyboardDoneButton()
    }

    private var remainingText: String {
        if remaining > 0 { return Money.text(remaining, in: currency) }
        if remaining < 0 { return Loc.inAdvance(Money.text(-remaining, in: currency)) }
        return Loc.settledUp
    }

    private func save() {
        guard canSave else { return }
        onSave(typed, receivedAt, note)
        onClose()
    }
}
