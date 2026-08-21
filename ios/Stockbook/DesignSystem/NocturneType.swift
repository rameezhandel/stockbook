import SwiftUI
import CoreText

/// The type scale from the handoff. Inter throughout, weight 400 for body and
/// 500 for headings — **never bolder than 500**; hierarchy comes from size and
/// space, not weight.
///
/// Inter is not bundled by default. Drop `Inter-Regular.ttf` / `Inter-Medium.ttf`
/// into `Stockbook/Resources/Fonts/` and they are registered at launch by
/// ``NocturneType/registerBundledFonts()``. Until then `Font.custom` falls back
/// to the system face at the same size, so the app renders correctly either way.
enum NocturneType {

    private static let regular = "Inter-Regular"
    private static let medium = "Inter-Medium"

    /// Registers any font files shipped in the app bundle. Safe to call when
    /// none are present.
    static func registerBundledFonts() {
        let urls = (Bundle.main.urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? [])
            + (Bundle.main.urls(forResourcesWithExtension: "otf", subdirectory: nil) ?? [])
        for url in urls where url.lastPathComponent.hasPrefix("Inter") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }

    static func inter(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
        .custom(weight == .regular ? regular : medium, size: size)
    }

    /// The size a figure has to drop to so it fits the half-width box it sits in.
    ///
    /// Chosen from the string's length rather than measured, and deliberately so.
    /// `minimumScaleFactor` is the obvious answer and it quietly stops working
    /// next to `contentTransition(.numericText())` — the rolling-digit animation
    /// these cards use — so the text truncates instead of shrinking. That is how
    /// "SAR 500,000" came out as "SAR 500,0…" on a card with room for it. Android
    /// has no scaling modifier at all and simply clipped.
    ///
    /// Length is a good enough proxy because there is only ever one kind of
    /// string here: a currency symbol and a grouped number. The thresholds are
    /// twinned in `NocturneType.fittedNumber` on Android and must move together.
    static func fittedNumber(_ text: String, max: CGFloat = 26) -> NocturneTextRole {
        switch text.count {
        case ...9: .bigNumber(max)
        case ...12: .bigNumber(max - 4)
        case ...15: .bigNumber(max - 7)
        default: .bigNumber(max - 9)
        }
    }
}

/// The named roles from the handoff's type table. Using the role rather than a
/// raw size keeps the scale in one place — there is no free-form `.font(.system(...))`
/// anywhere in the feature code.
enum NocturneTextRole {
    /// 25px, tracking −0.02em. "Items", "Bills", "Hello, Ahmed".
    case screenTitle
    /// 26px, tracking −0.02em. Setup step headings.
    case setupTitle
    /// 26–28px, tracking −0.025em. Stat values and bill totals.
    case bigNumber(CGFloat)
    /// 19px. Bottom-sheet titles.
    case sheetTitle
    /// 14.5px. The first line of a list row.
    case rowPrimary
    /// 14px. Row values and input text.
    case rowValue
    /// 13px / line-height 1.5, neutral-500. Explanatory copy.
    case body
    /// 11.5px, neutral-500. Sub-rows and meta lines.
    case meta
    /// 12px at 70% text. Field labels.
    case fieldLabel
    /// 10.5px uppercase, tracking 0.09em, neutral-500. Section kickers.
    case kicker
    /// 10.5px. Tab bar labels.
    case tabLabel

    fileprivate var size: CGFloat {
        switch self {
        case .screenTitle: 25
        case .setupTitle: 26
        case .bigNumber(let size): size
        case .sheetTitle: 19
        case .rowPrimary: 14.5
        case .rowValue: 14
        case .body: 13
        case .meta: 11.5
        case .fieldLabel: 12
        case .kicker, .tabLabel: 10.5
        }
    }

    fileprivate var weight: Font.Weight {
        switch self {
        case .screenTitle, .setupTitle, .bigNumber, .sheetTitle: .medium
        default: .regular
        }
    }

    /// Letter-spacing in points. CSS `em` values are relative to the font size.
    fileprivate var tracking: CGFloat {
        switch self {
        case .screenTitle, .setupTitle: size * -0.02
        case .bigNumber: size * -0.025
        case .kicker: size * 0.09
        default: 0
        }
    }

    /// Extra leading. CSS `line-height: 1.5` on 13px copy is roughly 4pt of
    /// additional space over SwiftUI's default line height.
    fileprivate var lineSpacing: CGFloat {
        switch self {
        case .body: 4
        default: 0
        }
    }

    /// Roles that carry their own colour. The rest inherit from the parent.
    fileprivate var color: Color? {
        switch self {
        case .body, .meta, .kicker: Nocturne.neutral500
        case .fieldLabel: Nocturne.text.opacity(0.7)
        default: nil
        }
    }

    fileprivate var uppercased: Bool {
        if case .kicker = self { return true }
        return false
    }
}

extension View {
    /// Applies a role from the Nocturne type scale.
    func nocturneText(_ role: NocturneTextRole) -> some View {
        modifier(NocturneTextModifier(role: role))
    }
}

private struct NocturneTextModifier: ViewModifier {
    let role: NocturneTextRole

    func body(content: Content) -> some View {
        let styled = content
            .font(NocturneType.inter(role.size, role.weight))
            .tracking(role.tracking)
            .lineSpacing(role.lineSpacing)
            .textCase(role.uppercased ? .uppercase : nil)

        if let color = role.color {
            styled.foregroundStyle(color)
        } else {
            styled
        }
    }
}
