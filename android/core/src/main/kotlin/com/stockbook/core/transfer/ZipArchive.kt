package com.stockbook.core.transfer

/**
 * A ZIP archive, written and read by hand, storing rather than compressing.
 *
 * `java.util.zip` would do this on Android in ten lines. It exists here anyway,
 * hand-written, for one reason: **iOS has no zip reader at all** and this project
 * has no dependencies, so the Swift side has to be written from the format
 * specification either way. Written twice from the specification, the two would
 * drift; written once here and ported line for line, they cannot. The Kotlin is
 * the one that can be tested in fifteen seconds against a real ZIP
 * implementation, which is what makes it the original rather than the copy.
 *
 * **Stored, never deflated.** Everything that goes in is either JSON — written
 * once, small — or a JPEG, which is already compressed and would grow slightly
 * if deflated. Dropping compression drops the only part of the format that would
 * be genuinely hard to hand-write, and the archives are within a percent of the
 * size either way.
 *
 * No zip64, no encryption, no data descriptors, no multi-disk. A shop's records
 * will not reach four gigabytes, and refusing what we do not write keeps the
 * reader small enough to hold in the head.
 */
object ZipArchive {

    /** One file in the archive. */
    data class Entry(val name: String, val bytes: ByteArray) {
        // `ByteArray` gives reference equality from the data-class generated
        // methods, which would make two identical entries unequal and quietly
        // break any test that compares them.
        override fun equals(other: Any?): Boolean =
            this === other || (other is Entry && name == other.name && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
    }

    /** Thrown for anything this reader will not accept. Callers turn it into a `BackupError`. */
    class Malformed(message: String) : Exception(message)

    private const val LOCAL_SIGNATURE = 0x04034b50
    private const val CENTRAL_SIGNATURE = 0x02014b50
    private const val END_SIGNATURE = 0x06054b50

    /**
     * The DOS timestamp every entry carries: 1980-01-01 00:00, the earliest the
     * format can express.
     *
     * Deliberately fixed rather than "now". Two exports of an unchanged shop
     * should be the same bytes, the way the JSON already is — and a real
     * timestamp here would be a second clock disagreeing with `exportedAt`
     * inside the document, in a field with no timezone to say what it means.
     */
    private const val DOS_TIME = 0
    private const val DOS_DATE = 0x0021

    // MARK: Writing

    /**
     * Builds an archive from [entries].
     *
     * A `Sequence` rather than a list so the caller can hand over one photograph
     * at a time, read from disk as it is asked for. Peak memory is then the
     * largest single file rather than the whole shop's pictures at once — which
     * is the entire reason this is a ZIP and not base64 inside the JSON.
     */
    fun write(entries: Sequence<Entry>): ByteArray {
        val out = Buffer()
        // Kept to write the central directory, which repeats each entry's
        // details at the end of the file. Names and offsets only — the bytes
        // themselves are already written and gone.
        val directory = mutableListOf<Triple<String, Int, Int>>() // name, crc, offset
        val sizes = mutableListOf<Int>()

        for (entry in entries) {
            val name = entry.name.toByteArray(Charsets.UTF_8)
            val crc = crc32(entry.bytes)
            val offset = out.size

            out.int(LOCAL_SIGNATURE)
            out.short(10)                    // version needed: 1.0 is enough for stored
            out.short(0)                     // flags
            out.short(0)                     // method: stored
            out.short(DOS_TIME)
            out.short(DOS_DATE)
            out.int(crc)
            out.int(entry.bytes.size)        // compressed size
            out.int(entry.bytes.size)        // uncompressed size
            out.short(name.size)
            out.short(0)                     // extra length
            out.bytes(name)
            out.bytes(entry.bytes)

            directory += Triple(entry.name, crc, offset)
            sizes += entry.bytes.size
        }

        val directoryStart = out.size
        for ((index, record) in directory.withIndex()) {
            val (entryName, crc, offset) = record
            val name = entryName.toByteArray(Charsets.UTF_8)
            out.int(CENTRAL_SIGNATURE)
            out.short(10)                    // version made by
            out.short(10)                    // version needed
            out.short(0)                     // flags
            out.short(0)                     // method
            out.short(DOS_TIME)
            out.short(DOS_DATE)
            out.int(crc)
            out.int(sizes[index])
            out.int(sizes[index])
            out.short(name.size)
            out.short(0)                     // extra
            out.short(0)                     // comment
            out.short(0)                     // disk number
            out.short(0)                     // internal attributes
            out.int(0)                       // external attributes
            out.int(offset)
            out.bytes(name)
        }
        val directorySize = out.size - directoryStart

        out.int(END_SIGNATURE)
        out.short(0)                         // this disk
        out.short(0)                         // disk with the central directory
        out.short(directory.size)
        out.short(directory.size)
        out.int(directorySize)
        out.int(directoryStart)
        out.short(0)                         // comment length

        return out.toByteArray()
    }

