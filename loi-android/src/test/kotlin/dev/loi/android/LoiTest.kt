package dev.loi.android

import dev.loi.core.api.EmbeddingTask
import dev.loi.core.api.Verification
import dev.loi.core.api.VerificationResult
import dev.loi.core.errors.LoiException
import dev.loi.models.embeddinggemma2.EmbeddingGemma2
import dev.loi.testing.FakeEngineFactory
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.math.sqrt
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoiTest {
    private val root: File = Files.createTempDirectory("loi-android").toFile()
    private val storage = File(root, "noBackup/loi")
    private val model = File(root, "model.litertlm").apply { writeText("not the real model") }
    private val factory = FakeEngineFactory().apply { vectorOf = { FloatArray(256) { (1.0 / sqrt(256.0)).toFloat() } } }

    @AfterTest fun cleanup() { root.deleteRecursively() }

    private fun embedder(level: Verification, dimensions: Int = 256) =
        Loi.embeddingGemma2(storage, model, EmbeddingGemma2.Options(dimensions = dimensions, verification = level), factory)

    private fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    @Test fun constructionDoesNotTouchTheFileOrTheStorage() {
        embedder(Verification.WARN).close()
        assertTrue(!storage.exists())
        assertTrue(factory.engines.isEmpty())
    }

    @Test fun warnUsesTheHashCacheFolder() = runBlocking<Unit> {
        val embedder = embedder(Verification.WARN)
        try {
            embedder.load()
            val sha = sha256(model)
            assertEquals(VerificationResult.Unknown(sha), embedder.model.verification)
            assertEquals(File(storage, "cache/$sha").absolutePath, factory.options.single().cacheDir)
            assertTrue(File(storage, "verify").listFiles()!!.any { it.name.endsWith(".txt") })
        } finally {
            embedder.close()
        }
    }

    @Test fun offUsesTheSizeAndTimeFolder() = runBlocking<Unit> {
        val embedder = embedder(Verification.OFF)
        try {
            embedder.load()
            assertEquals(VerificationResult.Skipped, embedder.model.verification)
            assertEquals(File(storage, "cache/off-${model.length()}-${model.lastModified()}").absolutePath, factory.options.single().cacheDir)
        } finally {
            embedder.close()
        }
    }

    @Test fun strictIsTheDefaultAndRejectsAnUnknownFile() = runBlocking<Unit> {
        assertEquals(Verification.STRICT, EmbeddingGemma2.Options().verification)
        val embedder = Loi.embeddingGemma2(storage, model, EmbeddingGemma2.Options(), factory)
        try {
            val error = assertFailsWith<LoiException.Verification> { embedder.load() }
            assertEquals(LoiException.Verification.Reason.UNKNOWN_SIZE, error.reason)
            assertTrue(factory.engines.isEmpty())
        } finally {
            embedder.close()
        }
    }

    @Test fun theEngineGetsTheEmbeddingGemma2Options() = runBlocking<Unit> {
        val embedder = embedder(Verification.OFF, dimensions = 256)
        try {
            embedder.embed("knee pain", EmbeddingTask.QUERY)
            val options = factory.options.single()
            assertEquals(model.canonicalPath, options.modelPath)
            assertEquals(256, options.outputDimensions)
            assertEquals(2048, options.maxInputTokens)
            assertEquals(listOf("task: search query | text: knee pain"), factory.engines.single().texts)
            assertEquals("embeddinggemma-2-text-270m-mrl256", embedder.model.id)
        } finally {
            embedder.close()
        }
    }

    @Test fun theOutputSizeIsChecked() = runBlocking<Unit> {
        val embedder = embedder(Verification.OFF, dimensions = 128)
        try {
            assertIs<LoiException.Output>(runCatching { embedder.embed("a", EmbeddingTask.QUERY) }.exceptionOrNull())
        } finally {
            embedder.close()
        }
    }
}
