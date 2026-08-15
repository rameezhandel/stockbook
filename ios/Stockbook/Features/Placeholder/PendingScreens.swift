import SwiftUI

// Screens whose architecture is in place but whose UI is the next pass. Each one
// names what the handoff asks for and what it can already call. See
// `ios/README.md` → "What is not built yet".

/// Handoff §5 and §6 — the product picker and the cart. The most important
/// screen in the app.
struct SellScreen: View {
    var body: some View {
        ScaffoldScreen(
            title: "New bill",
            subtitle: "the core flow — next up",
            requirements: [
                "Product picker: search or browse all, tap to add one piece at the product's current price, tap again to increment.",
                "Cart line card: stepper, live “pieces · N in stock” (“only N in stock” when the quantity exceeds it), and an editable price box.",
                "Price override: accent border and accent-300 value when changed, plus “✎ Usual price SAR 145 — changed for this bill only” and a Reset. The product's own price is never touched.",
                "Sticky footer: required customer field with a suggestion dropdown opening upwards, Paid in full / Part payment, total, and a save button labelled “Enter a customer name” until there is one.",
                "Saving shows the receipt (§7)."
            ],
            readyPieces: [
                "Cart holds lines, override state, payment mode and the customer name — including canSave, total, balance and paidForStorage.",
                "StockbookStore.saveBill snapshots each line, decrements stock (floored at 0), clamps a part payment and allocates the bill number.",
                "StockbookStore.customerSuggestions ranks by balance owed, then bill count, capped at four."
            ]
        )
    }
}

/// Handoff §8 — the bill history, and the only correction path there is.
struct BillsScreen: View {
    var body: some View {
        ScaffoldScreen(
            title: "Bills",
            subtitle: "history and void",
            requirements: [
                "Rows reusing BillRow with showsVoidAction — joined line names, meta, right-aligned total.",
                "Void & put stock back on every live bill; voided bills go muted and lose the action.",
                "Empty state: receipt icon, “Nothing sold yet. Every bill you save shows up here.” + Start a bill."
            ],
            readyPieces: [
                "BillRow already renders both the live and voided treatments.",
                "StockbookStore.void restores each line's quantity to product stock and marks the bill voided — bills are never deleted."
            ]
        )
    }
}

/// Handoff §11 — the settings screen, and the export/import handoff that is the
/// app's only route onto a new phone.
struct SettingsScreen: View {
    @Environment(AppRouter.self) private var router

    var body: some View {
        VStack(spacing: 0) {
            ScaffoldScreen(
                title: "Settings",
                subtitle: "export, import, start again",
                requirements: [
                    "THIS PHONE: editable owner name, plus Products / Bills / Customers counts.",
                    "Export card: Create backup file → file chip with name, counts and size, a Share action, and the note switching to “Written to Files…”.",
                    "Import card: Choose a file → accent-bordered summary box and the accent-300 warning naming exactly what will be replaced, then Cancel / Replace everything.",
                    "START AGAIN: Start over wipes everything and returns to setup step 1."
                ],
                readyPieces: [
                    "BackupDocument is the real versioned file format; BackupService encodes, writes a dated file, and validates version before shape on the way back in.",
                    "BackupFile (FileDocument) drives .fileExporter — the Today “Save file” button already writes a real backup.",
                    "StockbookStore.replaceEverything performs the destructive swap; startOver wipes and re-runs setup."
                ]
            )

            Button("Done") { router.showingSettings = false }
                .buttonStyle(.secondaryBlock)
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 24)
        }
        .background(Nocturne.bg.ignoresSafeArea())
    }
}

/// Handoff §7 — the confirmation the owner shows the customer.
struct ReceiptOverlay: View {
    let bill: Bill

    @Environment(AppRouter.self) private var router

    var body: some View {
        VStack(spacing: 0) {
            ScaffoldScreen(
                title: "Bill saved",
                subtitle: "Bill #\(bill.number) · \(bill.timeLabel) · \(bill.who)",
                requirements: [
                    "Circled accent check that pops in (scale overshoot ~1.25, then settles).",
                    "Card of lines: name · “2 × SAR 32” · line total, then the faded rule (FadedRule is built).",
                    "Total at 25px, then “Paid in full, cash.” or “Paid SAR 100 · Ahmed Contracting owes SAR 94” in accent-400.",
                    "Secondary “See bills” and primary “Next customer”."
                ]
            )

            Button("Close") { router.receipt = nil }
                .buttonStyle(.primaryBlock)
                .padding(.horizontal, Metrics.screenPadding)
                .padding(.bottom, 24)
        }
        .background(Nocturne.bg.ignoresSafeArea())
    }
}

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
