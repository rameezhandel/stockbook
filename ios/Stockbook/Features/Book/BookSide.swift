import Foundation

/// Which kind of record the book is showing: one of the four chips.
///
/// Top-level rather than nested in `BookScreen`, because the summary sheet and
/// the router both have to name it — which of the four a folded page is for
/// follows the chip the owner was reading, and a second enum saying the same
/// four things is a second enum to forget about when a fifth arrives.
///
/// The Kotlin twin is `BookSide` in `feature/book/BookScreen.kt`, `internal` for
/// exactly the same reason. It cannot be called `Side` there: a top-level
/// declaration in Kotlin is hidden from other *files* by `private` but still
/// puts its name in the package, so it would collide with `PeopleScreen`'s.
///
/// `rawValue` is what `@SceneStorage` writes, so the cases are named once and
/// renaming one would silently reset whichever chip an owner had left the book
/// on. Not worth doing.
enum BookSide: String, CaseIterable, Identifiable {
    case sales, purchases, payments, expenses

    var id: Self { self }
}
