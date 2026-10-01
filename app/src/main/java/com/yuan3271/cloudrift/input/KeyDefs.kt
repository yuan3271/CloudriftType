package com.yuan3271.cloudrift.input

/** Everything the controller needs to know about a key press. */
enum class KeyCode {
    /** Inserts [KeyDef.output] into the reading buffer (or straight into the editor). */
    Char,

    /** Same as [Char] but the text is committed immediately, bypassing composition. */
    Text,

    Shift,
    Backspace,
    Enter,
    Space,
    Language,
    Symbols,
    Numbers,
    Letters,
    Voice,
    Settings,
    HideKeyboard,
    CandidateNext,

    /** A layout spacer. Occupies weight, draws nothing and types nothing. */
    None,
}

/** Visual weight so the canvas can style variants without knowing key semantics. */
enum class KeyStyle {
    /** Letter or kana key: the primary surface. */
    Primary,

    /** Modifier key: a container tint. */
    Modifier,

    /** Destructive or attention key: backspace, enter. */
    Accent,

    /** The space bar. */
    Space,
}

/**
 * @param label what is drawn on the key. Defaults to [output].
 * @param output the text the key inserts.
 * @param alternates offered by a long press, in popup order.
 * @param repeatable keys fire immediately on press down and then repeat while held.
 * @param badge small secondary glyph in the key's corner, e.g. the digit on a nine key pad.
 * @param caption small glyph centred under the label, e.g. the letters on a phone dial pad.
 * @param swipeUp text inserted when the key is swiped upward.
 */
data class KeyDef(
    val code: KeyCode,
    val label: String = "",
    val output: String = "",
    val alternates: List<String> = emptyList(),
    val badge: String = "",
    val caption: String = "",
    val swipeUp: String = "",
    val weight: Float = 1f,
    val style: KeyStyle = KeyStyle.Primary,
    val repeatable: Boolean = false,
    /** Draw [label] with the large style even though there is no caption under it. */
    val largeLabel: Boolean = false,
    val action: String = "",
) {
    val display: String get() = label.ifEmpty { output }

    companion object {
        fun char(
            output: String,
            label: String = output,
            alternates: List<String> = emptyList(),
            badge: String = "",
            caption: String = "",
            swipeUp: String = "",
            weight: Float = 1f,
        ) = KeyDef(
            code = KeyCode.Char,
            label = label,
            output = output,
            alternates = alternates,
            badge = badge,
            caption = caption,
            swipeUp = swipeUp,
            weight = weight,
        )

        fun immediate(
            output: String,
            label: String = output,
            style: KeyStyle = KeyStyle.Primary,
            weight: Float = 1f,
            caption: String = "",
            largeLabel: Boolean = false,
        ) = KeyDef(
            code = KeyCode.Text,
            label = label,
            output = output,
            weight = weight,
            style = style,
            caption = caption,
            largeLabel = largeLabel,
        )

        fun modifier(code: KeyCode, label: String = "", weight: Float = 1.5f) =
            KeyDef(code = code, label = label, weight = weight, style = KeyStyle.Modifier)

        val backspace = KeyDef(
            code = KeyCode.Backspace,
            weight = 1.5f,
            style = KeyStyle.Modifier,
            repeatable = true,
        )

        val enter = KeyDef(code = KeyCode.Enter, weight = 1.5f, style = KeyStyle.Accent)

        val space = KeyDef(code = KeyCode.Space, weight = 4f, style = KeyStyle.Space)

        val shift = KeyDef(code = KeyCode.Shift, weight = 1.5f, style = KeyStyle.Modifier)

        /**
         * Half a key of empty space. Used to centre a row (the nine letter row of a QWERTY
         * keyboard) without stretching the keys in it.
         */
        fun spacer(weight: Float = 0.5f) = KeyDef(code = KeyCode.None, weight = weight)
    }
}
