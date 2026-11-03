package dev.loi.android

import android.content.Context
import dev.loi.core.api.Embedder
import dev.loi.core.engine.EmbeddingEngineFactory
import dev.loi.core.runtime.SerialEmbedder
import dev.loi.core.verify.FileModelPreparer
import dev.loi.models.embeddinggemma2.EmbeddingGemma2
import dev.loi.runtime.litertlm.LiteRtEmbeddingEngineFactory
import java.io.File

/** The entry point of loi on Android. See DESIGN.md section 4. */
public object Loi {
    /** The folder name under `noBackupFilesDir`. It holds the hash cache and the engine caches. */
    public const val STORAGE_FOLDER: String = "loi"

    /**
     * Makes an EmbeddingGemma 2 embedder for [modelFile].
     *
     * This call does not read the file or load the model. The first `embed` call, or `load()`, does that on the
     * embedder worker thread. Call `release()` on memory pressure, and `close()` at the end.
     */
    public fun embeddingGemma2(
        context: Context,
        modelFile: File,
        options: EmbeddingGemma2.Options = EmbeddingGemma2.Options(),
    ): Embedder = embeddingGemma2(storageRoot(context), modelFile, options, LiteRtEmbeddingEngineFactory)

    /** `<noBackupFilesDir>/loi`. Android does not back up this folder, and the files stay private to the app. */
    public fun storageRoot(context: Context): File = File(context.noBackupFilesDir, STORAGE_FOLDER)

    /** The composition without an Android `Context`, for tests. */
    internal fun embeddingGemma2(
        storageRoot: File,
        modelFile: File,
        options: EmbeddingGemma2.Options,
        factory: EmbeddingEngineFactory,
    ): Embedder {
        val preparer = FileModelPreparer(
            modelFile = modelFile,
            level = options.verification,
            known = EmbeddingGemma2.KNOWN_ARTIFACTS,
            storageRoot = storageRoot,
            options = { modelPath, cacheDir -> EmbeddingGemma2.engineOptions(options, modelPath, cacheDir) },
        )
        return SerialEmbedder(EmbeddingGemma2.spec(options), preparer, factory)
    }
}
