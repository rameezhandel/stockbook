package com.stockbook.app.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.stockbook.core.model.PhotoPolicy
import java.io.File
import java.io.InputStream

/**
 * Photographs of paper bills, on this phone.
 *
 * The book holds ids; this holds the pictures. They are kept apart because the
 * shop file is rewritten every time stock moves, and a photograph is a thousand
 * times the size of everything else in it — one sale would mean rewriting
 * megabytes.
 *
 * Files live under the app's own storage, beside the book. Nothing else on the
 * phone can read them: they are not in the gallery, not in the Files app, and not
 * offered to other apps' pickers. A shop's invoices should not turn up while
 * somebody is scrolling their photographs, and they do not. That location is also
 * what keeps the app's promise that it asks the phone for nothing — writing here
 * needs no permission at all.
 *
 * Deliberately not in `core`: an image codec is not domain work. What *is* domain
 * work — how large, how compressed, what a file is called — lives in
 * [PhotoPolicy], so both phones store the same kind of object.
 */
class PhotoStore(private val context: Context) {

    /** Beside `stockbook/shop.json`, so the book and its pictures share a fate. */
    private val directory: File
        get() = File(context.filesDir, "stockbook/photos").also { it.mkdirs() }

    fun file(id: String): File = File(directory, PhotoPolicy.fileName(id))

    /**
     * Whether this phone actually has the picture.
     *
     * A separate question from whether the bill names one, and asked every time
     * a photograph is shown. A book that arrived from another phone names
     * pictures this one has never had.
     */
    fun has(id: String): Boolean = file(id).isFile

    /**
     * Reads what the camera or the picker handed back, shrinks it, and keeps it.
     *
     * Returns the new id, or null if the image could not be read — a picker can
     * hand back a Uri that resolves to nothing, and that is not a crash.
     */
    fun save(source: Uri): String? {
        val bitmap = decodeScaled(source) ?: return null
        val id = PhotoPolicy.newId()
        return try {
            file(id).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, PhotoPolicy.qualityOutOfHundred, out)
            }
            id
        } catch (_: Exception) {
            file(id).delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    fun delete(id: String) {
        file(id).delete()
    }

    /**
     * Collects pictures the book no longer refers to.
     *
     * Runs one way only, and the asymmetry is the point: a file nothing points at
     * is rubbish, but an id whose file is missing is a photograph this phone has
     * not got *yet*. Restoring a book strands every picture on the phone, which
     * is what this is mainly for.
     *
     * Files it did not write are left alone — an app that tidies away things it
     * does not recognise is an app that eventually deletes something it should
     * not have.
     */
    fun sweep(keeping: Set<String>) {
        val files = directory.listFiles() ?: return
        for (candidate in files) {
            val id = PhotoPolicy.idFromFileName(candidate.name) ?: continue
            if (id !in keeping) candidate.delete()
        }
    }

    /** What the owner is spending on pictures: how many, and how much room. */
    fun usage(): Usage {
        val files = directory.listFiles().orEmpty()
            .filter { PhotoPolicy.idFromFileName(it.name) != null }
        return Usage(count = files.size, bytes = files.sumOf { it.length() })
    }

    data class Usage(val count: Int, val bytes: Long)

    /**
     * Decodes at a reduced size rather than decoding and then shrinking.
     *
     * A modern phone camera hands back twelve megapixels or more. Decoding that
     * whole bitmap to throw most of it away is how a mid-range phone runs out of
     * memory holding a picture of a receipt — so the bounds are read first and
     * the decoder is asked for something close to the size actually wanted.
     */
    private fun decodeScaled(source: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        read(source) { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = read(source) { BitmapFactory.decodeStream(it, null, options) } ?: return null

        // The camera records which way up the phone was rather than rotating the
        // pixels. Skip this and every photograph taken in portrait arrives on its
        // side — which on a document is not a cosmetic problem.
        val rotation = read(source) { rotationOf(ExifInterface(it)) } ?: 0
        return scaledAndTurned(decoded, rotation)
    }

    /**
     * The orientation tag, read as degrees.
     *
     * Spelled out rather than through a convenience getter, so it holds on every
     * API level this app runs on. The mirrored orientations are treated as their
     * nearest rotation: a photograph of a bill is never deliberately flipped, and
     * turning it the right way up matters more than reproducing a tag no camera
     * on a phone writes.
     */
    private fun rotationOf(exif: ExifInterface): Int =
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }

    /** Halvings, which is all `BitmapFactory` accepts, stopping above the target. */
    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= PhotoPolicy.maxEdge) sample *= 2
        return sample
    }

    private fun scaledAndTurned(source: Bitmap, rotation: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        val ratio = if (longest > PhotoPolicy.maxEdge) PhotoPolicy.maxEdge.toFloat() / longest else 1f
        if (ratio == 1f && rotation == 0) return source

        val matrix = Matrix().apply {
            postScale(ratio, ratio)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (result !== source) source.recycle()
        return result
    }

    private fun <T> read(source: Uri, block: (InputStream) -> T): T? = try {
        context.contentResolver.openInputStream(source)?.use(block)
    } catch (_: Exception) {
        null
    }
}
