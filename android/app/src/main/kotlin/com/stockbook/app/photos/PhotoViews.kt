package com.stockbook.app.photos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stockbook.app.design.GhostButton
import com.stockbook.app.design.Glyph
import com.stockbook.app.design.Icon
import com.stockbook.app.design.IconButton
import com.stockbook.app.design.Metrics
import com.stockbook.app.design.Nocturne
import com.stockbook.app.design.NocturneType
import com.stockbook.app.design.hairline
import com.stockbook.core.text.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The strip of photographs under a bill.
 *
 * Thumbnails rather than a list of file names, because the whole point of a
 * photograph is that it is recognised at a glance. Tapping one opens it full
 * screen; there is no second level of navigation for a picture of a receipt.
 */
@Composable
fun PhotoStrip(
    ids: List<String>,
    strings: Strings,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit
) {
    if (ids.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(ids, key = { it }) { id ->
            PhotoThumbnail(id = id, strings = strings, onClick = { onOpen(id) })
        }
    }
}

@Composable
private fun PhotoThumbnail(id: String, strings: Strings, onClick: () -> Unit) {
    val context = LocalContext.current
    val photos = remember(context) { PhotoStore(context) }

    // Decoding is file work, and file work on the frame thread is how a strip of
    // four pictures drops frames while it draws.
    val bitmap by produceState<android.graphics.Bitmap?>(null, id) {
        value = withContext(Dispatchers.IO) { photos.bitmap(id, edge = 320) }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 76.dp, height = 92.dp)
            .clip(RoundedCornerShape(Metrics.controlRadius))
            .background(Nocturne.surface)
            .hairline(radius = Metrics.controlRadius)
            .clickable(onClick = onClick)
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = strings.billPhotos,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // A book can arrive on a phone ahead of its pictures. Saying so is
            // the honest answer; an empty square is one the owner has to guess at.
            MissingPhoto(strings)
        }
    }
}

@Composable
private fun MissingPhoto(strings: Strings) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 6.dp)
    ) {
        Glyph(Icon.items, size = 14.dp, tint = Nocturne.neutral500)
        Spacer(Modifier.height(4.dp))
        Text(
            strings.photoNotOnThisPhone,
            style = NocturneType.meta,
            color = Nocturne.neutral500,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * One photograph, filling the screen.
 *
 * Pinch to zoom and drag to move, because the reason to open a picture of a bill
 * is almost always to read something small on it. Nothing else: no captions, no
 * editing, no filters. It is a photograph of a piece of paper.
 */
@Composable
fun PhotoViewer(
    id: String,
    strings: Strings,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val photos = remember(context) { PhotoStore(context) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, id) {
        value = withContext(Dispatchers.IO) { photos.bitmap(id) }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var confirmingRemoval by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                strings.billPhotos,
                style = NocturneType.inter(15.0),
                color = Nocturne.text,
                modifier = Modifier.weight(1f)
            )
            IconButton(Icon.share, onClick = onShare, size = 16.dp, contentDescription = strings.share)
            Spacer(Modifier.width(4.dp))
            IconButton(Icon.close, onClick = onClose, size = 16.dp, contentDescription = strings.done)
        }

        Spacer(Modifier.height(10.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(RoundedCornerShape(Metrics.controlRadius))
                .background(Color.Black)
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = strings.billPhotos,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                        .pointerInput(id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                // Floored at 1: a photograph smaller than its own
                                // frame is a picture nobody asked for. Capped at
                                // 5, which is past the point where a stored
                                // photograph has any more detail to give.
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    // Back to the middle on the way out, so the
                                    // next pinch does not start somewhere the
                                    // owner did not leave it.
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                )
            } else {
                MissingPhoto(strings)
            }
        }

        Spacer(Modifier.height(10.dp))

        GhostButton(
            if (confirmingRemoval) strings.tapAgainToRemove else strings.removePhoto,
            onClick = {
                if (confirmingRemoval) onRemove() else confirmingRemoval = true
            },
            tint = if (confirmingRemoval) Nocturne.accent400 else Nocturne.neutral500,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
