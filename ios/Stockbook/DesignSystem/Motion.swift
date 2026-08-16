import SwiftUI

/// How things move.
///
/// The rule this file exists to hold: **motion carries information or it does
/// not happen.** A number that rolls tells you it changed while you were looking
/// at something else. A row that slides in tells you it was added rather than
/// always having been there. A screen that fades tells you which way you went.
/// Anything that only decorates is a delay between the owner and the next
/// customer, on a phone being held in one hand over a counter.
enum Motion {

    /// Money and counts that change under the owner's eyes — a cart total as a
    /// quantity is tapped. Fast, no bounce: the number has to be readable the
    /// instant it stops.
    static let numbers = Animation.snappy(duration: 0.22, extraBounce: 0)

    /// Rows arriving and leaving a list.
    static let list = Animation.spring(response: 0.3, dampingFraction: 0.86)

    /// Something appearing that deserves noticing — the mark on a product
    /// already added to the bill.
    static let pop = Animation.spring(response: 0.28, dampingFraction: 0.6)

    /// Moving between screens and tabs.
    static let screen = Metrics.quick
}

extension View {

    /// `.animation(_:value:)` that obeys **Reduce Motion**.
    ///
    /// Applied centrally rather than checked at each call site, because the one
    /// that gets forgotten is the one that spins on a phone belonging to someone
    /// who asked it not to.
    func motion<V: Equatable>(_ animation: Animation, value: V) -> some View {
        modifier(MotionModifier(animation: animation, value: value))
    }

    /// Digits roll rather than swap when the number under them changes.
    ///
    /// Only for figures that move while being looked at. A number that is simply
    /// drawn once — a saved bill's total — has nothing to say by moving.
    func rollingNumber<V: Equatable>(_ value: V) -> some View {
        contentTransition(.numericText())
            .motion(Motion.numbers, value: value)
    }
}

private struct MotionModifier<V: Equatable>: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    let animation: Animation
    let value: V

    func body(content: Content) -> some View {
        content.animation(reduceMotion ? nil : animation, value: value)
    }
}
