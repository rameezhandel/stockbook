import SwiftUI

/// The bill being built. The most important screen in the app: it is what the
/// owner is looking at while a customer waits.
struct CartView: View {
    let onBrowse: () -> Void
    let onSave: () -> Void

    @Environment(Cart.self) private var cart
    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency
    @Environment(\.bottomSafeInset) private var bottomInset

    var body: some View {
        @Bindable var cart = cart

        VStack(spacing: 0) {
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(cart.lines) { line in
                        CartLineCard(
                            line: line,
                            stock: cart.stock(for: line, in: store),
                            onQuantity: { cart.setQuantity($0, for: line.id) },
                            onPrice: { cart.setPrice($0, for: line.id) },
                            onResetPrice: { cart.resetPrice(for: line.id) },
                            onRemove: { cart.remove(line.id) }
                        )
                    }

                    Button(action: onBrowse) {
                        Label(Loc.addAnotherItem, systemImage: Icon.browseAll)
                    }
                    .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 44, fontSize: 13.5))
                    .padding(.top, 2)
                }
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 10)
            }

            footer
        }
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .keyboardDoneButton()
    }

    // MARK: Sticky footer

    private var footer: some View {
        @Bindable var cart = cart

        return VStack(spacing: 10) {
            CustomerField(name: $cart.customer)

            HStack(spacing: 6) {
                PaymentPill(
                    title: Loc.paidInFull,
                    icon: Icon.money,
                    selected: cart.payMode == .full
                ) { cart.payMode = .full }

                PaymentPill(
                    title: Loc.partPayment,
                    icon: Icon.partPayment,
                    selected: cart.payMode == .part
                ) { cart.payMode = .part }
            }

            if cart.payMode == .part {
                NocturneField.number(label: Loc.paidNow, text: $cart.paidText, height: 40)
            }

            VStack(spacing: 4) {
                HStack(alignment: .firstTextBaseline) {
                    Text(Loc.total)
                        .font(NocturneType.inter(13))
                        .foregroundStyle(Nocturne.neutral500)
                    Spacer()
                    Text(Money.text(cart.total, in: currency))
                        .nocturneText(.bigNumber(28))
                }

                if cart.payMode == .part {
                    HStack(alignment: .firstTextBaseline) {
                        Text(Loc.balance)
                            .font(NocturneType.inter(12.5))
                            .foregroundStyle(Nocturne.neutral500)
                        Spacer()
                        Text(Money.text(cart.balance, in: currency))
                            .font(NocturneType.inter(15))
                            .foregroundStyle(Nocturne.accent400)
                    }
                }
            }

            // Validation is the button's label, never a toast: it says what is
            // missing and stays disabled until it isn't.
            Button(cart.canSave ? Loc.saveBill : Loc.enterCustomerName, action: onSave)
                .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 48, fontSize: 15))
                .disabled(!cart.canSave)
        }
        .padding(.horizontal, Metrics.screenPadding)
        .padding(.top, 12)
        .padding(.bottom, max(bottomInset, 24))
        .background(Nocturne.surface)
        .overlay(alignment: .top) {
            Rectangle().fill(Nocturne.neutral800).frame(height: 1)
        }
    }
}

// MARK: - Line

/// One product on the bill: quantity, live stock, and the editable price.
private struct CartLineCard: View {
    let line: Cart.Line
    /// Read from the store rather than carried on the line, so adding stock from
    /// another screen shows here immediately.
    let stock: Int
    let onQuantity: (Int) -> Void
    let onPrice: (Double) -> Void
    let onResetPrice: () -> Void
    let onRemove: () -> Void

    @Environment(\.currency) private var currency

    // Both numbers are edited as text so a half-typed value ("1." or "") is
    // representable. They are re-seeded from the model only when it has moved
    // somewhere the text cannot account for — a stepper tap or a price reset —
    // which is what stops a re-seed from fighting the keyboard mid-entry.
    @State private var qtyText = ""
    @State private var priceText = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Text(line.name)
                    .nocturneText(.rowPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .lineLimit(1)
                Text(Money.text(line.lineTotal, in: currency))
                    .font(NocturneType.inter(15))
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
                stepper
                // Wraps rather than truncates: "only 3 in stock" is the warning
                // that stops a wrong bill, so it is never worth eliding to fit
                // beside a stepper and a price box on a narrow phone.
                Text(stockNote)
                    .nocturneText(.meta)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .layoutPriority(-1)
                priceBox
            }

            if line.isPriceOverridden {
                HStack(spacing: 5) {
                    Glyph(Icon.edit, size: 11)
                    Text(Loc.usualPriceNote(Money.text(line.basePrice, in: currency)))
                        .font(NocturneType.inter(11))
                    Spacer(minLength: 6)
                    Button(Loc.reset) {
                        onResetPrice()
                        priceText = Money.amount(line.basePrice, in: currency)
                    }
                    .buttonStyle(GhostButtonStyle(fontSize: 11))
                }
                .foregroundStyle(Nocturne.accent400)
                .padding(.top, 8)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .onAppear {
            qtyText = String(line.qty)
            priceText = Money.amount(line.price, in: currency)
        }
        .onChange(of: line.qty) { _, new in
            if Int(qtyText.trimmed) != new { qtyText = String(new) }
        }
        .onChange(of: line.price) { _, new in
            if Money.parse(priceText) != new { priceText = Money.amount(new, in: currency) }
        }
    }

