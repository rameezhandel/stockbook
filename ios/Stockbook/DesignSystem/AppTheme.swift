import Foundation

/// Dark or light. Two, and no "System".
///
/// Following the phone would make the app's appearance somebody else's decision:
/// the shop owner and the phone's owner are not always the same person, and a
/// counter under a shop light does not change its mind at sunset. This is the
/// same reasoning the language already follows, and for the same reason it is
/// stored with the shop rather than read from the device.
///
/// The raw values are **persisted and exported**, so they must not change.
enum AppTheme: String, Codable, CaseIterable, Identifiable, Sendable {
    case dark
    case light

    var id: String { rawValue }

    /// What the app was drawn in, and what it opens in until somebody says
    /// otherwise.
    static let `default` = AppTheme.dark

    func name(_ strings: Strings) -> String {
        switch self {
        case .dark: strings.themeDark
        case .light: strings.themeLight
        }
    }
}
