import SwiftUI

/// Picking the one currency the shop bills in.
///
/// A `Menu` wrapping a `Picker` rather than a hand-built list: fourteen options
/// is too many for a row of pills and too few to deserve its own screen, and the
/// system menu already does the checkmark, the scrolling and the dismissal
/// correctly on a phone held one-handed behind a counter. The label is ours; the
/// list is the platform's.
///
/// Used twice — setup step 1 and Settings — which is the whole reason it is a
/// component rather than two similar blocks.
struct CurrencyField: View {
    var label: String?
    @Binding var currency: Currency

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            if let label {
                Text(label).nocturneText(.fieldLabel)
            }

            Menu {
                Picker("", selection: $currency) {
                    ForEach(Currency.supported) { option in
                        Text(Loc.currencyRow(option)).tag(option)
                    }
                }
            } label: {
                HStack(spacing: 9) {
                    // The symbol, at the size it will appear on a bill. This is
                    // the part the owner actually recognises — the code and the
                    // name are there to confirm it.
                    Text(currency.symbol.trimmed)
                        .font(NocturneType.inter(15, .medium))
                        .foregroundStyle(Nocturne.accent)
                        .frame(minWidth: 34, alignment: .leading)

                    Text(Loc.currencyName(currency))
                        .font(NocturneType.inter(14))
                        .foregroundStyle(Nocturne.text)
                        .lineLimit(1)

                    Spacer(minLength: 6)

                    Glyph(Icon.chooseFromList, size: 13)
                        .foregroundStyle(Nocturne.neutral500)
                }
                .padding(.horizontal, 10)
                .frame(height: Metrics.inputHeight)
                .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
                .hairline(radius: Metrics.controlRadius)
                .contentShape(Rectangle())
            }
            .accessibilityLabel(label ?? Loc.currencySection)
            .accessibilityValue(Loc.currencyName(currency))
        }
    }
}
