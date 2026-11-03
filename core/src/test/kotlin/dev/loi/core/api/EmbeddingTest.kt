package dev.loi.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class EmbeddingTest {
    private fun e(vararg v: Float, model: String = "m-3", task: EmbeddingTask = EmbeddingTask.QUERY) =
        Embedding(v, task, model)

    @Test fun equalVectorsHaveCosineOne() {
        assertEquals(1f, e(0.6f, 0.8f, 0f).cosine(e(0.6f, 0.8f, 0f)), 1e-6f)
    }

    @Test fun oppositeVectorsHaveCosineMinusOne() {
        assertEquals(-1f, e(0.6f, 0.8f, 0f).cosine(e(-0.6f, -0.8f, 0f)), 1e-6f)
    }

    @Test fun orthogonalVectorsHaveCosineZero() {
        assertEquals(0f, e(1f, 0f, 0f).cosine(e(0f, 1f, 0f)), 1e-6f)
    }

    @Test fun cosineDoesNotNeedUnitVectors() {
        assertEquals(1f, e(3f, 4f, 0f).cosine(e(0.6f, 0.8f, 0f)), 1e-6f)
    }

    @Test fun queryAndDocumentFromOneModelAreCompatible() {
        val doc = e(0.6f, 0.8f, 0f, task = EmbeddingTask.DOCUMENT)
        assertEquals(1f, e(0.6f, 0.8f, 0f).cosine(doc), 1e-6f)
    }

    @Test fun differentModelsAreIncompatible() {
        assertFailsWith<IllegalArgumentException> { e(1f, 0f, 0f).cosine(e(1f, 0f, 0f, model = "other")) }
    }

    @Test fun differentSizesAreIncompatible() {
        assertFailsWith<IllegalArgumentException> { e(1f, 0f, 0f).cosine(e(1f, 0f)) }
    }

    @Test fun zeroVectorIsRejectedByCosine() {
        assertFailsWith<IllegalArgumentException> { e(0f, 0f, 0f).cosine(e(1f, 0f, 0f)) }
    }

    @Test fun theVectorIsCopiedOnInputAndOutput() {
        val source = floatArrayOf(1f, 0f, 0f)
        val embedding = Embedding(source, EmbeddingTask.QUERY, "m")
        source[0] = 9f
        embedding.toFloatArray()[1] = 9f
        assertEquals(listOf(1f, 0f, 0f), embedding.toFloatArray().toList())
        assertEquals(1f, embedding[0])
        assertEquals(3, embedding.dimensions)
    }

    @Test fun equalityUsesContent() {
        assertEquals(e(1f, 0f, 0f), e(1f, 0f, 0f))
        assertEquals(e(1f, 0f, 0f).hashCode(), e(1f, 0f, 0f).hashCode())
        assertNotEquals(e(1f, 0f, 0f), e(1f, 0f, 0f, task = EmbeddingTask.DOCUMENT))
    }

    @Test fun textFormHasNoVectorValues() {
        val text = e(0.123f, 0.456f, 0f).toString()
        assertFalse("0.123" in text || "0.456" in text)
    }

    @Test fun emptyVectorOrBlankModelIsRejected() {
        assertFailsWith<IllegalArgumentException> { Embedding(floatArrayOf(), EmbeddingTask.QUERY, "m") }
        assertFailsWith<IllegalArgumentException> { Embedding(floatArrayOf(1f), EmbeddingTask.QUERY, " ") }
    }
}
