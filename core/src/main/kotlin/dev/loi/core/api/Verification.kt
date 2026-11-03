package dev.loi.core.api

/** How strictly loi checks the model file before a load. See DESIGN.md section 8. */
public enum class Verification {
    /** The size and SHA-256 must match a known artifact. */
    STRICT,

    /** An unknown file loads. [VerificationResult.Unknown] reports its SHA-256. */
    WARN,

    /** loi checks only that the file exists and can be read. */
    OFF,
}

/** A model file that loi knows, with its exact size and SHA-256. */
public class KnownArtifact(
    public val id: String,
    public val fileName: String,
    public val bytes: Long,
    public val sha256: String,
) {
    init {
        require(id.isNotBlank()) { "The artifact ID is blank." }
        require(fileName.isNotBlank()) { "The artifact file name is blank." }
        require(bytes > 0) { "The artifact size must be positive." }
        require(SHA256.matches(sha256)) { "The artifact SHA-256 must be 64 lowercase hex characters." }
    }

    override fun equals(other: Any?): Boolean =
        other is KnownArtifact && id == other.id && fileName == other.fileName && bytes == other.bytes && sha256 == other.sha256

    override fun hashCode(): Int = sha256.hashCode()

    override fun toString(): String = "KnownArtifact(id=$id, bytes=$bytes, sha256=$sha256)"

    private companion object {
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}

/** The result of the last model file check. */
public sealed interface VerificationResult {
    /** The model has not loaded yet. */
    public data object Pending : VerificationResult

    /** The file matches a known artifact. */
    public data class Known(val artifact: KnownArtifact) : VerificationResult

    /** The file is not a known artifact. It loaded at level `WARN`. */
    public data class Unknown(val sha256: String) : VerificationResult

    /** The file loaded at level `OFF`, with no hash check. */
    public data object Skipped : VerificationResult
}
