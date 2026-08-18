import SwiftUI

/// One delivery, opened from the book.
///
/// The bill sheet's mirror, including where voiding lives: on the document rather
/// than on the list row, so the thing being undone is on screen while it is
/// undone. Voiding a delivery takes its stock back off the shelf, which the
/// button says out loud — that is the part that surprises people.
struct PurchaseSheet: View {
    let purchase: Purchase
    let onClose: () -> Void

    @Environment(StockbookStore.self) private var store
    @Environment(\.currency) private var currency

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
            line(live.name, Loc.perPiece(qty: live.qty, cost: Money.text(live.unitCost, in: currency)))

            FadedRule().padding(.vertical, 10)

            line(Loc.total, Money.text(live.total, in: currency), strong: true)
            line(
                Loc.youOwe,
                live.balance > 0 ? Money.text(live.balance, in: currency) : Loc.settledUp,
                tint: live.balance > 0 ? Nocturne.accent400 : Nocturne.neutral400
            )

            if live.voided {
                Text(Loc.purchaseVoidedNote)
                    .nocturneText(.meta)
                    .padding(.top, 14)
            } else {
                // Two taps, because this one moves stock as well as money.
                Button(confirming ? Loc.tapAgainToRemove : Loc.voidAndRemoveStock) {
                    if confirming {
                        store.voidPurchase(id: live.id)
                        onClose()
                    } else {
                        withAnimation(Metrics.quick) { confirming = true }
                    }
                }
                .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 44, fontSize: 13.5))
                .padding(.top, 14)
            }
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
