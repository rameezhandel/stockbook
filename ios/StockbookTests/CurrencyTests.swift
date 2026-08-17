import Testing
import Foundation
@testable import Stockbook

/// The shop bills in exactly one currency, and every number on every screen is
/// drawn through it.
@Suite("Currency")
struct CurrencyTests {

    @Test("The symbol carries its own spacing")
    func spacing() {
        // Alphabetic codes get a space; glyphs do not. This is the whole reason
        // the spacing lives in the symbol rather than in `Money`.
        #expect(Money.text(194, in: .sar) == "SAR 194")
        #expect(Money.text(194, in: .inr) == "₹194")
        #expect(Money.text(194, in: .usd) == "$194")
    }

    @Test("Minor units follow the currency")
    func minorUnits() {
        // Two almost everywhere...
        #expect(Money.text(0.25, in: .sar) == "SAR 0.25")
        #expect(Money.text(0.5, in: .sar) == "SAR 0.50")
        // ...three for the dinars and the rial. Rendering 0.125 as 0.13 in a
        // shop that bills in fils is an error, not a rounding preference.
        #expect(Money.text(0.125, in: .kwd) == "KWD 0.125")
        #expect(Money.text(0.125, in: .bhd) == "BHD 0.125")
        #expect(Money.text(0.125, in: .omr) == "OMR 0.125")
        #expect(Money.text(0.125, in: .sar) == "SAR 0.13")
    }

    @Test("Whole numbers never grow a decimal point")
    func wholeNumbers() {
        #expect(Money.text(12, in: .kwd) == "KWD 12")
        #expect(Money.text(1240, in: .kwd) == "KWD 1,240")
    }

    @Test("Grouping is the app's rule, not the currency's")
    func grouping() {
        // The same number reads the same way whatever the shop bills in — an
        // INR shop does not start seeing lakh grouping.
        #expect(Money.text(124_000, in: .inr) == "₹124,000")
        #expect(Money.text(124_000, in: .sar) == "SAR 124,000")
    }

    @Test("Codes are unique, and so are symbols")
    func tableIsWellFormed() {
        let codes = Set(Currency.supported.map(\.code))
        let symbols = Set(Currency.supported.map { $0.symbol.trimmed })
        #expect(codes.count == Currency.supported.count)
        // Two currencies sharing a symbol would put an ambiguous mark on a bill,
        // which is the one place these strings are read by somebody who has no
        // Settings screen in front of them.
        #expect(symbols.count == Currency.supported.count)
        #expect(Currency.supported.contains(Currency.default))
    }

    @Test("An unknown code falls back rather than failing")
    func unknownCode() {
        // Showing the wrong symbol beats refusing to open the shop.
        #expect(Currency.named("ZZZ") == Currency.default)
        #expect(Currency.named("INR") == .inr)
    }
}

@Suite("Currency setting")
@MainActor
struct CurrencySettingTests {

    @Test("Choosing a currency persists it")
    func setCurrency() throws {
        let repository = InMemoryRepository()
        let store = StockbookStore(repository: repository)

        store.setCurrency(.inr)
        let onDisk = try repository.loadAll().settings

        #expect(store.settings.currency == .inr)
        #expect(onDisk.currencyCode == "INR")
        #expect(onDisk.currency.symbol == "₹")
    }

    @Test("Changing currency does not convert what is already saved")
    func nothingIsConverted() {
        let store = StockbookStore(repository: InMemoryRepository())
        let product = store.addProduct(name: "Padlock", stock: 5, cost: 10, price: 25)
        let bill = store.saveBill(
            lines: [DraftLine(productUID: product.uid, qty: 2, price: 25)],
            customer: "Ahmed",
            paid: nil
        )

        store.setCurrency(.inr)

        // The numbers are the owner's; only the symbol in front of them moved.
        #expect(store.bills.first?.total == 50)
        #expect(bill?.total == 50)
        #expect(store.products.first?.price == 25)
        #expect(Money.text(50, in: store.settings.currency) == "₹50")
    }

    @Test("A backup carries its currency to the new phone")
    func importTakesTheFilesCurrency() {
        let store = StockbookStore(repository: InMemoryRepository())
        store.setCurrency(.sar)

        // Unlike the language, the currency belongs to the numbers in the file:
        // those prices were entered in it.
        store.replaceEverything(with: BackupDocument(
            exportedAt: .now,
            ownerName: "Someone Else",
            currencyCode: "INR",
            products: [],
            bills: []
        ))

        #expect(store.settings.currency == .inr)
    }

    @Test("Export names the currency by its code")
    func exportCarriesTheCode() {
        let store = StockbookStore(repository: InMemoryRepository())
        store.setCurrency(.kwd)

        // The code, and only the code. The symbol used to be written beside it
        // for a build that could not read one; there is no such build.
        #expect(store.makeBackupDocument().currencyCode == "KWD")
    }
}
