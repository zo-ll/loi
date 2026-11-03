package dev.loi.core.validate

import dev.loi.core.errors.LoiException
import dev.loi.core.errors.LoiException.Output.Reason
import kotlin.math.abs
import kotlin.math.sqrt

/** Output vector rules, from forma `validation.ts` `vector`. */
internal object VectorRules {
    const val NORM_TOLERANCE: Double = 0.0001

    /** Returns [vector] if it is valid. If [normalized] is true, the L2 norm must be 1 within [NORM_TOLERANCE]. */
    fun check(vector: FloatArray, expectedSize: Int, normalized: Boolean): FloatArray {
        require(expectedSize > 0) { "The expected size must be positive." }
        if (vector.size != expectedSize) throw LoiException.Output(Reason.WRONG_SIZE)
        var sum = 0.0
        for (value in vector) {
            if (!value.isFinite()) throw LoiException.Output(Reason.NOT_FINITE)
            sum += value.toDouble() * value
        }
        val norm = sqrt(sum)
        if (norm == 0.0) throw LoiException.Output(Reason.ZERO_NORM)
        if (normalized && abs(norm - 1.0) > NORM_TOLERANCE) throw LoiException.Output(Reason.WRONG_NORM)
        return vector
    }
}
