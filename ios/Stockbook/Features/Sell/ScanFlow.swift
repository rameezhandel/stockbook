import SwiftUI
import UIKit

/// Scanning a paper bill into the cart.
///
/// Owns the whole path — camera, recognition, matching — so the Sell screen only
/// has to say "start" and be handed a result. Nothing here writes to the store:
/// a scan fills the cart and stops, and the bill is saved by the same button that
/// saves a hand-typed one.
@MainActor
@Observable
final class ScanFlow {

    enum Stage: Equatable {
        case idle
        /// The camera is up.
        case capturing
        /// Recognition is running. Long enough on a full page to need saying.
        case reading
        /// Nothing usable came back.
        case unreadable
        /// Done. `unmatched` is what the catalogue could not place.
        case done(unmatched: [ScannedLine])
    }

    private(set) var stage: Stage = .idle

    /// Kept for the diagnostic sheet: what the camera actually read, before any
    /// of the parsing had an opinion about it. The first question about a bad
    /// scan is always "did it read the page at all", and this answers it without
    /// a debugger.
    private(set) var rawText: [String] = []

    func start() {
        rawText = []
        stage = .capturing
    }

    func cancel() {
        stage = .idle
    }

    func dismissResult() {
        stage = .idle
    }

    /// Reads the captured pages and fills the cart.
    ///
    /// Recognition is off the main actor: `.accurate` on a page of handwriting is
    /// not instant, and holding the main thread through it freezes the camera
    /// dismissal.
    func finish(pages: [UIImage], cart: Cart, products: [Product]) async {
        stage = .reading

        let pieces: [ScannedText] = await Task.detached(priority: .userInitiated) {
            pages.flatMap { page in
                (try? TextScanner.read(page)) ?? []
            }
        }.value

        rawText = pieces.sorted { $0.midY > $1.midY }.map(\.text)

        let outcome = ScanOutcome.from(BillScanParser.parse(pieces), products: products)
        guard !outcome.isEmpty else {
            stage = .unreadable
            return
        }

        cart.fill(from: outcome)
        stage = .done(unmatched: outcome.unmatched)
    }
}

/// What the scan could not place, and what the owner can do about it.
///
/// Shown above the cart rather than as an alert: these are lines that exist on
/// the paper in the owner's hand, and they need to stay visible while the rest of
/// the bill is checked, not be acknowledged and forgotten.
struct ScanLeftoversCard: View {
    let unmatched: [ScannedLine]
    let hasUnconfirmed: Bool
    let onSearch: (String) -> Void
    let onDismiss: () -> Void

    @Environment(\.currency) private var currency

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Glyph(Icon.scan, size: 15)
                    .foregroundStyle(Nocturne.accent)
                Text(headline)
                    .font(NocturneType.inter(12.5, .medium))
                    .foregroundStyle(Nocturne.text)
                Spacer(minLength: 6)
                Button(action: onDismiss) {
                    Glyph(Icon.close, size: 13)
                        .foregroundStyle(Nocturne.neutral500)
                        .minimumTouchTarget()
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Loc.done)
            }

            if hasUnconfirmed {
                Text(Loc.scanCheckFigures)
                    .nocturneText(.meta)
                    .padding(.top, 4)
            }

            ForEach(Array(unmatched.enumerated()), id: \.offset) { _, line in
                Button {
                    onSearch(line.name)
                } label: {
                    HStack(spacing: 8) {
                        Text(line.name)
                            .font(NocturneType.inter(12.5))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .lineLimit(1)
                        if let price = line.unitPrice {
                            Text(Money.text(price, in: currency))
                                .nocturneText(.meta)
                        }
                        Glyph(Icon.browseAll, size: 12)
                            .foregroundStyle(Nocturne.accent)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 9)
                    .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(.top, 6)
            }
        }
        .padding(11)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .hairline(Nocturne.accent, radius: Metrics.cardRadius)
    }

    private var headline: String {
        unmatched.isEmpty ? Loc.scanFilledIn : Loc.scanCouldNotPlace(unmatched.count)
    }
}

/// Shown while recognition runs. `.accurate` on a page of handwriting takes long
/// enough that silence reads as a hang.
struct ScanProgressOverlay: View {
    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
                .tint(Nocturne.accent)
            Text(Loc.scanReading)
                .nocturneText(.body)
        }
        .padding(24)
        .background(Nocturne.surface, in: RoundedRectangle(cornerRadius: Metrics.cardRadius, style: .continuous))
        .hairline(radius: Metrics.cardRadius)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Nocturne.scrim.ignoresSafeArea())
    }
}

/// Identifies the failure sheet. A type rather than a `Bool` so it can go
/// through `nocturneSheet(item:)` like every other sheet in the app.
struct ScanFailure: Identifiable {
    let id = "scan-failed"
}

/// Shown when nothing usable came back.
///
/// The raw text is the point. "It did not work" is not actionable; "here is the
/// nine words it managed off your bill" tells the owner whether to try again in
/// better light or give up on scanning that handwriting — and tells us whether
/// the parser or the camera is at fault.
struct ScanUnreadableSheet: View {
    let rawText: [String]
    let onRetry: () -> Void
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SheetHeader(title: Loc.scanRawTitle, onClose: onClose)

            Text(Loc.scanNothingRead)
                .nocturneText(.body)
                .padding(.bottom, 12)

            if rawText.isEmpty {
                Text("—")
                    .nocturneText(.meta)
                    .padding(.bottom, 12)
            } else {
                VStack(alignment: .leading, spacing: 3) {
                    ForEach(Array(rawText.enumerated()), id: \.offset) { _, line in
                        Text(line)
                            .font(NocturneType.inter(12))
                            .foregroundStyle(Nocturne.neutral400)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(11)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                .hairline(radius: Metrics.controlRadius)
                .padding(.bottom, 12)
            }

            Button(Loc.scanTryAgain, action: onRetry)
                .buttonStyle(PrimaryButtonStyle(fullWidth: true, height: 44, fontSize: 13.5))
        }
    }
}
