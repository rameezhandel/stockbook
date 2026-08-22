import SwiftUI
import UIKit

/// Room under a scroll view for the keyboard sitting on top of it, and a way to
/// scroll the focused field up into that room.
///
/// **Why this has to be written by hand.** SwiftUI does both of these for free —
/// it insets a scroll view by the keyboard's height and scrolls the focused field
/// into view — and it does both through the keyboard *safe area*. This app turns
/// that safe area off, twice and on purpose: `RootView` because the keyboard must
/// never resize the app and shove the tab bar to the top of the display, and
/// `nocturneSheet` because a bottom sheet is bottom-aligned in a full-screen
/// overlay and would otherwise leap up with the keyboard. Both are right, and
/// both take the free behaviour with them — a field at the foot of a form simply
/// sat under the keyboard with no way to reach it.
///
/// The two cannot be had together. Apple's own guidance is that `ignoresSafeArea`
/// pinning a bottom bar and automatic field avoidance are mutually exclusive, so
/// what is left is to measure the keyboard and do the work here.
///
/// This inset does **not** resize anything. `safeAreaInset` on a scroll view adds
/// content inset — the view keeps its frame, the tab bar stays where it is, and
/// only what the scroll view can reach changes.
private struct KeyboardRoom: ViewModifier {
    /// Where focus is, when the screen tracks it. Scrolling happens on a change
    /// to either this or the height: the room arrives with the keyboard's own
    /// animation, so a scroll fired only on focus lands short of where the field
    /// ends up.
    let focused: FocusState<String?>.Binding?
    let proxy: ScrollViewProxy?

    @State private var height: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .safeAreaInset(edge: .bottom, spacing: 0) {
                Color.clear.frame(height: height)
            }
            // `willChangeFrame` rather than `willShow`: it also fires when the
            // keyboard changes height under a field that swaps a number pad for a
            // full keyboard, which `willShow` does not.
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) {
                height = Self.overlap(from: $0)
            }
            .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
                height = 0
            }
            .animation(.easeOut(duration: 0.25), value: height)
            .onChange(of: height) { _, _ in scrollToFocus() }
            .onChange(of: focused?.wrappedValue) { _, _ in scrollToFocus() }
    }

    private func scrollToFocus() {
        guard height > 0, let tag = focused?.wrappedValue, let proxy else { return }
        withAnimation(.easeOut(duration: 0.25)) {
            // `.bottom` rather than `.center`: the field is being typed into, and
            // what has to be visible is the field and whatever sits under it —
            // the note under a bill, the paid line under a delivery.
            proxy.scrollTo(tag, anchor: .bottom)
        }
    }

    /// How much of the screen the keyboard is actually covering.
    ///
    /// The reported frame is in screen coordinates, so the overlap is whatever
    /// falls below its top edge. The home indicator's inset comes off it because
    /// the keyboard is drawn over that strip rather than above it — counting both
    /// would leave a finger's width of dead space under every form.
    ///
    /// A hardware keyboard parks the bar mostly off-screen and this comes out
    /// near zero, which is the right answer: nothing is covered.
    private static func overlap(from note: Notification) -> CGFloat {
        guard let frame = note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else { return 0 }
        let window = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first
        guard let window else { return 0 }
        let covered = window.bounds.maxY - frame.minY
        return max(0, covered - window.safeAreaInsets.bottom)
    }
}

extension View {
    /// Apply to a **scroll view** whose content can end up under the keyboard.
    ///
    /// Pass the screen's focus and a `ScrollViewProxy` to have the focused field
    /// scrolled up as well; pass neither and the content merely becomes reachable
    /// by hand, which is still the difference between hidden and awkward.
    func keyboardRoom(
        focused: FocusState<String?>.Binding? = nil,
        in proxy: ScrollViewProxy? = nil
    ) -> some View {
        modifier(KeyboardRoom(focused: focused, proxy: proxy))
    }
}

/// The scroll view a bottom sheet's content is sitting inside.
///
/// A sheet does not own its own scroll view — `BottomSheetPanel` does — so a
/// sheet that wants a field scrolled out from under the keyboard has no proxy to
/// ask. `ScrollViewReader` has to be an *ancestor* of the scroll view, which puts
/// it out of reach of anything the sheet itself can write. The panel puts its
/// proxy here instead, and any sheet can pick it up.
private struct SheetScrollKey: EnvironmentKey {
    static let defaultValue: ScrollViewProxy? = nil
}

extension EnvironmentValues {
    var sheetScroll: ScrollViewProxy? {
        get { self[SheetScrollKey.self] }
        set { self[SheetScrollKey.self] = newValue }
    }
}
