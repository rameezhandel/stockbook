import Testing
import Foundation
@testable import Stockbook

/// The theme is a preference of the phone, not a fact about the shop.
///
/// That makes it behave like the language and unlike the currency, and the rules
/// below are the ones that difference produces. None of them can be checked by
/// looking at a screen — a theme that quietly resets itself looks like a screen
/// somebody drew wrong.
@MainActor
@Suite("Theme")
struct ThemeTests {

    private func makeStore() -> StockbookStore {
        StockbookStore(repository: InMemoryRepository())
    }

    /// `Nocturne` is global, so a test that leaves it on light would hand the
    /// next one a palette it never asked for.
    private func restoringTheme(_ work: () throws -> Void) rethrows {
        defer { Nocturne.use(.dark) }
        try work()
    }

    @Test("A shop opens dark until somebody says otherwise")
    func defaultsToDark() {
        #expect(makeStore().settings.theme == .dark)
        #expect(Nocturne.bg == Palette.dark.bg)
    }

    @Test("Choosing a theme persists it and takes effect immediately")
    func setTheme() throws {
        try restoringTheme {
            let repository = InMemoryRepository()
            let store = StockbookStore(repository: repository)

            store.setTheme(.light)

            let onDisk = try repository.loadAll().settings.theme

            #expect(store.settings.theme == .light)
            #expect(onDisk == .light, "not written to disk")
            #expect(Nocturne.bg == Palette.light.bg, "the palette in force did not follow the setting")
        }
    }

    /// A default value does **not** make Swift's synthesised decoder tolerate a
    /// missing key — the reason `Settings` decodes by hand, and the reason this
    /// test exists for every field added after v1.
    @Test("A shop file written before themes existed still opens, in the dark")
    func decodesWithoutTheKey() throws {
        let json = Data("""
        { "ownerName": "Khalid Al-Amri", "currencyCode": "SAR", "setupCompleted": true }
        """.utf8)

        let settings = try JSONDecoder().decode(Settings.self, from: json)

        #expect(settings.theme == .dark)
        #expect(settings.ownerName == "Khalid Al-Amri")
    }

    @Test("Starting over keeps the theme")
    func startOverKeepsTheme() {
        restoringTheme {
            let store = makeStore()
            store.setTheme(.light)
            store.setOwnerName("Khalid")

            store.startOver()

            #expect(store.settings.ownerName.isEmpty)
            #expect(store.settings.theme == .light,
                    "wiping the shop is a data decision, not a decision about how it looks")
        }
    }

    @Test("Importing another phone's shop does not repaint this one")
    func importKeepsThisPhonesTheme() {
        restoringTheme {
            let store = makeStore()
            store.setTheme(.light)

            store.replaceEverything(with: BackupDocument(
                exportedAt: .now,
                ownerName: "Someone Else",
                currencyCode: "SAR",
                products: [],
                bills: []
            ))

            #expect(store.settings.theme == .light)
            #expect(store.settings.ownerName == "Someone Else", "the shop itself did come from the file")
        }
    }
}
