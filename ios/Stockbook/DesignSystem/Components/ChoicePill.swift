import SwiftUI

/// One of two or three answers, all of them on screen at once: an icon, a word,
/// and an accent outline on the one in force.
///
/// Where a `DropdownField` hides the alternatives behind a tap, this shows them.
/// That is the right trade for a short list somebody chooses by comparing —
/// full payment against part payment, dark against light — and the wrong one for
/// fourteen currencies.
///
/// Started life as `PaymentPill` inside the cart, and moved here the second time
/// a screen needed it rather than the third.
struct ChoicePill: View {
    let title: String
    let icon: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Glyph(icon, size: 14)
                Text(title).font(NocturneType.inter(13, .medium))
            }
            .foregroundStyle(selected ? Nocturne.accent : Nocturne.neutral500)
            .frame(maxWidth: .infinity)
            .frame(height: 38)
            .hairline(selected ? Nocturne.accent : Nocturne.neutral800, radius: Metrics.controlRadius)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected, .isButton] : .isButton)
    }
}
