package dev.loi.sample

import android.content.Context
import dev.loi.core.api.Embedder
import dev.loi.core.api.EmbeddingTask
import dev.loi.models.embeddinggemma2.EmbeddingGemma2
import java.io.File
import kotlin.math.sqrt

/** The model location: app-specific external storage, so `adb push` can place the file with no permission. */
internal fun modelFile(context: Context): File =
    File(requireNotNull(context.getExternalFilesDir(null)) { "External app storage is not available." }, EmbeddingGemma2.ARTIFACT.fileName)

/** Synthetic texts only. The sample never logs user text. */
internal object SampleTexts {
    const val QUERY = "knee pain after squats"
    const val RELATED = "My left knee hurt during the last set of back squats."
    const val UNRELATED = "I bought rice, eggs, and coffee at the market."
}

internal class SampleResult(
    val verification: String,
    val loadMs: Long,
    val queryMs: Long,
    val documentMs: Long,
    val dimensions: Int,
    val queryNorm: Double,
    val related: Float,
    val unrelated: Float,
) {
    fun toJson(): String =
        """{"ok":true,"verification":"$verification","loadMs":$loadMs,"queryMs":$queryMs,"documentMs":$documentMs,""" +
            """"dimensions":$dimensions,"queryNorm":$queryNorm,"related":$related,"unrelated":$unrelated}"""
}

/** Loads the model, embeds one query and two documents, and measures each step. */
internal suspend fun runSample(embedder: Embedder): SampleResult {
    val loadStart = System.nanoTime()
    embedder.load()
    val loadMs = (System.nanoTime() - loadStart) / 1_000_000
    val queryStart = System.nanoTime()
    val query = embedder.embed(SampleTexts.QUERY, EmbeddingTask.QUERY)
    val queryMs = (System.nanoTime() - queryStart) / 1_000_000
    val documentStart = System.nanoTime()
    val related = embedder.embed(SampleTexts.RELATED, EmbeddingTask.DOCUMENT)
    val documentMs = (System.nanoTime() - documentStart) / 1_000_000
    val unrelated = embedder.embed(SampleTexts.UNRELATED, EmbeddingTask.DOCUMENT)
    val norm = sqrt(query.toFloatArray().sumOf { it.toDouble() * it })
    return SampleResult(
        verification = embedder.model.verification.toString(),
        loadMs = loadMs, queryMs = queryMs, documentMs = documentMs,
        dimensions = query.dimensions, queryNorm = norm,
        related = query.cosine(related), unrelated = query.cosine(unrelated),
    )
}
