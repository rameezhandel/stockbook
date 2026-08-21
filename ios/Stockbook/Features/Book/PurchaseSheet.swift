import SwiftUI

/// One delivery, opened from the book.
///
/// The bill sheet's mirror, including where correcting lives: on the document
/// rather than on the list row, so the thing being changed is on screen while it
/// is changed. Removing a delivery takes its stock back off the shelf, which the
/// note says out loud — that is the part that surprises people.
struct PurchaseSheet: View {
    let purchase: Purchase
    let onClose: () -> Void

    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.currency) private var currency

    /// Armed by the first tap on Remove. A delivery takes two, because removing
    /// one moves stock as well as money.
    @State private var confirming = false

    /// Falls back to what opened the sheet, which matters after a database
    /// replace has removed it from under the sheet.
    private var live: Purchase {
        store.purchases.first { $0.id == purchase.id } ?? purchase
    }

    private var supplierName: String {
        store.supplier(key: live.supplierKey)?.name ?? live.supplierKey
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(
                title: Loc.deliveryDetail,
                subtitle: Loc.longDate(live.createdAt),
                onClose: onClose
            )

            line(Loc.supplier, supplierName)
            // Only when there was one. An empty row headed "Invoice no." reads as
            // a number the app lost rather than one the delivery never had.
            if let invoiceNo = live.invoiceNo {
                line(Loc.invoiceNoField, invoiceNo)
            }
            // One row per line on the paper, and none at all where a product was
            // never named: a supplier bill entered as a figure has no product and
            // no count, and a row reading "× 0" is one the app lost rather than
            // one the delivery never had.
            ForEach(Array(live.items.enumerated()), id: \.offset) { _, item in
                line(item.name, Loc.perPiece(qty: item.qty, cost: Money.text(item.unitCost, in: currency)))
            }

            FadedRule().padding(.vertical, 10)

            line(Loc.total, Money.text(live.total, in: currency), strong: true)
            line(
                Loc.youOwe,
                live.balance > 0 ? Money.text(live.balance, in: currency) : Loc.settledUp,
                tint: live.balance > 0 ? Nocturne.accent400 : Nocturne.neutral400
            )

            // Back to the sheet it was entered on, filled in with what it says
            // now. Saving from there rewrites this delivery rather than recording
            // a second one, and moves the shelf by the difference.
            Button(Loc.editBill) {
                router.editDelivery(live, product: live.items.first?.productUID.flatMap { store.product(uid: $0) })
            }
            .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 44, fontSize: 13.5))
            .padding(.top, 14)

            // Removal is a second tap, and the note is why: whatever this put on
            // the shelf comes back off it.
            VStack(alignment: .leading, spacing: 6) {
                Button(confirming ? Loc.tapAgainToRemove : Loc.removeSupplierBill) {
                    if confirming {
                        store.deletePurchase(id: live.id)
                        onClose()
                    } else {
                        withAnimation(Metrics.quick) { confirming = true }
                    }
                }
                .buttonStyle(.ghostMuted)

                Text(Loc.removeSupplierBillNote).nocturneText(.meta)
            }
            .padding(.top, 10)
        }
    }

    private func line(_ label: String, _ value: String, strong: Bool = false, tint: Color? = nil) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text(label).nocturneText(.meta)
            Spacer(minLength: 8)
            Text(value)
                .font(NocturneType.inter(strong ? 15 : 13))
                .foregroundStyle(tint ?? Nocturne.text)
        }
        .padding(.vertical, 3)
    }
}
