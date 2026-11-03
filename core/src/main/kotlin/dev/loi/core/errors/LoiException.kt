package dev.loi.core.errors

/**
 * The base of all loi failures.
 *
 * Messages are fixed text. They never contain input text, vector values, or file paths.
 * An engine failure is attached as [cause]. Its message can contain input text or paths, so log it with care.
 * Cancellation uses the standard `CancellationException`, not this class.
 */
public sealed class LoiException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The model file is missing, is not a file, or cannot be read. */
    public class ModelFile(public val reason: Reason, cause: Throwable? = null) : LoiException(reason.message, cause) {
        public enum class Reason(internal val message: String) {
            MISSING("The model file does not exist."),
            NOT_A_FILE("The model path is not a regular file."),
            UNREADABLE("The model file cannot be read."),
        }
    }

    /** The model file does not match a known artifact at level `STRICT`. */
    public class Verification(public val reason: Reason) : LoiException(reason.message) {
        public enum class Reason(internal val message: String) {
            UNKNOWN_SIZE("The model file size does not match a known artifact."),
            UNKNOWN_HASH("The model file SHA-256 does not match a known artifact."),
        }
    }

    /** The engine failed to initialize. */
    public class Load(cause: Throwable? = null) : LoiException("The model engine failed to load.", cause)

    /** The input text breaks an input rule. */
    public class Input(public val reason: Reason) : LoiException(reason.message) {
        public enum class Reason(internal val message: String) {
            BLANK("The input text is blank."),
            TOO_LONG("The input text is longer than the limit."),
            NUL("The input text contains a NUL character."),
            UNPAIRED_SURROGATE("The input text contains an unpaired surrogate."),
        }
    }

    /** The engine output breaks an output rule, or the engine failed during inference. */
    public class Output(public val reason: Reason, cause: Throwable? = null) : LoiException(reason.message, cause) {
        public enum class Reason(internal val message: String) {
            ENGINE_FAILURE("The model engine failed during inference."),
            WRONG_SIZE("The output vector has the wrong size."),
            NOT_FINITE("The output vector contains a value that is not finite."),
            ZERO_NORM("The output vector has a zero norm."),
            WRONG_NORM("The output vector is not L2-normalized."),
        }
    }

    /** The embedder is closed. */
    public class Closed : LoiException("The embedder is closed.")

    /** The engine failed to close. The embedder does not load a second engine. */
    public class Resource(cause: Throwable? = null) :
        LoiException("The model engine failed to close. The embedder cannot load again.", cause)
}
