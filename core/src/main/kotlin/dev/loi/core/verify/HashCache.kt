package dev.loi.core.verify

import java.io.File

/**
 * Remembers the SHA-256 of a file while its path, size, and modification time stay the same.
 *
 * Warning: a file with different bytes but the same path, size, and modification time passes.
 * The owner accepted this risk. See DESIGN.md section 8.
 *
 * A damaged, unreadable, or unwritable record is a cache miss. It never fails a load.
 */
internal class HashCache(private val directory: File) {
    fun get(stamp: FileStamp): String? = try {
        val lines = record(stamp).takeIf { it.isFile }?.readLines() ?: return null
        val match = lines.size == 5 && lines[0] == VERSION && lines[1] == stamp.path &&
            lines[2] == stamp.bytes.toString() && lines[3] == stamp.modifiedMillis.toString() && SHA256.matches(lines[4])
        if (match) lines[4] else null
    } catch (_: Exception) {
        null
    }

    fun put(stamp: FileStamp, sha256: String) {
        try {
            if (!directory.isDirectory && !directory.mkdirs()) return
            val target = record(stamp)
            val temporary = File.createTempFile(".record-", ".tmp", directory)
            try {
                temporary.writeText(listOf(VERSION, stamp.path, stamp.bytes, stamp.modifiedMillis, sha256).joinToString("\n", postfix = "\n"))
                if (!temporary.renameTo(target)) {
                    target.delete()
                    temporary.renameTo(target)
                }
            } finally {
                temporary.delete()
            }
        } catch (_: Exception) {
            // The next load hashes the file again.
        }
    }

    /** One record for each canonical path. The file name is a hash, so it never contains the path. */
    private fun record(stamp: FileStamp) = File(directory, Sha256.of(stamp.path) + ".txt")

    private companion object {
        const val VERSION = "loi-hash-v1"
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}
