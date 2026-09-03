import SwiftUI

/// The slip for one payment: what was taken, from whom, and where the account
/// stands now.
///
/// **Two ways in, two shapes, the same page.** Straight after taking the money
/// this is full-screen and opaque, like the bill's own receipt: the payment is
/// written, there is nothing left to edit, and it is the page the owner turns to
/// face the customer. Looked up afterwards — from the payments list, or on the
/// way to a correction — it is a sheet over whatever you were reading, exactly
/// as a bill opened from a list is. `PaymentReceiptSheet` is that second shape.
///
/// **Drawn from `PaymentReceiptDocument` — the same structure the PDF draws.**
/// Not a screen that happens to show the same figures: the same wording, the
/// same order, the same formatting, decided once in shared code and tested
/// there. What the customer is shown on the phone and what comes out of the
/// printer cannot disagree, because there is nothing for them to disagree about.
struct PaymentReceiptOverlay: View {
    let receipt: PaymentReceipt

    @Environment(StockbookStore.self) private var store
    @Environment(\.topSafeInset) private var topInset
    @Environment(\.bottomSafeInset) private var bottomInset

    /// The check pops rather than fades, exactly as the bill's does: it is the
    /// same moment, and the overshoot is what makes it read as confirmation.
    @State private var checkScale: CGFloat = 0.4

    private var document: PaymentReceiptDocument {
        PaymentReceiptDocument.make(receipt: receipt, settings: store.settings, strings: Loc)
    }

    var body: some View {
        let slip = document
        VStack(spacing: 0) {
            header(slip)
            page(slip)
            actions(slip)
        }
        .padding(.horizontal, Metrics.screenPadding)
        .padding(.top, max(8, 66 - topInset))
        .padding(.bottom, max(bottomInset, 24))
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Nocturne.bg.ignoresSafeArea())
        .onAppear {
            withAnimation(.spring(response: 0.34, dampingFraction: 0.52)) {
                checkScale = 1
            }
        }
    }

    /// The tick belongs to the moment the money was taken. The same page looked
    /// up later comes through `PaymentReceiptSheet`, which has neither tick nor
    /// confirmation on it.
    private func header(_ document: PaymentReceiptDocument) -> some View {
        HStack(spacing: 11) {
            Glyph(Icon.confirm, size: 18)
                .foregroundStyle(Nocturne.accent)
                .frame(width: 36, height: 36)
                .overlay(Circle().strokeBorder(Nocturne.accent, lineWidth: 1))
                .scaleEffect(checkScale)
            Text(Loc.paymentSaved)
                .font(NocturneType.inter(18, .medium))
            Spacer(minLength: 0)
        }
        .padding(.bottom, 18)
    }

    private func page(_ document: PaymentReceiptDocument) -> some View {
        ScrollView {
            PaymentReceiptBody(document: document)
        }
        .scrollBounceBehavior(.basedOnSize)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func actions(_ document: PaymentReceiptDocument) -> some View {
        PaymentReceiptActions(receipt: receipt, document: document)
    }
}

/// The same slip as a sheet, for a payment being looked up rather than taken.
///
/// A bill opened from a list arrives this way and a receipt did not — it took
/// the whole screen, which reads as something having just happened and leaves
/// the owner nothing behind it to go back to.
///
/// **Shaped like `BillSheet`, because it is the same kind of thing.** The close
/// and the share sit in the header where a sheet's chrome belongs, and removal
/// is a ghost button at the foot behind a second tap. The full-screen
/// confirmation keeps its worded Done: there, finishing is the action, not
/// closing a document.
///
/// **No scroll of its own.** The sheet already scrolls its content, and a second
/// scroll nested in the first is the trap that has emptied a list on this
/// codebase before.
struct PaymentReceiptSheet: View {
    let receipt: PaymentReceipt

    @Environment(StockbookStore.self) private var store
    @Environment(AppRouter.self) private var router

    @State private var file: StatementFile?
    @State private var confirmingRemoval = false

    private var document: PaymentReceiptDocument {
        PaymentReceiptDocument.make(receipt: receipt, settings: store.settings, strings: Loc)
    }

    var body: some View {
        let slip = document
        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(
                title: slip.docType,
                // The figure, which is what the owner opened this to check.
                subtitle: slip.amountValue,
                onShare: { file = receiptFile(slip, at: receipt.at) },
                onClose: { router.paymentReceipt = nil }
            )

            PaymentReceiptBody(document: slip)

            // Removal is a second tap, exactly as a bill's is: it takes a figure
            // back out of somebody's account, and the balance moves the moment
            // it lands.
            //
            // Deleted here rather than by a closure the presenter hands down,
            // because the sheet is given the receipt itself and `paymentId` with
            // it — the same shape `BillSheet` deletes in. Which of the two books
            // to reach for is what `isSupplier` decides; money in and money out
            // are separate types and neither store will find the other's id.
            VStack(alignment: .leading, spacing: 6) {
                Button(confirmingRemoval ? Loc.tapAgainToRemove : Loc.deleteThisPayment) {
                    if confirmingRemoval {
                        if receipt.party.isSupplier {
                            store.deleteSupplierPayment(id: receipt.paymentId)
                        } else {
                            store.deletePayment(id: receipt.paymentId)
                        }
                        router.paymentReceipt = nil
                    } else {
                        withAnimation(Metrics.quick) { confirmingRemoval = true }
                    }
                }
                .buttonStyle(.ghostMuted)

                Text(Loc.removePaymentNote).nocturneText(.meta)
            }
            .padding(.top, 18)
        }
        .sheet(item: $file) { ShareSheet(url: $0.url) }
    }
}

