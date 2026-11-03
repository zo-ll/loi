package dev.loi.runtime.litertlm

import com.google.ai.edge.litertlm.EmbeddingEngine as SdkEngine
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.google.ai.edge.litertlm.LogSeverity
import dev.loi.core.engine.EmbeddingEngine
import dev.loi.core.engine.EmbeddingEngineFactory
import dev.loi.core.engine.EngineOptions
import dev.loi.core.engine.TooManyTokensException

/**
 * The LiteRT-LM engine factory. `SerialEmbedder` wraps each SDK failure in a `LoiException`
 * with the SDK exception as `cause`.
 *
 * Warning: the native log level is a process-wide SDK setting. The last engine to initialize sets it.
 */
public object LiteRtEmbeddingEngineFactory : EmbeddingEngineFactory {
    override fun create(options: EngineOptions): EmbeddingEngine =
        LiteRtEmbeddingEngine(options, SdkEmbedding.Real(SdkEngine(LiteRtConfig.engine(options)))) { Engine.setNativeMinLogSeverity(it) }
}

/** From forma `LiteRtEmbeddingModel`. The output size and the norm checks are in `SerialEmbedder`. */
internal class LiteRtEmbeddingEngine(
    private val options: EngineOptions,
    private val sdk: SdkEmbedding,
    private val setLogSeverity: (LogSeverity) -> Unit,
) : EmbeddingEngine {
    private val embedding = LiteRtConfig.embedding(options)

    override fun initialize() {
        setLogSeverity(LiteRtConfig.logSeverity(options))
        sdk.initialize()
    }

    override fun embed(prefixedText: String): FloatArray = try {
        sdk.compute(prefixedText, embedding).copyOf()
    } catch (error: LiteRtLmJniException) {
        if (SdkErrors.isTooManyTokens(error)) throw TooManyTokensException(error)
        throw error
    }

    override fun close() {
        if (sdk.isInitialized()) sdk.close()
    }
}

/**
 * The SDK error forms that loi knows.
 *
 * LiteRT-LM 0.18.0 has one exception type, [LiteRtLmJniException], with a message and no error code.
 * For an embedding with more tokens than `maxInputLength`, the message is `ComputeEmbedding failed: INVALID_ARGUMENT: `.
 * The native detail text after the status is empty. Issue #17 checked this on a device: only a token overflow gave
 * `INVALID_ARGUMENT`. Control characters, emoji, RTL text, BOM, private-use, and noncharacter text did not.
 *
 * Warning: this match depends on the SDK message. A change of the SDK version must run the device test
 * `inputLongerThanTheTokenLimit` again.
 */
internal object SdkErrors {
    private const val TOO_MANY_TOKENS = "ComputeEmbedding failed: INVALID_ARGUMENT"

    fun isTooManyTokens(error: LiteRtLmJniException): Boolean = error.message?.startsWith(TOO_MANY_TOKENS) == true
}

/** The SDK calls that the adapter uses. Tests replace it, because the real engine needs the native library. */
internal interface SdkEmbedding {
    fun initialize()
    fun isInitialized(): Boolean
    fun compute(text: String, options: EmbeddingOptions): FloatArray
    fun close()

    class Real(private val engine: SdkEngine) : SdkEmbedding {
        override fun initialize() = engine.initialize()
        override fun isInitialized() = engine.isInitialized()
        override fun compute(text: String, options: EmbeddingOptions): FloatArray =
            engine.computeEmbedding(listOf(InputData.Text(text)), options).embedding
        override fun close() = engine.close()
    }
}
