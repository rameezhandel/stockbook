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
    /// The payment being corrected, or nil to take a new one.
    var editing: Payment?
    /// Hands back the slip for the payment, and whether it was **just taken**.
    ///
    /// That flag cannot be worked out from the router afterwards: both ways in
    /// close this sheet on their way out, so by the time the receipt is on
    /// screen the two look identical. Only the caller knows which it was.
    let onReceipt: (PaymentReceipt, Bool) -> Void
    let onClose: () -> Void

    var body: some View {
        PaymentSheet(
            name: customer.name,
            key: editing?.id.uuidString ?? customer.key,
            owed: customer.owed,
            dateLabel: Loc.receivedOn,
            footnote: Loc.paymentNotAgainstOneBill,
            existing: editing.map {
                PaymentSheet.Existing(amount: $0.amount, note: $0.note, no: $0.paymentNo, date: $0.receivedAt)
            },
            // Never counting the one being corrected, or opening 008455 to fix
            // its amount would be told 008455 is taken — by itself.
            clashDate: { store.paymentWithNo($0, exceptId: editing?.id)?.receivedAt },
            onSave: { amount, at, note, no in
                let saved: Payment?
                if let editing {
                    saved = store.updatePayment(
                        id: editing.id, amount: amount, receivedAt: at, note: note, paymentNo: no
                    )
                } else {
                    saved = store.recordPayment(
                        customerKey: customer.key,
                        amount: amount,
                        receivedAt: at,
                        note: note,
                        paymentNo: no
                    )
                }
                // Read back through the store rather than built from what was
                // typed: the balance on the slip has to be the balance the
                // statement will show, and only the store knows what that is.
                if let saved, let slip = store.receipt(forPayment: saved.id) {
                    onReceipt(slip, true)
                }
            },
            onViewReceipt: editing.map { payment in
                {
                    if let slip = store.receipt(forPayment: payment.id) { onReceipt(slip, false) }
                }
            },
            onDelete: editing.map { payment in { store.deletePayment(id: payment.id) } },
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
    var editing: SupplierPayment?
    let onReceipt: (PaymentReceipt, Bool) -> Void
    let onClose: () -> Void

    var body: some View {
        PaymentSheet(
            name: supplier.name,
            key: editing?.id.uuidString ?? supplier.key,
            owed: supplier.owed,
            dateLabel: Loc.paidOn,
            footnote: Loc.paymentNotAgainstOnePurchase,
            existing: editing.map {
                PaymentSheet.Existing(amount: $0.amount, note: $0.note, no: $0.paymentNo, date: $0.paidAt)
            },
            clashDate: { store.supplierPaymentWithNo($0, exceptId: editing?.id)?.paidAt },
            onSave: { amount, at, note, no in
                let saved: SupplierPayment?
                if let editing {
                    saved = store.updateSupplierPayment(
                        id: editing.id, amount: amount, paidAt: at, note: note, paymentNo: no
                    )
                } else {
                    saved = store.recordSupplierPayment(
                        supplierKey: supplier.key,
                        amount: amount,
                        paidAt: at,
                        note: note,
                        paymentNo: no
                    )
                }
                if let saved, let slip = store.receipt(forSupplierPayment: saved.id) {
                    onReceipt(slip, true)
                }
            },
            onViewReceipt: editing.map { payment in
                {
                    if let slip = store.receipt(forSupplierPayment: payment.id) { onReceipt(slip, false) }
                }
            },
            onDelete: editing.map { payment in { store.deleteSupplierPayment(id: payment.id) } },
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
    /// What the sheet was opened on, when it was opened on something.
    struct Existing {
        let amount: Double
        let note: String?
        let no: String?
        let date: Date
    }

    let existing: Existing?
    /// When the receipt book already holds this number, the day it was used.
    let clashDate: (String) -> Date?
    let onSave: (Double, Date, String, String) -> Void
    /// Opens the slip for a payment that already exists.
    ///
    /// Present only when correcting, for the same reason `onDelete` is: a
    /// payment being taken has no receipt until it is saved, and the save opens
    /// one anyway. This is the way back to it — a customer who has lost their
    /// copy is the whole reason to print a second.
    let onViewReceipt: (() -> Void)?
    /// Present only when correcting: a payment being taken has nothing to remove.
    let onDelete: (() -> Void)?
    let onClose: () -> Void

    @State private var paymentNo = ""
    @State private var amount = ""
    @State private var receivedAt = Date.now
    @State private var note = ""
    @State private var confirmingRemoval = false
    @State private var seeded = false

    private var typed: Double { Money.parse(amount) ?? 0 }
    private var clash: Date? { clashDate(paymentNo) }
    private var canSave: Bool { typed > 0 && !paymentNo.isBlank && clash == nil }

    /// What will still be owed once this is saved. Shown live, because it is the
    /// number the owner is actually trying to reach — usually zero.
    ///
    /// A payment being corrected is already inside `owed`, so its old amount is
    /// added back before the new one comes off — otherwise correcting 300 to 350
    /// would read as though 650 had been paid.
    private var remaining: Double { owed + (existing?.amount ?? 0) - typed }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(
                title: onDelete == nil ? Loc.recordAPayment : Loc.correctAPayment,
                subtitle: name,
                onClose: onClose
            )

            paperRow
                .padding(.bottom, clash == nil ? 12 : 6)

            if let clash {
                Text(Loc.paymentNoAlreadyUsed(date: Loc.longDate(clash)))
                    .nocturneText(.meta)
                    .foregroundStyle(Nocturne.accent400)
                    .padding(.bottom, 12)
            }

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

            Button(saveTitle) { save() }
                .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 48, fontSize: 15))
                .disabled(!canSave)

            if let onViewReceipt {
                Button(Loc.viewReceipt) {
                    onViewReceipt()
                    onClose()
                }
                .buttonStyle(GhostButtonStyle(fontSize: 12.5))
                .frame(maxWidth: .infinity)
                .padding(.top, 6)
            }

            // Removal lives inside the correction, exactly as the credit note's
            // does. Two taps, because it takes a figure out of somebody's account.
            if let onDelete {
                Button(confirmingRemoval ? Loc.tapAgainToRemove : Loc.deleteThisPayment) {
                    if confirmingRemoval {
                        onDelete()
                        onClose()
                    } else {
                        withAnimation(Metrics.quick) { confirmingRemoval = true }
                    }
                }
                .buttonStyle(GhostButtonStyle(
                    fontSize: 12.5,
                    tint: confirmingRemoval ? Nocturne.accent400 : Nocturne.neutral500
                ))
                .frame(maxWidth: .infinity)
                .padding(.top, 6)
            }
        }
        .keyboardDoneButton()
        .onAppear(perform: seed)
    }

    /// Fills the form from what it was opened on, once.
    ///
    /// Guarded, because `onAppear` fires again when the sheet is re-presented and
    /// would otherwise throw away whatever the owner had just typed.
    private func seed() {
        guard !seeded, let existing else { return }
        seeded = true
        paymentNo = existing.no ?? ""
        amount = Money.amount(existing.amount, in: currency)
        note = existing.note ?? ""
        receivedAt = existing.date
    }

    /// The paper's number and the day it was written, side by side — the credit
    /// note's own first row, for the same reason: they describe the document
    /// rather than the money.
    private var paperRow: some View {
        HStack(alignment: .bottom, spacing: 8) {
            NocturneField(
                label: Loc.paymentNoField,
                placeholder: Loc.paymentNoHint,
                text: $paymentNo,
                height: 40,
                // Opens on digits, with letters a tap away. A receipt book is
                // numbered "1024" far more often than "A-1024", so a full
                // alphabetic keyboard makes the common case the slow one —
                // and a pure number pad would make the other case impossible.
                keyboard: .numbersAndPunctuation,
                isRequiredAndEmpty: paymentNo.isBlank,
                fontSize: 13.5,
                identifier: "payment.no"
            )
            NocturneDateField(
                label: dateLabel,
                date: $receivedAt,
                identifier: "payment.receivedAt"
            )
        }
    }

    private var saveTitle: String {
        if clash != nil { return Loc.changeThePaymentNo }
        if paymentNo.isBlank { return Loc.enterPaymentNumber }
        if typed <= 0 { return Loc.enterAnAmount }
        return onDelete == nil ? Loc.savePayment : Loc.saveChanges
    }

    private var remainingText: String {
        if remaining > 0 { return Money.text(remaining, in: currency) }
        if remaining < 0 { return Loc.inAdvance(Money.text(-remaining, in: currency)) }
        return Loc.settledUp
    }

    private func save() {
        guard canSave else { return }
        onSave(typed, receivedAt, note, paymentNo)
        onClose()
    }
}
