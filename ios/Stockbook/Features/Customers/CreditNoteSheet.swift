import SwiftUI

/// One line of returned goods while the note is being typed.
///
/// Both numbers are held as **text**, so a half-typed value ("1." or "") is
/// representable and the field is never re-formatted under the thumb that is
/// typing into it — the lesson the bill's own line card records.
@Observable
private final class ReturnLine: Identifiable {
    let id = UUID()
    let productUID: UUID
    let name: String
    var qtyText: String = "1"
    var priceText: String

    init(productUID: UUID, name: String, priceText: String) {
        self.productUID = productUID
        self.name = name
        self.priceText = priceText
    }

    var qty: Int { max(1, Int(qtyText.trimmed) ?? 1) }
    var price: Double { Money.parse(priceText) ?? 0 }
    var lineTotal: Double { Double(qty) * price }
}

/// Issuing — or correcting — a credit note against one customer.
///
/// The payment sheet's shape, because the two acts rhyme: an amount, a date, a
/// note, and a balance that has to come down by it. What differs is the one
/// thing the footnote says out loud — **no money changes hands** — and the
/// optional list of goods, which is what decides whether the shelf moves.
///
/// The number is required and typed. Its own series, so it is checked against
/// other credit notes and not against the bill book.
struct CreditNoteSheet: View {
    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

    let customer: Customer
    var editing: CreditNote?
    let onClose: () -> Void

    @State private var noteNo = ""
    @State private var amount = ""
    @State private var reason = ""
    @State private var issuedAt = Date.now
    @State private var lines: [ReturnLine] = []
    @State private var productQuery = ""
    @State private var adding = false
    @State private var seeded = false

    private var total: Double {
        lines.isEmpty ? (Money.parse(amount) ?? 0) : lines.reduce(0) { $0 + $1.lineTotal }
    }

    /// The note already carrying this number, never counting the one being
    /// corrected — or opening 00130 to fix its date would be told 00130 is
    /// already taken, by itself.
    private var clash: CreditNote? {
        store.creditNoteWithNo(noteNo, exceptId: editing?.id)
    }

    private var canSave: Bool { !noteNo.isBlank && total > 0 && clash == nil }

    /// What will still be owed once this is saved.
    private var remaining: Double { customer.owed - total }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(
                title: editing == nil ? Loc.issueACreditNote : Loc.editCreditNote,
                subtitle: customer.name,
                onClose: onClose
            )

            paperRow

            if let clash {
                Text(Loc.creditNoteAlreadyUsed(date: Loc.longDate(clash.issuedAt)))
                    .nocturneText(.meta)
                    .foregroundStyle(Nocturne.accent400)
                    .padding(.top, 6)
            }

            // The figure, however it was arrived at. Never both at once: a typed
            // amount beside a line sum is two answers to one question.
            Group {
                if lines.isEmpty {
                    NocturneField.number(
                        label: Loc.amountCredited,
                        text: $amount,
                        height: Metrics.tallInputHeight,
                        isRequiredAndEmpty: total <= 0,
                        emphasis: .sellingPrice,
                        prefix: currency.symbol.trimmed,
                        fontSize: 17,
                        identifier: "creditNote.amount"
                    )
                } else {
                    returnedTotal
                }
            }
            .padding(.top, 12)

            ForEach(lines) { line in
                ReturnedLineCard(line: line, onRemove: { remove(line) })
                    .padding(.top, 8)
            }

            // Quiet, and last: most credit notes here are a figure agreed across
            // a counter rather than a pile of goods coming back.
            Group {
                if adding {
                    ReturnedItemPicker(typed: $productQuery, onChoose: add)
                } else {
                    Button(lines.isEmpty ? Loc.addReturnedItems : Loc.addAnotherItem) { adding = true }
                        .buttonStyle(GhostButtonStyle(fontSize: 12.5, horizontalPadding: 0))
                }
            }
            .padding(.top, 10)

            HStack(spacing: 6) {
                Text(Loc.closingBalance).nocturneText(.meta)
                Spacer(minLength: 8)
                Text(remainingText)
                    .font(NocturneType.inter(13, .medium))
                    .foregroundStyle(remaining > 0 ? Nocturne.accent400 : Nocturne.neutral400)
                    .contentTransition(.numericText())
            }
            .motion(Motion.numbers, value: remaining)
            .padding(.top, 14)

