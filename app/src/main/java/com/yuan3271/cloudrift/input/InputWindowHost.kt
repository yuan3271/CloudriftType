package com.yuan3271.cloudrift.input

/**
 * 键鼠兼容面板里的两个窗口。
 *
 * 候选词面板（[Content]）与工具面板（[Toolbar]）各自是一个窗口：各有各的位置、各按自己的内容
 * 撑开——用户点名的"两窗分离"。窗口开不出来的机器上（没有「显示在其他应用上层」，或者 ROM 不
 * 给）退回把两块画进输入法窗口里，功能一件不少，只是不能各拖各的。
 */
enum class CompatPanelKind {
    /** 候选词面板，以及它展开的第二层（语音 / 剪贴板 / 更多候选 / 符号页 / 数字页）。 */
    Content,

    /** 工具面板：语言 / 符号 / 语音 / 剪贴板。 */
    Toolbar,
}

/**
 * The part of the input method window the keyboard UI is allowed to steer.
 *
 * The composables decide *what* frame the keyboard should have (they are the ones that know the
 * orientation and the user's setting) and the service applies it, because only the service owns the
 * window. Keeping it behind an interface also means the controller stays testable without a window.
 */
interface InputWindowHost {

    /**
     * @param floating a smaller card instead of the full width keyboard.
     * @param widthPercent width of the floating card, as a percentage of the screen.
     */
    fun applyInputFrame(floating: Boolean, widthPercent: Int)

    /** Moves the floating keyboard by a drag delta, in pixels. */
    fun moveInputWindowBy(dx: Float, dy: Float)

    /**
     * 键鼠兼容面板的一块该不该出现、在哪儿。两块面板各是一个窗口，所以这里按 [kind] 分别调用。
     *
     * 候选词面板的位置由服务自己算（跟着光标，见 [setCaretFollowing]），此处的 xPercent /
     * yPercent 只对工具面板这类"用户拖到哪就停在哪"的面板有意义。
     *
     * @param visible false 只是先收起来，窗口仍然留着：候选词面板在键鼠模式下就是"打字才出现、
     *   打完就收起"，每次重新 addView 会让它出现得慢半拍。
     * @param xPercent / yPercent 面板左上角在屏幕上的位置（0..100）；负数表示"用户还没拖过"，
     *   由服务放在默认位置（底部居中）。
     * @return true 表示这个窗口真的开出来了（或本来就开着）。两块里有一块开不出来，界面就把两块
     *   都画进输入法窗口里——宁可少一点自由，也不能让工具面板整个不见。
     */
    fun applyCompatPanel(
        kind: CompatPanelKind,
        visible: Boolean,
        xPercent: Int,
        yPercent: Int,
    ): Boolean

    /** Drag delta for a panel's grip, in pixels. */
    fun moveCompatPanelBy(kind: CompatPanelKind, dx: Float, dy: Float)

    /** The drag ended: persist where it was left. */
    fun commitCompatPanelPosition(kind: CompatPanelKind)

    /** 键鼠兼容模式关掉了：把两个窗口都拆掉（下次进这条模式再重建）。 */
    fun removeCompatPanels()

    /**
     * 候选词面板要不要跟着光标走。
     *
     * true 时服务开始向编辑器要 CursorAnchorInfo（`requestCursorUpdates`），拿到锚点就把候选词
     * 那个窗口摆到光标下面；false / 拿不到锚点时仍旧贴在屏幕底部。跟随期间光标每动一次都要重摆
     * 一次窗口，所以这条链路上不做任何多余的活。
     *
     * **每次同步都要重新订阅**：`CURSOR_UPDATE_MONITOR` 是挂在当前 `InputConnection` 上的，
     * 换一个输入框就是换一条连接，旧订阅也随之作废（见 CloudriftImeService.setCaretFollowing）。
     */
    fun setCaretFollowing(enabled: Boolean)

    /**
     * 把两块面板上的**拖动偏移**清掉：候选词面板的拖动是"相对光标"的位置微调，只该活在这一次
     * 输入里——用户点名"即使被拖来拖去，下次输入时也依然要跟随光标"。
     */
    fun resetCompatPanelNudges()
}
