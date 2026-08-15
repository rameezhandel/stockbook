import SwiftUI

/// Nocturne colour tokens, transcribed from `design_handoff_stockbook/styles.css`.
///
/// Stockbook is dark-only (`UIUserInterfaceStyle = Dark` in the build settings),
/// so these are literal values rather than adaptive asset-catalog colours. This
/// file is the only place a hex literal is allowed to appear — the handoff is
/// explicit that every colour in the UI comes from a token.
enum Nocturne {

    // MARK: Ground

    /// App background, and the field background inside sheets.
    static let bg = Color(hex: 0x161826)
    /// Cards, rows, tab bar, bottom sheets.
    static let surface = Color(hex: 0x232532)
    /// Primary text.
    static let text = Color(hex: 0xE9E9ED)

    // MARK: Accent ramp

    /// Primary action outlines, active tab, focus ring, required-field outline.
    ///
    /// Nothing in this design is *filled* with the accent — primary buttons are
    /// a 1px accent border on transparent.
    static let accent = Color(hex: 0x9184D9)
    /// Edited-price value, warning text on tinted ground.
    static let accent300 = Color(hex: 0xD2CEFD)
    /// Money owed, low/zero stock, secondary accent text.
    static let accent400 = Color(hex: 0xB5ABFC)
    /// Selling-price input border — marks the "money in" field.
    static let accent700 = Color(hex: 0x5D5294)
    /// Gradient start on the "Sold today" stat card.
    static let accent900 = Color(hex: 0x2B2741)

    // MARK: Neutral ramp

    /// Body copy on dark surfaces.
    static let neutral400 = Color(hex: 0xB2B6CA)
    /// Meta text, labels, inactive tab labels, muted icons.
    static let neutral500 = Color(hex: 0x9397AB)
    /// Empty-state icons only — too dark for text at 11–13px.
    static let neutral600 = Color(hex: 0x75798C)
    /// Borders, dividers, dashed empty-state outlines.
    static let neutral800 = Color(hex: 0x3F424D)
    /// Hairlines: `rgba(233,233,237,.16)`.
    static let divider = Color(hex: 0xE9E9ED, opacity: 0.16)

    // MARK: Derived

    /// Bottom-sheet scrim: `rgba(16,17,28,0.74)`.
    static let scrim = Color(hex: 0x10111C, opacity: 0.74)

    /// `linear-gradient(155deg, var(--color-accent-900), var(--color-surface))`
    /// on the "Sold today" stat card. 155° in CSS is measured clockwise from
    /// north, which lands just off vertical — hence the near-vertical unit points.
    static let soldTodayGradient = LinearGradient(
        colors: [accent900, surface],
        startPoint: UnitPoint(x: 0.29, y: 0.05),
        endPoint: UnitPoint(x: 0.71, y: 0.95)
    )

    // MARK: Interaction tints
    //
    // Pressed/hover states come off the accent ramp rather than from opacity on
    // the whole control. See `ButtonStyles.swift`.

    static let primaryPressed = accent.opacity(0.22)
    static let secondaryPressed = text.opacity(0.14)
    static let ghostPressed = accent.opacity(0.18)
    /// Disabled controls drop to 45% — the one place opacity carries state.
    static let disabledOpacity: Double = 0.45
}

extension Color {
    /// Builds a colour from a `0xRRGGBB` literal in sRGB.
    init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}
