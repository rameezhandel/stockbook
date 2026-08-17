import SwiftUI

/// First-run setup: name, then product names, then stock and prices, then the
/// regulars who buy on account.
///
/// Nothing here is persisted until "Open the shop". The whole flow is a draft
/// held in this view — a half-finished setup is not a shop, and abandoning it
/// mid-way should leave no trace to reconcile later.
///
/// Shown by `RootView` whenever `ShopSettings.setupCompleted` is false, which is
/// also what "Start over" resets, so there is no separate route back here.
struct SetupFlowView: View {
    @Environment(StockbookStore.self) private var store

    @State private var step: Step = .name
    @State private var ownerName = ""
    @State private var currency = Currency.default
    @State private var draftName = ""
    @State private var drafts: [ProductDraft] = []
    @State private var draftCustomer = ""
    @State private var draftOpening = ""
    @State private var customerDrafts: [CustomerDraft] = []

    /// Step 3's twelve-or-so boxes, focused from here rather than each one
    /// managing itself, so the keyboard toolbar knows what comes next.
    @FocusState private var priceFocus: String?

    @Environment(\.topSafeInset) private var topInset
    @Environment(\.bottomSafeInset) private var bottomInset

    private enum Step: Int, CaseIterable {
        case name, products, prices, customers
    }

    /// The exact set the owner asked for — a lock shop's four common lines, and
    /// nothing else. These only fill the field; nothing is added until tapped.
    private static let suggestions = [
        "Lever Handle Lock",
        "Cisa lock",
        "Padlock",
        "Deadbolt"
    ]

    /// A name and what they already owe. Phone and place wait for the editor
    /// sheet — but the carried-over balance belongs *here*, because this screen is
    /// where a paper book gets migrated, and going back to set twenty of them one
    /// at a time is how an owner decides the app is not worth it.
    private struct CustomerDraft: Identifiable {
        let id = UUID()
        var name: String
        var openingBalance: Double
    }

    private struct ProductDraft: Identifiable {
        let id = UUID()
        var name: String
        var stock = ""
        var cost = ""
        var price = ""

