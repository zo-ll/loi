package dev.loi.gradle.boundaries

import org.gradle.api.provider.ListProperty

abstract class ImportBoundariesExtension {
    abstract val rules: ListProperty<ImportBoundaryRule>

    fun forbid(name: String, pattern: String, onlyIn: List<String> = emptyList(), exceptIn: List<String> = emptyList()) {
        rules.add(ImportBoundaryRule(name, pattern, onlyIn, exceptIn))
    }
}
