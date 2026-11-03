package dev.loi.core.runtime

import dev.loi.core.api.VerificationResult
import dev.loi.core.engine.EngineOptions

/** The output of the file check: the engine options for one load and the check result. */
public class PreparedModel(public val options: EngineOptions, public val verification: VerificationResult)

/**
 * Checks the model file and makes the engine options before each load.
 *
 * The embedder calls it on its worker thread, so it can block on file IO.
 * It throws `LoiException.ModelFile` or `LoiException.Verification` if the file is not acceptable.
 */
public fun interface ModelPreparer {
    public fun prepare(): PreparedModel
}
