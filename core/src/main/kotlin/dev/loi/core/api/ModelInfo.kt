package dev.loi.core.api

/**
 * A snapshot of the model behind an embedder.
 *
 * [id] identifies the vector space: two embeddings are compatible only if their [id] values are equal.
 */
public data class ModelInfo(
    val id: String,
    val dimensions: Int,
    val verification: VerificationResult,
)