        var isComplete: Bool {
            StockbookStore.isProductDraftComplete(name: name, stock: stock, cost: cost, price: price)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            progressBar.padding(.bottom, 18)

            switch step {
            case .name: nameStep
            case .products: productsStep
            case .prices: pricesStep
            case .customers: customersStep
            }
        }
        .padding(.horizontal, Metrics.screenPadding)
        .padding(.top, max(0, 60 - topInset))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Nocturne.bg.ignoresSafeArea())
        // The keyboard overlays the screen; it does not shove it upwards. The
        // footer buttons stay where they are.
        .ignoresSafeArea(.keyboard, edges: .bottom)
        // One toolbar, declared unconditionally, whose *label* changes. An
        // earlier version gave every field its own conditional toolbar and the
        // screen hung as focus moved between them — three fields per product
        // meant a dozen toolbars appearing and disappearing at once.
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(keyboardButtonTitle) { keyboardButtonTapped() }
                    .font(NocturneType.inter(15, .medium))
                    .foregroundStyle(Nocturne.accent)
            }
        }
    }

    // MARK: The keyboard's one button

    /// Every box on step 3, in the order a thumb works through them: across each
    /// product, then down to the next.
    private var priceFieldTags: [String] {
        drafts.indices.flatMap { ["stock-\($0)", "cost-\($0)", "price-\($0)"] }
    }

    /// The next box after the focused one, or `nil` at the end of the last
    /// product — which is the one place the button should say "Done".
    private var nextPriceField: String? {
        guard let current = priceFocus,
              let index = priceFieldTags.firstIndex(of: current),
              index + 1 < priceFieldTags.count
        else { return nil }
        return priceFieldTags[index + 1]
    }

    private var keyboardButtonTitle: String {
        nextPriceField == nil ? Loc.done : Loc.next
    }

    private func keyboardButtonTapped() {
        if let next = nextPriceField {
            priceFocus = next
        } else {
            // Clearing the screen's focus dismisses the keyboard *and* tells
            // every field it no longer has focus. Resigning through the
            // responder chain does only the first, which is what used to leave
            // a box lit after the keyboard had gone.
            priceFocus = nil
            dismissKeyboard()
        }
    }

    private var progressBar: some View {
        HStack(spacing: 6) {
            ForEach(0..<Step.allCases.count, id: \.self) { index in
                Capsule()
                    .fill(index <= step.rawValue ? Nocturne.accent : Nocturne.neutral800)
                    .frame(height: 3)
            }
        }
    }

    // MARK: Step 1 — who

    private var nameStep: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Glyph(Icon.items, size: 20)
                    .foregroundStyle(Nocturne.accent)
                    .frame(width: 38, height: 38)
                    .hairline(Nocturne.accent, radius: 10)
                    .padding(.bottom, 14)

                Text(Loc.welcomeToStockbook)
                    .nocturneText(.setupTitle)
                    .padding(.bottom, 5)

                Text(Loc.welcomeBody)
                    .nocturneText(.body)
                    .padding(.bottom, 18)

                NocturneField(
                    label: Loc.yourName,
                    placeholder: Loc.businessOwnerName,
                    text: $ownerName,
                    height: Metrics.tallInputHeight,
                    isRequiredAndEmpty: ownerName.isBlank,
                    fontSize: 15,
                    identifier: "setup.ownerName",
                    onSubmit: { advance(to: .products) }
                )
                .padding(.bottom, 14)

                // Asked here rather than beside the prices, because by step 3
                // the owner is typing numbers and should already know which
                // ones they are.
                CurrencyField(label: Loc.currencySection, currency: $currency)

                Text(Loc.setupCurrencyNote)
                    .nocturneText(.meta)
                    .padding(.top, 6)

                Spacer(minLength: 0)
            }
            .frame(maxHeight: .infinity, alignment: .top)

            footer {
                Button(Loc.continueAction) { advance(to: .products) }
                    .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 48, fontSize: 15))
                    .disabled(ownerName.isBlank)
            }
        }
    }

    // MARK: Step 2 — what

    private var productsStep: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(ownerName.firstName.isEmpty ? Loc.yourShelves : Loc.greeting(ownerName.firstName))
                        .font(NocturneType.inter(11))
                        .tracking(11 * 0.09)
                        .textCase(.uppercase)
                        .foregroundStyle(Nocturne.accent)
                        .padding(.bottom, 6)

                    Text(Loc.whatDoYouStock)
                        .nocturneText(.setupTitle)
                        .padding(.bottom, 5)

                    Text(Loc.stockNamesBody)
                        .nocturneText(.body)
                        .padding(.bottom, 16)

                    HStack(spacing: 8) {
                        NocturneField(
                            placeholder: Loc.productNameExample,
                            text: $draftName,
                            height: Metrics.tallInputHeight,
                            fontSize: 15,
                            identifier: "setup.productName",
                            onSubmit: { addDraft(draftName) }
                        )
                        Button { addDraft(draftName) } label: {
                            Glyph(Icon.add, size: 18)
                        }
                        .buttonStyle(PrimaryButtonStyle(height: Metrics.tallInputHeight))
                    }
                    .padding(.bottom, 16)

                    if !availableSuggestions.isEmpty {
                        Kicker(Loc.commonHardwareLines).padding(.bottom, 8)
                        FlowLayout(spacing: 6) {
                            ForEach(availableSuggestions, id: \.self) { name in
                                Button { addDraft(name) } label: {
                                    Text("+ \(name)")
                                        .font(NocturneType.inter(11.5))
                                        .foregroundStyle(Nocturne.accent)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 3)
                                        .frame(minHeight: 30)
                                        .hairline(Nocturne.accent, radius: 6)
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.bottom, 20)
                    }

                    Kicker(drafts.isEmpty ? Loc.nothingAddedYetKicker : Loc.addedCount(drafts.count))
                        .padding(.bottom, 8)

                    VStack(spacing: Metrics.rowGap) {
                        ForEach(drafts) { draft in
                            HStack(spacing: 10) {
                                Text(draft.name).nocturneText(.rowPrimary)
                                Spacer(minLength: 0)
                                Button {
                                    drafts.removeAll { $0.id == draft.id }
                                } label: {
                                    Glyph(Icon.close, size: 16)
                                        .foregroundStyle(Nocturne.neutral500)
                                        .minimumTouchTarget()
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(Loc.remove(draft.name))
                            }
                            .padding(.leading, 13)
                            .padding(.trailing, 8)
                            .padding(.vertical, 9)
                            .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.bottom, 10)
            }
            .scrollDismissesKeyboard(.interactively)

            footer {
                HStack(spacing: 8) {
                    backButton(to: .name)
                    Button(Loc.nextStockAndPrices) { advance(to: .prices) }
                        .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 48, fontSize: 15))
                        .disabled(drafts.isEmpty)
                }
            }
        }
    }

    // MARK: Step 3 — how much

    private var pricesStep: some View {
        // Content scrolls; the footer is a plain sibling beneath it. A
        // safe-area inset floats over the scroll view and travels with its
        // bounce, which pushed the buttons up over the content. A sibling in a
        // stack simply cannot move: the scroll view takes the space that is
        // left, and the footer sits under it.
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(Loc.stockAndPrices)
                        .nocturneText(.setupTitle)
                        .padding(.bottom, 5)

                    Text(Loc.stockAndPricesBody)
                        .nocturneText(.body)
                        .padding(.bottom, 16)

                    // A plain stack: a handful of products, and a lazy one
                    // renders nothing when the keyboard squeezes its container.
                    VStack(spacing: 10) {
                        // Indexed rather than bound directly, because each
                        // card needs to know where it sits to tag its three
                        // boxes for the toolbar's Next.
                        ForEach(drafts.indices, id: \.self) { index in
                            priceCard(index: index, draft: $drafts[index])
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.bottom, 10)
            }
            .scrollDismissesKeyboard(.interactively)

            footer {
                VStack(spacing: 8) {
                    // The gate explains itself rather than leaving a dead button
                    // to be poked at.
                    Text(gateLine)
                        .font(NocturneType.inter(11.5))
                        .foregroundStyle(isComplete ? Nocturne.accent400 : Nocturne.neutral500)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)

                    HStack(spacing: 8) {
                        backButton(to: .products)
                        Button(Loc.nextCustomers) { advance(to: .customers) }
                            .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 48, fontSize: 15))
                            .disabled(!isComplete)
                    }
                }
            }
        }
    }

    private func priceCard(index: Int, draft: Binding<ProductDraft>) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(draft.wrappedValue.name)
                .nocturneText(.rowPrimary)
                .padding(.bottom, 10)

            HStack(alignment: .top, spacing: 8) {
                NocturneField.number(
                    label: Loc.inStock,
                    text: draft.stock,
                    isRequiredAndEmpty: draft.wrappedValue.stock.isBlank,
                    requiredMarking: .afterTouch,
                    identifier: "setup.stock",
                    focusTag: "stock-\(index)",
                    focus: $priceFocus
                )
                NocturneField.number(
                    label: Loc.youPay,
                    text: draft.cost,
                    isRequiredAndEmpty: draft.wrappedValue.cost.isBlank,
                    requiredMarking: .afterTouch,
                    identifier: "setup.cost",
                    focusTag: "cost-\(index)",
                    focus: $priceFocus
                )
                NocturneField.number(
                    label: Loc.youSell,
                    text: draft.price,
                    isRequiredAndEmpty: (Money.parse(draft.wrappedValue.price) ?? 0) <= 0,
                    requiredMarking: .afterTouch,
                    emphasis: .sellingPrice,
                    identifier: "setup.price",
                    focusTag: "price-\(index)",
                    focus: $priceFocus
                )
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
    }

    // MARK: Step 4 — who buys on account

    /// The only optional step in setup, and it says so.
    ///
    /// A shop can open without knowing a single customer — names can be typed at
    /// the counter, which is what happened before this screen existed. What this
    /// buys is the regulars being ready, and a statement being possible for them
    /// from the first bill rather than the second.
    private var customersStep: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Kicker(Loc.yourCustomers).padding(.bottom, 6)

                    Text(Loc.whoDoYouSellTo)
                        .nocturneText(.setupTitle)
                        .padding(.bottom, 5)

                    Text(Loc.customersSetupBody)
                        .nocturneText(.body)
                        .padding(.bottom, 16)

                    HStack(spacing: 8) {
                        NocturneField(
                            placeholder: Loc.customerNameExample,
                            text: $draftCustomer,
                            height: Metrics.tallInputHeight,
                            fontSize: 15,
                            identifier: "setup.customerName",
                            onSubmit: { addCustomerDraft(draftCustomer) }
                        )
                        NocturneField.number(
                            placeholder: Loc.openingBalanceField,
                            text: $draftOpening,
                            height: Metrics.tallInputHeight,
                            prefix: currency.symbol.trimmed,
                            fontSize: 15,
                            identifier: "setup.customerOpening"
                        )
                        .frame(width: 118)
                        Button { addCustomerDraft(draftCustomer) } label: {
                            Glyph(Icon.add, size: 18)
                        }
                        .buttonStyle(PrimaryButtonStyle(height: Metrics.tallInputHeight))
                    }
                    .padding(.bottom, 6)

                    Text(Loc.openingBalanceNote)
                        .nocturneText(.meta)
                        .padding(.bottom, 16)

                    Kicker(customerDrafts.isEmpty ? Loc.noCustomersYetKicker : Loc.addedCount(customerDrafts.count))
                        .padding(.bottom, 8)

                    VStack(spacing: Metrics.rowGap) {
                        ForEach(customerDrafts) { draft in
                            HStack(spacing: 10) {
                                Glyph(Icon.customer, size: 13)
                                    .foregroundStyle(Nocturne.neutral500)
                                Text(draft.name).nocturneText(.rowPrimary)
                                Spacer(minLength: 0)
                                if draft.openingBalance > 0 {
                                    Text(Loc.owes(Money.text(draft.openingBalance, in: currency)))
                                        .font(NocturneType.inter(11.5))
                                        .foregroundStyle(Nocturne.accent400)
                                }
                                Button {
                                    customerDrafts.removeAll { $0.id == draft.id }
                                } label: {
                                    Glyph(Icon.close, size: 16)
                                        .foregroundStyle(Nocturne.neutral500)
                                        .minimumTouchTarget()
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(Loc.remove(draft.name))
                            }
                            .padding(.leading, 13)
                            .padding(.trailing, 8)
                            .padding(.vertical, 9)
                            .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.bottom, 10)
            }
            .scrollDismissesKeyboard(.interactively)

            footer {
                HStack(spacing: 8) {
                    backButton(to: .prices)
                    // Never disabled. Nobody is required here, and a dead button
                    // on an optional step reads as a wall.
                    Button(Loc.openTheShop, action: finish)
                        .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 48, fontSize: 15))
                }
            }
        }
    }

    // MARK: Pieces

    private func footer<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        content()
            .padding(.top, 10)
            .padding(.bottom, max(bottomInset, 24))
    }

    private func backButton(to destination: Step) -> some View {
        Button {
            withAnimation(Metrics.quick) { step = destination }
        } label: {
            Glyph(Icon.back, size: 17)
        }
        .buttonStyle(SecondaryButtonStyle(height: 48))
        .frame(width: 56)
        .accessibilityLabel(Loc.back)
    }

    private var availableSuggestions: [String] {
        Self.suggestions.filter { suggestion in
            !drafts.contains { $0.name.lowercased() == suggestion.lowercased() }
        }
    }

    private var incompleteCount: Int {
        drafts.filter { !$0.isComplete }.count
    }

    private var isComplete: Bool {
        !drafts.isEmpty && incompleteCount == 0
    }

    private var gateLine: String {
        guard incompleteCount > 0 else { return Loc.allSet }
        return Loc.stillNeedPrices(incompleteCount)
    }

    // MARK: Actions

    /// Duplicates are ignored in silence — the owner tapping a capsule twice has
    /// made no mistake worth interrupting them for.
    private func addDraft(_ name: String) {
        let cleaned = name.trimmed
        guard !cleaned.isEmpty else { return }
        guard !drafts.contains(where: { $0.name.lowercased() == cleaned.lowercased() }) else {
            draftName = ""
            return
        }
        drafts.append(ProductDraft(name: cleaned))
        draftName = ""
    }

    /// Same rule as the product list: a name already there is not a mistake
    /// worth interrupting anybody for.
    private func addCustomerDraft(_ name: String) {
        let cleaned = name.trimmed
        guard !cleaned.isEmpty else { return }
        guard !customerDrafts.contains(where: { Customer.key(for: $0.name) == Customer.key(for: cleaned) }) else {
            draftCustomer = ""
            draftOpening = ""
            return
        }
        customerDrafts.append(
            CustomerDraft(name: cleaned, openingBalance: Money.parse(draftOpening) ?? 0)
        )
        draftCustomer = ""
        draftOpening = ""
    }

    private func advance(to destination: Step) {
        switch destination {
        case .products where ownerName.isBlank: return
        case .prices where drafts.isEmpty: return
        case .customers where !isComplete: return
        default: withAnimation(Metrics.quick) { step = destination }
        }
    }

    private func finish() {
        guard isComplete else { return }
        store.setOwnerName(ownerName)
        store.setCurrency(currency)
        for draft in drafts {
            store.addProduct(
                name: draft.name,
                stock: Int(draft.stock.trimmed) ?? Int(Money.parse(draft.stock) ?? 0),
                cost: Money.parse(draft.cost) ?? 0,
                price: Money.parse(draft.price) ?? 0
            )
        }
        for draft in customerDrafts {
            store.addCustomer(name: draft.name, openingBalance: draft.openingBalance)
        }
        store.completeSetup()
    }
}
