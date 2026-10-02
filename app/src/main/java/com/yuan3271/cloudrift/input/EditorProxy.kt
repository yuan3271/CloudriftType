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
     * 编辑器**此刻**是否有一段被选中的文本。
     *
     * 不能用服务回调里缓存的那个标志：`onUpdateSelection` 不是每次都送到，用户在输入框里拖选、
     * 或者上一次选中被编辑器自己改掉之后，缓存值就会和真实状态对不上——这正是"按退格时而能删
     * 时而不能"的来源。`getSelectedText` 是当场问编辑器，最可靠。
     */
    fun hasLiveSelection(): Boolean = runCatching {
        connection?.getSelectedText(0)?.isNotEmpty() == true
    }.getOrDefault(false)

    /**
     * 光标左边那 [length] 个字符，编辑器不肯说时返回 null。
     *
     * 用途只有一个：确认一条联想候选还配不配得上当前光标。联想条说的是"刚才上屏的那个词
     * 之后接什么"，一旦光标被挪到别的地方，那条联想就是在替一个不相干的词做预测。
     */
    fun textBeforeCaret(length: Int): String? = runCatching {
        if (length <= 0) return@runCatching ""
        val request = ExtractedTextRequest().apply {
            flags = 0
            hintMaxChars = 10_000
            hintMaxLines = 10
        }
        val extracted = connection?.getExtractedText(request, 0) ?: return@runCatching null
        val text = extracted.text?.toString() ?: return@runCatching null
        val end = extracted.selectionEnd.coerceIn(0, text.length)
        val start = (end - length).coerceAtLeast(0)
        text.substring(start, end)
    }.getOrNull()

    /**
     * 删掉当前选中的一段。`commitText("")` 会把选区替换成空串，这是各编辑器都认的做法
     * （`deleteSurroundingText` 在有选区时基本会被忽略）。
     */
    fun deleteSelection(): Boolean = runCatching {
        if (connection?.commitText("", 1) == true) true else false
    }.getOrDefault(false)

    /** 选中的那段文字；没有选区时返回 null。 */
    fun selectedText(): String? = runCatching {
        connection?.getSelectedText(0)?.toString()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * 把光标左边 [length] 个字符换成 [text]。
     *
     * 走"先选中、再输入"这条路：`commitText` 在多数编辑器里就是"替换当前选区"，这也正是
     * 选中文字时按键盘输入该有的行为。
     */
    fun replaceBeforeCaret(length: Int, text: String): Boolean {
        if (length <= 0) return false
        val connection = connection ?: return false
        val request = ExtractedTextRequest().apply {
            flags = 0
            hintMaxChars = 10_000
            hintMaxLines = 10
        }
        val extracted = runCatching { connection.getExtractedText(request, 0) }.getOrNull()
            ?: return false
        val end = extracted.selectionEnd
        val start = end - length
        if (start < 0) return false
        val selected = runCatching { connection.setSelection(start, end) }.getOrDefault(false)
        if (!selected) return false
        return runCatching { connection.commitText(text, 1) }.getOrDefault(false)
    }

    /** 把当前选中的一段换成 [text]。 */
    fun replaceSelection(text: String): Boolean = runCatching {
        connection?.commitText(text, 1) == true
    }.getOrDefault(false)

    /**
     * 成对输入：插入 [open][close]，然后把光标退回两个符号**中间**。
     *
     * 位置不是猜的：插完再问编辑器当前光标在哪，比"假设它在 close 之后"稳——有些编辑器会把
     * 光标放在插入点之前，或者顺手把整段选中。
     */
    fun insertPair(open: String, close: String) {
        val connection = connection ?: return
        if (!connection.commitText(open + close, 1)) return
        val end = runCatching {
            connection.getExtractedText(ExtractedTextRequest(), 0)?.selectionEnd
        }.getOrNull() ?: return
        runCatching { connection.setSelection(end - close.length, end - close.length) }
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
     * Empties the editor: select everything, then press delete. Going through the editor's own
     * select-all action is what makes this work in fields that ignore a large
     * [deleteSurroundingBefore] - which is most of them.
     */
    fun clearAll() {
        val connection = connection ?: return
        connection.performContextMenuAction(android.R.id.selectAll)
        sendBackspaceKey()
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
