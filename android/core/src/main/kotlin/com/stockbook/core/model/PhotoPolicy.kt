package com.stockbook.core.model

import java.util.UUID

/**
 * What a stored photograph of a bill *is*, agreed once for both phones.
 *
 * The pixels are unavoidably platform work — a JPEG encoder is not domain code —
 * but the decisions around them are not, and two hand-tuned sets of constants
 * would drift the first time either was adjusted. A photograph taken on an
 * iPhone and one taken on an Android phone have to be the same kind of object,
 * because they end up in the same book and, one day, the same archive.
 *
 * Everything here is deliberately checkable without a device.
 */
object PhotoPolicy {

    /**
     * The longest edge a stored photograph may have, in pixels.
     *
     * An invoice has to be *legible*, not archival. At 1600 the writing on a
     * hand-filled A5 bill reads comfortably at full zoom, and the file lands
     * somewhere around a fifth of a megabyte — which is the difference between a
     * book that can be carried to a new phone and one that cannot.
     */
    const val maxEdge = 1600

    /**
     * JPEG quality, 0–1.
     *
     * Low enough to matter, high enough that pen strokes do not smear into the
     * paper. Photographs of documents are mostly flat white, which compresses
     * far better than a photograph of a room.
     */
    const val quality = 0.6

    /** Android's `Bitmap.compress` counts quality out of 100; Core Graphics wants 0–1. */
    const val qualityOutOfHundred = 60

    const val fileExtension = "jpg"

    /**
     * A new id.
     *
     * Random rather than derived from the bill, because one bill may carry
     * several photographs and because bills are edited — naming a file after its
     * bill means renaming files during an edit, which is precisely how a book
     * ends up pointing at something that is no longer there.
     */
    fun newId(): String = UUID.randomUUID().toString()

    /** `7f3a1c….jpg` — the name this id has on disk, and inside an archive. */
    fun fileName(id: String): String = "$id.$fileExtension"

    /**
     * Whether a name on disk is one of ours.
     *
     * Used when sweeping, so a stray file that arrived some other way is left
     * alone rather than deleted by an app that did not put it there.
     */
    fun idFromFileName(name: String): String? =
        name.removeSuffix(".$fileExtension").takeIf { it != name && it.isNotEmpty() }
}
