package com.stockbook.core.text

/**
 * The four tabs. Settings is deliberately *not* one.
 *
 * [BOOK] was Bills, and grew rather than moved: it holds both halves of the
 * account book now — what was sold and to whom, what arrived and from whom. Two
 * chips inside it rather than two tabs out here, because a delivery arrives once
 * a week and a sale happens fifty times a day, and a tab bar is weighted by how
 * often a thumb goes there.
 */
enum class AppTab { TODAY, ITEMS, SELL, BOOK }
