import SwiftUI

/// Joining two accounts for one firm, from the one that will be the one to go.
///
/// Two steps in one sheet: pick who to keep, then agree to the figures. The
/// second step is the point of the whole sheet. A merge rewrites history and
/// there is no undo in this app, so the owner is shown what will move and what
/// the survivor will owe **before** they agree, rather than being left to notice
/// a changed balance afterwards.
///
/// The list is everybody else, searchable, because the account you are joining
/// to is by definition one you already have and may be nowhere near the top of
/// any order.
struct MergeSheet: View {
    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

    /// The account being merged away, by key.
    let fromKey: String
    let isSupplier: Bool
    /// Closed without joining anything. The account behind is still there.
    let onClose: () -> Void
    /// Joined. Told apart from `onClose` because the account the owner was
    /// looking at is the one that has gone — the screen behind this sheet is now
    /// about somebody who no longer exists, and has to be closed with it.
    let onMerged: () -> Void

    @State private var query = ""
    @State private var chosen: String?

    private var fromName: String? {
        isSupplier ? store.supplier(key: fromKey)?.name : store.customer(key: fromKey)?.name
    }

    /// Everybody except the one going. Read straight off the store rather than
    /// snapshotted: it is `@Observable`, so the confirmation cannot go stale.
    private var candidates: [(key: String, name: String)] {
        let found: [(String, String)]
        if isSupplier {
            let all = query.isBlank ? store.suppliers() : store.suppliers(matching: query)
            found = all.map { ($0.key, $0.name) }
        } else {
            let all = query.isBlank ? store.customers() : store.customers(matching: query)
            found = all.map { ($0.key, $0.name) }
        }
        return found.filter { $0.0 != fromKey }.map { (key: $0.0, name: $0.1) }
    }

    private var preview: MergePreview? {
        guard let chosen else { return nil }
        return isSupplier
            ? store.previewSupplierMerge(from: fromKey, into: chosen)
            : store.previewCustomerMerge(from: fromKey, into: chosen)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let fromName {
                SheetHeader(
                    title: Loc.mergeAccounts,
                    subtitle: fromName,
                    // Back to the list rather than out of the sheet: the owner
                    // who picked the wrong name wants the other name, not to
                    // start again.
                    onClose: preview == nil ? onClose : { chosen = nil }
                )

                if let preview {
                    confirmation(preview)
                } else {
                    picker(fromName)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func picker(_ fromName: String) -> some View {
        Text(Loc.mergeChoose(fromName))
            .nocturneText(.meta)
            .padding(.bottom, 12)

        NocturneField(placeholder: Loc.search, text: $query, height: 40, fontSize: 13.5)
            .padding(.bottom, Metrics.rowGap)

        if candidates.isEmpty {
            Text(query.isBlank ? Loc.nobodyToMergeWith : Loc.nobodyMatches)
                .nocturneText(.meta)
                .padding(.vertical, 14)
        } else {
            VStack(spacing: Metrics.rowGap) {
                ForEach(candidates, id: \.key) { row in
                    HStack(spacing: 9) {
                        Glyph(Icon.customer, size: 13)
                            .foregroundStyle(Nocturne.neutral500)
                        Text(row.name)
                            .nocturneText(.rowPrimary)
                            .lineLimit(1)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 11)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                    .contentShape(Rectangle())
                    .onTapGesture { chosen = row.key }
                }
            }
            .padding(.bottom, 4)
        }
    }

    @ViewBuilder
    private func confirmation(_ preview: MergePreview) -> some View {
        Text(Loc.mergeConfirm(from: preview.from, into: preview.into))
            .nocturneText(.rowPrimary)
            .padding(.bottom, 10)

        VStack(alignment: .leading, spacing: 0) {
            // Only the lines with something on them. A supplier has no bills and
            // no credit notes, and a zero drawn for each would read as a figure
            // somebody checked.
            let moving = [
                preview.bills > 0 ? Loc.billsMoving(preview.bills) : nil,
                preview.deliveries > 0 ? Loc.deliveriesMoving(preview.deliveries) : nil,
                preview.payments > 0 ? Loc.paymentsMoving(preview.payments) : nil,
                preview.creditNotes > 0 ? Loc.creditNotesMoving(preview.creditNotes) : nil,
            ].compactMap { $0 }

            ForEach(moving, id: \.self) { line in
                Text(line)
                    .font(NocturneType.inter(11.5))
                    .foregroundStyle(Nocturne.text)
            }

            // The figure the owner is really agreeing to, so it is the one drawn
            // largest and last.
            HStack(alignment: .bottom, spacing: 8) {
                Text(Loc.willOwe(preview.into))
                    .nocturneText(.meta)
                    .lineLimit(1)
                Spacer(minLength: 0)
                Text(Money.text(preview.owed, in: currency))
                    .nocturneText(.rowPrimary)
                    .foregroundStyle(Nocturne.accent400)
            }
            .padding(.top, moving.isEmpty ? 0 : 8)

            Text(Loc.willBeGone(preview.from))
                .nocturneText(.meta)
                .padding(.top, 4)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))

        Text(Loc.mergeCannotBeUndone)
            .nocturneText(.meta)
            .padding(.top, 8)

        Button(Loc.mergeAccounts) {
            guard let chosen else { return }
            let done = isSupplier
                ? store.mergeSupplier(from: fromKey, into: chosen)
                : store.mergeCustomer(from: fromKey, into: chosen)
            if done { onMerged() } else { onClose() }
        }
        .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 46, fontSize: 15))
        .padding(.top, 14)

        Button(Loc.cancel) { chosen = nil }
            .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 40, fontSize: 13))
            .padding(.top, 8)
    }
}
