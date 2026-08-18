import SwiftUI

/// Nocturne colour tokens, transcribed from `design_handoff_stockbook/styles.css`
/// — in whichever theme is in force.
///
/// **Two palettes, one set of names.** Every screen reads `Nocturne.surface` and
/// gets the surface of the theme the owner chose; nothing outside this file knows
/// there is more than one. That is what keeps a second theme from becoming a
/// second design: a screen cannot forget to handle light, because it never asks.
///
/// This file is the only place a hex literal is allowed to appear — the handoff
/// is explicit that every colour in the UI comes from a token.
enum Nocturne {

    /// The palette in force. Set by `Nocturne.use`, exactly as `L10n.use` sets
    /// the language, and read on the main actor by every view body.
    ///
    /// A plain holder rather than something observable: theme changes are rare
    /// and the app answers them the way it answers a language change, by
    /// rebuilding the whole tree (see `RootView`), which is both simpler and more
    /// thorough than threading an environment value into 199 call sites.
    private(set) static var palette = Palette.dark

    static func use(_ theme: AppTheme) {
        palette = theme == .light ? .light : .dark
    }

    // MARK: Ground

    /// App background, and the field background inside sheets.
    static var bg: Color { palette.bg }
    /// Cards, rows, tab bar, bottom sheets.
    static var surface: Color { palette.surface }
    /// Primary text.
    static var text: Color { palette.text }

    // MARK: Accent ramp

    /// Primary action outlines, active tab, focus ring, required-field outline.
    ///
    /// Nothing in this design is *filled* with the accent — primary buttons are
    /// a 1px accent border on transparent.
    static var accent: Color { palette.accent }
    /// Edited-price value, warning text on tinted ground.
    static var accent300: Color { palette.accent300 }
    /// Money owed, low/zero stock, secondary accent text.
    static var accent400: Color { palette.accent400 }
    /// Selling-price input border — marks the "money in" field.
    static var accent700: Color { palette.accent700 }
    /// Gradient start on the "Sold today" stat card.
    static var accent900: Color { palette.accent900 }

    // MARK: Neutral ramp

    /// Body copy on cards.
    static var neutral400: Color { palette.neutral400 }
    /// Meta text, labels, inactive tab labels, muted icons.
    static var neutral500: Color { palette.neutral500 }
    /// Empty-state icons only — too low-contrast for text at 11–13px.
    static var neutral600: Color { palette.neutral600 }
    /// Borders, dividers, dashed empty-state outlines.
    static var neutral800: Color { palette.neutral800 }
    /// Hairlines.
    static var divider: Color { palette.divider }

    // MARK: Derived

    /// Bottom-sheet scrim. Dark in both themes — a scrim's job is to put the
    /// screen behind it out of reach, and a pale one over a pale screen does not.
    static var scrim: Color { palette.scrim }

    /// `linear-gradient(155deg, var(--color-accent-900), var(--color-surface))`
    /// on Home's emphasised stat card. 155° in CSS is measured clockwise from
    /// north, which lands just off vertical — hence the near-vertical unit points.
    static var statCardGradient: LinearGradient {
        LinearGradient(
            colors: [palette.accent900, palette.surface],
            startPoint: UnitPoint(x: 0.29, y: 0.05),
            endPoint: UnitPoint(x: 0.71, y: 0.95)
        )
    }

    /// What is cast by the two things that float: the bottom sheet and the
    /// customer picker. A token rather than `.black.opacity(…)` at the call site,
    /// because a shadow tuned for a near-black ground is a smudge on a white one.
    static var shadow: Color { palette.shadow }
    static var sheetShadow: Color { palette.sheetShadow }

    // MARK: Interaction tints
    //
    // Pressed/hover states come off the accent ramp rather than from opacity on
    // the whole control. See `ButtonStyles.swift`.

    static var primaryPressed: Color { palette.accent.opacity(palette.pressedStrength) }
    static var secondaryPressed: Color { palette.text.opacity(palette.pressedStrength * 0.64) }
    static var ghostPressed: Color { palette.accent.opacity(palette.pressedStrength * 0.82) }
    /// Disabled controls drop to 45% — the one place opacity carries state.
    static let disabledOpacity: Double = 0.45
}

/// One theme's worth of colour.
///
/// The two are written out in full rather than derived from one another. A light
/// theme is not a dark theme with the lightness flipped: the accent has to darken
/// to stay legible on white while the neutrals do not simply invert, and every
/// value here was chosen against the contrast it needs to hold.
struct Palette {

    let bg: Color
    let surface: Color
    let text: Color

    let accent: Color
    let accent300: Color
    let accent400: Color
    let accent700: Color
    let accent900: Color

    let neutral400: Color
    let neutral500: Color
    let neutral600: Color
    let neutral800: Color
    let divider: Color

    let scrim: Color
    let shadow: Color
    let sheetShadow: Color

    /// How hard a pressed control is washed with its own colour. Lower in light,
    /// where the accent is darker and 22% of it reads as a filled button — the one
    /// thing this design says it never has.
    let pressedStrength: Double

    /// The design as drawn: `styles.css`, unchanged.
    static let dark = Palette(
        bg: Color(hex: 0x161826),
        surface: Color(hex: 0x232532),
        text: Color(hex: 0xE9E9ED),
        accent: Color(hex: 0x9184D9),
        accent300: Color(hex: 0xD2CEFD),
        accent400: Color(hex: 0xB5ABFC),
        accent700: Color(hex: 0x5D5294),
        accent900: Color(hex: 0x2B2741),
        neutral400: Color(hex: 0xB2B6CA),
        neutral500: Color(hex: 0x9397AB),
        neutral600: Color(hex: 0x75798C),
        neutral800: Color(hex: 0x3F424D),
        divider: Color(hex: 0xE9E9ED, opacity: 0.16),
        scrim: Color(hex: 0x10111C, opacity: 0.74),
        shadow: Color(hex: 0x000000, opacity: 0.55),
        sheetShadow: Color(hex: 0x000000, opacity: 0.65),
        pressedStrength: 0.22
    )

    /// The same design in daylight, token for token.
    ///
    /// The ground and the surface swap *roles* rather than values: on the dark
    /// theme the card is lighter than the page, here it is the page that recedes
    /// and the card that stays white, so a card still reads as the nearer thing.
    /// The accent ramp darkens — `accent300`, the loudest shade on dark, becomes
    /// the deepest here, because what the numbers mean is "more attention", not
    /// "more light".
    static let light = Palette(
        bg: Color(hex: 0xF3F3F8),
        surface: Color(hex: 0xFFFFFF),
        text: Color(hex: 0x1B1D2B),
        accent: Color(hex: 0x5C4FC4),
        accent300: Color(hex: 0x453BA0),
        accent400: Color(hex: 0x6558CC),
        accent700: Color(hex: 0x8B80DC),
        accent900: Color(hex: 0xE9E6FA),
        neutral400: Color(hex: 0x4C5163),
        neutral500: Color(hex: 0x5F6478),
        neutral600: Color(hex: 0x8A8FA3),
        neutral800: Color(hex: 0xD5D7E2),
        divider: Color(hex: 0x1B1D2B, opacity: 0.12),
        scrim: Color(hex: 0x10111C, opacity: 0.45),
        shadow: Color(hex: 0x1B1D2B, opacity: 0.14),
        sheetShadow: Color(hex: 0x1B1D2B, opacity: 0.18),
        pressedStrength: 0.14
    )
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
