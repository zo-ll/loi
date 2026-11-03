package dev.loi.testing

import dev.loi.core.api.EmbeddingTask
import dev.loi.core.api.VerificationResult
import dev.loi.core.engine.EmbeddingEngine
import dev.loi.core.engine.EmbeddingEngineFactory
import dev.loi.core.engine.EmbeddingSpec
import dev.loi.core.engine.EngineOptions
import dev.loi.core.runtime.ModelPreparer
import dev.loi.core.runtime.PreparedModel
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/** A small model spec for tests: 3 dimensions, L2-normalized, prefixes `q: ` and `d: `. */
public class FakeSpec(
    override val modelId: String = "fake-3",
    override val dimensions: Int = 3,
    override val normalized: Boolean = true,
    override val maxInputCharacters: Int = 100,
) : EmbeddingSpec {
    override fun prefix(task: EmbeddingTask): String = when (task) {
        EmbeddingTask.QUERY -> "q: "
        EmbeddingTask.DOCUMENT -> "d: "
    }
}

/** A preparer that returns fixed options. Set [failure] to make the next prepare calls fail. */
public class FakePreparer(
    public val verification: VerificationResult = VerificationResult.Skipped,
    public val options: EngineOptions = EngineOptions(
        modelPath = "/fake/model", cacheDir = "/fake/cache", maxInputTokens = 64,
        outputDimensions = 3, normalize = true, insertSpecialTokens = true,
    ),
) : ModelPreparer {
    @Volatile public var failure: Throwable? = null
    public val calls: AtomicInteger = AtomicInteger()

    override fun prepare(): PreparedModel {
        calls.incrementAndGet()
        failure?.let { throw it }
        return PreparedModel(options, verification)
    }
}

/**
 * A factory of fake engines. Each test controls the engine behavior through these fields.
 * Each engine records the thread of each call.
 */
public class FakeEngineFactory : EmbeddingEngineFactory {
    /** Returns the raw vector for a prefixed text. The default is a unit vector from the text hash. */
    @Volatile public var vectorOf: (String) -> FloatArray = ::hashVector
    @Volatile public var createFailure: Throwable? = null
    @Volatile public var initializeFailure: Throwable? = null
    @Volatile public var embedFailure: Throwable? = null
    @Volatile public var closeFailure: Throwable? = null

    /** If not null, each embed call counts down [embedStarted] and then waits for this latch. */
    @Volatile public var embedGate: CountDownLatch? = null
    @Volatile public var embedStarted: CountDownLatch = CountDownLatch(0)

    public val engines: MutableList<FakeEmbeddingEngine> = Collections.synchronizedList(mutableListOf())
    public val options: MutableList<EngineOptions> = Collections.synchronizedList(mutableListOf())

    override fun create(options: EngineOptions): EmbeddingEngine {
        this.options += options
        createFailure?.let { throw it }
        return FakeEmbeddingEngine(this).also { engines += it }
    }

    /** All thread names that touched an engine. */
    public fun threads(): Set<String> = engines.flatMap { it.threads.toList() }.toSet()

    public companion object {
        public fun hashVector(text: String): FloatArray {
            val h = text.hashCode()
            val raw = floatArrayOf(1f + (h and 0xff), 1f + ((h shr 8) and 0xff), 1f + ((h shr 16) and 0xff))
            val norm = sqrt(raw.sumOf { it.toDouble() * it }).toFloat()
            return FloatArray(raw.size) { raw[it] / norm }
        }
    }
}

public class FakeEmbeddingEngine internal constructor(private val factory: FakeEngineFactory) : EmbeddingEngine {
    public val threads: MutableList<String> = Collections.synchronizedList(mutableListOf())
    public val texts: MutableList<String> = Collections.synchronizedList(mutableListOf())
    public val initializeCalls: AtomicInteger = AtomicInteger()
    public val closeCalls: AtomicInteger = AtomicInteger()

    @Volatile public var closed: Boolean = false
        private set

    override fun initialize() {
        threads += workerName()
        initializeCalls.incrementAndGet()
        factory.initializeFailure?.let { throw it }
    }

    override fun embed(prefixedText: String): FloatArray {
        threads += workerName()
        check(!closed) { "The fake engine is closed." }
        texts += prefixedText
        factory.embedStarted.countDown()
        factory.embedGate?.let { gate -> check(gate.await(10, TimeUnit.SECONDS)) { "The test did not open the gate." } }
        factory.embedFailure?.let { throw it }
        return factory.vectorOf(prefixedText)
    }

    override fun close() {
        threads += workerName()
        closeCalls.incrementAndGet()
        factory.closeFailure?.let { throw it }
        closed = true
    }
}

/** The thread name without the ` @coroutine#N` suffix that the coroutines debug mode adds. */
private fun workerName(): String = Thread.currentThread().name.substringBefore(" @coroutine")
