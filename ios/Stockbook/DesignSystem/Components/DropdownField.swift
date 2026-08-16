import SwiftUI

/// The shared look of a "pick one of these" row: a leading mark, the current
/// choice, and a caret, over a `Menu` holding the list.
///
/// A `Menu` wrapping a `Picker` rather than a hand-built list. The system menu
/// already does the checkmark, the scrolling and the dismissal correctly on a
/// phone held one-handed behind a counter; the label is ours, the list is the
/// platform's.
struct DropdownField<MenuContent: View>: View {
    var label: String?
    /// The short recognisable stamp on the left — a currency symbol, a language
    /// code. Sized so both dropdowns line their titles up.
    let mark: String
    let title: String
    var accessibilityName: String
    @ViewBuilder var menu: MenuContent

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            if let label {
                Text(label).nocturneText(.fieldLabel)
            }

            Menu {
                menu
            } label: {
                HStack(spacing: 9) {
                    Text(mark)
                        .font(NocturneType.inter(15, .medium))
                        .foregroundStyle(Nocturne.accent)
                        .frame(minWidth: 34, alignment: .leading)

                    Text(title)
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
            .accessibilityLabel(accessibilityName)
            .accessibilityValue(title)
        }
    }
}

/// Picking the one currency the shop bills in. Used on setup step 1 and in
/// Settings, which is the whole reason it is a component and not two blocks.
struct CurrencyField: View {
    var label: String?
    @Binding var currency: Currency

    var body: some View {
        DropdownField(
            label: label,
            // The symbol as it will appear on a bill: the part the owner
            // actually recognises. The code and name are there to confirm it.
            mark: currency.symbol.trimmed,
            title: Loc.currencyName(currency),
            accessibilityName: label ?? Loc.currencySection
        ) {
            Picker("", selection: $currency) {
                ForEach(Currency.supported) { option in
                    Text(Loc.currencyRow(option)).tag(option)
                }
            }
        }
    }
}

/// Picking the interface language.
///
/// The list is written in the languages themselves, never translated into the
/// one currently showing: somebody who has landed in a language they cannot read
/// has to be able to find their way out of it by recognising the shape of their
/// own, and "ಕನ್ನಡ" does that where "Kannada" would not.
struct LanguageField: View {
    var label: String?
    @Binding var language: AppLanguage

    var body: some View {
        DropdownField(
            label: label,
            mark: language.rawValue.uppercased(),
            title: language.endonym,
            accessibilityName: label ?? Loc.languageSection
        ) {
            Picker("", selection: $language) {
                ForEach(AppLanguage.allCases) { option in
                    Text(option.endonym).tag(option)
                }
            }
        }
    }
}
