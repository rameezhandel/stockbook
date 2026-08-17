package com.stockbook.app.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
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
    val back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack

    // Actions
    val add: ImageVector = Icons.Filled.Add
    val remove: ImageVector = Icons.Filled.Remove
    val delete: ImageVector = Icons.Filled.Delete
    val edit: ImageVector = Icons.Filled.Edit
    val close: ImageVector = Icons.Filled.Close
    val confirm: ImageVector = Icons.Filled.Check
    val openRow: ImageVector = Icons.Filled.KeyboardArrowRight
    val chooseFromList: ImageVector = Icons.Filled.UnfoldMore
    val share: ImageVector = Icons.Filled.Share            // phosphor: share-network

    // People
    val customer: ImageVector = Icons.Filled.Person
}
