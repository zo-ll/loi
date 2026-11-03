package dev.loi.core.runtime

import dev.loi.core.api.EmbeddingTask.DOCUMENT
import dev.loi.core.api.EmbeddingTask.QUERY
import dev.loi.core.api.VerificationResult
import dev.loi.core.errors.LoiException
import dev.loi.testing.FakeEngineFactory
import dev.loi.testing.FakePreparer
import dev.loi.testing.FakeSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SerialEmbedderTest {
    private val spec = FakeSpec()
    private val preparer = FakePreparer()
    private val factory = FakeEngineFactory()
    private val embedder = SerialEmbedder(spec, preparer, factory)

    @AfterTest fun cleanup() {
        factory.closeFailure = null
        factory.embedGate?.let { while (it.count > 0) it.countDown() }
        runCatching { embedder.close() }
    }

    private fun test(block: suspend CoroutineScope.() -> Unit) = runBlocking { withTimeout(10_000) { block() } }

    /** Makes the next engine call wait for the returned gate. */
    private fun gate(): CountDownLatch {
        val gate = CountDownLatch(1)
        factory.embedStarted = CountDownLatch(1)
        factory.embedGate = gate
        return gate
    }

    private fun awaitEngineCall() = assertTrue(factory.embedStarted.await(5, TimeUnit.SECONDS), "The engine call did not start.")

    // Load

    @Test fun constructionDoesNotLoad() {
        assertTrue(factory.engines.isEmpty())
        assertEquals(0, preparer.calls.get())
        assertEquals(VerificationResult.Pending, embedder.model.verification)
    }

    @Test fun firstEmbedLoadsOnceAndAddsThePrefixOnce() = test {
        val first = embedder.embed("knee pain", QUERY)
        embedder.embed("squat note", DOCUMENT)
        assertEquals(1, factory.engines.size)
        assertEquals(1, factory.engines[0].initializeCalls.get())
        assertEquals(listOf("q: knee pain", "d: squat note"), factory.engines[0].texts)
        assertEquals("fake-3", first.modelId)
        assertEquals(QUERY, first.task)
        assertEquals(3, first.dimensions)
    }

    @Test fun explicitLoadReportsTheVerification() = test {
        val known = VerificationResult.Unknown("a".repeat(64))
        val embedder = SerialEmbedder(spec, FakePreparer(verification = known), factory)
        try {
            embedder.load()
            assertEquals(1, factory.engines.size)
            assertEquals(known, embedder.model.verification)
        } finally {
            embedder.close()
        }
    }

    @Test fun theEngineReceivesThePreparedOptions() = test {
        embedder.load()
        assertEquals(preparer.options, factory.options.single())
    }

    @Test fun onlyTheWorkerThreadTouchesTheEngine() = test {
        embedder.load()
        embedder.embed("a", QUERY)
        embedder.embedAll(listOf("b", "c"), DOCUMENT)
        embedder.release()
        embedder.embed("d", QUERY)
        val threads = factory.threads()
        assertEquals(1, threads.size, "Threads: $threads")
        assertTrue(threads.single().startsWith("loi-embedder-"))
        assertFalse(Thread.currentThread().name in threads)
    }

    // Input and output

    @Test fun invalidInputFailsWithoutALoad() = test {
        assertEquals(LoiException.Input.Reason.BLANK, assertFailsWith<LoiException.Input> { embedder.embed(" ", QUERY) }.reason)
        assertEquals(LoiException.Input.Reason.TOO_LONG,
            assertFailsWith<LoiException.Input> { embedder.embed("x".repeat(101), QUERY) }.reason)
        assertTrue(factory.engines.isEmpty())
    }

    @Test fun theCharacterLimitDoesNotCountThePrefix() = test {
        embedder.embed("x".repeat(100), QUERY)
    }

    @Test fun badOutputFails() = test {
        factory.vectorOf = { floatArrayOf(1f, 0f) }
        assertEquals(LoiException.Output.Reason.WRONG_SIZE, assertFailsWith<LoiException.Output> { embedder.embed("a", QUERY) }.reason)
        factory.vectorOf = { floatArrayOf(1f, 1f, 0f) }
        assertEquals(LoiException.Output.Reason.WRONG_NORM, assertFailsWith<LoiException.Output> { embedder.embed("a", QUERY) }.reason)
        factory.vectorOf = { floatArrayOf(Float.NaN, 0f, 0f) }
        assertEquals(LoiException.Output.Reason.NOT_FINITE, assertFailsWith<LoiException.Output> { embedder.embed("a", QUERY) }.reason)
    }

    @Test fun engineFailureDuringEmbedKeepsTheCauseAndTheEngine() = test {
        val cause = IllegalStateException("sdk")
        factory.embedFailure = cause
        val error = assertFailsWith<LoiException.Output> { embedder.embed("a", QUERY) }
        assertEquals(LoiException.Output.Reason.ENGINE_FAILURE, error.reason)
        assertSame(cause, error.cause)
        factory.embedFailure = null
        embedder.embed("a", QUERY)
        assertEquals(1, factory.engines.size)
    }

    // Load failures

    @Test fun preparerLoiExceptionPassesThrough() = test {
        preparer.failure = LoiException.Verification(LoiException.Verification.Reason.UNKNOWN_HASH)
        assertFailsWith<LoiException.Verification> { embedder.embed("a", QUERY) }
        assertTrue(factory.engines.isEmpty())
    }

    @Test fun otherPreparerFailureBecomesLoad() = test {
        val cause = java.io.IOException("disk")
        preparer.failure = cause
        assertSame(cause, assertFailsWith<LoiException.Load> { embedder.embed("a", QUERY) }.cause)
    }

    @Test fun createFailureBecomesLoad() = test {
        factory.createFailure = IllegalStateException("create")
        assertFailsWith<LoiException.Load> { embedder.load() }
    }

    @Test fun initializeFailureClosesTheEngineAndALaterCallRetries() = test {
        factory.initializeFailure = IllegalStateException("init")
        assertFailsWith<LoiException.Load> { embedder.load() }
        assertEquals(1, factory.engines[0].closeCalls.get())
        factory.initializeFailure = null
        embedder.embed("a", QUERY)
        assertEquals(2, factory.engines.size)
        assertEquals(1, factory.engines[1].texts.size)
    }

    @Test fun initializeAndCloseFailurePoisons() = test {
        factory.initializeFailure = IllegalStateException("init")
        factory.closeFailure = IllegalStateException("close")
        assertFailsWith<LoiException.Resource> { embedder.load() }
        factory.initializeFailure = null
        factory.closeFailure = null
        assertFailsWith<LoiException.Resource> { embedder.load() }
        assertEquals(1, factory.engines.size)
    }

    // Release and close

    @Test fun releaseClosesTheEngineAndTheNextCallLoadsANewOne() = test {
        embedder.embed("a", QUERY)
        embedder.release()
        assertTrue(factory.engines[0].closed)
        embedder.embed("b", QUERY)
        assertEquals(2, factory.engines.size)
        assertEquals(listOf("q: b"), factory.engines[1].texts)
        assertEquals(2, preparer.calls.get())
    }

    @Test fun releaseWithoutALoadDoesNothing() = test {
        embedder.release()
        assertTrue(factory.engines.isEmpty())
    }

    @Test fun closeUnloadsAndRejectsLaterCalls() = test {
        embedder.embed("a", QUERY)
        embedder.close()
        assertTrue(factory.engines[0].closed)
        assertFailsWith<LoiException.Closed> { embedder.embed("a", QUERY) }
        assertFailsWith<LoiException.Closed> { embedder.embedAll(emptyList(), QUERY) }
        assertFailsWith<LoiException.Closed> { embedder.load() }
        assertFailsWith<LoiException.Closed> { embedder.release() }
        embedder.close()
        assertEquals(1, factory.engines[0].closeCalls.get())
    }

    @Test fun closeWaitsForTheActiveCall() = test {
        val gate = gate()
        val call = async(Dispatchers.Default) { embedder.embed("a", QUERY) }
        awaitEngineCall()
        val closer = Thread { embedder.close() }.apply { start() }
        closer.join(300)
        assertTrue(closer.isAlive, "close() returned before the active call finished.")
        assertFalse(factory.engines[0].closed)
        gate.countDown()
        closer.join(5_000)
        assertFalse(closer.isAlive)
        assertEquals(3, call.await().dimensions)
        assertTrue(factory.engines[0].closed)
    }

    @Test fun closeStopsTheWorkerThread() = test {
        embedder.load()
        val name = factory.threads().single()
        embedder.close()
        val deadline = System.nanoTime() + 5_000_000_000
        while (Thread.getAllStackTraces().keys.any { it.name == name && it.isAlive } && System.nanoTime() < deadline) Thread.sleep(10)
        assertFalse(Thread.getAllStackTraces().keys.any { it.name == name && it.isAlive })
    }

    @Test fun failedReleasePoisonsTheEmbedder() = test {
        embedder.embed("a", QUERY)
        val cause = IllegalStateException("close")
        factory.closeFailure = cause
        assertSame(cause, assertFailsWith<LoiException.Resource> { embedder.release() }.cause)
        factory.closeFailure = null
        assertFailsWith<LoiException.Resource> { embedder.embed("b", QUERY) }
        assertFailsWith<LoiException.Resource> { embedder.release() }
        assertEquals(1, factory.engines.size, "A poisoned embedder must not load a second engine.")
        assertFailsWith<LoiException.Resource> { embedder.close() }
    }

    // Cancellation

    @Test fun cancelBeforeTheEngineCallSkipsTheEngine() = test {
        embedder.load()
        val gate = gate()
        val first = async(Dispatchers.Default) { embedder.embed("first", QUERY) }
        awaitEngineCall()
        val second = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { embedder.embed("second", QUERY) }
        second.cancel()
        assertFailsWith<CancellationException> { second.await() }
        gate.countDown()
        first.await()
        embedder.embed("third", QUERY)
        assertEquals(listOf("q: first", "q: third"), factory.engines[0].texts)
    }

    @Test fun cancelDuringTheEngineCallResumesTheCallerAtOnceAndDiscardsTheResult() = test {
        embedder.load()
        val gate = gate()
        val call = async(Dispatchers.Default) { embedder.embed("slow", QUERY) }
        awaitEngineCall()
        call.cancel()
        // The engine is still blocked here, but the caller already has its result.
        assertFailsWith<CancellationException> { call.await() }
        assertEquals(1L, gate.count)
        gate.countDown()
        // The worker finishes the engine call, then serves the next call with the same engine.
        val next = embedder.embed("next", QUERY)
        assertEquals(3, next.dimensions)
        assertEquals(listOf("q: slow", "q: next"), factory.engines[0].texts)
        assertEquals(1, factory.engines.size)
    }

    @Test fun releaseAfterACancelledCallWaitsForTheEngineCall() = test {
        embedder.load()
        val gate = gate()
        val call = async(Dispatchers.Default) { embedder.embed("slow", QUERY) }
        awaitEngineCall()
        call.cancel()
        val release = async(Dispatchers.Default) { embedder.release() }
        Thread.sleep(200)
        assertFalse(release.isCompleted)
        assertFalse(factory.engines[0].closed)
        gate.countDown()
        release.await()
        assertTrue(factory.engines[0].closed)
    }

    // Batches

    @Test fun embedAllKeepsTheOrder() = test {
        val result = embedder.embedAll(listOf("a", "b", "c"), DOCUMENT)
        assertEquals(listOf("d: a", "d: b", "d: c"), factory.engines[0].texts)
        assertEquals(listOf("d: a", "d: b", "d: c").map { FakeEngineFactory.hashVector(it).toList() },
            result.map { it.toFloatArray().toList() })
    }

    @Test fun embedAllChecksAllTextsBeforeEngineWork() = test {
        assertIs<LoiException.Input>(runCatching { embedder.embedAll(listOf("a", "", "c"), QUERY) }.exceptionOrNull())
        assertTrue(factory.engines.isEmpty())
    }

    @Test fun embedAllOfNothingDoesNotLoad() = test {
        assertEquals(emptyList(), embedder.embedAll(emptyList(), QUERY))
        assertTrue(factory.engines.isEmpty())
    }

    @Test fun cancelDuringABatchStopsTheRemainingTexts() = test {
        embedder.load()
        val gate = gate()
        val call = async(Dispatchers.Default) { embedder.embedAll(listOf("a", "b", "c"), QUERY) }
        awaitEngineCall()
        call.cancel()
        assertFailsWith<CancellationException> { call.await() }
        factory.embedGate = null
        gate.countDown()
        embedder.release()
        assertEquals(listOf("q: a"), factory.engines[0].texts)
    }
}
