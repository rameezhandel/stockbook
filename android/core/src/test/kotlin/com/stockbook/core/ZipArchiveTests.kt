package com.stockbook.core

import com.stockbook.core.transfer.ZipArchive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The archive format, checked against a real ZIP implementation.
 *
 * A round trip through our own code would prove only that it is self-consistent
 * — and self-consistent is exactly what a wrong implementation is. So the tests
 * that matter here are the two that cross over: `java.util.zip` reading what we
 * wrote, and us reading what it wrote. The Swift port has no such neighbour to
 * check against, which is why this suite is where the format is pinned down.
 */
class ZipArchiveTests {

    private fun entry(name: String, text: String) = ZipArchive.Entry(name, text.toByteArray())

    @Test
    fun `what goes in comes out`() {
        val written = ZipArchive.write(
            sequenceOf(
                entry("stockbook.json", """{"version":3}"""),
                entry("photos/abc.jpg", "not really a jpeg")
            )
        )

        val read = ZipArchive.read(written)

        assertEquals(listOf("stockbook.json", "photos/abc.jpg"), read.map { it.name })
        assertEquals("""{"version":3}""", String(read[0].bytes))
        assertEquals("not really a jpeg", String(read[1].bytes))
    }

    @Test
    fun `a real zip reader can open what we write`() {
        // The test that would have caught a wrong offset, a missing field or a
        // little-endian slip. Our own reader shares every one of those mistakes;
        // the JDK's does not.
        val written = ZipArchive.write(
            sequenceOf(entry("stockbook.json", "{}"), entry("photos/a.jpg", "aaaa"))
        )

        val found = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(written)).use { zip ->
            while (true) {
                val next = zip.nextEntry ?: break
                found[next.name] = zip.readBytes().decodeToString()
            }
        }

        assertEquals(mapOf("stockbook.json" to "{}", "photos/a.jpg" to "aaaa"), found)
    }

    @Test
    fun `we can open what a real zip writer writes`() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            val payload = "hello".toByteArray()
            // Stored, because that is all our reader accepts — and all we write.
            val record = ZipEntry("stockbook.json").apply {
                method = ZipEntry.STORED
                size = payload.size.toLong()
                compressedSize = payload.size.toLong()
                crc = CRC32().apply { update(payload) }.value
            }
            zip.putNextEntry(record)
            zip.write(payload)
            zip.closeEntry()
        }

        val read = ZipArchive.read(bytes.toByteArray())

        assertEquals(1, read.size)
        assertEquals("stockbook.json", read[0].name)
        assertEquals("hello", String(read[0].bytes))
    }

    @Test
    fun `our crc is the same number the JDK computes`() {
        val payload = "the quick brown fox".toByteArray()
        val expected = CRC32().apply { update(payload) }.value.toInt()

        assertEquals(expected, ZipArchive.crc32(payload))
    }

    @Test
    fun `an empty archive is still a valid archive`() {
        // A shop with no photographs and — briefly, during setup — nothing else.
        val written = ZipArchive.write(emptySequence())

        assertTrue(ZipArchive.read(written).isEmpty())
        ZipInputStream(ByteArrayInputStream(written)).use { assertEquals(null, it.nextEntry) }
    }

    @Test
    fun `bytes survive that are not text`() {
        // Photographs are the point, and a JPEG is full of bytes that mean
        // something in one encoding and nothing in another. Nothing in this path
        // may treat a file as a string.
        val payload = ByteArray(512) { (it * 7 % 256).toByte() }

        val read = ZipArchive.read(ZipArchive.write(sequenceOf(ZipArchive.Entry("photos/x.jpg", payload))))

        assertContentEquals(payload, read.single().bytes)
    }

    @Test
    fun `a damaged entry is refused rather than half-read`() {
        val name = "stockbook.json"
        val written = ZipArchive.write(sequenceOf(entry(name, "{\"version\":3}")))
        // The first byte of the entry's own data: a 30-byte local header, then
        // the name. Aimed rather than approximate — half this archive is header
        // and directory, and a bit flipped in a field the reader does not check
        // proves nothing about whether the CRC is doing its job.
        val data = 30 + name.length
        written[data] = (written[data] + 1).toByte()

        val failure = assertFailsWith<ZipArchive.Malformed> { ZipArchive.read(written) }
        assertTrue(failure.message!!.contains("damaged"), failure.message!!)
    }

    @Test
    fun `something that is not an archive at all is refused`() {
        assertFailsWith<ZipArchive.Malformed> { ZipArchive.read("{\"version\":3}".toByteArray()) }
    }

    @Test
    fun `a compressed archive is refused in words`() {
        // Deflate is the default everywhere else, so this is what somebody else's
        // zip will be. Refusing it is right; refusing it silently is not.
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("stockbook.json"))
            zip.write("{}".repeat(200).toByteArray())
            zip.closeEntry()
        }

        val failure = assertFailsWith<ZipArchive.Malformed> { ZipArchive.read(bytes.toByteArray()) }
        assertTrue(failure.message!!.contains("compressed"), failure.message!!)
    }

    @Test
    fun `the magic bytes tell an archive from a json file`() {
        assertTrue(ZipArchive.looksLikeZip(ZipArchive.write(sequenceOf(entry("a", "b")))))
        assertFalse(ZipArchive.looksLikeZip("""{"version":3}""".toByteArray()))
        assertFalse(ZipArchive.looksLikeZip(ByteArray(0)))
    }

    @Test
    fun `two exports of the same thing are the same bytes`() {
        // The JSON already has this property and it is worth keeping: a fixed DOS
        // timestamp is what stops an unchanged shop producing a different file
        // every time it is exported.
        val once = ZipArchive.write(sequenceOf(entry("stockbook.json", "{}")))
        val twice = ZipArchive.write(sequenceOf(entry("stockbook.json", "{}")))

        assertContentEquals(once, twice)
    }

    @Test
    fun `a big entry survives the buffer growing`() {
        // The write buffer starts at a kilobyte and doubles. A photograph is
        // three orders of magnitude past that, so the growth path is the ordinary
        // case rather than an edge one.
        val payload = ByteArray(300_000) { (it % 251).toByte() }

        val read = ZipArchive.read(ZipArchive.write(sequenceOf(ZipArchive.Entry("photos/big.jpg", payload))))

        assertContentEquals(payload, read.single().bytes)
    }
}
