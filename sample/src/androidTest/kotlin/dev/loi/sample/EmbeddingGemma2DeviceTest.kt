package dev.loi.sample

import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.loi.android.Loi
import dev.loi.core.api.EmbeddingTask
import dev.loi.core.api.VerificationResult
import dev.loi.core.errors.LoiException
import dev.loi.models.embeddinggemma2.EmbeddingGemma2
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Loads the real EmbeddingGemma 2 file on the device. See README.md for the file location and the command.
 * The test reports its measurements as instrumentation status values with the `loi.` prefix.
 */
@RunWith(AndroidJUnit4::class)
class EmbeddingGemma2DeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val model = modelFile(context)

    @Before fun modelFileIsPresent() {
        if (!model.isFile) fail("Push the model file to ${model.absolutePath} before the test. See README.md.")
    }

    private fun report(key: String, value: Any) {
        Log.i("LoiDeviceTest", "$key=$value")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("loi.$key", value.toString()) })
    }

    private fun norm(vector: FloatArray) = sqrt(vector.sumOf { it.toDouble() * it })

    @Test fun strictLoadEmbedsAndRanksTheRelatedDocumentFirst() = runBlocking<Unit> {
        // A cold load needs an empty engine cache.
        Loi.storageRoot(context).deleteRecursively()
        val embedder = Loi.embeddingGemma2(context, model)
        try {
            val result = runSample(embedder)
            assertEquals(VerificationResult.Known(EmbeddingGemma2.ARTIFACT), embedder.model.verification)
            assertEquals(256, result.dimensions)
            assertTrue(abs(result.queryNorm - 1.0) <= 0.0001, "The norm is ${result.queryNorm}.")
            assertTrue(result.related > result.unrelated, "related ${result.related} <= unrelated ${result.unrelated}")
            report("coldLoadMs", result.loadMs)
            report("queryMs", result.queryMs)
            report("documentMs", result.documentMs)
            report("related", result.related)
            report("unrelated", result.unrelated)
        } finally {
            embedder.close()
        }
    }

    @Test fun warmLoadUsesTheHashCacheAndTheEngineCache() = runBlocking<Unit> {
        Loi.embeddingGemma2(context, model).use { it.load() }
        val embedder = Loi.embeddingGemma2(context, model)
        try {
            val start = System.nanoTime()
            embedder.load()
            report("warmLoadMs", (System.nanoTime() - start) / 1_000_000)
        } finally {
            embedder.close()
        }
    }

    @Test fun releaseReloadsAndCloseRejects() = runBlocking<Unit> {
        val embedder = Loi.embeddingGemma2(context, model)
        val first = embedder.embed(SampleTexts.QUERY, EmbeddingTask.QUERY)
        embedder.release()
        val second = embedder.embed(SampleTexts.QUERY, EmbeddingTask.QUERY)
        assertTrue(first.cosine(second) > 0.9999f, "A reload changed the vector: ${first.cosine(second)}")
        embedder.close()
        assertFailsWith<LoiException.Closed> { embedder.embed(SampleTexts.QUERY, EmbeddingTask.QUERY) }
    }

    @Test fun everyOutputSizeIsNormalized() = runBlocking<Unit> {
        for (dimensions in EmbeddingGemma2.DIMENSIONS) {
            Loi.embeddingGemma2(context, model, EmbeddingGemma2.Options(dimensions = dimensions)).use { embedder ->
                val vector = embedder.embed(SampleTexts.QUERY, EmbeddingTask.QUERY).toFloatArray()
                assertEquals(dimensions, vector.size)
                assertTrue(abs(norm(vector) - 1.0) <= 0.0001)
            }
        }
    }

    /** Issue #17: the SDK fails for a text with more tokens than the limit, and loi reports an input error. */
    @Test fun inputLongerThanTheTokenLimit() = runBlocking<Unit> {
        val text = (1..1500).joinToString(" ") { "word$it" }
        Loi.embeddingGemma2(context, model, EmbeddingGemma2.Options(maxInputTokens = 128)).use { embedder ->
            val error = assertFailsWith<LoiException.Input> { embedder.embed(text, EmbeddingTask.DOCUMENT) }
            assertEquals(LoiException.Input.Reason.TOO_MANY_TOKENS, error.reason)
            report("overLimitOutcome", "Input(${error.reason})")
            // The engine stays usable after the input error.
            assertEquals(256, embedder.embed(SampleTexts.QUERY, EmbeddingTask.QUERY).toFloatArray().size)
        }
    }

    private suspend fun <T> dev.loi.core.api.Embedder.use(block: suspend (dev.loi.core.api.Embedder) -> T): T =
        try { block(this) } finally { close() }
}
