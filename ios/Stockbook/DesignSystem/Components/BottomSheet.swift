import SwiftUI

/// Bottom sheets, drawn by the app rather than by `.sheet`.
///
/// The design is specific in ways the system sheet does not expose: an
/// `rgba(16,17,28,0.74)` scrim, 18px rounding on the **top corners only**, a
/// `0 -16px 40px rgba(0,0,0,0.65)` shadow, and a max height of 84%. Presenting
/// it as an overlay inside the app's own root stack gives us all of that and
/// keeps the tab bar visible behind the scrim, which is what the prototype does.
///
/// There is **no grab handle**. The design called for one and it was drawn, but
/// this sheet has no drag gesture — the handle was an invitation to do something
/// that did nothing. A sheet closes by its own Close or Done button, or by
/// tapping the scrim. Either add the gesture or do not draw the affordance;
/// drawing it alone is the one option that teaches the owner the app ignores
/// them.

/// The rounded card itself. The scrim behind it is a **sibling**, not a child —
/// see `nocturneSheet`, which is the only thing that builds either.
struct BottomSheetPanel<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        // The reader wraps the scroll view because it has to be its ancestor, and
        // the proxy goes into the environment because the sheet's own content is
        // its descendant — see `sheetScroll`. Without both, a sheet can neither
        // reach its own scroll view nor scroll a field out from under the
        // keyboard.
        ScrollViewReader { proxy in
            ScrollView {
                content
                    .padding(.horizontal, Metrics.screenPadding)
                    // The header still needs air above it, or it sits against
                    // the rounded corner.
                    .padding(.top, 16)
                    .padding(.bottom, 32)
                    .environment(\.sheetScroll, proxy)
            }
            .scrollBounceBehavior(.basedOnSize)
            // The sheet is inside an overlay that ignores the keyboard, so it
            // stays put rather than leaping up — which also means nothing insets
            // its content for the keyboard unless this does.
            .keyboardRoom()
            .scrollDismissesKeyboard(.interactively)
        }
        .frame(maxWidth: .infinity)
        .background(Nocturne.surface)
        .clipShape(TopRoundedRectangle(radius: Metrics.sheetRadius))
        .shadow(color: Nocturne.sheetShadow, radius: 20, x: 0, y: -16)
    }
}

/// Rounds the top two corners only — bottom sheets sit flush against the
/// bottom edge.
struct TopRoundedRectangle: Shape {
    let radius: CGFloat

    func path(in rect: CGRect) -> Path {
        Path(
            roundedRect: rect,
            cornerRadii: RectangleCornerRadii(topLeading: radius, bottomLeading: 0, bottomTrailing: 0, topTrailing: radius),
            style: .continuous
        )
    }
}

/// The title row every sheet opens with: heading, optional sub-line, close `x`.
struct SheetHeader: View {
    let title: String
    var subtitle: String?
    let onClose: () -> Void

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).nocturneText(.sheetTitle)
                if let subtitle {
                    Text(subtitle).nocturneText(.meta)
                }
            }
            Spacer(minLength: 12)
            Button(action: onClose) {
                Glyph(Icon.close, size: 16)
                    .foregroundStyle(Nocturne.neutral500)
                    .minimumTouchTarget()
            }
            .buttonStyle(.plain)
        }
        .padding(.bottom, 14)
    }
}

extension View {
    /// Presents `content` as a Nocturne bottom sheet whenever `item` is non-nil.
    ///
    /// The scrim and the panel are separate `if`s inside one ZStack that is
    /// always mounted, and that is the whole trick. They used to be children of
    /// a single container view, each carrying its own `.transition` — and every
    /// sheet in the app faded in anyway. A `.transition` only runs when *that*
    /// view is the one being inserted; when an ancestor is inserted instead,
    /// SwiftUI animates the ancestor with the default `.opacity` and the
    /// transitions written inside it never get a turn. Lifting both out to
    /// where the condition lives is what lets the scrim fade while the panel
    /// slides up from the bottom edge.
    func nocturneSheet<Item: Identifiable, SheetContent: View>(
        item: Binding<Item?>,
        @ViewBuilder content: @escaping (Item) -> SheetContent
    ) -> some View {
        overlay {
            ZStack(alignment: .bottom) {
                if item.wrappedValue != nil {
                    SheetScrim { item.wrappedValue = nil }
                }
                if let value = item.wrappedValue {
                    BottomSheetPanel { content(value) }
                        .transition(.move(edge: .bottom))
                }
            }
            // No frame on the stack itself: while nothing is presented it holds
            // nothing, and a full-size empty layer over every screen is a thing
            // to be sure about rather than to leave lying there. Presented, the
            // scrim is a `Color` that takes whatever the overlay proposes — the
            // whole screen — and the panel settles against its bottom edge.
            .ignoresSafeArea(.keyboard, edges: .bottom)
            .animation(Metrics.sheet, value: item.wrappedValue != nil)
        }
    }

    /// The same sheet, for something that is either showing or not.
    ///
    /// Most sheets here carry the thing they are about, so `item:` is the usual
    /// form. A sheet whose whole content is a question — "which product arrived?"
    /// — has nothing to carry, and inventing an `Identifiable` box for a boolean
    /// would be ceremony.
    func nocturneSheet<SheetContent: View>(
        isPresented: Binding<Bool>,
        @ViewBuilder content: @escaping () -> SheetContent
    ) -> some View {
        overlay {
            ZStack(alignment: .bottom) {
                if isPresented.wrappedValue {
                    SheetScrim { isPresented.wrappedValue = false }
                    BottomSheetPanel { content() }
                        .transition(.move(edge: .bottom))
                }
            }
            // No frame on the stack itself: while nothing is presented it holds
            // nothing, and a full-size empty layer over every screen is a thing
            // to be sure about rather than to leave lying there. Presented, the
            // scrim is a `Color` that takes whatever the overlay proposes — the
            // whole screen — and the panel settles against its bottom edge.
            .ignoresSafeArea(.keyboard, edges: .bottom)
            .animation(Metrics.sheet, value: isPresented.wrappedValue)
        }
    }
}

/// The dimmed ground behind a sheet, and the second way out of one.
///
/// Carries its own `.transition` because it is inserted directly into the
/// sheet layer's stack, alongside the panel rather than inside it.
struct SheetScrim: View {
    let onTap: () -> Void

    var body: some View {
        Nocturne.scrim
            .ignoresSafeArea()
            .contentShape(Rectangle())
            .onTapGesture(perform: onTap)
            .transition(.opacity)
    }
}
