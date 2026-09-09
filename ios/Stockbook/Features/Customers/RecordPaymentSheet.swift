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
    /// The loan being corrected, where the sheet was opened on one.
    var editingLoan: Loan?
    /// Hands back the slip for the payment, and whether it was **just taken**.
    ///
    /// That flag cannot be worked out from the router afterwards: both ways in
    /// close this sheet on their way out, so by the time the receipt is on
    /// screen the two look identical. Only the caller knows which it was.
    let onReceipt: (PaymentReceipt, Bool) -> Void
    let onClose: () -> Void

    /// Which way the money is going.
    ///
    /// Money in is where the sheet opens, because taking money is what a shop
    /// does fifty times for every once it lends any. A sheet opened to correct an
    /// existing record starts on that record's own direction and stays there —
    /// the two are different types, and a payment cannot become a loan by tapping
    /// a pill any more than it can by having a sign put on it.
    @State private var giving = false

    private var correcting: Bool { editing != nil || editingLoan != nil }

    var body: some View {
        PaymentSheet(
            name: customer.name,
            key: editing?.id.uuidString ?? editingLoan?.id.uuidString ?? "\(customer.key)-\(giving)",
            owed: customer.owed,
            dateLabel: Loc.receivedOn,
            footnote: giving ? Loc.loanAddsToWhatTheyOwe : Loc.paymentNotAgainstOneBill,
            amountLabel: giving ? Loc.amountLent : Loc.amountReceived,
            // A loan comes out of no numbered book, and money handed over adds to
            // what is owed rather than taking off it. Those two are the whole of
            // the difference on this sheet.
            numbered: !giving,
            sign: giving ? 1 : -1,
            header: correcting ? nil : AnyView(DirectionPills(giving: $giving)),
            title: {
                if giving { return correcting ? Loc.correctALoan : Loc.recordALoan }
                return correcting ? Loc.correctAPayment : Loc.recordAPayment
            }(),
            saveWording: giving ? Loc.saveLoan : Loc.savePayment,
            existing: existing,
            // Never counting the one being corrected, or opening 008455 to fix
            // its amount would be told 008455 is taken — by itself.
            clashDate: { store.paymentWithNo($0, exceptId: editing?.id)?.receivedAt },
            onSave: { amount, at, note, no in
                if let editingLoan {
                    _ = store.updateLoan(id: editingLoan.id, amount: amount, lentAt: at, note: note)
                } else if giving {
                    _ = store.recordLoan(
                        customerKey: customer.key, amount: amount, lentAt: at, note: note
                    )
                } else {
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
                }
            },
            // A loan has no slip. There is no numbered receipt behind it to
            // reprint, and inventing one would be the app claiming paperwork the
            // shop has not got.
            onViewReceipt: editing.map { payment in
                {
                    if let slip = store.receipt(forPayment: payment.id) { onReceipt(slip, false) }
                }
            },
            onDelete: onDelete,
            onClose: onClose
        )
        // The sheet is built once per customer, so the direction has to be put
        // back where a correction opens it — otherwise a loan corrected after a
        // payment would open on the payment's pills.
        .onAppear { giving = editingLoan != nil }
    }

    private var existing: PaymentSheet.Existing? {
        if let editingLoan {
            return PaymentSheet.Existing(
                amount: editingLoan.amount, note: editingLoan.note, no: nil, date: editingLoan.lentAt
            )
        }
        return editing.map {
            PaymentSheet.Existing(
                amount: $0.amount, note: $0.note, no: $0.paymentNo, date: $0.receivedAt
            )
        }
    }

    private var onDelete: (() -> Void)? {
        if let editingLoan { return { store.deleteLoan(id: editingLoan.id) } }
        return editing.map { payment in { store.deletePayment(id: payment.id) } }
    }
}

/// Received or given, above everything else on the sheet.
///
/// At the top because it changes what every field below it means — the amount's
/// label, whether a receipt number is asked for, and which way the balance line
/// moves. A control that rewrites the form under it belongs before the form.
///
/// Only when writing something new. Correcting a record cannot change which of
/// the two it is: money in and money out are separate types, and turning one into
/// the other would be a delete and a write, not an edit.
private struct DirectionPills: View {
    @Binding var giving: Bool