    // MARK: Reading

    /** Whether [bytes] begins with the local-header signature — `PK`. */
    fun looksLikeZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    /** Where one file sits in the archive. Reading the directory costs no data. */
    private data class Record(val name: String, val offset: Int, val size: Int, val crc: Int)

    /** Every file in the archive, in the order the central directory lists them. */
    fun read(bytes: ByteArray): List<Entry> {
        val entries = mutableListOf<Entry>()
        forEach(bytes) { name, data -> entries += Entry(name, data) }
        return entries
    }

    /**
     * The same walk, handing each file over as it is found and keeping none.
     *
     * This is what the import path uses. `read` materialises the whole archive,
     * which for a shop with two hundred photographs would be the pictures twice
     * over — once as the archive still in hand, once as the list. Here the only
     * thing alive at any moment is the photograph being written to disk.
     */
    fun forEach(bytes: ByteArray, action: (name: String, data: ByteArray) -> Unit) {
        for (record in records(bytes)) {
            action(record.name, dataAt(bytes, record))
        }
    }

    /**
     * One named file, or null if the archive does not have it.
     *
     * Separate from [forEach] so that finding the document does not read every
     * photograph on the way past. That is not only wasted work: every entry it
     * touched would have its CRC checked, so a single damaged picture would have
     * refused the whole book — which is exactly backwards, since the book is the
     * part that cannot be replaced.
     */
    fun entry(bytes: ByteArray, name: String): ByteArray? =
        records(bytes).firstOrNull { it.name == name }?.let { dataAt(bytes, it) }

    /**
     * The central directory, read for what it says rather than for what it
     * points at.
     *
     * The directory rather than a walk of local headers, because a writer that
     * used a data descriptor leaves the local header's sizes as zero and only the
     * directory is then telling the truth. We never write one; other people's
     * tools do.
     */
    private fun records(bytes: ByteArray): List<Record> {
        val end = findEndRecord(bytes) ?: throw Malformed("no end-of-central-directory record")
        val count = bytes.short(end + 10)
        val directoryStart = bytes.int(end + 16)
        if (directoryStart < 0 || directoryStart > bytes.size) throw Malformed("central directory outside the file")

        val found = mutableListOf<Record>()
        var cursor = directoryStart
        repeat(count) {
            if (cursor + 46 > bytes.size) throw Malformed("central directory ends early")
            if (bytes.int(cursor) != CENTRAL_SIGNATURE) throw Malformed("bad central directory header")

            val method = bytes.short(cursor + 10)
            val crc = bytes.int(cursor + 16)
            val size = bytes.int(cursor + 24)
            val nameLength = bytes.short(cursor + 28)
            val extraLength = bytes.short(cursor + 30)
            val commentLength = bytes.short(cursor + 32)
            val localOffset = bytes.int(cursor + 42)
            val name = String(bytes, cursor + 46, nameLength, Charsets.UTF_8)

            // Said plainly rather than guessed at: a deflated archive is somebody
            // else's file, and half-reading it would be worse than refusing it.
            if (method != 0) throw Malformed("entry '$name' is compressed; this reader only stores")

            found += Record(name, localOffset, size, crc)
            cursor += 46 + nameLength + extraLength + commentLength
        }
        return found
    }

