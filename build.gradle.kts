plugins {
    alias(libs.plugins.loi.boundaries)
}

importBoundaries {
    // Only the LiteRT-LM runtime module can use the engine SDK.
    forbid(
        name = "litertlm-only-in-runtime",
        pattern = """\bcom\.google\.ai\.edge\b""",
        exceptIn = listOf(":runtime-litertlm"),
    )
    // The core and the model specs stay free of Android types.
    forbid(
        name = "no-android-in-pure-modules",
        pattern = """^\s*import\s+androidx?\.""",
        onlyIn = listOf(":core", ":models:embeddinggemma2", ":testing"),
    )
}

tasks.register("check") {
    group = "verification"
    description = "Runs all checks in all modules and in the build logic."
    // Intermediate folders, such as `:models`, have no build file and no check task.
    dependsOn(subprojects.filter { it.buildFile.exists() }.map { "${it.path}:check" })
    dependsOn("checkImportBoundaries")
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}
