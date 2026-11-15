package top.ntutn.sonovelreader.data.fonts

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Test

class FontValidationTest {
    @Test fun boundedCopyAcceptsBoundaryAndHashesActualBytes() {
        val bytes = "abc".toByteArray()
        val copy = FontValidation.copy(ByteArrayInputStream(bytes), ByteArrayOutputStream(), 3)
        assertEquals(3L, copy.bytes)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", copy.sha256)
    }

    @Test fun boundedCopyRejectsOneBytePastBoundary() {
        expect(FontError.TOO_LARGE) {
            FontValidation.copy(ByteArrayInputStream(ByteArray(4)), ByteArrayOutputStream(), 3)
        }
        expect(FontError.CORRUPT) {
            FontValidation.copy(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream())
        }
    }

    @Test fun namesAreSafeLimitedByCodePointsAndHaveFallbacks() {
        assertEquals("ABCD", FontValidation.displayName("A/B\\C\nD", null))
        assertEquals("你好", FontValidation.displayName(null, "你好.ttf"))
        assertEquals("导入字体", FontValidation.displayName("\u0000", null))
        val emoji = "😀".repeat(100)
        val name = FontValidation.displayName(emoji, null)
        assertEquals(80, name.codePointCount(0, name.length))
    }

    @Test fun rejectsCollectionsWebFontsAndUnknownSignatures() {
        for (signature in listOf(0x74746366, 0x774f4646, 0x774f4632, 0x41414141)) {
            withFont(signature = signature) { file ->
                expect(FontError.UNSUPPORTED) { FontValidation.inspect(file) }
            }
        }
    }

    @Test fun validatesStaticSfntAndRejectsVariableAxesAndTruncatedTables() {
        withFont { assertEquals("ttf", FontValidation.inspect(it).format) }
        withFont(variable = true) { expect(FontError.UNSUPPORTED) { FontValidation.inspect(it) } }
        withFont { file ->
            RandomAccessFile(file, "rw").use { it.setLength(30) }
            expect(FontError.CORRUPT) { FontValidation.inspect(file) }
        }
    }

    @Test fun rejectsOutOfBoundsOffsetsAndMissingRequiredTables() {
        withFont { file ->
            RandomAccessFile(file, "rw").use { it.seek(20); it.writeInt(Int.MAX_VALUE) }
            expect(FontError.CORRUPT) { FontValidation.inspect(file) }
        }
        withFont { file ->
            RandomAccessFile(file, "rw").use { it.seek(12); it.writeBytes("xxxx") }
            expect(FontError.CORRUPT) { FontValidation.inspect(file) }
        }
    }

    @Test fun zeroBulkReadsCannotSpinAndReadFailuresAreNotRelabeledAsCorruption() {
        val source = object : java.io.InputStream() {
            private var count = 0
            override fun read(b: ByteArray, off: Int, len: Int) = 0
            override fun read() = if (count++ < 3) 'a'.code else -1
        }
        val target = ByteArrayOutputStream()
        assertEquals(3L, FontValidation.copy(source, target).bytes)
        assertEquals("aaa", target.toString("UTF-8"))
        val failure = java.io.IOException("provider interrupted")
        val broken = object : java.io.InputStream() {
            override fun read(): Int = throw failure
        }
        try {
            FontValidation.copy(broken, ByteArrayOutputStream())
            fail("Read failure swallowed")
        } catch (e: java.io.IOException) { assertSame(failure, e) }
    }

    @Test fun actualFiftyMiBBoundaryReadsAtMostOneExtraByte() {
        var remaining = MAX_FONT_BYTES + 100
        var consumed = 0L
        val unknownSize = object : java.io.InputStream() {
            override fun available() = 0
            override fun read() = throw AssertionError("Bulk stream expected")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining == 0L) return -1
                val count = minOf(remaining, len.toLong()).toInt()
                java.util.Arrays.fill(b, off, off + count, 0.toByte())
                remaining -= count
                consumed += count
                return count
            }
        }
        val discard = object : java.io.OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) = Unit
        }
        expect(FontError.TOO_LARGE) { FontValidation.copy(unknownSize, discard) }
        assertEquals(MAX_FONT_BYTES + 1, consumed)
        remaining = MAX_FONT_BYTES
        consumed = 0
        assertEquals(MAX_FONT_BYTES, FontValidation.copy(unknownSize, discard).bytes)
        assertEquals(MAX_FONT_BYTES, consumed)
    }

    @Test fun overlappingTablesAreRejectedAndBrokenMetadataFallsBack() {
        withFont { file ->
            RandomAccessFile(file, "rw").use { f ->
                f.seek(20); val firstOffset = f.readInt()
                f.seek(36); f.writeInt(firstOffset)
            }
            expect(FontError.CORRUPT) { FontValidation.inspect(file) }
        }
        withFont { file ->
            RandomAccessFile(file, "rw").use { f ->
                f.seek(12 + 5 * 16 + 8L); val nameOffset = f.readInt()
                f.seek(nameOffset.toLong())
                f.writeShort(0); f.writeShort(1); f.writeShort(0) // string storage overlaps header
            }
            assertNull(FontValidation.inspect(file).name)
        }
    }

    @Test fun truncatedOrRandomShortInputsHaveOnlyControlledFormatErrors() {
        val random = java.util.Random(123)
        repeat(300) {
            val file = File.createTempFile("font-fuzz", ".bin")
            try {
                file.writeBytes(ByteArray(random.nextInt(256)).also { random.nextBytes(it) })
                try { FontValidation.inspect(file); fail("Random bytes unexpectedly valid") }
                catch (e: FontException) { assertTrue(e.code in listOf(FontError.CORRUPT, FontError.UNSUPPORTED)) }
            } finally { file.delete() }
        }
    }

    private fun withFont(
        signature: Int = 0x00010000,
        variable: Boolean = false,
        block: (File) -> Unit,
    ) {
        val file = File.createTempFile("font-validation", ".ttf")
        try {
            val tags = mutableListOf("head", "hhea", "hmtx", "maxp", "cmap", "name", "glyf", "loca")
            if (variable) tags += "fvar"
            RandomAccessFile(file, "rw").use { f ->
                f.writeInt(signature)
                f.writeShort(tags.size)
                f.write(ByteArray(6))
                var offset = 12 + tags.size * 16
                tags.forEach { tag ->
                    f.writeBytes(tag); f.writeInt(0); f.writeInt(offset); f.writeInt(64)
                    offset += 64
                }
                f.setLength(offset.toLong())
                f.seek((12 + tags.size * 16 + 12).toLong())
                f.writeInt(0x5f0f3cf5)
            }
            block(file)
        } finally { file.delete() }
    }

    private fun expect(error: FontError, block: () -> Unit) {
        try { block(); fail("Expected $error") } catch (e: FontException) { assertEquals(error, e.code) }
    }
}
