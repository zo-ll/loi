package dev.loi.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(name: String): String =
    findVersion(name).orElseThrow { IllegalStateException("The version catalog has no version '$name'.") }.requiredVersion

internal fun VersionCatalog.library(name: String): Any =
    findLibrary(name).orElseThrow { IllegalStateException("The version catalog has no library '$name'.") }.get()