            NocturneField(
                label: Loc.creditReason,
                placeholder: Loc.creditReasonExample,
                text: $reason,
                identifier: "creditNote.reason"
            )
            .padding(.top, 14)

            Text(Loc.creditNoteNotAPayment)
                .nocturneText(.meta)
                .padding(.top, 8)
                .padding(.bottom, 16)

            Button(saveTitle) { save() }
                .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 48, fontSize: 15))
                .disabled(!canSave)

            if let editing {
                Button(Loc.removeCreditNote) {
                    store.deleteCreditNote(id: editing.id)
                    onClose()
                }
                .buttonStyle(.ghostMuted)
                .frame(maxWidth: .infinity)
                .padding(.top, 6)
            }
        }
        .keyboardDoneButton()
        .onAppear(perform: seed)
    }

    /// The paper's number and the day it was written, side by side — the bill
    /// form's own first row, for the same reason: they describe the document
    /// rather than the money.
    private var paperRow: some View {
        HStack(alignment: .bottom, spacing: 8) {
            NocturneField(
                label: Loc.creditNoteNo,
                placeholder: Loc.creditNoteNoHint,
                text: $noteNo,
                height: 40,
                // Opens on digits, with letters a tap away. A note book is
                // numbered "1024" far more often than "A-1024", so a full
                // alphabetic keyboard makes the common case the slow one —
                // and a pure number pad would make the other case impossible.
                keyboard: .numbersAndPunctuation,
                isRequiredAndEmpty: noteNo.isBlank,
                fontSize: 13.5,
                identifier: "creditNote.no"
            )
            VStack(alignment: .leading, spacing: 5) {
                Text(Loc.creditedOn).nocturneText(.fieldLabel)
                DatePicker("", selection: $issuedAt, displayedComponents: .date)
                    .labelsHidden()
                    .datePickerStyle(.compact)
                    .font(NocturneType.inter(13))
                    .tint(Nocturne.accent)
                    .frame(height: 40)
            }
        }
    }

    /// The total once goods have been named, and the way back to typing a
    /// figure. Takes the amount box's place rather than sitting beside it,
    /// exactly as the bill's does.
    private var returnedTotal: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .lastTextBaseline) {
                Text(Loc.itemsReturned)
                    .font(NocturneType.inter(13))
                    .foregroundStyle(Nocturne.neutral500)
                Spacer()
                Text(Money.text(total, in: currency))
                    .nocturneText(.bigNumber(26))
                    .rollingNumber(total)
            }
            HStack {
                Text(Loc.fromItems(lines.count)).nocturneText(.meta)
                Spacer()
                Button(Loc.removeItems) { lines.removeAll() }
                    .buttonStyle(GhostButtonStyle(fontSize: 12))
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .hairline(Nocturne.accent700, radius: Metrics.controlRadius)
    }

    private var remainingText: String {
        if remaining > 0 { return Money.text(remaining, in: currency) }
        if remaining < 0 { return Loc.inAdvance(Money.text(-remaining, in: currency)) }
        return Loc.settledUp
    }

    private var saveTitle: String {
        // A number on two notes is two documents the shop cannot tell apart
        // later, so this one is a refusal rather than a warning.
        if clash != nil { return Loc.changeTheCreditNoteNo }
        if noteNo.isBlank { return Loc.enterCreditNoteNumber }
        if total <= 0 { return Loc.enterAnAmount }
        return Loc.saveCreditNote
    }

    // MARK: Actions

    /// Fills the form from the note being corrected. Once: re-running it would
    /// throw away whatever the owner has typed since.
    private func seed() {
        guard !seeded else { return }
        seeded = true
        guard let editing else { return }
        noteNo = editing.noteNo ?? ""
        reason = editing.reason ?? ""
        issuedAt = editing.issuedAt
        // Only where there is nothing to add up. A typed amount sitting behind
        // lines is the second answer this form refuses to hold, as on the bill.
        amount = editing.lines.isEmpty ? Money.amount(editing.total, in: currency) : ""
        lines = editing.lines.compactMap { line in
            guard let uid = line.productUID else { return nil }
            let made = ReturnLine(
                productUID: uid,
                name: line.name,
                priceText: Money.amount(line.price, in: currency)
            )
            made.qtyText = String(line.qty)
            return made
        }
    }

    private func add(_ product: Product) {
        if let existing = lines.first(where: { $0.productUID == product.uid }) {
            existing.qtyText = String(existing.qty + 1)
        } else {
            lines.append(
                ReturnLine(
                    productUID: product.uid,
                    name: product.name,
                    priceText: Money.amount(product.price, in: currency)
                )
            )
        }
        productQuery = ""
        adding = false
    }

    private func remove(_ line: ReturnLine) {
        lines.removeAll { $0.id == line.id }
    }

    private func save() {
        guard canSave else { return }
        let drafts = lines.map { DraftLine(productUID: $0.productUID, qty: $0.qty, price: $0.price) }
        if let editing {
            store.updateCreditNote(
                id: editing.id,
                customerKey: customer.key,
                lines: drafts,
                amount: Money.parse(amount),
                noteNo: noteNo,
                reason: reason,
                issuedAt: issuedAt
            )
        } else {
            store.addCreditNote(
                customerKey: customer.key,
                lines: drafts,
                amount: Money.parse(amount),
                noteNo: noteNo,
                reason: reason,
                issuedAt: issuedAt
            )
        }
        onClose()
    }
}

