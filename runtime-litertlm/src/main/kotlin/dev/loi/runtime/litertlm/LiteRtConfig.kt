package dev.loi.runtime.litertlm

import com.google.ai.edge.litertlm.ActivationDataType
import com.google.ai.edge.litertlm.Backend as SdkBackend
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.LogSeverity
import dev.loi.core.engine.Backend
import dev.loi.core.engine.EngineOptions
import dev.loi.core.engine.Precision

/**
 * Maps [EngineOptions] to the LiteRT-LM 0.18.0 configuration objects. From forma `LiteRtInferenceConfig`.
 *
 * These are real SDK objects, and tests can inspect them without a native load.
 */
internal object LiteRtConfig {
    fun engine(options: EngineOptions): EmbeddingEngineConfig = EmbeddingEngineConfig(
        modelPath = options.modelPath,
        backend = backend(options.backend),
        cacheDir = options.cacheDir,
        maxInputLength = options.maxInputTokens,
        activationDataType = precision(options.activationPrecision),
    )

    fun embedding(options: EngineOptions): EmbeddingOptions = EmbeddingOptions(
        normalize = options.normalize,
        insertSpecialTokens = options.insertSpecialTokens,
        outputSize = options.outputDimensions,
    )

    /** Native logs can contain input text or paths, so they are off unless the caller asks for them. */
    fun logSeverity(options: EngineOptions): LogSeverity = if (options.nativeLogs) LogSeverity.INFO else LogSeverity.INFINITY

    private fun backend(value: Backend): SdkBackend = when (value) {
        Backend.CPU -> SdkBackend.CPU()
    }

    private fun precision(value: Precision): ActivationDataType = when (value) {
        Precision.FLOAT32 -> ActivationDataType.FLOAT32
    }
}
