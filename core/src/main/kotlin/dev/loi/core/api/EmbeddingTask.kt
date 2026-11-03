package dev.loi.core.api

/** The purpose of a text. The model spec maps each task to its prompt prefix. */
public enum class EmbeddingTask {
    /** A search query. */
    QUERY,

    /** A text to search in, such as a note or a document. */
    DOCUMENT,
}
