import SwiftUI

/// A text field in the Nocturne style.
///
/// Validation in this app is never a toast or red text — a required-but-empty
/// field simply carries an accent border, and the primary action goes disabled
/// with an explanatory label. That accent border is what `isRequiredAndEmpty`
/// drives; `emphasis` covers the other two border treatments (the selling-price
/// field's `accent-700`, and the accent border on an overridden price).
struct NocturneField: View {

    enum Emphasis {
        /// Ordinary field: neutral-800 border.
        case none
        /// The selling-price field — marked out as the "money in" number.
        case sellingPrice
        /// A value that has been changed from its default (an overridden price).
        case changed
    }

    var label: String?
    var placeholder: String
    @Binding var text: String
    var height: CGFloat = Metrics.inputHeight
    var keyboard: UIKeyboardType = .default
    var isRequiredAndEmpty: Bool = false
    var emphasis: Emphasis = .none
    var alignment: TextAlignment = .leading
    /// Rendered inside the field, before the value — the `SAR` on a price box.
    var prefix: String?
    var fontSize: CGFloat = 14
    var onSubmit: (() -> Void)?

    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            if let label {
                Text(label).nocturneText(.fieldLabel)
            }

            HStack(spacing: 6) {
                if let prefix {
                    Text(prefix)
                        .font(NocturneType.inter(fontSize))
                        .foregroundStyle(Nocturne.neutral500)
                }

                ZStack(alignment: alignment == .trailing ? .trailing : .leading) {
                    if text.isEmpty {
                        Text(placeholder)
                            .font(NocturneType.inter(fontSize))
                            .foregroundStyle(Nocturne.neutral500)
                            .allowsHitTesting(false)
                    }
                    TextField("", text: $text)
                        .font(NocturneType.inter(fontSize))
                        .foregroundStyle(valueColor)
                        .keyboardType(keyboard)
                        .multilineTextAlignment(alignment)
                        .focused($focused)
                        .tint(Nocturne.accent)          // caret-color
                        .submitLabel(onSubmit == nil ? .return : .done)
                        .onSubmit { onSubmit?() }
                }
            }
            .padding(.horizontal, 10)
            .frame(height: height)
            .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
            .hairline(borderColor, radius: Metrics.controlRadius)
            .animation(Metrics.quick, value: borderColor)
        }
    }

    private var borderColor: Color {
        if isRequiredAndEmpty { return Nocturne.accent }
        switch emphasis {
        case .changed: return Nocturne.accent
        case .sellingPrice: return Nocturne.accent700
        case .none: return focused ? Nocturne.accent : Nocturne.neutral800
        }
    }

    private var valueColor: Color {
        emphasis == .changed ? Nocturne.accent300 : Nocturne.text
    }
}

/// A numeric field bound to an optional-ish decimal held as text.
///
/// Prices and counts are edited as strings so a half-typed value ("1." or "")
/// is representable — the same thing the prototype does, and the reason the
/// completeness gates test for "filled in" rather than "parses to a number".
extension NocturneField {
    static func number(
        label: String? = nil,
        placeholder: String = "",
        text: Binding<String>,
        height: CGFloat = Metrics.inputHeight,
        isRequiredAndEmpty: Bool = false,
        emphasis: Emphasis = .none,
        alignment: TextAlignment = .leading,
        prefix: String? = nil,
        fontSize: CGFloat = 14
    ) -> NocturneField {
        NocturneField(
            label: label,
            placeholder: placeholder,
            text: text,
            height: height,
            keyboard: .decimalPad,
            isRequiredAndEmpty: isRequiredAndEmpty,
            emphasis: emphasis,
            alignment: alignment,
            prefix: prefix,
            fontSize: fontSize
        )
    }
}
