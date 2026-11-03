package dev.loi.core.verify

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {
    @Test fun knownVectors() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.of("abc"))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.of(""))
    }

    @Test fun fileHashMatchesTextHashAcrossChunks() {
        val file = Files.createTempFile("sha", ".bin").toFile()
        try {
            val text = "x".repeat(65536 * 2 + 17)
            file.writeText(text)
            assertEquals(Sha256.of(text), Sha256.of(file))
        } finally {
            file.delete()
        }
    }
}
