package dev.loi.core.validate

import dev.loi.core.errors.LoiException
import dev.loi.core.errors.LoiException.Output.Reason
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class VectorRulesTest {
    private val unit = floatArrayOf(0.6f, 0.8f, 0f)

    private fun reason(vector: FloatArray, size: Int = 3, normalized: Boolean = true): Reason =
        assertFailsWith<LoiException.Output> { VectorRules.check(vector, size, normalized) }.reason

    @Test fun unitVectorPasses() {
        assertSame(unit, VectorRules.check(unit, 3, normalized = true))
    }

    @Test fun normWithinTolerancePasses() {
        val s = (1.0 / sqrt(3.0)).toFloat()
        VectorRules.check(floatArrayOf(s, s, s), 3, normalized = true)
    }

    @Test fun wrongSizeFails() {
        assertEquals(Reason.WRONG_SIZE, reason(floatArrayOf(1f, 0f)))
        assertEquals(Reason.WRONG_SIZE, reason(floatArrayOf()))
    }

    @Test fun nonFiniteValuesFail() {
        assertEquals(Reason.NOT_FINITE, reason(floatArrayOf(Float.NaN, 0f, 0f)))
        assertEquals(Reason.NOT_FINITE, reason(floatArrayOf(Float.POSITIVE_INFINITY, 0f, 0f)))
        assertEquals(Reason.NOT_FINITE, reason(floatArrayOf(0f, Float.NEGATIVE_INFINITY, 0f)))
    }

    @Test fun zeroVectorFails() {
        assertEquals(Reason.ZERO_NORM, reason(floatArrayOf(0f, 0f, 0f)))
        assertEquals(Reason.ZERO_NORM, reason(floatArrayOf(0f, 0f, 0f), normalized = false))
    }

    @Test fun unnormalizedVectorFailsOnlyWhenNormalizationIsExpected() {
        assertEquals(Reason.WRONG_NORM, reason(floatArrayOf(1f, 1f, 0f)))
        assertEquals(Reason.WRONG_NORM, reason(floatArrayOf(1.001f, 0f, 0f)))
        VectorRules.check(floatArrayOf(1f, 1f, 0f), 3, normalized = false)
    }

    private fun assertEquals(expected: Reason, actual: Reason) = kotlin.test.assertEquals(expected, actual)
}
