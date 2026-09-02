import SwiftUI

/// One of two to four answers, all of them on screen at once: an icon, a word,
/// and **the one in force filled in accent**.
///
/// Where a `DropdownField` hides the alternatives behind a tap, this shows them.
/// That is the right trade for a short list somebody chooses by comparing —
/// full payment against part payment, dark against light — and the wrong one for
/// fourteen currencies.
///
/// **Filled rather than outlined, the way `PeriodPicker`'s chips are.** An accent
/// outline against a dark surface is most of a hairline's worth of difference,
/// and on a row of four it stopped reading as a selection at all — the owner
/// could not tell at a glance which half of the book they were looking at. Both
/// rows on the book's screen answer "which one of these", so both answer it the
/// same way; the span row stays shorter, which is what keeps them apart.
///
/// Started life as `PaymentPill` inside the cart, and moved here the second time
/// a screen needed it rather than the third.
///
/// **The icon is optional, and four across a phone is why.** At 13pt with a
/// glyph and its gap, "Purchases" needs about 80pt and a quarter of a 360pt
/// screen is 77 — so the fourth chip on the book's row would have arrived with
/// its label cut in half. Two or three chips keep their icons.
struct ChoicePill: View {
    let title: String
    var icon: String?
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let icon { Glyph(icon, size: 14) }
                Text(title)
                    .font(NocturneType.inter(13, .medium))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .padding(.horizontal, 6)
            // The page's own background on the filled one, which is the only
            // colour guaranteed to have contrast against the accent in both
            // themes — a fixed white would vanish on the light one.
            .foregroundStyle(selected ? Nocturne.bg : Nocturne.neutral500)
            .frame(maxWidth: .infinity)
            .frame(height: 38)
            .background(
                selected ? Nocturne.accent : Color.clear,
                in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous)
            )
            .hairline(selected ? Nocturne.accent : Nocturne.neutral800, radius: Metrics.controlRadius)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}
