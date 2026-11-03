package dev.loi.core.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KnownArtifactTest {
    private val hash = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb"

    @Test fun validArtifactIsAccepted() {
        val a = KnownArtifact("id", "model.litertlm", 10, hash)
        assertEquals(a, KnownArtifact("id", "model.litertlm", 10, hash))
    }

    @Test fun invalidValuesAreRejected() {
        assertFailsWith<IllegalArgumentException> { KnownArtifact(" ", "f", 10, hash) }
        assertFailsWith<IllegalArgumentException> { KnownArtifact("id", "", 10, hash) }
        assertFailsWith<IllegalArgumentException> { KnownArtifact("id", "f", 0, hash) }
        assertFailsWith<IllegalArgumentException> { KnownArtifact("id", "f", 10, hash.uppercase()) }
        assertFailsWith<IllegalArgumentException> { KnownArtifact("id", "f", 10, hash.dropLast(1)) }
    }
}
