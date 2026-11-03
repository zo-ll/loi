package dev.loi.core.verify

import java.io.File

/**
 * The identity of a file for the hash cache: canonical path, size, and modification time.
 *
 * This class uses `java.io.File` only. `java.nio.file` needs Android API 26, and loi supports API 24.
 */
internal data class FileStamp(val path: String, val bytes: Long, val modifiedMillis: Long) {
    companion object {
        fun of(file: File): FileStamp = FileStamp(file.canonicalPath, file.length(), file.lastModified())
    }
}
