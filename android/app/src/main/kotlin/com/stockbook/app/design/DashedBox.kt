package com.stockbook.app.design

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * The dashed container used for every empty state and the backup nudge.
 *
 * Drawn rather than composed from a border: Compose has no dashed
 * `BorderStroke`, and the design's `1px dash 4 4` is specific enough to be worth
 * honouring exactly.
 */
fun Modifier.dashedBox(): Modifier = drawBehind {
    val width = 1.dp.toPx()
    val dash = 4.dp.toPx()
    val stroke = Stroke(
        width = width,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))
    )
    val inset = width / 2
    drawRoundRect(
        color = Nocturne.neutral800,
        topLeft = Offset(inset, inset),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(Metrics.cardRadius.toPx()),
        style = stroke
    )
}
