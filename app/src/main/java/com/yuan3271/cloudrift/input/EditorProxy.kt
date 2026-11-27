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
class EditorProxy(
    /**
     * 发一次真正的退格按键。默认是 KeyEvent DEL 的按下 + 抬起；单元测试换成一个记录器，
     * 这样"编辑器不吃任何删除接口时退回按键"这条路径也能被验证。
     *
     * 放在第一个参数，是为了让 connectionProvider 留在最后：调用方仍然可以写
     * EditorProxy { service.currentInputConnection } 这种尾随 lambda。
     */
    private val backspaceKeySender: (InputConnection) -> Unit = { connection ->
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
    },
    private val connectionProvider: () -> InputConnection?,
) {

    /** True while a composing region is present, i.e. the host is showing our buffer. */
    var isComposing: Boolean = false
        private set

    private val connection: InputConnection? get() = connectionProvider()

    /**
     * @param cursorOffset where the caret should land inside [text]. The default puts it after the
     *   whole preview; editing in the middle of an existing composition passes the edited offset.
     */
    fun setComposing(text: String, cursorOffset: Int = text.length) {
        if (text.isEmpty()) return
        val connection = connection ?: return
        val offset = cursorOffset.coerceIn(0, text.length)
        if (offset == text.length) {
            // The normal path is also the one every editor is required to support: cursor position
            // 1 means immediately after the complete composing text. Do not ask for ExtractedText
            // or issue a second setSelection here. Some browser, WebView and custom editors return
            // an incomplete selection snapshot while a composition is being updated; interpreting
            // it as an absolute editor offset moved the caret to the beginning of the field.
            connection.setComposingText(text, 1)
        } else {
            // InputConnection cannot place the caret inside brand-new composing text, so an absolute
            // selection is unavoidable when the user is editing in the middle. Keep both writes in
            // one batch so compliant editors deliver one final selection update rather than one from
            // the composing replacement and another from setSelection.
            connection.beginBatchEdit()
            try {
                connection.setComposingText(text, 1)
                val selectionEnd = extractedSelectionEnd(connection)
                val target = composingSelectionTarget(
                    selectionEndAfterComposing = selectionEnd,
                    textLength = text.length,
                    cursorOffset = offset,
                )
                target?.let { connection.setSelection(it, it) }
            } finally {
                connection.endBatchEdit()
            }
        }
        isComposing = true
    }

    private fun extractedSelectionEnd(connection: InputConnection): Int? = runCatching {
        val request = ExtractedTextRequest().apply {
            flags = 0
            hintMaxChars = 10_000
            hintMaxLines = 10
        }
        connection.getExtractedText(request, 0)?.selectionEnd
    }.getOrNull()

    fun commit(text: String) {
        if (text.isEmpty()) return
        connection?.commitText(text, 1)
        isComposing = false
    }

    /**
     * 丢掉正在显示的上屏区，**连同它的文字一起**。
     *
     * 这里原来用 `finishComposingText()`，而它的语义是"把上屏区原地定稿、文字留下"。上屏区在
     * 这个输入法里始终只是"当前缓冲的预览"（中文模式下就是那串还没成字的拼音字母），从来没有
     * 该留下的时候——于是缓冲从 1 个字清到 0 时，最后一个字母会留在输入框里不动：退格看着
     * 像没反应。先把上屏区替换成空串（真的删掉），再收尾结束这次组合。
     */
    fun clearComposing() {
        if (!isComposing) return
        val connection = connection
        connection?.setComposingText("", 1)
        connection?.finishComposingText()
        isComposing = false
    }

    /** Deletes text to the left of the cursor. */
    fun deleteSurroundingBefore(length: Int = 1) {
        if (length <= 0) return
        connection?.deleteSurroundingText(length, 0)
    }

    /**
     * 删掉光标左边"用户眼里的一个字"，并在编辑器不配合时逐级退回。
     *
     * 调用前提：没有上屏区、没有选区（有选区走 [deleteSelection]，有读音缓冲走输入法自己的
     * 缓冲删除）。
     *
     * 顺序：
     * 1. 读光标前的一小段文本与"内容末端位置"，找到末尾那个字素簇；
     * 2. deleteSurroundingTextInCodePoints：官方按码点删的接口，emoji 不会被劈成半个；
     * 3. 编辑器不认它（内容没变短）就退回 deleteSurroundingText（按 UTF-16 单元）——
     *    不少定制输入框只实现了老的那一个；
     * 4. 还不认，发一次真正的退格按键：WebView、自绘输入框、只处理 onKeyDown 的控件往往
     *    只认按键；
     * 5. 连文本都读不到的编辑器，直接从按键开始——读不到就没法验证，按键是覆盖面最广的路。
     *
     * 以前这里固定按一个 UTF-16 单元删、并且只认一条接口：emoji 会删成半个，不实现那条
     * 接口的应用里按了没反应。
     */
    fun deleteBeforeCaret() {
        val connection = connection ?: return
        val before = snapshot(connection)
        if (before == null || before.before.isEmpty()) {
            // 读不到、或者光标前没有字：按键不会更糟（它同时也会让编辑器自己处理选区与上屏区）。
            backspaceKeySender(connection)
            return
        }
        val codePoints = Graphemes.lastClusterCodePoints(before.before)
        val units = Graphemes.lastCluster(before.before)?.utf16Length ?: 1
        if (deleteInCodePoints(connection, codePoints, before)) return
        if (deleteInUnits(connection, units, before)) return
        backspaceKeySender(connection)
    }

    /**
     * 编辑器此刻的选中文本。null 表示编辑器没告诉我们（读不到），空串表示没有选区。
     *
     * 与 [hasLiveSelection] 的区别就是 null：选区判断要区分"没有选中"和"问不出来"——
     * 前者该按字符退格，后者要退回缓存的那个标志。
     */
    fun selectedTextOrNull(): String? = runCatching {
        connection?.getSelectedText(0)?.toString()
    }.getOrNull()

    /** 光标前的一段文本 + 内容末端位置，用来判断一次删除有没有真的发生。 */
    private class Snapshot(val before: String, val extent: Int)

    private fun snapshot(connection: InputConnection, window: Int = DELETE_WINDOW): Snapshot? =
        runCatching {
            val request = ExtractedTextRequest().apply {
                flags = 0
                hintMaxChars = window
                hintMaxLines = 4
            }
            val extracted = connection.getExtractedText(request, 0) ?: return@runCatching null
            val text = extracted.text?.toString() ?: return@runCatching null
            val end = extracted.selectionEnd.coerceIn(0, text.length)
            val start = (end - window).coerceAtLeast(0)
            // startOffset + 窗口长度 = 内容末端。删除发生在光标左边时这两个数加起来一定变小
            // （窗口左移一格，或者窗口本身变短），比"再读一遍光标前的文本"可靠：整段都是同一个
            // 字（aaaaaaaa）时，后者前后一模一样，会把删成功误判成没反应、再删两遍。
            Snapshot(
                before = text.substring(start, end),
                extent = extracted.startOffset + text.length,
            )
        }.getOrNull()

    /**
     * 这次删除有没有真的发生。
     *
     * 两个信号取并集：内容末端变短（窗口跟着左移的那种编辑器），或者光标前的文本变了
     * （只返回固定开头一段、末端数字不动的那种编辑器）。只看其中一个都会在某一类编辑器里
     * 把"删成功了"误判成"没反应"，然后多删两次。
     */
    private fun deletionHappened(connection: InputConnection, previous: Snapshot): Boolean {
        val now = snapshot(connection) ?: return false
        return now.extent < previous.extent || now.before != previous.before
    }

    private fun deleteInCodePoints(
        connection: InputConnection,
        count: Int,
        previous: Snapshot,
    ): Boolean {
        if (count <= 0) return false
        runCatching { connection.deleteSurroundingTextInCodePoints(count, 0) }
        return deletionHappened(connection, previous)
    }

    private fun deleteInUnits(
        connection: InputConnection,
        units: Int,
        previous: Snapshot,
    ): Boolean {
        if (units <= 0) return false
        runCatching { connection.deleteSurroundingText(units, 0) }
        return deletionHappened(connection, previous)
    }

    // ---- 编辑器自己的编辑动作（外接键盘的 ⌘C / ⌘V / ⌘X / ⌘A）--------------------------
    //
    // 输入法**不该**自己去读写剪贴板：那拿不到别的应用选中的内容、要额外的权限，也绕过了编辑器
    // 自己的撤销栈。`performContextMenuAction` 是官方给输入法留的口子，等于替用户点了长按菜单里
    // 那一项；编辑器不支持时返回 false，调用方当作"这颗键没接住"。

    fun copySelection() = contextMenuAction(android.R.id.copy)
    fun cutSelection() = contextMenuAction(android.R.id.cut)
    fun pasteFromClipboard() = contextMenuAction(android.R.id.paste)
    fun selectAllText() = contextMenuAction(android.R.id.selectAll)

    private fun contextMenuAction(id: Int): Boolean =
        runCatching { connection?.performContextMenuAction(id) == true }.getOrDefault(false)

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
        backspaceKeySender(connection)
    }

    /**
     * Empties the editor: select everything, then press delete. Going through the editor's own
     * select-all action is what makes this work in fields that ignore a large
     * [deleteSurroundingBefore] - which is most of them.
     */
    fun clearAll() {
        val connection = connection ?: return
        connection.performContextMenuAction(android.R.id.selectAll)
        val selected = selectedTextOrNull()
        if (selected == null || selected.isNotEmpty()) {
            // 选中了（或编辑器不报选区）：按键会删掉整段选区。
            backspaceKeySender(connection)
            return
        }
        // 编辑器不吃「全选」：退回到在光标两侧尽量多删。数字远大于正常文本，编辑器会自己夹到
        // 可删的范围——总比「清空」只删掉一个字强。
        runCatching { connection.deleteSurroundingText(CLEAR_ALL_CHARS, CLEAR_ALL_CHARS) }
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
        /**
         * 读光标前多少字符来找最后一个字素簇。32 个 UTF-16 单元足够覆盖一个 ZWJ emoji 序列，
         * 又不会让每次退格都去要一大段文本。
         */
        private const val DELETE_WINDOW = 32

        /** 「清空」退路一次请求删掉的长度：远大于正常文本，编辑器自己会夹紧。 */
        private const val CLEAR_ALL_CHARS = 100_000

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

/**
 * Absolute caret position after the host has placed the composing caret at the end:
 * `nhao` with the caret after `ni` => composing end 14, length 5, offset 2 => 11.
 */
internal fun composingSelectionTarget(
    selectionEndAfterComposing: Int?,
    textLength: Int,
    cursorOffset: Int,
): Int? {
    val end = selectionEndAfterComposing ?: return null
    val start = (end - textLength).coerceAtLeast(0)
    return (start + cursorOffset.coerceIn(0, textLength)).coerceAtMost(end)
}
