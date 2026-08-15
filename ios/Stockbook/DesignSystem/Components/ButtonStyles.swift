import SwiftUI

/// Primary action: a 1px accent border on transparent. Nothing in this design is
/// filled with the accent.
struct PrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    var fullWidth = false
    var height: CGFloat = Metrics.primaryButtonHeight
    var fontSize: CGFloat = 14

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(NocturneType.inter(fontSize, .medium))
            .foregroundStyle(Nocturne.accent)
            .frame(maxWidth: fullWidth ? .infinity : nil, minHeight: height)
            .padding(.horizontal, 14)
            .background(
                configuration.isPressed ? Nocturne.primaryPressed : Color.clear,
                in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous)
            )
            .hairline(Nocturne.accent, radius: Metrics.controlRadius)
            .opacity(isEnabled ? 1 : Nocturne.disabledOpacity)
            .animation(Metrics.quick, value: configuration.isPressed)
    }
}

/// Secondary action: a divider-weight border, text in the primary colour.
struct SecondaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    var fullWidth = false
    var height: CGFloat = Metrics.primaryButtonHeight
    var fontSize: CGFloat = 14

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(NocturneType.inter(fontSize, .medium))
            .foregroundStyle(Nocturne.text)
            .frame(maxWidth: fullWidth ? .infinity : nil, minHeight: height)
            .padding(.horizontal, 14)
            .background(
                configuration.isPressed ? Nocturne.secondaryPressed : Color.clear,
                in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous)
            )
            .hairline(Nocturne.divider, radius: Metrics.controlRadius)
            .opacity(isEnabled ? 1 : Nocturne.disabledOpacity)
            .animation(Metrics.quick, value: configuration.isPressed)
    }
}

/// Ghost action: accent text, no border. Used for "All", "Reset", "Void & put
/// stock back", "Remove this product".
struct GhostButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    var fontSize: CGFloat = 12
    var tint: Color = Nocturne.accent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(NocturneType.inter(fontSize, .medium))
            .foregroundStyle(tint)
            .padding(.horizontal, 4)
            .padding(.vertical, 4)
            .background(
                configuration.isPressed ? Nocturne.ghostPressed : Color.clear,
                in: RoundedRectangle(cornerRadius: 6, style: .continuous)
            )
            .opacity(isEnabled ? 1 : Nocturne.disabledOpacity)
            .contentShape(Rectangle())
            .animation(Metrics.quick, value: configuration.isPressed)
    }
}

/// A 36pt square secondary button holding a single glyph (the Today gear).
struct IconButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    var size: CGFloat = Metrics.iconButtonSize

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(Nocturne.text)
            .frame(width: size, height: size)
            .background(
                configuration.isPressed ? Nocturne.secondaryPressed : Color.clear,
                in: RoundedRectangle(cornerRadius: Metrics.controlRadius, style: .continuous)
            )
            .hairline(Nocturne.divider, radius: Metrics.controlRadius)
            .opacity(isEnabled ? 1 : Nocturne.disabledOpacity)
            .minimumTouchTarget()
            .animation(Metrics.quick, value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == PrimaryButtonStyle {
    static var primary: PrimaryButtonStyle { PrimaryButtonStyle() }
    /// Compact inline variant — the "＋ Add" in a screen header, the button
    /// inside an empty-state box.
    static var primaryCompact: PrimaryButtonStyle {
        PrimaryButtonStyle(height: 34, fontSize: 13)
    }
    static var primaryBlock: PrimaryButtonStyle {
        PrimaryButtonStyle(fullWidth: true)
    }
}

extension ButtonStyle where Self == SecondaryButtonStyle {
    static var secondary: SecondaryButtonStyle { SecondaryButtonStyle() }
    static var secondaryCompact: SecondaryButtonStyle {
        SecondaryButtonStyle(height: 34, fontSize: 12)
    }
    static var secondaryBlock: SecondaryButtonStyle {
        SecondaryButtonStyle(fullWidth: true)
    }
}

extension ButtonStyle where Self == GhostButtonStyle {
    static var ghost: GhostButtonStyle { GhostButtonStyle() }
    /// The destructive ghost link on the product editor.
    static var ghostMuted: GhostButtonStyle {
        GhostButtonStyle(fontSize: 12, tint: Nocturne.neutral500)
    }
}

extension ButtonStyle where Self == IconButtonStyle {
    static var iconOnly: IconButtonStyle { IconButtonStyle() }
}