    var body: some View {
        HStack(spacing: 6) {
            ChoicePill(title: Loc.moneyReceived, icon: Icon.confirm, selected: !giving) {
                giving = false
            }
            ChoicePill(title: Loc.moneyGiven, icon: Icon.expenses, selected: giving) {
                giving = true
            }
        }
        .padding(.bottom, 14)
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
            // Money the shop hands a supplier settles what it owes them, so it
            // comes off that balance exactly as a customer's payment comes off
            // theirs — the default sign, and no direction to choose between.
            amountLabel: Loc.amountPaid,
            title: editing == nil ? Loc.recordAPayment : Loc.correctAPayment,
            saveWording: Loc.savePayment,
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
    /// What the amount box asks for, which is the one word that differs between
    /// taking money and handing it over.
    let amountLabel: String
    /// Whether a number is asked for and required.
    ///
    /// False for a loan, and it is not a detail: an invoice, a receipt and a
    /// credit note each come out of a numbered book the shop keeps, and a hand of
    /// cash across a counter comes out of no book at all. Asking for a number
    /// there would be the app inventing paperwork the shop does not have — and
    /// `canSave` would then never let the owner past it.
    var numbered = true
    /// Which way the money moves: −1 takes it off what is owed, +1 puts it on.
    ///
    /// The whole of what a loan changes in the arithmetic here. Everything else
    /// on this sheet — the balance line, the clamp, the correction that takes the
    /// old figure back out before applying the new — reads the same either way.
    var sign = -1.0
    /// A pair of pills above everything, where the sheet records both directions.
    var header: AnyView?
    /// What the sheet calls itself and what its button promises.
    ///
    /// Passed in rather than derived, because the sheet is shared and the words
    /// are not. A page headed "Record a payment" whose button says "Save payment"
    /// is lying about the record it is about to write, and the owner has no other
    /// way to tell which of the two they are making.
    let title: String
    let saveWording: String
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
    private var canSave: Bool { typed > 0 && (!numbered || !paymentNo.isBlank) && clash == nil }

    /// What will still be owed once this is saved. Shown live, because it is the
    /// number the owner is actually trying to reach — usually zero.
    ///
    /// A payment being corrected is already inside `owed`, so its old amount is
    /// taken back out before the new one is applied — otherwise correcting 300 to
    /// 350 would read as though 650 had been paid.
    ///
    /// `sign` is what makes this serve a loan too: money handed over adds to the
    /// balance rather than taking off it, and every other line here is unchanged.
    private var remaining: Double { owed - sign * (existing?.amount ?? 0) + sign * typed }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(
                title: title,
                subtitle: name,
                onClose: onClose
            )

            header

            paperRow
                .padding(.bottom, clash == nil ? 12 : 6)

            if let clash {
                Text(Loc.paymentNoAlreadyUsed(date: Loc.longDate(clash)))
                    .nocturneText(.meta)
                    .foregroundStyle(Nocturne.accent400)
                    .padding(.bottom, 12)
            }

            NocturneField.number(
                label: amountLabel,
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
            // A loan has no paper, so the date stands alone across the full width
            // rather than beside an empty half — a gap where a field was reads as
            // a field that failed to draw.
            if numbered {
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
            }
            NocturneDateField(
                label: dateLabel,
                date: $receivedAt,
                identifier: "payment.receivedAt"
            )
        }
    }

    private var saveTitle: String {
        if clash != nil { return Loc.changeThePaymentNo }
        // Gated on `numbered`, exactly as `canSave` is. Without that the button
        // read "Enter a receipt number" on a loan and saved anyway when tapped —
        // the label and the button disagreeing about the same condition.
        if numbered, paymentNo.isBlank { return Loc.enterPaymentNumber }
        if typed <= 0 { return Loc.enterAnAmount }
        return onDelete == nil ? saveWording : Loc.saveChanges
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
