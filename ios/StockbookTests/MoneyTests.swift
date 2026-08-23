import Testing
@testable import Stockbook

@Suite("Currency formatting")
struct MoneyTests {

    @Test("Integers render without decimals")
    func integers() {
        #expect(Money.text(194) == "SAR 194")
        #expect(Money.text(0) == "SAR 0")
    }

    @Test("Non-integers render to exactly two places")
    func fractions() {
        #expect(Money.text(0.25) == "SAR 0.25")
        #expect(Money.text(0.5) == "SAR 0.50")
    }

    @Test("Thousands are grouped en-US")
    func grouping() {
        #expect(Money.text(1240) == "SAR 1,240")
        #expect(Money.text(1240.5) == "SAR 1,240.50")
    }

    @Test("Rounding happens before the integer test")
    func rounding() {
        // 193.999 rounds to 194, which is then an integer and loses its decimals.
        #expect(Money.text(193.999) == "SAR 194")
        #expect(Money.text(0.005) == "SAR 0.01")
    }

    @Test("Negative zero does not print a minus sign")
    func negativeZero() {
        #expect(Money.text(-0.0) == "SAR 0")
    }

    @Test("A signed figure carries the minus in front of the symbol")
    func signedFigures() {
        // `SAR -150` is what putting the symbol first gets you, and it is not
        // how anybody writes a negative.
        #expect(Money.signed(-150) == "-SAR 150")
        #expect(Money.signed(150) == "SAR 150")
        #expect(Money.signed(-12.5, in: .inr) == "-₹12.50")
    }

    @Test("A signed figure that rounds to nothing carries no sign")
    func signedZero() {
        // A loss of nothing is not a loss.
        #expect(Money.signed(-0.004) == "SAR 0")
        #expect(Money.signed(-0.0) == "SAR 0")
    }

    @Test("The symbol is configurable")
    func symbol() {
        #expect(Money.text(12, in: .inr) == "₹12")
    }

    @Test("Parsing tells empty from zero")
    func parsing() {
        #expect(Money.parse("") == nil)
        #expect(Money.parse("   ") == nil)
        #expect(Money.parse("0") == 0)
        #expect(Money.parse("1,240") == 1240)
        #expect(Money.parse("abc") == nil)
    }
}

@Suite("Copy helpers")
struct CopyTests {

    @Test("First name is the first whitespace-separated word")
    func firstName() {
        #expect("Ahmed Al-Amri".firstName == "Ahmed")
        #expect("  Khalid  ".firstName == "Khalid")
        #expect("".firstName == "")
    }
}
