package com.yuan3271.cloudrift.input

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.KeyEvent

/**
 * The only place that talks to the host editor. Everything above it works in terms of
 * "show this composing text" and "commit this string", which keeps the keyboard logic free
 * of InputConnection quirks.
 */
class EditorProxy(private val connectionProvider: () -> InputConnection?) {

    /** True while a composing region is present, i.e. the host is showing our buffer. */
    var isComposing: Boolean = false
        private set

    private val connection: InputConnection? get() = connectionProvider()

    fun setComposing(text: String) {
        if (text.isEmpty()) return
        connection?.setComposingText(text, 1)
        isComposing = true
    }

    fun commit(text: String) {
        if (text.isEmpty()) return
        connection?.commitText(text, 1)
        isComposing = false
    }

    /** Drops the composing region without inserting anything. */
    fun clearComposing() {
        if (!isComposing) return
        connection?.finishComposingText()
        isComposing = false
    }

    /** Deletes text to the left of the cursor. */
    fun deleteSurroundingBefore(length: Int = 1) {
        if (length <= 0) return
        connection?.deleteSurroundingText(length, 0)
    }

    /**
     * A real backspace key press. [deleteSurroundingBefore] only ever removes text next to the
     * caret, and most editors ignore it while the user has a selection - pressing the key is what
     * makes them delete the selection, which is how every keyboard handles a selected range.
     */
    fun sendBackspaceKey() {
        val connection = connection ?: return
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
    }

    /**
     * The text just before the caret, or null when the editor will not say. Used to check that a
     * commit is still where we left it before a backspace takes the whole of it back.
     */
    fun textBefore(length: Int): String? {
        if (length <= 0) return null
        return connection?.getTextBeforeCursor(length, 0)?.toString()
    }

    fun newline() {
        isComposing = false
        connection?.commitText("\n", 1)
    }

    /**
     * Moves the cursor by [delta] characters. Used by the space bar drag gesture.
     *
     * [InputConnection.getExtractedText] is the accurate route but some editors refuse it, in
     * which case committing an empty string with a relative cursor position still moves the caret.
     */
    fun moveCursor(delta: Int) {
        if (delta == 0) return
        val connection = connection ?: return
        val request = ExtractedTextRequest().apply {
            flags = 0
            hintMaxChars = 10_000
            hintMaxLines = 10
        }
        val extracted = runCatching { connection.getExtractedText(request, 0) }.getOrNull()
        val text = extracted?.text
        if (extracted != null && text != null) {
            val anchor = if (delta > 0) extracted.selectionEnd else extracted.selectionStart
            val target = (anchor + delta).coerceIn(0, text.length)
            connection.setSelection(target, target)
        } else {
            connection.commitText("", if (delta > 0) delta + 1 else delta)
        }
    }

    /**
     * Sends the editor's own action when it declares one (search, send, next...), otherwise
     * inserts a newline so the key never becomes a no-op.
     */
    fun performEditorAction(info: EditorInfo?) {
        isComposing = false
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val connection = connection
        if (connection == null || action == EditorInfo.IME_ACTION_NONE ||
            action == EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            newline()
            return
        }
        if (!connection.performEditorAction(action)) newline()
    }

    companion object {
        fun isTextField(info: EditorInfo?): Boolean {
            val type = info?.inputType ?: return false
            return when (type and android.text.InputType.TYPE_MASK_CLASS) {
                android.text.InputType.TYPE_CLASS_TEXT,
                android.text.InputType.TYPE_CLASS_NUMBER,
                android.text.InputType.TYPE_CLASS_PHONE,
                android.text.InputType.TYPE_CLASS_DATETIME,
                -> true

                else -> false
            }
        }

        /** True when the editor wants the first letter of a sentence capitalised. */
        fun shouldCapitalize(info: EditorInfo?): Boolean {
            val type = info?.inputType ?: return false
            if (type and android.text.InputType.TYPE_MASK_CLASS != android.text.InputType.TYPE_CLASS_TEXT) {
                return false
            }
            val flags = type and android.text.InputType.TYPE_MASK_FLAGS
            return flags and android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 ||
                flags and android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0
        }
    }
}
