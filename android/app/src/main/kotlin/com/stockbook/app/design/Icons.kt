package com.stockbook.app.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every glyph the app uses, in one place.
 *
 * The design calls for **Phosphor Icons**, which are not on the system, so each
 * Phosphor name maps to its nearest Material equivalent *here* — exactly as the
 * iOS build maps them to SF Symbols. Adding the real Phosphor set changes this
 * file and nothing else.
 */
object Icon {
    // Navigation / chrome
    val settings: ImageVector = Icons.Filled.Settings          // phosphor: gear-six
    val today: ImageVector = Icons.Outlined.Home               // phosphor: house
    val todayActive: ImageVector = Icons.Filled.Home
    val items: ImageVector = Icons.Outlined.Inventory2         // phosphor: shapes
    val itemsActive: ImageVector = Icons.Filled.Inventory2
    val sell: ImageVector = Icons.Outlined.AddCircle           // phosphor: plus-circle
    val sellActive: ImageVector = Icons.Filled.Add
    val bills: ImageVector = Icons.Outlined.Receipt            // phosphor: receipt
    val billsActive: ImageVector = Icons.Filled.Receipt
    val people: ImageVector = Icons.Outlined.Person            // phosphor: user
    val peopleActive: ImageVector = Icons.Filled.Person
    val back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

    // Actions
    val add: ImageVector = Icons.Filled.Add
    val remove: ImageVector = Icons.Filled.Remove
    val delete: ImageVector = Icons.Filled.Delete
    val edit: ImageVector = Icons.Filled.Edit
    val addStock: ImageVector = Icons.Filled.LibraryAdd      // phosphor: stack-plus
    val close: ImageVector = Icons.Filled.Close
    val confirm: ImageVector = Icons.Filled.Check
    val openRow: ImageVector = Icons.Filled.KeyboardArrowRight
    // Stepping one day at a time. Distinct from `back`, which leaves a screen —
    // these stay on it and change what it is showing.
    val stepBack: ImageVector = Icons.Filled.KeyboardArrowLeft  // phosphor: caret-left
    val stepForward: ImageVector = Icons.Filled.KeyboardArrowRight // phosphor: caret-right
    val chooseFromList: ImageVector = Icons.Filled.UnfoldMore
    val share: ImageVector = Icons.Filled.Share            // phosphor: share-network

    // People & money
    val customer: ImageVector = Icons.Filled.Person
    /**
     * Money changing hands: the owed banner, and the quick action that opens it.
     *
     * Banknotes rather than a currency mark. Every `$` glyph in an icon set is
     * wrong for a shop billing in riyals, and the two Material ones that carry a
     * sign say the wrong thing in the one place this is drawn.
     */
    val owed: ImageVector = Icons.Filled.Payments             // phosphor: hand-coins

    /** The owner's own spending. */
    val expenses: ImageVector = Icons.Outlined.Receipt

    // Appearance
    val themeDark: ImageVector = Icons.Filled.DarkMode        // phosphor: moon
    val themeLight: ImageVector = Icons.Filled.LightMode      // phosphor: sun
}
