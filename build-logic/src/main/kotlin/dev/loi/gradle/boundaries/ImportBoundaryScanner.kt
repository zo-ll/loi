package dev.loi.gradle.boundaries

import java.io.File

/** Pure scan logic. The Gradle task supplies the module source folders. */
object ImportBoundaryScanner {
    private val sourceExtensions = setOf("kt", "kts", "java")

    /** Returns the rule names that refer to a module that does not exist. */
    fun unknownModules(rules: List<ImportBoundaryRule>, modules: Set<String>): List<String> =
        rules.flatMap { rule -> rule.modules.filter { it !in modules }.map { "${rule.name}: $it" } }

    fun scan(sourceRoots: Map<String, File>, rules: List<ImportBoundaryRule>): List<ImportBoundaryViolation> {
        val compiled = rules.map { it to Regex(it.pattern) }
        val violations = mutableListOf<ImportBoundaryViolation>()
        for ((module, root) in sourceRoots.toSortedMap()) {
            val active = compiled.filter { (rule, _) -> rule.appliesTo(module) }
            if (active.isEmpty() || !root.isDirectory) continue
            val files = root.walkTopDown().filter { it.isFile && it.extension in sourceExtensions }.sortedBy { it.path }
            for (file in files) {
                file.readLines().forEachIndexed { index, line ->
                    for ((rule, regex) in active) {
                        if (regex.containsMatchIn(line)) {
                            violations += ImportBoundaryViolation(rule.name, module, file.relativeTo(root).path, index + 1, line)
                        }
                    }
                }
            }
        }
        return violations
    }
}
