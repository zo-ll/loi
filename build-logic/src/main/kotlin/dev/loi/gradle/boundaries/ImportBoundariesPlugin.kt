package dev.loi.gradle.boundaries

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

/** Apply to the root project only. It scans the `src` folder of each module that has a build file. */
class ImportBoundariesPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target == target.rootProject) { "Apply loi.boundaries to the root project only." }
        val extension = target.extensions.create<ImportBoundariesExtension>("importBoundaries")
        val modules = target.subprojects.filter { it.buildFile.exists() }
        target.tasks.register<CheckImportBoundariesTask>("checkImportBoundaries") {
            group = "verification"
            description = "Fails if a module uses a source pattern that its boundary forbids."
            rules.set(extension.rules)
            sourceRoots.set(modules.associate { it.path to it.file("src").absolutePath })
            sources.from(modules.map { it.fileTree("src") })
            report.set(target.layout.buildDirectory.file("reports/import-boundaries.txt"))
        }
    }
}
