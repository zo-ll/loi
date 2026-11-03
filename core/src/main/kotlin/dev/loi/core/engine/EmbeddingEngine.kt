package dev.loi.core.engine

/**
 * The engine port. A runtime module, such as `runtime-litertlm`, implements it.
 *
 * Only the embedder worker thread calls these methods, one at a time. An implementation does not need its own locks.
 */
public interface EmbeddingEngine : AutoCloseable {
    /** Loads the model. The embedder calls this method once, before the first [embed] call. */
    public fun initialize()

    /** Returns the raw vector for text that already has its task prefix. This call blocks. */
    public fun embed(prefixedText: String): FloatArray

    override fun close()
}

/** Makes one engine for one load. The embedder calls it again after a release. */
public fun interface EmbeddingEngineFactory {
    public fun create(options: EngineOptions): EmbeddingEngine
}

public enum class Backend { CPU }

public enum class Precision { FLOAT32 }

/** All values that a runtime needs to make an engine. No value is specific to one SDK. */
public data class EngineOptions(
    val modelPath: String,
    val cacheDir: String,
    val maxInputTokens: Int,
    val outputDimensions: Int,
    val normalize: Boolean,
    val insertSpecialTokens: Boolean,
    val backend: Backend = Backend.CPU,
    val activationPrecision: Precision = Precision.FLOAT32,
    /** Native SDK logs can contain input text or paths. They are off by default. */
    val nativeLogs: Boolean = false,
) {
    init {
        require(modelPath.isNotBlank()) { "The model path is blank." }
        require(cacheDir.isNotBlank()) { "The cache directory is blank." }
        require(maxInputTokens > 0) { "The input token limit must be positive." }
        require(outputDimensions > 0) { "The output size must be positive." }
    }

    /** The text form does not contain paths. */
    override fun toString(): String =
        "EngineOptions(maxInputTokens=$maxInputTokens, outputDimensions=$outputDimensions, normalize=$normalize, " +
            "insertSpecialTokens=$insertSpecialTokens, backend=$backend, activationPrecision=$activationPrecision, " +
            "nativeLogs=$nativeLogs)"
}
