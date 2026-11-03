package dev.loi.core.api

import kotlin.math.sqrt

/**
 * One embedding vector with its task and model.
 *
 * The class keeps a private copy of the vector, so a caller cannot change it after construction.
 */
public class Embedding(vector: FloatArray, public val task: EmbeddingTask, public val modelId: String) {
    private val values: FloatArray = vector.copyOf()

    init {
        require(values.isNotEmpty()) { "The vector is empty." }
        require(modelId.isNotBlank()) { "The model ID is blank." }
    }

    public val dimensions: Int get() = values.size

    public operator fun get(index: Int): Float = values[index]

    /** Returns a new copy of the vector. */
    public fun toFloatArray(): FloatArray = values.copyOf()

    /**
     * Returns the cosine similarity, from -1 to 1.
     *
     * @throws IllegalArgumentException if the two embeddings come from different models or have different sizes.
     */
    public fun cosine(other: Embedding): Float {
        require(modelId == other.modelId) { "The embeddings come from different models." }
        require(dimensions == other.dimensions) { "The embeddings have different sizes." }
        var dot = 0.0
        var left = 0.0
        var right = 0.0
        for (i in values.indices) {
            val a = values[i].toDouble()
            val b = other.values[i].toDouble()
            dot += a * b
            left += a * a
            right += b * b
        }
        require(left > 0.0 && right > 0.0) { "An embedding has a zero norm." }
        return (dot / (sqrt(left) * sqrt(right))).coerceIn(-1.0, 1.0).toFloat()
    }

    override fun equals(other: Any?): Boolean =
        other is Embedding && task == other.task && modelId == other.modelId && values.contentEquals(other.values)

    override fun hashCode(): Int = 31 * (31 * values.contentHashCode() + task.hashCode()) + modelId.hashCode()

    /** The text form does not contain vector values. */
    override fun toString(): String = "Embedding(modelId=$modelId, task=$task, dimensions=$dimensions)"
}
