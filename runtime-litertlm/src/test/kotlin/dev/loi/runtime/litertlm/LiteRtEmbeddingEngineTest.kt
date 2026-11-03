package dev.loi.runtime.litertlm

import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.google.ai.edge.litertlm.LogSeverity
import dev.loi.core.engine.EngineOptions
import dev.loi.core.engine.TooManyTokensException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class LiteRtEmbeddingEngineTest {
    private val options = EngineOptions(
        modelPath = "/m", cacheDir = "/c", maxInputTokens = 256, outputDimensions = 3, normalize = true, insertSpecialTokens = true,
    )

    private class FakeSdk : SdkEmbedding {
        val calls = mutableListOf<String>()
        var initialized = false
        val output = floatArrayOf(0.6f, 0.8f, 0f)
        var lastOptions: EmbeddingOptions? = null
        var failure: Throwable? = null
        override fun initialize() { calls += "initialize"; initialized = true }
        override fun isInitialized() = initialized
        override fun compute(text: String, options: EmbeddingOptions): FloatArray {
            calls += "compute:$text"; lastOptions = options; failure?.let { throw it }; return output
        }
        override fun close() { calls += "close"; initialized = false }
    }

    @Test fun initializeSetsTheLogLevelFirst() {
        val sdk = FakeSdk()
        LiteRtEmbeddingEngine(options, sdk) { sdk.calls += "severity:$it" }.initialize()
        assertEquals(listOf("severity:${LogSeverity.INFINITY}", "initialize"), sdk.calls)
    }

    @Test fun embedPassesTheExactTextAndTheMappedOptions() {
        val sdk = FakeSdk()
        val engine = LiteRtEmbeddingEngine(options, sdk) {}
        engine.initialize()
        engine.embed("task: search query | text: knee")
        assertEquals("compute:task: search query | text: knee", sdk.calls.last())
        assertEquals(LiteRtConfig.embedding(options), sdk.lastOptions)
    }

    @Test fun embedReturnsACopyOfTheSdkArray() {
        val sdk = FakeSdk()
        val engine = LiteRtEmbeddingEngine(options, sdk) {}
        val vector = engine.embed("x")
        vector[0] = 9f
        assertEquals(0.6f, sdk.output[0])
    }

    @Test fun invalidArgumentFromComputeIsTooManyTokens() {
        val sdk = FakeSdk()
        val cause = LiteRtLmJniException("ComputeEmbedding failed: INVALID_ARGUMENT: ")
        sdk.failure = cause
        val error = assertFailsWith<TooManyTokensException> { LiteRtEmbeddingEngine(options, sdk) {}.embed("x") }
        assertSame(cause, error.cause)
    }

    @Test fun otherSdkFailuresPassThrough() {
        val failures = listOf(
            LiteRtLmJniException("ComputeEmbedding failed: INTERNAL: "),
            LiteRtLmJniException("Engine failed: INVALID_ARGUMENT: "),
            IllegalStateException("ComputeEmbedding failed: INVALID_ARGUMENT: "),
        )
        for (failure in failures) {
            val sdk = FakeSdk().apply { this.failure = failure }
            assertSame(failure, assertFailsWith<Throwable> { LiteRtEmbeddingEngine(options, sdk) {}.embed("x") })
        }
    }

    @Test fun closeSkipsAnEngineThatNeverInitialized() {
        val sdk = FakeSdk()
        LiteRtEmbeddingEngine(options, sdk) {}.close()
        assertEquals(emptyList(), sdk.calls)
    }

    @Test fun closeClosesAnInitializedEngine() {
        val sdk = FakeSdk()
        val engine = LiteRtEmbeddingEngine(options, sdk) {}
        engine.initialize()
        engine.close()
        assertEquals(listOf("initialize", "close"), sdk.calls)
    }
}
