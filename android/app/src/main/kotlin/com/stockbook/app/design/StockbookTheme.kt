package com.stockbook.app.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The handful of surfaces Material draws for us, taught the Nocturne palette.
 *
 * Almost nothing in this app is a Material component — the design system draws
 * its own fields, buttons, cards and sheets. But three things are the platform's
 * and have to be: the dropdown menu behind a `DropdownField`, the date picker on
 * the statement screen, and the dialog that carries it. Without this they render
 * in Material's default *light* scheme, which is how a white calendar came to
 * open over a near-black app.
 *
 * Only the roles those three actually read are mapped. The rest of the scheme is
 * left at its default deliberately: a value nothing reads is a value nobody can
 * check, and filling all thirty in would read as a second palette living beside
 * the real one.
 */
@Composable
fun StockbookTheme(content: @Composable () -> Unit) {
    val base = if (Nocturne.isLight) lightColorScheme() else darkColorScheme()
    MaterialTheme(
        colorScheme = base.copy(
            primary = Nocturne.accent,
            onPrimary = Nocturne.surface,
            background = Nocturne.bg,
            onBackground = Nocturne.text,
            surface = Nocturne.surface,
            onSurface = Nocturne.text,
            // What a menu and a dialog actually sit on.
            surfaceContainer = Nocturne.surface,
            surfaceContainerHigh = Nocturne.surface,
            surfaceContainerHighest = Nocturne.surface,
            onSurfaceVariant = Nocturne.neutral500,
            outline = Nocturne.neutral800,
            outlineVariant = Nocturne.neutral800,
            scrim = Nocturne.scrim
        ),
        content = content
    )
}
