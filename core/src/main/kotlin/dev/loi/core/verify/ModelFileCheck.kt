package dev.loi.core.verify

import dev.loi.core.api.KnownArtifact
import dev.loi.core.api.Verification
import dev.loi.core.api.VerificationResult
import dev.loi.core.errors.LoiException
import dev.loi.core.errors.LoiException.ModelFile.Reason
import java.io.File

/** The file check with the three levels. See DESIGN.md section 8. */
internal class ModelFileCheck(
    private val known: List<KnownArtifact>,
    private val cache: HashCache,
    private val hasher: (File) -> String = Sha256::of,
) {
    class Result(val verification: VerificationResult, val stamp: FileStamp)

    fun check(file: File, level: Verification): Result {
        val stamp = readable(file)
        return when (level) {
            Verification.OFF -> Result(VerificationResult.Skipped, stamp)
            Verification.WARN -> {
                val sha = sha256(file, stamp)
                Result(match(stamp, sha)?.let { VerificationResult.Known(it) } ?: VerificationResult.Unknown(sha), stamp)
            }
            Verification.STRICT -> {
                // A size check is fast. It avoids a long hash of a file that cannot match.
                if (known.none { it.bytes == stamp.bytes }) {
                    throw LoiException.Verification(LoiException.Verification.Reason.UNKNOWN_SIZE)
                }
                val artifact = match(stamp, sha256(file, stamp))
                    ?: throw LoiException.Verification(LoiException.Verification.Reason.UNKNOWN_HASH)
                Result(VerificationResult.Known(artifact), stamp)
            }
        }
    }

    private fun match(stamp: FileStamp, sha: String): KnownArtifact? = known.firstOrNull { it.sha256 == sha && it.bytes == stamp.bytes }

    private fun readable(file: File): FileStamp {
        try {
            if (!file.exists()) throw LoiException.ModelFile(Reason.MISSING)
            if (!file.isFile) throw LoiException.ModelFile(Reason.NOT_A_FILE)
            if (!file.canRead()) throw LoiException.ModelFile(Reason.UNREADABLE)
            return FileStamp.of(file)
        } catch (error: LoiException) {
            throw error
        } catch (error: Exception) {
            throw LoiException.ModelFile(Reason.UNREADABLE, error)
        }
    }

    private fun sha256(file: File, before: FileStamp): String {
        cache.get(before)?.let { return it }
        val sha = try {
            hasher(file)
        } catch (error: Exception) {
            throw LoiException.ModelFile(Reason.UNREADABLE, error)
        }
        // A hash of a file that changed during the read is not the hash of any one version of the file.
        val after = try { FileStamp.of(file) } catch (error: Exception) { throw LoiException.ModelFile(Reason.UNREADABLE, error) }
        if (after != before) throw LoiException.ModelFile(Reason.CHANGED)
        cache.put(before, sha)
        return sha
    }
}
