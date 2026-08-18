import SwiftUI

/// Opening a bill from history.
///
/// The bill is looked up **live from the store** rather than rendered from the
/// value that opened the sheet, so a correction made from in here redraws the
/// document in place — which is the only way to see that the tap did what it
/// said.
///
/// Correcting lives here rather than on the list row: editing and removing are
/// the app's two actions on saved history, and asking for a tap to open the bill
/// before either can be reached is the cheapest possible confirmation step.
struct BillSheet: View {
    let bill: Bill

    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(Cart.self) private var cart
    @Environment(\.currency) private var currency

    /// Armed by the first tap on Remove. A bill takes two, because removing one
    /// moves the shelf as well as the money.
    @State private var confirmingRemoval = false

    /// Falls back to the value it was opened with, which matters after a
    /// database replace has removed it from under the sheet.
    private var live: Bill {
        store.bills.first { $0.number == bill.number } ?? bill
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            SheetHeader(
                title: Loc.billDetailTitle,
                // What it lists, or — where it lists nothing — what it came to. A
                // bill entered as a figure is not "0 items"; that reads as a
                // document whose contents went missing.
                subtitle: live.isItemised
                    ? Loc.items(live.lines.count)
                    : Money.text(live.total, in: currency)
            ) {
                router.billDetail = nil
            }

            BillTemplate(bill: live, shopName: store.settings.ownerName)

            // The bill as something to send: the customer asking for "the
            // invoice" wants it on their phone, and plain text is what reaches
            // them there.
            ShareLink(item: plainText(live)) {
                Label(Loc.share, systemImage: Icon.share)
            }
            .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 44, fontSize: 13.5))

            // Back to the form it was typed on, filled in with what it says now.
            // Saving from there rewrites this bill rather than writing a second
            // one, and moves the shelf by the difference.
            Button(Loc.editBill) {
                cart.load(live, in: store)
                router.billDetail = nil
                router.tab = .sell
            }
            .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 44, fontSize: 13.5))

            // Removal is a second tap, and the note is why: what was on the bill
            // goes back on the shelf, which is the part that surprises people.
            VStack(alignment: .leading, spacing: 6) {
                Button(confirmingRemoval ? Loc.tapAgainToRemove : Loc.removeBill) {
                    if confirmingRemoval {
                        store.deleteBill(number: live.number)
                        router.billDetail = nil
                    } else {
                        withAnimation(Metrics.quick) { confirmingRemoval = true }
                    }
                }
                .buttonStyle(.ghostMuted)

                Text(Loc.removeBillNote).nocturneText(.meta)
            }
        }
    }

    private func plainText(_ bill: Bill) -> String {
        BillText.plainText(
            bill,
            shopName: store.settings.ownerName,
            currency: store.settings.currency,
            strings: Loc
        )
    }
}
