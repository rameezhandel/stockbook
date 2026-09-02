package com.stockbook.core.text

/**
 * The five tabs. Settings is deliberately *not* one.
 *
 * [PEOPLE] and [BOOK] were one screen, and it was two: a directory you go to in
 * order to *find* somebody, stacked on a ledger you go to in order to *browse
 * records*. Different verbs, one scroll — and the chip row at the top switched
 * both halves at once, which is why expenses, having no people, never fitted the
 * pattern. They are separate now, and each does one thing.
 *
 * [PEOPLE] sits beside [BOOK] because that is where it came from, and after
 * [SELL] because writing a bill is still the thing a thumb reaches for most.
 * Customers and suppliers share it rather than taking a tab each: a shop looks
 * up a name, and which side of the counter that name is on is something it
 * already knows.
 */
enum class AppTab { TODAY, ITEMS, SELL, PEOPLE, BOOK }
