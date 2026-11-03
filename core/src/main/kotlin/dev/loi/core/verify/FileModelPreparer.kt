package dev.loi.core.verify

import dev.loi.core.api.KnownArtifact
import dev.loi.core.api.Verification
import dev.loi.core.api.VerificationResult
import dev.loi.core.engine.EngineOptions
import dev.loi.core.errors.LoiException
import dev.loi.core.runtime.ModelPreparer
import dev.loi.core.runtime.PreparedModel
import java.io.File

/**
 * Checks a model file before each load and makes the engine options.
 *
 * Folders under [storageRoot]:
 * - `verify/`: hash cache records.
 * - `cache/<key>/`: the engine cache for one model file. The key is the SHA-256. At level `OFF`,
 *   the key is `off-<size>-<modified>` because loi does not hash the file.
 *
 * @param options makes the engine options from the model path and the engine cache folder.
 */
public class FileModelPreparer internal constructor(
    private val modelFile: File,
    private val level: Verification,
    known: List<KnownArtifact>,
    private val storageRoot: File,
    private val options: (modelPath: String, cacheDir: String) -> EngineOptions,
    hasher: (File) -> String,
) : ModelPreparer {
    public constructor(
        modelFile: File,
        level: Verification,
        known: List<KnownArtifact>,
        storageRoot: File,
        options: (modelPath: String, cacheDir: String) -> EngineOptions,
    ) : this(modelFile, level, known, storageRoot, options, Sha256::of)

    private val check = ModelFileCheck(known, HashCache(File(storageRoot, "verify")), hasher)

    override fun prepare(): PreparedModel {
        val result = check.check(modelFile, level)
        val key = when (val v = result.verification) {
            is VerificationResult.Known -> v.artifact.sha256
            is VerificationResult.Unknown -> v.sha256
            else -> "off-${result.stamp.bytes}-${result.stamp.modifiedMillis}"
        }
        val cacheDir = File(storageRoot, "cache/$key")
        if (!cacheDir.isDirectory && !cacheDir.mkdirs()) {
            throw LoiException.Load(java.io.IOException("The engine cache folder cannot be made."))
        }
        return PreparedModel(options(modelFile.canonicalPath, cacheDir.absolutePath), result.verification)
    }
}
