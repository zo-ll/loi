package dev.loi.core.validate

import dev.loi.core.errors.LoiException
import dev.loi.core.errors.LoiException.Input.Reason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InputRulesTest {
    private fun reason(text: String, max: Int = 100): Reason =
        assertFailsWith<LoiException.Input> { InputRules.check(text, max) }.reason

    @Test fun validTextPasses() {
        assertEquals("knee pain", InputRules.check("knee pain", 100))
        assertEquals("x".repeat(100), InputRules.check("x".repeat(100), 100))
    }

    @Test fun pairedSurrogatesPass() {
        val emoji = "squat 🏋"
        assertEquals(emoji, InputRules.check(emoji, 100))
    }

    @Test fun blankTextFails() {
        assertEquals(Reason.BLANK, reason(""))
        assertEquals(Reason.BLANK, reason(" \n\t "))
    }

    @Test fun longTextFails() {
        assertEquals(Reason.TOO_LONG, reason("x".repeat(101)))
    }

    @Test fun nulFails() {
        assertEquals(Reason.NUL, reason("a\u0000b"))
    }

    @Test fun unpairedHighSurrogateFails() {
        assertEquals(Reason.UNPAIRED_SURROGATE, reason("a\uD83C"))
        assertEquals(Reason.UNPAIRED_SURROGATE, reason("a\uD83Cb"))
    }

    @Test fun unpairedLowSurrogateFails() {
        assertEquals(Reason.UNPAIRED_SURROGATE, reason("a\uDFCBb"))
    }

    @Test fun nonPositiveLimitIsAProgrammerError() {
        assertFailsWith<IllegalArgumentException> { InputRules.check("a", 0) }
    }
}
