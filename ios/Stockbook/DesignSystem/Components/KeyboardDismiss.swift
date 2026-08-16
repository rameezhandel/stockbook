import SwiftUI
import UIKit

/// A "Done" button above the keyboard, declared **once per screen**.
///
/// Numeric keypads have no return key, so a price field can otherwise only be
/// dismissed by moving focus elsewhere — a real problem for someone entering
/// prices one-handed behind a counter.
///
/// The first attempt put this on `NocturneField` itself, which meant every
/// numeric field declared its own toolbar, conditional on that field's focus.
/// Setup step 3 shows three per product, so a handful of products produced a
/// dozen toolbars appearing and disappearing as focus moved between them, and
/// the screen hung. One unconditional toolbar per screen has neither problem.
///
/// Focus is cleared through the responder chain rather than a `FocusState`,
/// because the toolbar is declared far above the field that owns the focus.
struct KeyboardDoneButton: ViewModifier {
    func body(content: Content) -> some View {
        content.toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(Loc.done) { dismissKeyboard() }
                    .font(NocturneType.inter(15, .medium))
                    .foregroundStyle(Nocturne.accent)
            }
        }
    }
}

extension View {
    /// Apply once, to a screen or sheet that contains numeric fields.
    func keyboardDoneButton() -> some View {
        modifier(KeyboardDoneButton())
    }
}

func dismissKeyboard() {
    UIApplication.shared.sendAction(
        #selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil
    )
}
