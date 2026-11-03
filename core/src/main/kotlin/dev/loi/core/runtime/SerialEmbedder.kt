package dev.loi.core.runtime

import dev.loi.core.api.Embedder
import dev.loi.core.api.Embedding
import dev.loi.core.api.EmbeddingTask
import dev.loi.core.api.ModelInfo
import dev.loi.core.api.VerificationResult
import dev.loi.core.engine.EmbeddingEngine
import dev.loi.core.engine.EmbeddingEngineFactory
import dev.loi.core.engine.EmbeddingSpec
import dev.loi.core.errors.LoiException
import dev.loi.core.validate.InputRules
import dev.loi.core.validate.VectorRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * An [Embedder] that runs all engine work on one dedicated worker thread.
 *
 * Rules, from forma `InferenceCoordinator` and `SerialModelRuntime`:
 * - The embedder holds at most one engine. It loads on the first call or on [load].
 * - A cancel before the engine call stops the call. The engine is not called.
 * - A cancel during the engine call cannot stop the native work. The caller resumes at once with
 *   `CancellationException`. The worker finishes the engine call and discards the result.
 * - A failed engine close poisons the embedder. It keeps the handle and never loads a second engine.
 */
public class SerialEmbedder(
    private val spec: EmbeddingSpec,
    private val preparer: ModelPreparer,
    private val factory: EmbeddingEngineFactory,
) : Embedder {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "loi-embedder-${THREADS.incrementAndGet()}").apply { isDaemon = true }
    }
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())

    // The worker thread alone reads and writes these two fields.
    private var engine: EmbeddingEngine? = null
    private var poisoned: Throwable? = null

    @Volatile private var closed = false
    @Volatile private var verification: VerificationResult = VerificationResult.Pending

    override val model: ModelInfo get() = ModelInfo(spec.modelId, spec.dimensions, verification)

    override suspend fun load() {
        onWorker { ensureEngine() }
    }

    override suspend fun embed(text: String, task: EmbeddingTask): Embedding {
        val prefixed = prefixed(text, task)
        return onWorker { compute(prefixed, task) }
    }

    override suspend fun embedAll(texts: List<String>, task: EmbeddingTask): List<Embedding> {
        // Check all texts before any engine work, so one bad text does not waste a long batch.
        val prefixed = texts.map { prefixed(it, task) }
        if (prefixed.isEmpty()) {
            ensureOpen()
            return emptyList()
        }
        return onWorker { prefixed.map { ensureActive(); compute(it, task) } }
    }

    override suspend fun release() {
        onWorker { unload() }
    }

    override fun close() {
        if (closed) return
        closed = true
        // Queued calls run first. Then the worker unloads the engine and stops.
        val unload = executor.submit<Unit> { unload() }
        executor.shutdown()
        try {
            unload.get()
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        } finally {
            scope.cancel()
        }
    }

    private fun prefixed(text: String, task: EmbeddingTask): String {
        ensureOpen()
        InputRules.check(text, spec.maxInputCharacters)
        return spec.prefix(task) + text
    }

    private fun ensureOpen() {
        if (closed) throw LoiException.Closed()
    }

    /** Runs [block] on the worker. The caller can stop its wait before the worker finishes. */
    private suspend fun <T> onWorker(block: CoroutineScope.() -> T): T {
        ensureOpen()
        val work = scope.async { ensureActive(); block() }
        try {
            return work.await()
        } catch (error: CancellationException) {
            work.cancel()
            // The caller is still active, so close() stopped the worker. The caller did not cancel.
            if (closed && currentCoroutineContext().isActive) throw LoiException.Closed()
            throw error
        }
    }

    private fun CoroutineScope.compute(prefixed: String, task: EmbeddingTask): Embedding {
        val current = ensureEngine()
        ensureActive()
        val vector = try {
            current.embed(prefixed)
        } catch (error: Throwable) {
            if (error is LoiException) throw error
            throw LoiException.Output(LoiException.Output.Reason.ENGINE_FAILURE, error)
        }
        // A cancel during the engine call discards the result here.
        ensureActive()
        VectorRules.check(vector, spec.dimensions, spec.normalized)
        return Embedding(vector, task, spec.modelId)
    }

    private fun CoroutineScope.ensureEngine(): EmbeddingEngine {
        poisoned?.let { throw LoiException.Resource(it) }
        engine?.let { return it }
        val prepared = try {
            preparer.prepare()
        } catch (error: Throwable) {
            if (error is LoiException) throw error
            throw LoiException.Load(error)
        }
        verification = prepared.verification
        ensureActive()
        val created = try {
            factory.create(prepared.options)
        } catch (error: Throwable) {
            throw LoiException.Load(error)
        }
        try {
            created.initialize()
        } catch (error: Throwable) {
            dispose(created)
            throw LoiException.Load(error)
        }
        engine = created
        return created
    }

    private fun unload() {
        poisoned?.let { throw LoiException.Resource(it) }
        val current = engine ?: return
        dispose(current)
        engine = null
    }

    /** Closes [target]. On failure, the embedder keeps the handle and is poisoned. */
    private fun dispose(target: EmbeddingEngine) {
        try {
            target.close()
        } catch (error: Throwable) {
            engine = target
            poisoned = error
            throw LoiException.Resource(error)
        }
    }

    private companion object {
        val THREADS = AtomicInteger()
    }
}
