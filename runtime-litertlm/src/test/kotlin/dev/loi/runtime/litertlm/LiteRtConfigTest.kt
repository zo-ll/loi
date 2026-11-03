package dev.loi.runtime.litertlm

import com.google.ai.edge.litertlm.ActivationDataType
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.LogSeverity
import dev.loi.core.engine.EngineOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Port of forma `LiteRtInferenceConfigTest`. It inspects real SDK objects without a native load. */
class LiteRtConfigTest {
    private val options = EngineOptions(
        modelPath = "/data/model.litertlm", cacheDir = "/data/cache", maxInputTokens = 1024,
        outputDimensions = 512, normalize = true, insertSpecialTokens = true,
    )

    @Test fun engineConfigMapsEachField() {
        val config = LiteRtConfig.engine(options)
        assertEquals("/data/model.litertlm", config.modelPath)
        assertEquals("/data/cache", config.cacheDir)
        assertEquals(1024, config.maxInputLength)
        assertEquals(ActivationDataType.FLOAT32, config.activationDataType)
        assertIs<Backend.CPU>(config.backend)
    }

    @Test fun engineConfigUsesNoVisionAudioOrFileDescriptor() {
        val config = LiteRtConfig.engine(options)
        assertNull(config.visionBackend)
        assertNull(config.audioBackend)
        assertNull(config.modelFd)
        assertNull(config.visionTokensPerImage)
    }

    @Test fun embeddingOptionsMapEachField() {
        val embedding = LiteRtConfig.embedding(options)
        assertEquals(true, embedding.normalize)
        assertEquals(true, embedding.insertSpecialTokens)
        assertEquals(512, embedding.outputSize)
        assertNull(embedding.visionTokensPerImage)
        val other = LiteRtConfig.embedding(options.copy(normalize = false, insertSpecialTokens = false, outputDimensions = 128))
        assertEquals(false, other.normalize)
        assertEquals(false, other.insertSpecialTokens)
        assertEquals(128, other.outputSize)
    }

    @Test fun nativeLogsAreOffByDefault() {
        assertEquals(LogSeverity.INFINITY, LiteRtConfig.logSeverity(options))
        assertEquals(LogSeverity.INFO, LiteRtConfig.logSeverity(options.copy(nativeLogs = true)))
    }
}
