package dev.loi.gradle.boundaries

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class CheckImportBoundariesTask : DefaultTask() {
    @get:Input abstract val rules: ListProperty<ImportBoundaryRule>

    /** Module path to the absolute path of its `src` folder. */
    @get:Input abstract val sourceRoots: MapProperty<String, String>

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:OutputFile abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val roots = sourceRoots.get().mapValues { File(it.value) }
        val unknown = ImportBoundaryScanner.unknownModules(rules.get(), roots.keys)
        if (unknown.isNotEmpty()) throw GradleException("Import boundary rules name unknown modules:\n" + unknown.joinToString("\n"))
        val violations = ImportBoundaryScanner.scan(roots, rules.get())
        val text = if (violations.isEmpty()) "No import boundary violations.\n" else violations.joinToString("\n", postfix = "\n")
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(text)
        if (violations.isNotEmpty()) throw GradleException("Import boundary violations:\n$text")
    }
}
