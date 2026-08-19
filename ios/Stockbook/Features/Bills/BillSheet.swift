import SwiftUI
import PhotosUI

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

    @State private var viewing: String?
    @State private var picked: PhotosPickerItem?
    @State private var takingPhoto = false
    @State private var trouble: String?

    private let photos = PhotoStore()

    /// Falls back to the value it was opened with, which matters after a
    /// database replace has removed it from under the sheet.
    private var live: Bill {
        store.bills.first { $0.number == bill.number } ?? bill
    }

    var body: some View {
        // One photograph, filling the sheet. Opening it replaces what is under it
        // rather than stacking a second sheet on top: there is one thing to look
        // at, and a way back.
        if let viewing {
            PhotoViewer(
                id: viewing,
                onRemove: {
                    // The book forgets it first, then the file goes. In that
                    // order a crash in between leaves a picture nothing points at
                    // — which the sweep collects — rather than a bill pointing at
                    // nothing.
                    store.detachPhoto(billNumber: live.number, photoID: viewing)
                    photos.delete(id: viewing)
                    self.viewing = nil
                },
                onClose: { self.viewing = nil }
            )
        } else {
            details
        }
    }

    private var details: some View {
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

            // The paper itself, where the owner photographed it. Under the bill
            // rather than beside it: the figures are what the sheet is for, and
            // the picture is the evidence behind them.
            if !live.photoIDs.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Kicker(Loc.billPhotos)
                    PhotoStrip(ids: live.photoIDs) { viewing = $0 }
                }
            }

            photoButtons(chooseTitle: Loc.chooseFromPhotos)

            if let trouble {
                Text(trouble).nocturneText(.meta).foregroundStyle(Nocturne.accent400)
            }

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
                        // Its pictures go with it. Swept rather than deleted by
                        // name, so a photograph another bill also names — after
                        // a restore, say — is not taken away from that one.
                        photos.sweep(keeping: store.photoIDsInUse())
                        router.billDetail = nil
                    } else {
                        withAnimation(Metrics.quick) { confirmingRemoval = true }
                    }
                }
                .buttonStyle(.ghostMuted)

                Text(Loc.removeBillNote).nocturneText(.meta)
            }
        }
        .fullScreenCover(isPresented: $takingPhoto) {
            CameraSheet { data in
                takingPhoto = false
                guard let data else { return }
                keep(data)
            }
            .ignoresSafeArea()
        }
        .onChange(of: picked) { _, item in
            guard let item else { return }
            Task {
                // Loaded as `Data` rather than as an `Image`: the picker can hand
                // back something that is not readable as one, and `PhotoStore` is
                // where a photograph is measured and shrunk.
                let data = try? await item.loadTransferable(type: Data.self)
                picked = nil
                guard let data else {
                    trouble = Loc.couldNotReadThatPhoto
                    return
                }
                keep(data)
            }
        }
    }

    /// The two ways in.
    ///
    /// `chooseTitle` is passed rather than read inside: `PhotosPicker`'s label is
    /// a plain closure, not a main-actor one, and `Loc` is main-actor isolated —
    /// so the string is read out here, where the isolation holds, and the closure
    /// captures an ordinary `String`.
    private func photoButtons(chooseTitle: String) -> some View {
        HStack(spacing: 12) {
            // Absent on a phone with no camera — a simulator, mostly — rather
            // than offered and then failing.
            if CameraSheet.isAvailable {
                Button(Loc.takePhoto) { takingPhoto = true }
                    .buttonStyle(GhostButtonStyle(fontSize: 12.5, horizontalPadding: 0))
            }
            PhotosPicker(selection: $picked, matching: .images, photoLibrary: .shared()) {
                Text(chooseTitle)
                    .font(NocturneType.inter(12.5, .medium))
                    .foregroundStyle(Nocturne.accent)
            }
        }
    }

    /// Shrinks and keeps one photograph, then tells the bill about it.
    ///
    /// The file is written before the book names it, so a crash in between leaves
    /// a picture nothing points at — which the sweep collects — rather than a
    /// bill pointing at nothing.
    private func keep(_ data: Data) {
        guard let id = photos.save(data) else {
            trouble = Loc.couldNotReadThatPhoto
            return
        }
        store.attachPhoto(billNumber: live.number, photoID: id)
        trouble = nil
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
