import SwiftUI

// Screens whose architecture is in place but whose UI is the next pass. Each one
// names what the handoff asks for and what it can already call. See
// `ios/README.md` → "What is not built yet".

/// Handoff §0–§2 — first-run setup.
///
/// Until the three steps are built this offers a way past them, so the rest of
/// the app can be run and reviewed. That bypass goes when setup lands.
struct SetupFlowView: View {
    @Environment(StockbookStore.self) private var store

    @State private var ownerName = ""

    var body: some View {
        VStack(spacing: 0) {
            ScaffoldScreen(
                title: "Welcome to Stockbook",
                subtitle: "first-run setup — three steps",
                requirements: [
                    "Step 1: three-segment progress bar, accent shapes tile, required “Your name” field (placeholder “Business owner name”), Continue disabled until it is filled.",
                    "Step 2: product names only. Add row + suggestion capsules — exactly Lever Handle Lock, Cisa lock, Padlock, Deadbolt — and case-insensitive dedupe.",
                    "Step 3: a card per product with In stock / You pay / You sell, the gate line counting what is still missing, and “Open the shop” disabled until every item is complete."
                ],
                readyPieces: [
                    "StockbookStore.addProduct dedupes names case-insensitively and returns the existing product.",
                    "StockbookStore.isProductDraftComplete is the same completeness rule step 3 and the product editor both gate on.",
                    "setOwnerName and completeSetup persist the outcome; startOver brings the owner back here."
                ]
            )

            VStack(spacing: Metrics.fieldGap) {
                NocturneField(
                    label: "Your name",
                    placeholder: "Business owner name",
                    text: $ownerName,
                    height: Metrics.tallInputHeight,
                    isRequiredAndEmpty: ownerName.isBlank
                )

                Button("Continue") {
                    store.setOwnerName(ownerName)
                    store.completeSetup()
                }
                .buttonStyle(.primaryBlock)
                .disabled(ownerName.isBlank)
            }
            .padding(.horizontal, Metrics.screenPadding)
            .padding(.bottom, 24)
        }
        .background(Nocturne.bg.ignoresSafeArea())
    }
}
