import XCTest
@testable import Stockbook

/// Currency formatting
final class MoneyTests: XCTestCase {

    /// Integers render without decimals
    func testIntegers() {
        XCTAssertEqual(Money.text(194), "SAR 194")
        XCTAssertEqual(Money.text(0), "SAR 0")
    }

    /// Non-integers render to exactly two places
    func testFractions() {
        XCTAssertEqual(Money.text(0.25), "SAR 0.25")
        XCTAssertEqual(Money.text(0.5), "SAR 0.50")
    }

    /// Thousands are grouped en-US
    func testGrouping() {
        XCTAssertEqual(Money.text(1240), "SAR 1,240")
        XCTAssertEqual(Money.text(1240.5), "SAR 1,240.50")
    }

    /// Rounding happens before the integer test
    func testRounding() {
        // 193.999 rounds to 194, which is then an integer and loses its decimals.
        XCTAssertEqual(Money.text(193.999), "SAR 194")
        XCTAssertEqual(Money.text(0.005), "SAR 0.01")
    }

    /// Negative zero does not print a minus sign
    func testNegativeZero() {
        XCTAssertEqual(Money.text(-0.0), "SAR 0")
    }

    /// The symbol is configurable
    func testSymbol() {
        XCTAssertEqual(Money.text(12, symbol: "₹"), "₹12")
    }

    /// Parsing tells empty from zero
    func testParsing() {
        XCTAssertNil(Money.parse(""))
        XCTAssertNil(Money.parse("   "))
        XCTAssertEqual(Money.parse("0"), 0)
        XCTAssertEqual(Money.parse("1,240"), 1240)
        XCTAssertNil(Money.parse("abc"))
    }
}

/// Copy helpers
final class CopyTests: XCTestCase {

    /// Counts inflect
    func testPlurals() {
        XCTAssertEqual(Copy.count(1, "product"), "1 product")
        XCTAssertEqual(Copy.count(0, "product"), "0 products")
        XCTAssertEqual(Copy.count(4, "bill"), "4 bills")
        XCTAssertEqual(Copy.count(1, "piece"), "1 piece")
    }

    /// First name is the first whitespace-separated word
    func testFirstName() {
        XCTAssertEqual("Ahmed Al-Amri".firstName, "Ahmed")
        XCTAssertEqual("  Khalid  ".firstName, "Khalid")
        XCTAssertEqual("".firstName, "")
    }
}