// MARK: - Line

/// One returned line: how many, at what they were charged.
private struct ReturnedLineCard: View {
    @Bindable var line: ReturnLine
    let onRemove: () -> Void

    @Environment(\.currency) private var currency

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text(line.name)
                    .nocturneText(.rowPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .lineLimit(1)
                Text(Money.text(line.lineTotal, in: currency))
                    .font(NocturneType.inter(15))
                    .rollingNumber(line.lineTotal)
                Button(action: onRemove) {
                    Glyph(Icon.delete, size: 15)
                        .foregroundStyle(Nocturne.neutral500)
                        .minimumTouchTarget()
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Loc.remove(line.name))
            }
            .padding(.bottom, 10)

            HStack(spacing: 8) {
                NocturneField.number(
                    label: Loc.howMany,
                    text: $line.qtyText,
                    height: Metrics.compactControlHeight,
                    fontSize: 13.5
                )
                NocturneField.number(
                    label: Loc.paidPerPiece,
                    text: $line.priceText,
                    height: Metrics.compactControlHeight,
                    prefix: currency.symbol.trimmed,
                    fontSize: 13.5
                )
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
    }
}

// MARK: - Picker

/// Which goods came back.
///
/// The delivery sheet's product picker with the selling price shown rather than
/// the buying one: this list exists to credit what somebody was charged.
private struct ReturnedItemPicker: View {
    @Binding var typed: String
    let onChoose: (Product) -> Void

    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

    private var matches: [Product] {
        store.products(matching: typed)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            NocturneField(
                label: Loc.itemsReturned,
                placeholder: Loc.search,
                text: $typed,
                identifier: "creditNote.product"
            )

            if !matches.isEmpty {
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(matches) { candidate in
                            Button { onChoose(candidate) } label: {
                                HStack(spacing: 9) {
                                    Glyph(Icon.items, size: 13)
                                        .foregroundStyle(Nocturne.neutral500)
                                    Text(candidate.name)
                                        .font(NocturneType.inter(13.5))
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .lineLimit(1)
                                    Text(Money.text(candidate.price, in: currency))
                                        .font(NocturneType.inter(13))
                                        .foregroundStyle(Nocturne.accent400)
                                }
                                .padding(.horizontal, 11)
                                .frame(height: 35)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .frame(height: min(CGFloat(matches.count) * 35, 150))
                .scrollBounceBehavior(.basedOnSize)
                .padding(.vertical, 3)
                .frame(maxWidth: .infinity)
                .background(Nocturne.surface)
                .clipShape(RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                .hairline(Nocturne.accent, radius: Metrics.controlRadius)
            }
        }
    }
}
