package dev.loi.core.validate

import dev.loi.core.errors.LoiException
import dev.loi.core.errors.LoiException.Input.Reason

/** Input text rules, from forma `validInferenceText`. */
internal object InputRules {
    /** Returns [text] if it is valid. The limit counts UTF-16 characters. */
    fun check(text: String, maxCharacters: Int): String {
        require(maxCharacters > 0) { "The character limit must be positive." }
        if (text.isBlank()) throw LoiException.Input(Reason.BLANK)
        if (text.length > maxCharacters) throw LoiException.Input(Reason.TOO_LONG)
        var i = 0
        while (i < text.length) {
            val c = text[i++]
            when {
                c == '\u0000' -> throw LoiException.Input(Reason.NUL)
                c.isHighSurrogate() -> if (i == text.length || !text[i++].isLowSurrogate()) {
                    throw LoiException.Input(Reason.UNPAIRED_SURROGATE)
                }
                c.isLowSurrogate() -> throw LoiException.Input(Reason.UNPAIRED_SURROGATE)
            }
        }
        return text
    }
}
