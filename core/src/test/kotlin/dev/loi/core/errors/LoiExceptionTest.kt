package dev.loi.core.errors

import dev.loi.core.validate.InputRules
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LoiExceptionTest {
    private fun all(cause: Throwable?): List<LoiException> =
        LoiException.ModelFile.Reason.entries.map { LoiException.ModelFile(it, cause) } +
            LoiException.Verification.Reason.entries.map { LoiException.Verification(it) } +
            LoiException.Input.Reason.entries.map { LoiException.Input(it) } +
            LoiException.Output.Reason.entries.map { LoiException.Output(it, cause) } +
            listOf(LoiException.Load(cause), LoiException.Closed(), LoiException.Resource(cause))

    @Test fun messagesAreFixedText() {
        val secret = "private note text /data/user/0/app/model.litertlm"
        for (error in all(RuntimeException(secret))) {
            val message = error.message ?: ""
            assertTrue(message.isNotBlank(), "${error::class.simpleName} has no message")
            assertFalse(secret in message || "/data/" in message, "${error::class.simpleName} leaks the cause")
        }
    }

    @Test fun inputRuleMessagesDoNotContainTheInput() {
        val input = "my private knee note" + "x".repeat(200)
        val error = runCatching { InputRules.check(input, 10) }.exceptionOrNull() as LoiException.Input
        assertFalse("private" in (error.message ?: ""))
    }

    @Test fun theEngineFailureIsKeptAsCause() {
        val cause = IllegalStateException("sdk")
        assertSame(cause, LoiException.Load(cause).cause)
        assertSame(cause, LoiException.Resource(cause).cause)
    }
}
