package dev.loi.models.embeddinggemma2

import dev.loi.core.api.EmbeddingTask
import dev.loi.core.api.Verification
import dev.loi.core.engine.Backend
import dev.loi.core.engine.EngineOptions
import dev.loi.core.engine.Precision
import dev.loi.core.runtime.SerialEmbedder
import dev.loi.testing.FakeEngineFactory
import dev.loi.testing.FakePreparer
import kotlinx.coroutines.runBlocking
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class EmbeddingGemma2Test {
    @Test fun prefixesMatchTheModelPolicy() {
        val spec = EmbeddingGemma2.spec()
        assertEquals("task: search query | text: ", spec.prefix(EmbeddingTask.QUERY))
        assertEquals("task: search result | text: ", spec.prefix(EmbeddingTask.DOCUMENT))
    }

    @Test fun theEmbedderAddsThePrefixOnceAndOnlyOnce() = runBlocking {
        val factory = FakeEngineFactory().apply { vectorOf = { unit(256) } }
        val embedder = SerialEmbedder(EmbeddingGemma2.spec(), FakePreparer(), factory)
        try {
            embedder.embed("knee pain", EmbeddingTask.QUERY)
            // A caller text that already looks prefixed is still plain caller text.
            embedder.embed("task: search result | text: squat", EmbeddingTask.DOCUMENT)
            assertEquals(
                listOf("task: search query | text: knee pain", "task: search result | text: task: search result | text: squat"),
                factory.engines.single().texts,
            )
        } finally {
            embedder.close()
        }
    }

    @Test fun defaults() {
        val options = EmbeddingGemma2.Options()
        assertEquals(256, options.dimensions)
        assertEquals(2048, options.maxInputTokens)
        assertEquals(Verification.STRICT, options.verification)
        assertEquals(false, options.nativeLogs)
    }

    @Test fun everyAllowedSizeIsAccepted() {
        for (d in listOf(128, 256, 512, 768)) assertEquals(d, EmbeddingGemma2.spec(EmbeddingGemma2.Options(dimensions = d)).dimensions)
        for (t in listOf(128, 256, 512, 1024, 2048, 8192)) EmbeddingGemma2.Options(maxInputTokens = t)
    }

    @Test fun sizesOutsideTheListsAreRejected() {
        for (d in listOf(0, 1, 64, 255, 257, 1024)) assertFailsWith<IllegalArgumentException> { EmbeddingGemma2.Options(dimensions = d) }
        for (t in listOf(0, 100, 2047, 4096, 16384)) assertFailsWith<IllegalArgumentException> { EmbeddingGemma2.Options(maxInputTokens = t) }
        assertFailsWith<IllegalArgumentException> { EmbeddingGemma2.modelId(300) }
    }

    @Test fun engineOptionsValues() {
        val options = EmbeddingGemma2.Options(dimensions = 512, maxInputTokens = 1024, nativeLogs = true)
        assertEquals(
            EngineOptions(
                modelPath = "/m", cacheDir = "/c", maxInputTokens = 1024, outputDimensions = 512, normalize = true,
                insertSpecialTokens = true, backend = Backend.CPU, activationPrecision = Precision.FLOAT32, nativeLogs = true,
            ),
            EmbeddingGemma2.engineOptions(options, "/m", "/c"),
        )
        assertEquals(false, EmbeddingGemma2.engineOptions(EmbeddingGemma2.Options(), "/m", "/c").nativeLogs)
    }

    @Test fun theModelIdIncludesTheSize() {
        assertEquals("embeddinggemma-2-text-270m-mrl256", EmbeddingGemma2.spec().modelId)
        assertNotEquals(EmbeddingGemma2.modelId(128), EmbeddingGemma2.modelId(256))
    }

    @Test fun specRules() {
        val spec = EmbeddingGemma2.spec()
        assertEquals(true, spec.normalized)
        assertEquals(16384, spec.maxInputCharacters)
    }

    @Test fun theKnownArtifactMatchesTheModelPolicy() {
        val a = EmbeddingGemma2.ARTIFACT
        assertEquals("embeddinggemma-2-text-270m.litertlm", a.fileName)
        assertEquals(164_626_432L, a.bytes)
        assertEquals("2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb", a.sha256)
        assertEquals(listOf(a), EmbeddingGemma2.KNOWN_ARTIFACTS)
    }

    private fun unit(size: Int): FloatArray = FloatArray(size) { (1.0 / sqrt(size.toDouble())).toFloat() }
}
