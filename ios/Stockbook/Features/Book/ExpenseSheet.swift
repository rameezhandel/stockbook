import SwiftUI

/// Writing down money the owner spent, and correcting it later.
///
/// Two fields and a date. There is no number to type, unlike every other
/// document in this app: an invoice, a receipt and a credit note all have a
/// number because there is a slip in a drawer carrying the same one, and there
/// is no such slip behind a tank of petrol.
///
/// Removing is a plain ghost button with no confirmation, where removing a bill
/// asks twice. That is not carelessness about the owner's data — it is that
/// nothing else moves. A deleted bill puts stock back and frees a number; this
/// is a line leaving a private list, and it can be typed again in ten seconds.
struct ExpenseSheet: View {
    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

    /// The expense being corrected, or nil for a new one.
    var editing: Expense?
    let onClose: () -> Void

    @State private var amount = ""
    @State private var note = ""
    @State private var spentAt = Date.now

    private var typed: Double { Money.parse(amount) ?? 0 }
    private var canSave: Bool { typed > 0 && !note.isBlank }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(
                title: editing == nil ? Loc.newExpense : Loc.editExpense,
                onClose: onClose
            )

            NocturneField(
                label: Loc.expenseWhatFor,
                placeholder: Loc.expenseWhatForHint,
                text: $note,
                isRequiredAndEmpty: note.isBlank,
                identifier: "expense.note"
            )
            .padding(.bottom, 10)

            HStack(alignment: .bottom, spacing: 8) {
                NocturneField(
                    label: Loc.amountField,
                    placeholder: "0",
                    text: $amount,
                    height: 40,
                    keyboard: .decimalPad,
                    isRequiredAndEmpty: typed <= 0,
                    prefix: currency.symbol.trimmed,
                    fontSize: 13.5,
                    identifier: "expense.amount"
                )
                NocturneDateField(
                    label: Loc.expenseSpentOn,
                    date: $spentAt,
                    identifier: "expense.spentAt"
                )
            }

            Text(Loc.expensesArePrivate)
                .nocturneText(.meta)
                .padding(.top, 8)

            // The button says what is missing rather than going dead and silent —
            // the same rule every other sheet here follows.
            Button(saveTitle) { save() }
                .buttonStyle(PrimaryButtonStyle(fullWidth: true))
                .disabled(!canSave)
                .padding(.top, 14)

            if let editing {
                Button(Loc.removeExpense) {
                    store.deleteExpense(id: editing.id)
                    onClose()
                }
                .buttonStyle(GhostButtonStyle(tint: Nocturne.neutral500))
                .frame(maxWidth: .infinity)
                .padding(.top, 6)

                Text(Loc.removeExpenseNote)
                    .nocturneText(.meta)
                    .padding(.top, 4)
            }
        }
        .keyboardDoneButton()
        .onAppear(perform: fill)
    }

    private var saveTitle: String {
        if note.isBlank { return Loc.enterWhatItWasFor }
        if typed <= 0 { return Loc.enterAnAmount }
        return editing == nil ? Loc.saveExpense : Loc.saveChanges
    }

    /// Filled here rather than in the property initialisers, which cannot see
    /// `editing` — the same shape every other editing sheet in this app uses.
    private func fill() {
        guard let editing else { return }
        amount = Money.amount(editing.amount, in: currency)
        note = editing.note
        spentAt = editing.spentAt
    }

    private func save() {
        guard canSave else { return }
        if let editing {
            store.updateExpense(id: editing.id, amount: typed, note: note, spentAt: spentAt)
        } else {
            store.addExpense(amount: typed, note: note, spentAt: spentAt)
        }
        onClose()
    }
}
