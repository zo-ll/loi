package dev.loi.core.api

/**
 * Makes embeddings with one model on one worker thread.
 *
 * The embedder loads the model on the first call, or on [load]. The caller sends plain text.
 * The embedder adds the task prefix that the model needs.
 */
public interface Embedder : AutoCloseable {
    /** The model and the result of the last file check. */
    public val model: ModelInfo

    /** Checks the file and loads the model now. Without this call, the first [embed] call loads the model. */
    public suspend fun load()

    public suspend fun embed(text: String, task: EmbeddingTask): Embedding

    /** Embeds the texts in sequence, in the same order. */
    public suspend fun embedAll(texts: List<String>, task: EmbeddingTask): List<Embedding>

    /** Waits for the active call and unloads the model. The embedder stays usable, and the next call loads again. */
    public suspend fun release()

    /** Waits for the active call, unloads the model, and rejects later calls. */
    override fun close()
}
