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
/// **Everybody, not the top five.** The list used to stop at five names with an
/// "All" underneath, because every row was built whether or not it was on screen
/// — two hundred customers meant two hundred rows composed to show five. A
/// `LazyVStack` builds only what is visible, which removes the reason for the cap
/// rather than moving it: no page size, no button, just the list.
///
/// Nothing to fetch, either. The roster is already in memory — `customers()` is a
/// walk over a snapshot — so there is no page to load, only rows to draw, and
/// drawing them on demand is the whole of it.
///
/// Biggest debt first, which is the order `customers()` hands them over in.
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

    /// Above how many names a search box earns its place.
    ///
    /// Not a page size — the list shows everybody. This is only the point at
    /// which scrolling stops being the quickest way to find one.
    private static let searchable = 5

    private var searching: Bool { !query.isBlank }

    private var shown: [PartyRow] { searching ? search(query) : rows }

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
                if rows.count > Self.searchable {
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
                    // Lazy: the enclosing `ScrollView` builds a row when it comes
                    // into view and not before, which is what lets this show the
                    // whole roster.
                    LazyVStack(spacing: Metrics.rowGap) {
                        ForEach(shown) { row in
                            Button {
                                onOpen(row.key)
                            } label: {
                                PartyRowView(row: row)
                            }
                            .buttonStyle(.plain)
                        }
                    }
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
