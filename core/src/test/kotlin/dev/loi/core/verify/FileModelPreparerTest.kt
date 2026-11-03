package dev.loi.core.verify

import dev.loi.core.api.KnownArtifact
import dev.loi.core.api.Verification
import dev.loi.core.api.VerificationResult
import dev.loi.core.engine.EngineOptions
import dev.loi.core.errors.LoiException
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FileModelPreparerTest {
    private val root: File = Files.createTempDirectory("loi-verify").toFile()
    private val storage = File(root, "storage")
    private val model = File(root, "model.litertlm").apply { writeText("model bytes v1") }
    private val sha = Sha256.of(model)
    private val known = listOf(KnownArtifact("fake", "model.litertlm", model.length(), sha))
    private var hashes = 0

    @AfterTest fun cleanup() { root.deleteRecursively() }

    private fun preparer(level: Verification, file: File = model, artifacts: List<KnownArtifact> = known) =
        FileModelPreparer(file, level, artifacts, storage, ::options) { hashes++; Sha256.of(it) }

    private fun options(path: String, cache: String) = EngineOptions(
        modelPath = path, cacheDir = cache, maxInputTokens = 8, outputDimensions = 3, normalize = true, insertSpecialTokens = true,
    )

    private fun fileReason(level: Verification, file: File) =
        assertFailsWith<LoiException.ModelFile> { preparer(level, file).prepare() }.reason

    private fun verificationReason(file: File = model, artifacts: List<KnownArtifact> = known) =
        assertFailsWith<LoiException.Verification> { preparer(Verification.STRICT, file, artifacts).prepare() }.reason

    /** Writes new bytes with the same size and restores the old modification time. */
    private fun replaceBytesKeepStamp(file: File, text: String) {
        val modified = file.lastModified()
        file.writeText(text)
        assertTrue(file.setLastModified(modified))
    }

    // Levels with a known file

    @Test fun strictAcceptsAKnownFile() {
        val prepared = preparer(Verification.STRICT).prepare()
        assertEquals(VerificationResult.Known(known[0]), prepared.verification)
        assertEquals(model.canonicalPath, prepared.options.modelPath)
        assertEquals(File(storage, "cache/$sha").absolutePath, prepared.options.cacheDir)
        assertTrue(File(prepared.options.cacheDir).isDirectory)
    }

    @Test fun warnReportsAKnownFileAsKnown() {
        assertEquals(VerificationResult.Known(known[0]), preparer(Verification.WARN).prepare().verification)
    }

    @Test fun offSkipsTheHash() {
        val prepared = preparer(Verification.OFF).prepare()
        assertEquals(VerificationResult.Skipped, prepared.verification)
        assertEquals(0, hashes)
        assertEquals(File(storage, "cache/off-${model.length()}-${model.lastModified()}").absolutePath, prepared.options.cacheDir)
    }

    // Unknown files

    @Test fun strictRejectsAnUnknownHashOfAKnownSize() {
        replaceBytesKeepStamp(model, "model bytes v2")
        File(storage, "verify").deleteRecursively()
        assertEquals(LoiException.Verification.Reason.UNKNOWN_HASH, verificationReason())
    }

    @Test fun strictRejectsAWrongSizeWithoutAHash() {
        model.writeText("a different size")
        assertEquals(LoiException.Verification.Reason.UNKNOWN_SIZE, verificationReason())
        assertEquals(0, hashes)
    }

    @Test fun strictWithNoKnownArtifactRejectsEveryFile() {
        assertEquals(LoiException.Verification.Reason.UNKNOWN_SIZE, verificationReason(artifacts = emptyList()))
    }

    @Test fun warnLoadsAnUnknownFileAndReportsItsHash() {
        val other = File(root, "other.litertlm").apply { writeText("other model") }
        val prepared = preparer(Verification.WARN, other).prepare()
        val otherSha = Sha256.of(other)
        assertEquals(VerificationResult.Unknown(otherSha), prepared.verification)
        assertEquals(File(storage, "cache/$otherSha").absolutePath, prepared.options.cacheDir)
    }

    @Test fun offLoadsAnUnknownFile() {
        val other = File(root, "other.litertlm").apply { writeText("other model") }
        assertEquals(VerificationResult.Skipped, preparer(Verification.OFF, other).prepare().verification)
    }

    // Missing and unreadable files, at every level

    @Test fun missingFileFailsAtEveryLevel() {
        for (level in Verification.entries) {
            assertEquals(LoiException.ModelFile.Reason.MISSING, fileReason(level, File(root, "absent.litertlm")))
        }
    }

    @Test fun folderFailsAtEveryLevel() {
        for (level in Verification.entries) assertEquals(LoiException.ModelFile.Reason.NOT_A_FILE, fileReason(level, root))
    }

    @Test fun unreadableFileFails() {
        assertTrue(model.setReadable(false))
        // The root user can read any file. The check then has nothing to prove.
        if (model.canRead()) return
        for (level in Verification.entries) assertEquals(LoiException.ModelFile.Reason.UNREADABLE, fileReason(level, model))
    }

    // Hash cache

    @Test fun theCacheSkipsTheHashWhenTheStampMatches() {
        preparer(Verification.STRICT).prepare()
        preparer(Verification.STRICT).prepare()
        preparer(Verification.WARN).prepare()
        assertEquals(1, hashes)
    }

    @Test fun aNewModificationTimeForcesANewHash() {
        preparer(Verification.STRICT).prepare()
        assertTrue(model.setLastModified(model.lastModified() - 10_000))
        preparer(Verification.STRICT).prepare()
        assertEquals(2, hashes)
    }

    @Test fun aNewSizeForcesANewHash() {
        preparer(Verification.WARN).prepare()
        val modified = model.lastModified()
        model.writeText("model bytes v1 and more")
        assertTrue(model.setLastModified(modified))
        assertIs<VerificationResult.Unknown>(preparer(Verification.WARN).prepare().verification)
        assertEquals(2, hashes)
    }

    @Test fun theAcceptedRiskSameStampDifferentBytesPasses() {
        preparer(Verification.STRICT).prepare()
        replaceBytesKeepStamp(model, "model bytes v2")
        // DESIGN.md section 8 accepts this risk. This test documents it.
        assertEquals(VerificationResult.Known(known[0]), preparer(Verification.STRICT).prepare().verification)
        assertEquals(1, hashes)
    }

    @Test fun aDamagedRecordForcesANewHashAndDoesNotFail() {
        preparer(Verification.STRICT).prepare()
        val records = File(storage, "verify").listFiles()!!.filter { it.name.endsWith(".txt") }
        assertEquals(1, records.size)
        records[0].writeText("garbage")
        assertEquals(VerificationResult.Known(known[0]), preparer(Verification.STRICT).prepare().verification)
        assertEquals(2, hashes)
        // The new hash replaces the damaged record.
        preparer(Verification.STRICT).prepare()
        assertEquals(2, hashes)
    }

    @Test fun anUnreadableRecordFolderDoesNotFail() {
        storage.mkdirs()
        File(storage, "verify").writeText("a file where the folder must be")
        assertEquals(VerificationResult.Known(known[0]), preparer(Verification.STRICT).prepare().verification)
        assertEquals(VerificationResult.Known(known[0]), preparer(Verification.STRICT).prepare().verification)
        assertEquals(2, hashes)
    }

    @Test fun recordNamesDoNotContainThePath() {
        preparer(Verification.STRICT).prepare()
        val names = File(storage, "verify").list()!!.toList()
        assertFalse(names.any { "model" in it })
    }

    @Test fun aFileThatChangesDuringTheHashFails() {
        val preparer = FileModelPreparer(model, Verification.WARN, known, storage, ::options) {
            val sha = Sha256.of(it)
            it.appendText(" more")
            sha
        }
        assertEquals(LoiException.ModelFile.Reason.CHANGED, assertFailsWith<LoiException.ModelFile> { preparer.prepare() }.reason)
        assertTrue(File(storage, "verify").listFiles().orEmpty().none { it.name.endsWith(".txt") })
    }

    @Test fun aHashFailureIsUnreadableWithTheCause() {
        val cause = java.io.IOException("read")
        val preparer = FileModelPreparer(model, Verification.WARN, known, storage, ::options) { throw cause }
        val error = assertFailsWith<LoiException.ModelFile> { preparer.prepare() }
        assertEquals(LoiException.ModelFile.Reason.UNREADABLE, error.reason)
        assertEquals(cause, error.cause)
    }

    @Test fun aBlockedCacheFolderFailsTheLoad() {
        File(storage, "cache").apply { parentFile.mkdirs() }.writeText("a file where the folder must be")
        assertFailsWith<LoiException.Load> { preparer(Verification.STRICT).prepare() }
    }
}