    /** The bytes of one entry, found through its local header and checked against [crc]. */
    private fun dataAt(bytes: ByteArray, record: Record): ByteArray {
        val (name, offset, size, crc) = record
        if (offset < 0 || offset + 30 > bytes.size) throw Malformed("entry '$name' points outside the file")
        if (bytes.int(offset) != LOCAL_SIGNATURE) throw Malformed("bad local header for '$name'")

        // The local header's own name and extra lengths, not the directory's:
        // the extra field is allowed to differ between the two, and using the
        // wrong one lands the read a few bytes into the file's contents.
        val nameLength = bytes.short(offset + 26)
        val extraLength = bytes.short(offset + 28)
        val start = offset + 30 + nameLength + extraLength
        if (start + size > bytes.size) throw Malformed("entry '$name' ends past the end of the file")

        val data = bytes.copyOfRange(start, start + size)
        if (crc32(data) != crc) throw Malformed("entry '$name' is damaged")
        return data
    }

    /**
     * Scans backwards for the end record.
     *
     * Backwards because the record is last, and only *nearly* last: the format
     * allows a trailing comment of up to 64 KB after it. Scanning forwards would
     * also risk stopping at a signature that happens to appear inside a JPEG.
     */
    private fun findEndRecord(bytes: ByteArray): Int? {
        if (bytes.size < 22) return null
        val earliest = maxOf(0, bytes.size - 22 - 0xFFFF)
        for (index in bytes.size - 22 downTo earliest) {
            if (bytes.int(index) == END_SIGNATURE) return index
        }
        return null
    }

    // MARK: Bytes

    /** A growable little-endian byte buffer. */
    private class Buffer {
        private var storage = ByteArray(1024)
        var size = 0
            private set

        fun bytes(value: ByteArray) {
            ensure(value.size)
            value.copyInto(storage, size)
            size += value.size
        }

        fun short(value: Int) {
            ensure(2)
            storage[size] = (value and 0xFF).toByte()
            storage[size + 1] = ((value ushr 8) and 0xFF).toByte()
            size += 2
        }

        fun int(value: Int) {
            ensure(4)
            for (shift in 0 until 4) {
                storage[size + shift] = ((value ushr (8 * shift)) and 0xFF).toByte()
            }
            size += 4
        }

        fun toByteArray(): ByteArray = storage.copyOf(size)

        private fun ensure(extra: Int) {
            if (size + extra <= storage.size) return
            var capacity = storage.size
            while (capacity < size + extra) capacity *= 2
            storage = storage.copyOf(capacity)
        }
    }

    private fun ByteArray.short(at: Int): Int =
        (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.int(at: Int): Int =
        (this[at].toInt() and 0xFF) or
            ((this[at + 1].toInt() and 0xFF) shl 8) or
            ((this[at + 2].toInt() and 0xFF) shl 16) or
            ((this[at + 3].toInt() and 0xFF) shl 24)

    // MARK: CRC-32

    /**
     * The table for the CRC-32 every ZIP entry carries, polynomial `0xEDB88320`.
     *
     * Not `java.util.zip.CRC32`, for the same reason as everything else in this
     * file: the Swift side has to compute the identical number and there is
     * nothing to borrow there, so the arithmetic is written out where both can
     * be checked against it.
     */
    private val table: IntArray = IntArray(256) { index ->
        var value = index
        repeat(8) {
            value = if (value and 1 != 0) (value ushr 1) xor 0xEDB88320.toInt() else value ushr 1
        }
        value
    }

    fun crc32(bytes: ByteArray): Int {
        var crc = -1
        for (byte in bytes) {
            crc = (crc ushr 8) xor table[(crc xor byte.toInt()) and 0xFF]
        }
        return crc.inv()
    }
}
