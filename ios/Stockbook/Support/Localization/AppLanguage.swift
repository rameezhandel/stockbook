import Foundation

/// The languages the app is written in.
///
/// Two, chosen deliberately: this is a shop counter, not a translation project.
/// Adding a third means adding one more case and one more column to `Strings` —
/// the compiler then walks you through every line that needs a word.
///
/// The raw values are the ISO codes and are **persisted and exported**, so they
/// must not change.
enum AppLanguage: String, Codable, CaseIterable, Identifiable, Sendable {
    case english = "en"
    case kannada = "kn"

    var id: String { rawValue }

    /// The language's name **in that language** — the only label that is any use
    /// to someone who cannot read the other one.
    var endonym: String {
        switch self {
        case .english: "English"
        case .kannada: "ಕನ್ನಡ"
        }
    }

    /// Used for dates and weekday names only. Money keeps its own locale, so a
    /// shop billing in SAR does not suddenly group in lakhs.
    var locale: Locale {
        switch self {
        case .english: Locale(identifier: "en_US")
        case .kannada: Locale(identifier: "kn_IN")
        }
    }
}

/// The language in force, for code that renders a string outside a view.
///
/// A plain holder rather than an observable object: language changes are rare
/// and the app answers them by rebuilding the whole tree (see `RootView`), which
/// is both simpler and more thorough than threading an environment value into
/// every screen. Everything that reads it renders on the main actor already.
@MainActor
enum L10n {
    private(set) static var language: AppLanguage = .english

    static func use(_ language: AppLanguage) {
        self.language = language
    }
}

/// Every user-facing string, in the language currently in force.
///
/// `Loc.saveBill`, `Loc.onlyNInStock(3)`. Views read this directly; anything
/// off the main actor builds a `Strings` with an explicit language instead.
@MainActor
var Loc: Strings { Strings(language: L10n.language) }
