import SwiftUI

/// First-run setup: name, then product names, then stock and prices.
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
    @State private var draftName = ""
    @State private var drafts: [ProductDraft] = []

    @Environment(\.topSafeInset) private var topInset
    @Environment(\.bottomSafeInset) private var bottomInset

    private enum Step: Int {
        case name, products, prices
    }

    /// The exact set the owner asked for — a lock shop's four common lines, and
    /// nothing else. These only fill the field; nothing is added until tapped.
    private static let suggestions = [
        "Lever Handle Lock",
        "Cisa lock",
        "Padlock",
        "Deadbolt"
    ]

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
            }
        }
        .padding(.horizontal, Metrics.screenPadding)
        .padding(.top, max(0, 60 - topInset))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Nocturne.bg.ignoresSafeArea())
    }

    private var progressBar: some View {
        HStack(spacing: 6) {
            ForEach(0..<3, id: \.self) { index in
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

                Text("Welcome to Stockbook")
                    .nocturneText(.setupTitle)
                    .padding(.bottom, 5)

                Text("Everything stays on this phone — no account, no signal needed. First, what should we call you?")
                    .nocturneText(.body)
                    .padding(.bottom, 18)

                NocturneField(
                    label: "Your name",
                    placeholder: "Business owner name",
                    text: $ownerName,
                    height: Metrics.tallInputHeight,
                    isRequiredAndEmpty: ownerName.isBlank,
                    fontSize: 15,
                    identifier: "setup.ownerName",
                    onSubmit: { advance(to: .products) }
                )

                Spacer(minLength: 0)
            }
            .frame(maxHeight: .infinity, alignment: .top)

            footer {
                Button("Continue") { advance(to: .products) }
                    .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 48, fontSize: 15))
                    .disabled(ownerName.isBlank)
            }
        }
    }

    // MARK: Step 2 — what

    private var productsStep: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text(ownerName.firstName.isEmpty ? "Your shelves" : "Hello, \(ownerName.firstName)")
                    .font(NocturneType.inter(11))
                    .tracking(11 * 0.09)
                    .textCase(.uppercase)
                    .foregroundStyle(Nocturne.accent)
                    .padding(.bottom, 6)

                Text("What do you stock?")
                    .nocturneText(.setupTitle)
                    .padding(.bottom, 5)

                Text("Names only for now. Prices and counts come next, and you can add or remove items any time after.")
                    .nocturneText(.body)
                    .padding(.bottom, 16)

                HStack(spacing: 8) {
                    NocturneField(
                        placeholder: "e.g. 4 inch hinge",
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
                    Kicker("Common hardware lines").padding(.bottom, 8)
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

                Kicker(drafts.isEmpty ? "Nothing added yet" : "Added · \(drafts.count)")
                    .padding(.bottom, 8)

                ScrollView {
                    LazyVStack(spacing: Metrics.rowGap) {
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
                                .accessibilityLabel("Remove \(draft.name)")
                            }
                            .padding(.leading, 13)
                            .padding(.trailing, 8)
                            .padding(.vertical, 9)
                            .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                        }
                    }
                    .padding(.bottom, 10)
                }
            }
            .frame(maxHeight: .infinity, alignment: .top)

            footer {
                HStack(spacing: 8) {
                    backButton(to: .name)
                    Button("Next — stock & prices") { advance(to: .prices) }
                        .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 48, fontSize: 15))
                        .disabled(drafts.isEmpty)
                }
            }
        }
    }

    // MARK: Step 3 — how much

    private var pricesStep: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                Text("Stock and prices")
                    .nocturneText(.setupTitle)
                    .padding(.bottom, 5)

                Text("All three are needed for every item — the count on the shelf, what you paid, what you charge.")
                    .nocturneText(.body)
                    .padding(.bottom, 16)

                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach($drafts) { $draft in
                            VStack(alignment: .leading, spacing: 0) {
                                Text(draft.name)
                                    .nocturneText(.rowPrimary)
                                    .padding(.bottom, 10)

                                HStack(alignment: .top, spacing: 8) {
                                    NocturneField.number(
                                        label: "In stock",
                                        text: $draft.stock,
                                        isRequiredAndEmpty: draft.stock.isBlank,
                                        requiredMarking: .afterTouch,
                                        identifier: "setup.stock"
                                    )
                                    NocturneField.number(
                                        label: "You pay",
                                        text: $draft.cost,
                                        isRequiredAndEmpty: draft.cost.isBlank,
                                        requiredMarking: .afterTouch,
                                        identifier: "setup.cost"
                                    )
                                    NocturneField.number(
                                        label: "You sell",
                                        text: $draft.price,
                                        isRequiredAndEmpty: (Money.parse(draft.price) ?? 0) <= 0,
                                        requiredMarking: .afterTouch,
                                        emphasis: .sellingPrice,
                                        identifier: "setup.price"
                                    )
                                }
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
                        }
                    }
                    .padding(.bottom, 10)
                }
            }
            .frame(maxHeight: .infinity, alignment: .top)

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
                        Button("Open the shop", action: finish)
                            .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 48, fontSize: 15))
                            .disabled(!isComplete)
                    }
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
        .accessibilityLabel("Back")
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
        guard incompleteCount > 0 else { return "All set — stock and both prices filled in." }
        let verb = incompleteCount == 1 ? "item still needs" : "items still need"
        return "\(incompleteCount) \(verb) stock, buying and selling price."
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

    private func advance(to destination: Step) {
        switch destination {
        case .products where ownerName.isBlank: return
        case .prices where drafts.isEmpty: return
        default: withAnimation(Metrics.quick) { step = destination }
        }
    }

    private func finish() {
        guard isComplete else { return }
        store.setOwnerName(ownerName)
        for draft in drafts {
            store.addProduct(
                name: draft.name,
                stock: Int(draft.stock.trimmed) ?? Int(Money.parse(draft.stock) ?? 0),
                cost: Money.parse(draft.cost) ?? 0,
                price: Money.parse(draft.price) ?? 0
            )
        }
        store.completeSetup()
    }
}
