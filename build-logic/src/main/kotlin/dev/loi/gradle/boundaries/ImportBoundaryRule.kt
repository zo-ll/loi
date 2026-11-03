package dev.loi.gradle.boundaries

import java.io.Serializable

/**
 * One forbidden source pattern.
 *
 * The rule applies to a module if [onlyIn] is empty or contains the module, and [exceptIn] does not contain it.
 */
data class ImportBoundaryRule(
    val name: String,
    val pattern: String,
    val onlyIn: List<String> = emptyList(),
    val exceptIn: List<String> = emptyList(),
) : Serializable {
    fun appliesTo(module: String): Boolean =
        (onlyIn.isEmpty() || module in onlyIn) && module !in exceptIn

    val modules: List<String> get() = onlyIn + exceptIn

    companion object {
        private const val serialVersionUID = 1L
    }
}

data class ImportBoundaryViolation(val rule: String, val module: String, val path: String, val line: Int, val text: String) {
    override fun toString(): String = "[$rule] $module: $path:$line: ${text.trim()}"
}
