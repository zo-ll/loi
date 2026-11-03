package dev.loi.core.engine

import dev.loi.core.api.EmbeddingTask

/**
 * The facts about one embedding model that the embedder needs. A model module, such as
 * `models:embeddinggemma2`, implements it.
 */
public interface EmbeddingSpec {
    /** Identifies the vector space. Two embeddings are compatible only if this value is equal. */
    public val modelId: String

    /** The output vector size. */
    public val dimensions: Int

    /** True if the engine returns L2-normalized vectors. The embedder then checks the norm. */
    public val normalized: Boolean

    /** The character limit for the caller text, without the prefix. */
    public val maxInputCharacters: Int

    /** The prompt prefix for [task]. The embedder adds it once to the caller text. */
    public fun prefix(task: EmbeddingTask): String
}
