package dev.loi.gradle.boundaries

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportBoundaryScannerTest {
    private val root: File = Files.createTempDirectory("boundaries").toFile()

    @AfterTest fun cleanup() { root.deleteRecursively() }

    private fun source(module: String, path: String, text: String): File =
        File(root, "$module/src/$path").apply { parentFile.mkdirs(); writeText(text) }

    private fun roots(vararg modules: String) = modules.associate { ":$it" to File(root, "$it/src") }

    private val litert = ImportBoundaryRule("litert", """\bcom\.google\.ai\.edge\b""", exceptIn = listOf(":runtime"))
    private val android = ImportBoundaryRule("android", """^\s*import\s+androidx?\.""", onlyIn = listOf(":core"))

    @Test fun cleanSourcesPass() {
        source("core", "main/kotlin/A.kt", "package a\nimport kotlin.math.sqrt\n")
        source("runtime", "main/kotlin/B.kt", "import com.google.ai.edge.litertlm.Engine\n")
        assertTrue(ImportBoundaryScanner.scan(roots("core", "runtime"), listOf(litert, android)).isEmpty())
    }

    @Test fun forbiddenImportFails() {
        source("core", "main/kotlin/A.kt", "package a\nimport com.google.ai.edge.litertlm.Engine\n")
        val violations = ImportBoundaryScanner.scan(roots("core", "runtime"), listOf(litert))
        assertEquals(1, violations.size)
        assertEquals("litert", violations[0].rule)
        assertEquals(":core", violations[0].module)
        assertEquals(2, violations[0].line)
    }

    @Test fun fullyQualifiedUseFails() {
        source("core", "main/kotlin/A.kt", "val e = com.google.ai.edge.litertlm.Engine::class\n")
        assertEquals(1, ImportBoundaryScanner.scan(roots("core"), listOf(litert)).size)
    }

    @Test fun testSourcesAreAlsoChecked() {
        source("core", "test/kotlin/T.kt", "import android.content.Context\n")
        assertEquals(1, ImportBoundaryScanner.scan(roots("core"), listOf(android)).size)
    }

    @Test fun androidxImportFails() {
        source("core", "main/kotlin/A.kt", "  import androidx.annotation.Keep\n")
        assertEquals(1, ImportBoundaryScanner.scan(roots("core"), listOf(android)).size)
    }

    @Test fun onlyInLimitsTheRule() {
        source("runtime", "main/kotlin/A.kt", "import android.content.Context\n")
        assertTrue(ImportBoundaryScanner.scan(roots("core", "runtime"), listOf(android)).isEmpty())
    }

    @Test fun nonSourceFilesAreIgnored() {
        source("core", "main/res/notes.txt", "import com.google.ai.edge.litertlm.Engine\n")
        assertTrue(ImportBoundaryScanner.scan(roots("core"), listOf(litert)).isEmpty())
    }

    @Test fun missingSourceFolderIsIgnored() {
        assertTrue(ImportBoundaryScanner.scan(roots("core"), listOf(litert)).isEmpty())
    }

    @Test fun unknownModuleNamesAreReported() {
        val typo = ImportBoundaryRule("typo", "x", onlyIn = listOf(":cor"))
        assertEquals(listOf("typo: :cor"), ImportBoundaryScanner.unknownModules(listOf(typo, android), setOf(":core")))
    }
}
