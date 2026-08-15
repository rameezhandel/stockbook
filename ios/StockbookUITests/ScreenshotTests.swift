import XCTest

/// Walks the app end to end and photographs every screen.
///
/// This exists because the app is written where it cannot be run — CI is the
/// only place these views are ever rendered. It is a *camera*, not an assertion
/// suite: `StockbookTests` covers correctness, and a navigation step that fails
/// here should still leave you with pictures of everything up to that point
/// rather than an empty artifact and a red tick.
///
/// Every step is therefore guarded and the run continues past failures. What it
/// cannot do is tell you a screen looks *right* — that judgement is still a
/// human looking at the output.
final class ScreenshotTests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = true
        app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
    }

    func testCaptureEveryScreen() {
        // Fresh simulator, so the app opens on first-run setup.
        capture("01-setup-name")

        type("Ahmed Al-Amri", into: field("setup.ownerName"))
        capture("02-setup-name-filled")
        tap(app.buttons["Continue"])

        capture("03-setup-products")
        // One from the capsules and one typed, so both add paths are exercised —
        // and only two, so every step-3 card stays on screen and reachable.
        tap(app.buttons["+ Padlock"], required: false)
        type("4 inch hinge\n", into: field("setup.productName"))
        capture("04-setup-products-added")
        tap(app.buttons["Next — stock & prices"])

        capture("05-setup-prices-empty")
        fillPriceGrid()
        capture("06-setup-prices-filled")
        tap(app.buttons["Open the shop"])

        capture("07-today")

        tap(app.buttons["tab.items"])
        capture("08-items")
        tap(app.cells.firstMatch, required: false)
        tapFirstProductRow()
        capture("09-product-editor")
        dismissSheet()

        tap(app.buttons["tab.sell"])
        capture("10-sell-picker")
        tapFirstProductRow()
        capture("11-cart")

        type("Ahmed Contracting", into: field("cart.customer"))
        dismissKeyboard()
        capture("12-cart-customer")

        tap(app.buttons["Save bill"])
        capture("13-receipt")

        tap(app.buttons["See bills"])
        capture("14-bills")

        tap(app.buttons["tab.today"])
        capture("15-today-with-bill")

        tap(app.buttons["Settings"], required: false)
        capture("16-settings")
    }

    // MARK: Capture

    private func capture(_ name: String) {
        // Let animations settle; a screenshot mid-transition is worse than useless
        // because it looks like a layout bug.
        Thread.sleep(forTimeInterval: 0.6)
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    // MARK: Interaction

    @discardableResult
    private func tap(_ element: XCUIElement, required: Bool = true, timeout: TimeInterval = 8) -> Bool {
        guard element.waitForExistence(timeout: timeout), element.isHittable else {
            if required {
                XCTContext.runActivity(named: "could not tap \(element)") { _ in }
            }
            return false
        }
        element.tap()
        return true
    }

    private func field(_ identifier: String) -> XCUIElement {
        let match = app.textFields[identifier]
        return match.exists ? match : app.textFields.firstMatch
    }

    /// Taps, then waits for the keyboard to actually come up before typing.
    ///
    /// Tapping a SwiftUI `TextField` does not focus it synchronously, and typing
    /// into an unfocused field throws "Neither element nor any descendant has
    /// keyboard focus" — which is what stopped the first run at the price grid.
    private func type(_ text: String, into element: XCUIElement) {
        guard element.waitForExistence(timeout: 8) else { return }
        for _ in 0..<3 {
            element.tap()
            if app.keyboards.firstMatch.waitForExistence(timeout: 3) {
                element.typeText(text)
                return
            }
        }
        XCTContext.runActivity(named: "never gained focus: \(element)") { _ in }
    }

    /// The three numeric fields per product card share identifiers, so they are
    /// filled by position rather than by name.
    private func fillPriceGrid() {
        let values = [("stock", "40"), ("cost", "12"), ("price", "25")]
        for (key, value) in values {
            let fields = app.textFields.matching(identifier: "setup.\(key)")
            for index in 0..<fields.count {
                let element = fields.element(boundBy: index)
                guard element.exists, element.isHittable else { continue }
                type(value, into: element)
                dismissKeyboard()
            }
        }
    }

    private func dismissKeyboard() {
        let done = app.buttons["Done"]
        if done.exists, done.isHittable {
            done.tap()
        } else if app.keyboards.count > 0 {
            app.typeText("\n")
        }
    }

    /// Product rows are buttons wrapping a stack, so they surface as whichever
    /// element type SwiftUI chose — try the likely ones rather than assume.
    private func tapFirstProductRow() {
        for candidate in [app.buttons, app.cells, app.otherElements] {
            let row = candidate.containing(.staticText, identifier: "Padlock").firstMatch
            if row.exists, row.isHittable {
                row.tap()
                return
            }
        }
        let fallback = app.staticTexts["Padlock"]
        if fallback.exists { fallback.tap() }
    }

    private func dismissSheet() {
        // The sheet's scrim covers the whole screen; a tap near the top dismisses.
        let top = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08))
        top.tap()
    }
}
