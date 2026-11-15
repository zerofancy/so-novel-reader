package top.ntutn.sonovelreader.data.fonts

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

internal data class FontInspection(val format: String, val name: String?)
internal data class CopiedFont(val bytes: Long, val sha256: String)

internal object FontValidation {
    fun copy(input: InputStream, output: OutputStream, limit: Long = MAX_FONT_BYTES): CopiedFont {
        require(limit in 0..MAX_FONT_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var size = 0L
        while (true) {
            // Request at most one byte past the bound, including unknown-size providers.
            var count = input.read(buffer, 0, minOf(buffer.size.toLong(), limit - size + 1).toInt())
            if (count < 0) break
            if (count == 0) {
                // A broken provider must not make us spin forever on zero-length bulk reads.
                val byte = input.read()
                if (byte < 0) break
                buffer[0] = byte.toByte()
                count = 1
            }
            size += count
            if (size > limit) throw FontException(FontError.TOO_LARGE)
            output.write(buffer, 0, count)
            digest.update(buffer, 0, count)
        }
        if (size == 0L) throw FontException(FontError.CORRUPT)
        return CopiedFont(size, digest.digest().joinToString("") { "%02x".format(it) })
    }

    fun inspect(file: File): FontInspection = RandomAccessFile(file, "r").use { f ->
        fun invalid(): Nothing = throw FontException(FontError.CORRUPT)
        if (f.length() < 12) invalid()
        val signature = f.readInt()
        val format = when (signature) {
            0x00010000 -> "ttf"
            0x4f54544f -> "otf"
            0x74746366, 0x774f4646, 0x774f4632 -> throw FontException(FontError.UNSUPPORTED)
            else -> throw FontException(FontError.UNSUPPORTED)
        }
        val count = f.readUnsignedShort()
        if (count == 0 || count > 4096 || 12L + count * 16L > f.length()) invalid()
        f.seek(12)
        val tables = mutableMapOf<String, Pair<Long, Long>>()
        repeat(count) {
            val tagBytes = ByteArray(4).also { f.readFully(it) }
            val tag = String(tagBytes, Charsets.US_ASCII)
            f.readInt()
            val offset = f.readInt().toLong() and 0xffffffffL
            val length = f.readInt().toLong() and 0xffffffffL
            if (offset < 12L + count * 16L || offset + length > f.length()) invalid()
            if (tables.put(tag, offset to length) != null) invalid()
        }
        val ranges = tables.values.filter { it.second > 0 }.sortedBy { it.first }
        ranges.zipWithNext().forEach { (previous, next) ->
            if (previous.first + previous.second > next.first) invalid()
        }
        if (tables.keys.any { it in setOf("fvar", "gvar", "avar", "CFF2") }) {
            throw FontException(FontError.UNSUPPORTED)
        }
        for (tag in listOf("head", "hhea", "hmtx", "maxp", "cmap", "name")) {
            if ((tables[tag]?.second ?: 0) == 0L) invalid()
        }
        if (format == "ttf" && ("glyf" !in tables || "loca" !in tables)) invalid()
        if (format == "otf" && "CFF " !in tables) invalid()
        val head = tables.getValue("head")
        if (head.second < 54) invalid()
        f.seek(head.first + 12)
        if (f.readInt() != 0x5f0f3cf5) invalid()
        FontInspection(format, readName(f, tables.getValue("name")))
    }

    private fun readName(f: RandomAccessFile, table: Pair<Long, Long>): String? {
        if (table.second < 6) return null
        f.seek(table.first)
        val format = f.readUnsignedShort()
        val count = f.readUnsignedShort()
        val strings = f.readUnsignedShort()
        if (format !in 0..1 || 6L + count * 12L > table.second || strings < 6L + count * 12L) return null
        data class Name(val text: String, val score: Int)
        // Keep only four winning records. Repeated references to one long string in a
        // malicious name table must not amplify memory by record count (up to 65,535).
        val names = mutableMapOf<Int, Name>()
        repeat(count) { i ->
            f.seek(table.first + 6 + i * 12)
            val platform = f.readUnsignedShort()
            f.readUnsignedShort()
            val language = f.readUnsignedShort()
            val id = f.readUnsignedShort()
            val length = f.readUnsignedShort()
            val offset = f.readUnsignedShort()
            val score = if (language == 0x409) 2 else 1
            if (id in listOf(1, 2, 16, 17) && length in 2..4096 && length % 2 == 0 &&
                strings.toLong() + offset + length <= table.second && platform in listOf(0, 3) &&
                score > (names[id]?.score ?: 0)
            ) {
                f.seek(table.first + strings + offset)
                val bytes = ByteArray(length).also { f.readFully(it) }
                val text = String(bytes, Charsets.UTF_16BE)
                if (displayName(text, null) != "导入字体") names[id] = Name(text, score)
            }
        }
        fun best(vararg ids: Int) = ids.asList().firstNotNullOfOrNull { names[it]?.text }
        val family = best(16, 1) ?: return null
        return listOfNotNull(family, best(17, 2)).joinToString(" ")
    }

    fun displayName(metadata: String?, fileName: String?): String {
        fun clean(value: String) = buildString {
            value.codePoints().forEach { cp ->
                if (!Character.isISOControl(cp) && Character.getType(cp) != Character.FORMAT.toInt() &&
                    cp !in listOf('/'.code, '\\'.code, ':'.code)
                ) appendCodePoint(cp)
            }
        }.trim().let { text ->
            val count = text.codePointCount(0, text.length)
            if (count <= 80) text else text.substring(0, text.offsetByCodePoints(0, 80))
        }
        return metadata?.let(::clean)?.takeIf { it.isNotBlank() }
            ?: fileName?.substringBeforeLast('.')?.let(::clean)?.takeIf { it.isNotBlank() }
            ?: "导入字体"
    }
}
