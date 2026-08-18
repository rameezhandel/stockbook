import SwiftUI

/// Spacing, radius and elevation constants from the handoff.
enum Metrics {

    // MARK: Layout

    /// Every screen's horizontal padding.
    static let screenPadding: CGFloat = 20

    /// The handoff specifies 58px from the top of the screen to the header —
    /// i.e. the header sits just below the status bar. Rather than hard-coding
    /// 58 (which collides with the Dynamic Island on devices whose top safe-area
    /// inset is larger), screens apply this via ``View/screenHeaderPadding()``,
    /// which tops up the safe area to 58 and no further.
    static let headerTopFromScreenEdge: CGFloat = 58

    // MARK: Gaps

    /// Between list rows.
    static let rowGap: CGFloat = 6
    /// Between cards.
    static let cardGap: CGFloat = 9
    /// Between form fields.
    static let fieldGap: CGFloat = 10

    // MARK: Radius

    static let rowRadius: CGFloat = 9
    static let cardRadius: CGFloat = 10
    static let statRadius: CGFloat = 12
    static let controlRadius: CGFloat = 8
    static let sheetRadius: CGFloat = 18
    static let pillRadius: CGFloat = 7

    // MARK: Controls
    //
    // Minimum touch target is 44pt. Where the design draws a smaller box (the
    // 34pt quantity stepper, the 30pt suggestion capsules) the *hit area* is
    // still padded out to 44 — see `.minimumTouchTarget()`.

    static let minimumTouchTarget: CGFloat = 44
    /// Search fields, setup grid cells.
    static let inputHeight: CGFloat = 42
    /// Setup name field, product-name field in the editor.
    static let tallInputHeight: CGFloat = 46
    static let primaryButtonHeight: CGFloat = 46
    static let iconButtonSize: CGFloat = 36
    /// The in-cart stepper and price boxes.
    static let compactControlHeight: CGFloat = 34

    // MARK: Motion

    /// Entrances and state changes: short, ease-out.
    static let quick = Animation.easeOut(duration: 0.18)
    /// Bottom sheets.
    static let sheet = Animation.easeOut(duration: 0.27)
}

extension View {

    /// The hairline edge that stands in for elevation on this dark ground:
    /// `box-shadow: 0 0 0 1px var(--color-neutral-800)`. Never a stack of shadows.
    func hairline(_ color: Color = Nocturne.neutral800, radius: CGFloat) -> some View {
        overlay(
            RoundedRectangle(cornerRadius: radius, style: .continuous)
                .strokeBorder(color, lineWidth: 1)
        )
    }

    /// Surface + radius + hairline, the standard card treatment.
    func nocturneCard(radius: CGFloat = Metrics.cardRadius, background: Color = Nocturne.surface) -> some View {
        self
            .background(background, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .hairline(radius: radius)
    }

    /// Guarantees a 44pt hit area without changing the drawn size.
    func minimumTouchTarget() -> some View {
        contentShape(Rectangle())
            .frame(minWidth: Metrics.minimumTouchTarget, minHeight: Metrics.minimumTouchTarget)
    }

    /// Tops the safe area up to 58pt from the screen edge, never past it.
    func screenHeaderPadding() -> some View {
        modifier(ScreenHeaderPadding())
    }
}

private struct ScreenHeaderPadding: ViewModifier {
    @Environment(\.topSafeInset) private var topInset

    func body(content: Content) -> some View {
        content.padding(.top, max(0, Metrics.headerTopFromScreenEdge - topInset))
    }
}

/// The device's top safe-area inset, measured once at the root (see `RootView`)
/// and read wherever a screen needs to position itself against the physical
/// screen edge rather than against the safe area.
private struct TopSafeInsetKey: EnvironmentKey {
    static let defaultValue: CGFloat = 0
}

/// The device's bottom safe-area inset, used by the tab bar.
private struct BottomSafeInsetKey: EnvironmentKey {
    static let defaultValue: CGFloat = 0
}

extension EnvironmentValues {
    var topSafeInset: CGFloat {
        get { self[TopSafeInsetKey.self] }
        set { self[TopSafeInsetKey.self] = newValue }
    }

    var bottomSafeInset: CGFloat {
        get { self[BottomSafeInsetKey.self] }
        set { self[BottomSafeInsetKey.self] = newValue }
    }
}
