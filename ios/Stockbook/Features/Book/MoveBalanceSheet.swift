import SwiftUI

/// Moving what one account owes onto another, both of them real.
///
/// The same two steps as `MergeSheet` — pick the other account, then agree to
/// the figures — because it is the same kind of act seen from the same place.
/// What it is *not* is the same operation: nothing is absorbed, both accounts
/// survive, and every invoice stays where it was issued. The sheet says so in as
/// many words, because the two entry points sit next to each other on the party
/// screen and the wrong one would be very hard to notice afterwards.
struct MoveBalanceSheet: View {
    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

    /// The account the balance leaves, by key.
    let fromKey: String
    let isSupplier: Bool
    let onClose: () -> Void

    @State private var query = ""
    @State private var chosen: String?
    @State private var amount = ""
    @State private var why = ""

    /// Name and outstanding figure, reduced inside each branch. `Customer` and
    /// `Supplier` share no supertype, so a value holding either would be typed as
    /// `Any` and neither field would resolve.
    private var leaving: (name: String, owed: Double)? {
        if isSupplier {
            guard let supplier = store.supplier(key: fromKey) else { return nil }
            return (supplier.name, supplier.owed)
        }
        guard let customer = store.customer(key: fromKey) else { return nil }
        return (customer.name, customer.owed)
    }

    private var candidates: [(key: String, name: String, owed: Double)] {
        if isSupplier {
            let all = query.isBlank ? store.suppliers() : store.suppliers(matching: query)
            return all.filter { $0.key != fromKey }.map { (key: $0.key, name: $0.name, owed: $0.owed) }
        }
        let all = query.isBlank ? store.customers() : store.customers(matching: query)
        return all.filter { $0.key != fromKey }.map { (key: $0.key, name: $0.name, owed: $0.owed) }
    }

    private var target: (key: String, name: String, owed: Double)? {
        guard let chosen else { return nil }
        return candidates.first { $0.key == chosen }
    }

    private var typed: Double { Money.parse(amount) ?? 0 }

    private var canSave: Bool { typed > 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let leaving {
                SheetHeader(
                    title: Loc.moveABalance,
                    subtitle: leaving.name,
                    // Back to the list rather than out of the sheet, as the merge
                    // sheet does: the owner who picked the wrong name wants the
                    // other name.
                    onClose: target == nil ? onClose : { chosen = nil }
                )

                if let target {
                    confirmation(leaving: leaving, target: target)
                } else {
                    picker(leaving: leaving)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func picker(leaving: (name: String, owed: Double)) -> some View {
        Text(Loc.moveBalanceChoose(leaving.name))
            .nocturneText(.meta)
            .padding(.bottom, 12)

        NocturneField(placeholder: Loc.search, text: $query, height: 40, fontSize: 13.5)
            .padding(.bottom, Metrics.rowGap)

        if candidates.isEmpty {
            Text(query.isBlank ? Loc.nobodyToMoveTo : Loc.nobodyMatches)
                .nocturneText(.meta)
                .padding(.vertical, 14)
        } else {
            VStack(spacing: Metrics.rowGap) {
                ForEach(candidates, id: \.key) { row in
                    HStack(spacing: 9) {
                        Glyph(Icon.customer, size: 13)
                            .foregroundStyle(Nocturne.neutral500)
                        VStack(alignment: .leading, spacing: 0) {
                            Text(row.name).nocturneText(.rowPrimary).lineLimit(1)
                            Text(Money.text(row.owed, in: currency))
                                .font(NocturneType.inter(11.5))
                                .foregroundStyle(row.owed > 0 ? Nocturne.accent400 : Nocturne.neutral500)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        Nocturne.surface,
                        in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous)
                    )
                    .contentShape(Rectangle())
                    .onTapGesture {
                        chosen = row.key
                        // The whole outstanding figure is what a consolidation
                        // moves, so it is offered rather than typed — and still
                        // editable, because a part transfer is a real thing.
                        if amount.isBlank, leaving.owed > 0 {
                            amount = Money.amount(leaving.owed, in: currency)
                        }
                    }
                }
            }
            .padding(.bottom, 4)
        }
    }

    @ViewBuilder
    private func confirmation(
        leaving: (name: String, owed: Double),
        target: (key: String, name: String, owed: Double)
    ) -> some View {
        NocturneField.number(
            label: Loc.amountToMove,
            placeholder: Money.amount(0, in: currency),
            text: $amount,
            prefix: currency.symbol.trimmed
        )
        .padding(.bottom, 12)

        NocturneField(label: Loc.whyMoved, placeholder: Loc.whyMovedExample, text: $why)
            .padding(.bottom, 14)

        VStack(alignment: .leading, spacing: 4) {
            // Both sides, because the owner is agreeing to two figures and only
            // one of them is on the screen they came from.
            afterLine(Loc.willOweAfter(leaving.name), leaving.owed - typed)
            afterLine(Loc.willOweAfter(target.name), target.owed + typed)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))

        Text(Loc.movedBalanceIsAnEntry)
            .nocturneText(.meta)
            .padding(.top, 8)

        Button(canSave ? Loc.moveABalance : Loc.enterAnAmountToMove) {
            guard canSave else { return }
            let moved = store.transferBalance(
                fromKey: fromKey,
                intoKey: target.key,
                amount: typed,
                isSupplier: isSupplier,
                note: why
            )
            if moved != nil { onClose() }
        }
        .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 46, fontSize: 15))
        .disabled(!canSave)
        .padding(.top, 14)

        Button(Loc.cancel) { chosen = nil }
            .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 40, fontSize: 13))
            .padding(.top, 8)
    }

    /// One side of "what each will owe", with the figure carrying its own sign.
    @ViewBuilder
    private func afterLine(_ label: String, _ owed: Double) -> some View {
        HStack(alignment: .bottom, spacing: 8) {
            Text(label).nocturneText(.meta).lineLimit(1)
            Spacer(minLength: 0)
            Text(Money.signed(owed, in: currency))
                .nocturneText(.rowPrimary)
                // A balance moved past zero is money held in advance, which is
                // allowed and worth looking different from a debt.
                .foregroundStyle(owed >= 0 ? Nocturne.accent400 : Nocturne.neutral500)
        }
    }
}
