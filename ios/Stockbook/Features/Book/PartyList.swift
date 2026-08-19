import SwiftUI

/// One person on the list, flattened out of `Customer` or `Supplier`.
///
/// The two are the same shape pointed in opposite directions, but they are
/// separate types in the domain and giving them a shared protocol there would be
/// inventing a concept the arithmetic does not have. Flattening at the edge,
/// where the only question is what to draw, costs one struct and keeps the
/// domain honest. The Kotlin twin does exactly this.
struct PartyRow: Identifiable {
    let key: String
    let name: String
    let contact: String?
    let owed: Double

    var id: String { key }
}

/// The directory at the top of each half of the Book: who the shop deals with,
/// and the way into any one of them.
///
/// This replaced a dropdown. A customer has a balance, a statement, payments and
/// credit notes against them, and until now the only way to reach any of it was
/// to pick a name out of a filter on a list of bills — which meant that people,
/// the thing half this app is about, were the one entity with no screen. The
/// evidence that this was wrong is that Today needed a banner *and* a
/// purpose-built sheet to get from "Ahmed still owes" to Ahmed.
///
/// Capped rather than complete. A shop with two hundred customers should not
/// have to scroll past all of them to reach today's bills, so the list shows the
/// few who owe most — `customers()` hands them over in that order — and the rest
/// are behind the search box or the toggle.
struct PartyList: View {
    let title: String
    let rows: [PartyRow]
    /// Everybody, by name, matching what has been typed. Read only while searching.
    let search: (String) -> [PartyRow]
    let addTitle: String
    let emptyMessage: String
    let onAdd: () -> Void
    let onOpen: (String) -> Void

    @State private var query = ""
    @State private var expanded = false

    /// How many fit above the documents before the list stops being a summary.
    private static let visible = 5

    private var searching: Bool { !query.isBlank }

    private var shown: [PartyRow] {
        if searching { return search(query) }
        return expanded ? rows : Array(rows.prefix(Self.visible))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Kicker(title)
                Spacer(minLength: 6)
                Button(addTitle) { onAdd() }
                    .buttonStyle(GhostButtonStyle(fontSize: 12))
            }
            .padding(.bottom, 8)

            if rows.isEmpty {
                Text(emptyMessage).nocturneText(.meta)
            } else {
                // Offered only once the list is longer than it is worth reading
                // through. A shop with four customers does not need a way to
                // search four names.
                if rows.count > Self.visible {
                    NocturneField(
                        placeholder: Loc.search,
                        text: $query,
                        height: 40,
                        fontSize: 13.5
                    )
                    .padding(.bottom, Metrics.rowGap)
                }

                if shown.isEmpty {
                    Text(Loc.nobodyMatches).nocturneText(.meta)
                } else {
                    VStack(spacing: Metrics.rowGap) {
                        ForEach(shown) { row in
                            Button {
                                onOpen(row.key)
                            } label: {
                                PartyRowView(row: row)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .motion(Motion.list, value: shown.count)
                }

                // Nothing to expand while searching: the search already showed
                // everybody who answers to what was typed.
                if !searching, !expanded, rows.count > Self.visible {
                    Button(Loc.all) { expanded = true }
                        .buttonStyle(GhostButtonStyle(fontSize: 12))
                        .padding(.top, Metrics.rowGap)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// One name and what stands against it.
///
/// The balance is the row's whole reason to be read, so it is what the eye lands
/// on: accent where money is outstanding, neutral where it is not. "Settled up"
/// rather than a blank — the absence of a figure reads as a row that failed to
/// load.
private struct PartyRowView: View {
    let row: PartyRow

    @Environment(\.currency) private var currency

    var body: some View {
        HStack(spacing: 9) {
            Glyph(Icon.customer, size: 14)
                .foregroundStyle(Nocturne.neutral500)
            VStack(alignment: .leading, spacing: 1) {
                Text(row.name).nocturneText(.rowPrimary).lineLimit(1)
                if let contact = row.contact {
                    Text(contact).nocturneText(.meta).lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Text(balanceText)
                .font(NocturneType.inter(13))
                .foregroundStyle(row.owed > 0 ? Nocturne.accent400 : Nocturne.neutral500)
                .lineLimit(1)

            Glyph(Icon.openRow, size: 12)
                .foregroundStyle(Nocturne.neutral500)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.rowRadius, style: .continuous))
        .contentShape(Rectangle())
    }

    private var balanceText: String {
        if row.owed > 0 { return Money.text(row.owed, in: currency) }
        if row.owed < 0 { return Loc.inAdvance(Money.text(-row.owed, in: currency)) }
        return Loc.settledUp
    }
}

extension Customer {
    /// As the directory draws it.
    var directoryRow: PartyRow {
        PartyRow(key: key, name: name, contact: contactLine, owed: owed)
    }

    /// `0500 111 222 · Al Khobar`, or nothing for somebody who is only a name.
    var contactLine: String? {
        let details = [phone, place].compactMap { $0 }
        return details.isEmpty ? nil : details.joined(separator: " · ")
    }
}

extension Supplier {
    var directoryRow: PartyRow {
        PartyRow(key: key, name: name, contact: contactLine, owed: owed)
    }

    var contactLine: String? {
        let details = [phone, place].compactMap { $0 }
        return details.isEmpty ? nil : details.joined(separator: " · ")
    }
}
