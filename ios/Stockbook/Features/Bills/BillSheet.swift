import SwiftUI

/// Opening a bill from history.
///
/// The bill is looked up **live from the store** rather than rendered from the
/// value that opened the sheet, so voiding from in here redraws the document in
/// place — the voided mark and the note appear on the thing you are looking at,
/// which is the only way to see that the tap did what it said.
///
/// Voiding lives here rather than on the list row: it is the app's one
/// destructive action on history, and asking for a tap to open the bill before
/// it can be reached is the cheapest possible confirmation step.
struct BillSheet: View {
    let bill: Bill

    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router

    /// Falls back to the value it was opened with, which matters after a
    /// database replace has removed it from under the sheet.
    private var live: Bill {
        store.bills.first { $0.number == bill.number } ?? bill
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            SheetHeader(
                title: Loc.billDetailTitle,
                subtitle: Loc.items(live.lines.count)
            ) {
                router.billDetail = nil
            }

            BillTemplate(bill: live, shopName: store.settings.ownerName)

            if !live.voided {
                Button(Loc.voidAndRestock) {
                    store.void(live)
                }
                .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 44, fontSize: 13.5))
            }
        }
    }
}
