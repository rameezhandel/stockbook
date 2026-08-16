import SwiftUI

/// A text field in the Nocturne style.
///
/// Validation in this app is never a toast or red text — a required-but-empty
/// field simply carries an accent border, and the primary action goes disabled
/// with an explanatory label. That accent border is what `isRequiredAndEmpty`
/// drives; `emphasis` covers the other two border treatments (the selling-price
/// field's `accent-700`, and the accent border on an overridden price).
struct NocturneField: View {

    /// When a required-but-empty field starts wearing its accent border.
    ///
    /// `immediate` is right for a handful of fields — the product editor asks
    /// four questions and marking them at once reads as a checklist. On setup
    /// step 3 it does not scale: four products is twelve outlined fields on
    /// arrival, which reads as twelve errors rather than a prompt, before the
    /// owner has done anything wrong. `afterTouch` waits until a field has been
    /// visited and left empty, and lets the footer's gate line carry the rest.
    enum RequiredMarking {
        case immediate
        case afterTouch
    }

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
    var requiredMarking: RequiredMarking = .immediate
    var emphasis: Emphasis = .none
    var alignment: TextAlignment = .leading
    /// Rendered inside the field, before the value — the `SAR` on a price box.
    var prefix: String?
    var fontSize: CGFloat = 14
    /// Accessibility identifier, so UI tests can find a field whose visible
    /// label is drawn as a separate view rather than set on the control.
    var identifier: String?
    var onSubmit: (() -> Void)?

    /// Focus driven by the **screen** rather than by the field.
    ///
    /// A field left to itself cannot know what comes after it, so a keyboard
    /// toolbar cannot offer "Next". Where a screen wants that — setup step 3,
    /// with three boxes per product — it holds one `FocusState` for all of them
    /// and hands each field its tag. Fields that need nothing of the sort keep
    /// their own focus and pass neither.
    var focusTag: String?
    var focus: FocusState<String?>.Binding?

    @FocusState private var focused: Bool
    @State private var hasBeenFocused = false

    /// True when *this* field holds focus, whichever of the two owns it.
    private var isFocused: Bool {
        if let focus, let focusTag { return focus.wrappedValue == focusTag }
        return focused
    }

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
                        .modifier(FocusRouting(external: focus, tag: focusTag, own: $focused))
                        .tint(Nocturne.accent)          // caret-color
                        .submitLabel(onSubmit == nil ? .return : .done)
                        .onSubmit { onSubmit?() }
                        .accessibilityIdentifier(identifier ?? "")
                }
            }
            .padding(.horizontal, 10)
            .frame(height: height)
            .background(Nocturne.bg, in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous))
            .hairline(borderColor, radius: Metrics.controlRadius)
            .animation(Metrics.quick, value: borderColor)
        }
        .onChange(of: isFocused) { _, nowFocused in
            if nowFocused { hasBeenFocused = true }
        }
        // The accent border says "you are typing here". Dismissing the keyboard
        // through the responder chain — which is what a toolbar button does —
        // does not tell SwiftUI's `FocusState` anything, so without this the
        // field went on claiming focus it no longer had, and the border stayed
        // lit after the keyboard was gone.
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardDidHideNotification)) { _ in
            focused = false
        }
    }

    private var borderColor: Color {
        if isRequiredAndEmpty, requiredMarking == .immediate || hasBeenFocused {
            return Nocturne.accent
        }
        switch emphasis {
        case .changed: return Nocturne.accent
        case .sellingPrice: return Nocturne.accent700
        case .none: return isFocused ? Nocturne.accent : Nocturne.neutral800
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
        requiredMarking: RequiredMarking = .immediate,
        emphasis: Emphasis = .none,
        alignment: TextAlignment = .leading,
        prefix: String? = nil,
        fontSize: CGFloat = 14,
        identifier: String? = nil,
        focusTag: String? = nil,
        focus: FocusState<String?>.Binding? = nil
    ) -> NocturneField {
        NocturneField(
            label: label,
            placeholder: placeholder,
            text: text,
            height: height,
            keyboard: .decimalPad,
            isRequiredAndEmpty: isRequiredAndEmpty,
            requiredMarking: requiredMarking,
            emphasis: emphasis,
            alignment: alignment,
            prefix: prefix,
            fontSize: fontSize,
            identifier: identifier,
            focusTag: focusTag,
            focus: focus
        )
    }
}

/// Sends the field's focus either to the screen's `FocusState` or to its own.
///
/// A `ViewModifier` rather than a branch in `body`, because `.focused` has two
/// different shapes for the two cases and they cannot be unified inline.
private struct FocusRouting: ViewModifier {
    let external: FocusState<String?>.Binding?
    let tag: String?
    let own: FocusState<Bool>.Binding

    @ViewBuilder
    func body(content: Content) -> some View {
        if let external, let tag {
            content.focused(external, equals: tag)
        } else {
            content.focused(own)
        }
    }
}
