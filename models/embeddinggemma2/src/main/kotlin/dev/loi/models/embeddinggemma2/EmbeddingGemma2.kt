package dev.loi.models.embeddinggemma2

import dev.loi.core.api.EmbeddingTask
import dev.loi.core.api.KnownArtifact
import dev.loi.core.api.Verification
import dev.loi.core.engine.Backend
import dev.loi.core.engine.EmbeddingSpec
import dev.loi.core.engine.EngineOptions
import dev.loi.core.engine.Precision

/**
 * The facts about EmbeddingGemma 2 Text 270M. See DESIGN.md section 10.
 *
 * Sources: forma `docs/model-policy.md` and `LiteRtInferenceConfig.kt`, and the model card for the allowed sizes.
 */
public object EmbeddingGemma2 {
    public const val NAME: String = "embeddinggemma-2-text-270m"
    public const val QUERY_PREFIX: String = "task: search query | text: "
    public const val DOCUMENT_PREFIX: String = "task: search result | text: "

    /** Output sizes that the model supports with MRL truncation. */
    public val DIMENSIONS: List<Int> = listOf(128, 256, 512, 768)

    /** Input token limits that the model supports. */
    public val INPUT_TOKENS: List<Int> = listOf(128, 256, 512, 1024, 2048, 8192)

    public const val DEFAULT_DIMENSIONS: Int = 256
    public const val DEFAULT_INPUT_TOKENS: Int = 2048

    /** The character limit for the caller text, from the forma inference limits. */
    public const val MAX_INPUT_CHARACTERS: Int = 16384

    /** `litert-community/embeddinggemma-2-text-270m-litert-lm`, Apache-2.0. loi does not include the file. */
    public val ARTIFACT: KnownArtifact = KnownArtifact(
        id = NAME,
        fileName = "embeddinggemma-2-text-270m.litertlm",
        bytes = 164_626_432L,
        sha256 = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb",
    )

    public val KNOWN_ARTIFACTS: List<KnownArtifact> = listOf(ARTIFACT)

    /**
     * Caller options. The constructor rejects sizes that the model does not support.
     *
     * @param nativeLogs turns on the LiteRT-LM native logs. They can contain input text or paths.
     */
    public class Options(
        public val dimensions: Int = DEFAULT_DIMENSIONS,
        public val maxInputTokens: Int = DEFAULT_INPUT_TOKENS,
        public val verification: Verification = Verification.STRICT,
        public val nativeLogs: Boolean = false,
    ) {
        init {
            require(dimensions in DIMENSIONS) { "EmbeddingGemma 2 supports output sizes $DIMENSIONS." }
            require(maxInputTokens in INPUT_TOKENS) { "EmbeddingGemma 2 supports input token limits $INPUT_TOKENS." }
        }

        override fun toString(): String =
            "Options(dimensions=$dimensions, maxInputTokens=$maxInputTokens, verification=$verification, nativeLogs=$nativeLogs)"
    }

    /** The model ID includes the output size, so `Embedding.cosine` rejects vectors with different sizes. */
    public fun modelId(dimensions: Int): String {
        require(dimensions in DIMENSIONS) { "EmbeddingGemma 2 supports output sizes $DIMENSIONS." }
        return "$NAME-mrl$dimensions"
    }

    public fun spec(options: Options = Options()): EmbeddingSpec = Spec(options.dimensions)

    public fun engineOptions(options: Options, modelPath: String, cacheDir: String): EngineOptions = EngineOptions(
        modelPath = modelPath,
        cacheDir = cacheDir,
        maxInputTokens = options.maxInputTokens,
        outputDimensions = options.dimensions,
        // The SDK does the MRL truncation and the L2 normalization. The embedder checks the norm.
        normalize = true,
        insertSpecialTokens = true,
        backend = Backend.CPU,
        activationPrecision = Precision.FLOAT32,
        nativeLogs = options.nativeLogs,
    )

    private class Spec(override val dimensions: Int) : EmbeddingSpec {
        override val modelId: String = modelId(dimensions)
        override val normalized: Boolean = true
        override val maxInputCharacters: Int = MAX_INPUT_CHARACTERS

        override fun prefix(task: EmbeddingTask): String = when (task) {
            EmbeddingTask.QUERY -> QUERY_PREFIX
            EmbeddingTask.DOCUMENT -> DOCUMENT_PREFIX
        }
    }
}