    private var stepper: some View {
        HStack(spacing: 0) {
            Button {
                onQuantity(line.qty - 1)
            } label: {
                Glyph(Icon.remove, size: 15)
                    .frame(width: 34, height: 34)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Loc.oneFewer)

            TextField("", text: $qtyText)
                .font(NocturneType.inter(14))
                .multilineTextAlignment(.center)
                .keyboardType(.numberPad)
                .tint(Nocturne.accent)
                .frame(width: 44, height: 34)
                .onChange(of: qtyText) { _, new in
                    if let value = Int(new.trimmed), value != line.qty { onQuantity(value) }
                }

            Button {
                onQuantity(line.qty + 1)
            } label: {
                Glyph(Icon.add, size: 15)
                    .frame(width: 34, height: 34)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Loc.oneMore)
        }
        .foregroundStyle(Nocturne.text)
        .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .hairline(radius: Metrics.controlRadius)
    }

    private var priceBox: some View {
        HStack(spacing: 5) {
            Text(currency.symbol.trimmed)
                .font(NocturneType.inter(12.5))
                .foregroundStyle(Nocturne.neutral500)
                .lineLimit(1)
                // Without this the row's squeeze lands here first and "SAR"
                // wraps onto two lines inside a 34pt box.
                .fixedSize(horizontal: true, vertical: false)
            TextField("", text: $priceText)
                .font(NocturneType.inter(14))
                .foregroundStyle(line.isPriceOverridden ? Nocturne.accent300 : Nocturne.text)
                .multilineTextAlignment(.trailing)
                .keyboardType(.decimalPad)
                .tint(Nocturne.accent)
                .frame(width: 56)
                .onChange(of: priceText) { _, new in
                    if let value = Money.parse(new), value != line.price { onPrice(value) }
                }
        }
        .padding(.horizontal, 11)
        .frame(height: Metrics.compactControlHeight)
        .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .hairline(line.isPriceOverridden ? Nocturne.accent : Nocturne.neutral800, radius: Metrics.controlRadius)
    }

    /// The shelf, honestly. Overselling is allowed — the customer is standing
    /// there and the count may simply be wrong — but it is never silent.
    private var stockNote: String {
        qty > stock ? Loc.onlyInStock(stock) : Loc.piecesInStock(stock)
    }

    private var qty: Int { line.qty }
}

// MARK: - Customer

/// Required on every bill, with suggestions drawn from who has been billed
/// before — debtors first, because those are the names worth recognising.
private struct CustomerField: View {
    @Binding var name: String

    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency
    @FocusState private var focused: Bool

    private var suggestions: [CustomerSuggestion] {
        store.customerSuggestions(matching: name)
    }

    var body: some View {
        // The list sits **above** the field as an ordinary sibling, not as an
        // overlay. Two attempts to float it — an overlay with an alignment
        // guide, then the same with `fixedSize` — both landed back on top of
        // the field, because an overlay is proposed its parent's 40pt height
        // and no amount of guide arithmetic reliably undoes that. A sibling in
        // the stack cannot overlap by construction: the footer simply grows
        // upwards to make room, which is what a popover looks like here anyway.
        VStack(spacing: 6) {
            if focused, !suggestions.isEmpty {
                suggestionCard
                    .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
            field
        }
        .animation(.easeOut(duration: 0.14), value: focused)
        .animation(.easeOut(duration: 0.14), value: suggestions.count)
    }

    private var field: some View {
        ZStack(alignment: .leading) {
            if name.isEmpty {
                Text(Loc.customerName)
                    .font(NocturneType.inter(14))
                    .foregroundStyle(Nocturne.neutral500)
                    .allowsHitTesting(false)
            }
            TextField("", text: $name)
                .font(NocturneType.inter(14))
                .accessibilityIdentifier("cart.customer")
                .focused($focused)
                .tint(Nocturne.accent)
                .submitLabel(.done)
        }
        .padding(.horizontal, 10)
        .frame(height: 40)
        .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .hairline(name.isBlank ? Nocturne.accent : Nocturne.neutral800, radius: Metrics.controlRadius)
    }

    private var suggestionCard: some View {
        VStack(spacing: 0) {
            ForEach(Array(suggestions.enumerated()), id: \.element.id) { index, suggestion in
                Button {
                    name = suggestion.name
                    focused = false
                } label: {
                    HStack(spacing: 8) {
                        Glyph(Icon.customer, size: 15)
                            .foregroundStyle(Nocturne.neutral500)
                        Text(suggestion.name)
                            .font(NocturneType.inter(13.5))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .lineLimit(1)
                        Text(suggestion.meta(in: currency, strings: Loc))
                            .font(NocturneType.inter(11))
                            .foregroundStyle(Nocturne.neutral500)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if index < suggestions.count - 1 {
                    Rectangle().fill(Nocturne.neutral800).frame(height: 1)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface)
        .clipShape(RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
        .hairline(radius: Metrics.controlRadius)
        .shadow(color: .black.opacity(0.55), radius: 9, x: 0, y: 6)
    }
}

// MARK: - Payment

private struct PaymentPill: View {
    let title: String
    let icon: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Glyph(icon, size: 14)
                Text(title).font(NocturneType.inter(13, .medium))
            }
            .foregroundStyle(selected ? Nocturne.accent : Nocturne.neutral500)
            .frame(maxWidth: .infinity)
            .frame(height: 38)
            .hairline(selected ? Nocturne.accent : Nocturne.neutral800, radius: Metrics.controlRadius)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}
