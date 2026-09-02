import SwiftUI

/// The slip for one payment: what was taken, from whom, and where the account
/// stands now.
///
/// Full-screen and opaque, like the bill's receipt, and for the same reason: the
/// payment is written and there is nothing left to edit here. It is the page the
/// owner turns to face the customer.
///
/// **Drawn from `PaymentReceiptDocument` — the same structure the PDF draws.**
/// Not a screen that happens to show the same figures: the same wording, the
/// same order, the same formatting, decided once in shared code and tested
/// there. What the customer is shown on the phone and what comes out of the
/// printer cannot disagree, because there is nothing for them to disagree about.
struct PaymentReceiptOverlay: View {
    let receipt: PaymentReceipt

    @Environment(AppRouter.self) private var router
    @Environment(StockbookStore.self) private var store
    @Environment(\.topSafeInset) private var topInset
    @Environment(\.bottomSafeInset) private var bottomInset

    /// The check pops rather than fades, exactly as the bill's does: it is the
    /// same moment, and the overshoot is what makes it read as confirmation.
    @State private var checkScale: CGFloat = 0.4

    /// The rendered slip, waiting for the share sheet.
    @State private var file: StatementFile?

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
        .sheet(item: $file) { ShareSheet(url: $0.url) }
        .onAppear {
            guard router.paymentReceiptIsNew else { return }
            withAnimation(.spring(response: 0.34, dampingFraction: 0.52)) {
                checkScale = 1
            }
        }
    }

    /// Only a payment just taken gets the tick. The same page reached from a
    /// correction is a document being looked up, and a confirmation on it would
    /// be claiming something happened that did not.
    private func header(_ document: PaymentReceiptDocument) -> some View {
        HStack(spacing: 11) {
            if router.paymentReceiptIsNew {
                Glyph(Icon.confirm, size: 18)
                    .foregroundStyle(Nocturne.accent)
                    .frame(width: 36, height: 36)
                    .overlay(Circle().strokeBorder(Nocturne.accent, lineWidth: 1))
                    .scaleEffect(checkScale)
            }
            Text(router.paymentReceiptIsNew ? Loc.paymentSaved : document.docType)
                .font(NocturneType.inter(18, .medium))
            Spacer(minLength: 0)
        }
        .padding(.bottom, 18)
    }

    private func page(_ document: PaymentReceiptDocument) -> some View {
        ScrollView {
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
        .scrollBounceBehavior(.basedOnSize)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func actions(_ document: PaymentReceiptDocument) -> some View {
        VStack(spacing: 8) {
            // Print it now or never: the customer is still at the counter, and
            // this is the moment they want the slip.
            Button {
                share(document)
            } label: {
                Label(Loc.sharePdf, systemImage: Icon.share)
            }
            .buttonStyle(SecondaryButtonStyle(fullWidth: true, height: 44, fontSize: 13.5))

            Button(Loc.done) { router.paymentReceipt = nil }
                .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 46))
        }
        .padding(.top, 14)
    }

    /// A failure leaves `file` nil and nothing opens, which is the honest
    /// outcome the other printed pages already settled on.
    private func share(_ document: PaymentReceiptDocument) {
        let name = document.partyName
            .replacingOccurrences(of: "[^A-Za-z0-9]+", with: "-", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))
            .lowercased()
        guard let url = try? PaymentReceiptPDF.write(
            document,
            fileName: Loc.receiptFileName(name, Copy.fileDate(receipt.at))
        ) else { return }
        file = StatementFile(url: url)
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