/// The slip as a file, named after whoever it was for and the day it was taken.
///
/// A failure returns nil and nothing opens, which is the honest outcome the
/// other printed pages already settled on. Shared by both shapes of the page so
/// the file the counter hands out and the file a copy is asked for a week later
/// are the same document under the same name.
private func receiptFile(_ document: PaymentReceiptDocument, at moment: Date) -> StatementFile? {
    let name = document.partyName
        .replacingOccurrences(of: "[^A-Za-z0-9]+", with: "-", options: .regularExpression)
        .trimmingCharacters(in: CharacterSet(charactersIn: "-"))
        .lowercased()
    guard let url = try? PaymentReceiptPDF.write(
        document,
        fileName: Loc.receiptFileName(name, Copy.fileDate(moment))
    ) else { return nil }
    return StatementFile(url: url)
}

/// Print it now or never: the customer is still at the counter, and this is the
/// moment they want the slip.
///
/// Worded buttons rather than the sheet's header chrome, because this page is a
/// confirmation: the owner is being told the payment landed and then dismissing
/// it, and Done is the thing they are doing. There is no removal here either —
/// the money was just taken, and a delete beside the tick invites the wrong tap.
private struct PaymentReceiptActions: View {
    let receipt: PaymentReceipt
    let document: PaymentReceiptDocument

    @Environment(AppRouter.self) private var router

    @State private var file: StatementFile?

    var body: some View {
        VStack(spacing: 8) {
            Button {
                file = receiptFile(document, at: receipt.at)
            } label: {
                Label(Loc.sharePdf, systemImage: Icon.share)
            }
            .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 44, fontSize: 13.5))

            Button(Loc.done) { router.paymentReceipt = nil }
                .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 46))
        }
        .padding(.top, 14)
        .sheet(item: $file) { ShareSheet(url: $0.url) }
    }
}

/// Everything the slip states, in the order the printed page states it.
///
/// Neither sized nor scrolled here: the full-screen page gives it a scroll view
/// and the sheet lets its own scroll carry it.
struct PaymentReceiptBody: View {
    let document: PaymentReceiptDocument

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {

            // The letterhead, as it prints: the shop, then what the paper is.
            Text(document.shopName).font(NocturneType.inter(15, .medium))
            if !document.shopAddressLines.isEmpty {
                Text(document.shopAddressLines.joined(separator: ", "))
                    .nocturneText(.meta)
                    .padding(.top, 2)
            }

            fact(document.addressedToLabel, document.partyName, document.partyLines.joined(separator: " · "))
                .padding(.top, 16)

            HStack(alignment: .top, spacing: 12) {
                fact(document.receiptLabel, document.receiptValue).frame(maxWidth: .infinity, alignment: .leading)
                fact(document.dateLabel, document.dateValue).frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.top, 12)

            // The figure the page exists to state, set alone so it is the one
            // thing read across a counter.
            VStack(alignment: .leading, spacing: 4) {
                Text(document.amountLabel.uppercased())
                    .nocturneText(.meta)
                    .foregroundStyle(Nocturne.accent400)
                Text(document.amountValue)
                    .font(NocturneType.inter(26, .semibold))
                    .foregroundStyle(Nocturne.accent)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous)
                    .fill(Nocturne.surface)
            )
            .padding(.top, 16)

            if let noteLabel = document.noteLabel {
                VStack(alignment: .leading, spacing: 2) {
                    Text(noteLabel.uppercased()).nocturneText(.meta)
                    Text(document.noteValue ?? "").font(NocturneType.inter(13.5))
                }
                .padding(.top, 14)
            }

            Text(document.summaryTitle.uppercased())
                .nocturneText(.meta)
                .padding(.top, 18)
                .padding(.bottom, 8)

            // By position rather than by identity: two lines of a summary
            // can legitimately read the same, and `StatementDocument.Row`
            // carries no id of its own.
            ForEach(Array(document.summaryRows.enumerated()), id: \.offset) { _, row in
                summaryLine(row.label, row.deduction ? "(\(row.value))" : row.value)
            }

            HStack(spacing: 6) {
                Text(document.closingLabel).font(NocturneType.inter(13.5, .medium))
                Spacer(minLength: 8)
                Text(document.closingValue)
                    .font(NocturneType.inter(17, .semibold))
                    .foregroundStyle(Nocturne.accent)
            }
            .padding(.top, 6)

        Text(document.footnote)
            .nocturneText(.meta)
            .padding(.top, 16)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// A label with the fact under it, as the printed page sets its boxed facts.
    private func fact(_ label: String, _ value: String, _ detail: String? = nil) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label.uppercased())
                .nocturneText(.meta)
                .foregroundStyle(Nocturne.accent400)
            Text(value).font(NocturneType.inter(14, .medium))
            if let detail, !detail.isBlank {
                Text(detail).nocturneText(.meta)
            }
        }
    }

    private func summaryLine(_ label: String, _ value: String) -> some View {
        HStack(spacing: 6) {
            Text(label)
                .font(NocturneType.inter(13))
                .foregroundStyle(Nocturne.neutral400)
            Spacer(minLength: 8)
            Text(value).font(NocturneType.inter(13))
        }
    }
}
