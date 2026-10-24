package com.yuan3271.cloudrift.ime

/**
 * Pure helpers for editing the reading buffer while the editor's caret is inside the composing
 * region. Keeping this arithmetic out of [ImeController] makes the user-visible rule testable:
 * moving inside a composition edits it, it never means "accept the first candidate".
 */
internal data class EditedReading(val text: String, val caret: Int)

/**
 * The caret's character offset from the start of the composing region, or null when the selection
 * is not a plain caret inside that region.
 */
internal fun compositionCaretOffset(
    selectionStart: Int,
    selectionEnd: Int,
    composingStart: Int,
    composingEnd: Int,
    previewLength: Int,
): Int? {
    if (selectionStart != selectionEnd) return null
    if (composingStart < 0 || composingEnd < composingStart) return null
    if (selectionStart !in composingStart..composingEnd) return null
    val offset = selectionStart - composingStart
    return offset.takeIf { it <= previewLength }
}

/** Inserts [insert] at [caret], defaulting to the end when there is no live editing caret. */
internal fun insertReading(raw: String, caret: Int?, insert: String): EditedReading {
    val at = caret?.coerceIn(0, raw.length) ?: raw.length
    return EditedReading(
        text = raw.substring(0, at) + insert + raw.substring(at),
        caret = at + insert.length,
    )
}

/**
 * Removes the character immediately before [caret]. With no editing caret this is the traditional
 * end-of-buffer backspace.
 */
internal fun backspaceReading(raw: String, caret: Int?): EditedReading {
    if (raw.isEmpty()) return EditedReading("", 0)
    val at = (caret ?: raw.length).coerceIn(0, raw.length)
    // At the very start of the composing region there is no reading character before the caret
    // to remove. In particular, do not wrap around and eat the first letter.
    if (at == 0) return EditedReading(raw, 0)
    return EditedReading(
        text = raw.removeRange(at - 1, at),
        caret = at - 1,
    )
}

/** One left/right arrow step inside a raw composition, clamped to its ends. */
internal fun movedReadingCaret(raw: String, caret: Int?, delta: Int): Int =
    ((caret ?: raw.length) + delta).coerceIn(0, raw.length)
