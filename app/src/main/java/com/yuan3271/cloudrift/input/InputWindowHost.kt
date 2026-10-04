package com.yuan3271.cloudrift.input

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
     * 键鼠兼容面板里的**工具面板**是另一个窗口（候选词面板是输入法自己的那个窗口）。
     *
     * @param xPercent / yPercent 面板左上角在屏幕上的位置（0..100）；负数表示"用户还没拖过"，
     *   由服务放在默认位置（底部居中）。
     * @return true 表示这个窗口真的开出来了。开不出来（个别 ROM 不允许输入法开第二个窗口）时返回
     *   false，界面会把工具面板画进候选词那个窗口里——功能一件不少，只是不能各拖各的。
     */
    fun applyToolbarWindow(visible: Boolean, xPercent: Int, yPercent: Int): Boolean

    /** Drag delta for the tool panel's grip, in pixels. */
    fun moveToolbarWindowBy(dx: Float, dy: Float)

    /** The drag ended: persist where it was left. */
    fun commitToolbarWindowPosition()

    /**
     * 候选词面板要不要跟着光标走。
     *
     * true 时服务开始向编辑器要 CursorAnchorInfo（`requestCursorUpdates`），拿到锚点就把候选词
     * 那个窗口摆到光标下面；false / 拿不到锚点时仍旧贴在屏幕底部。
     */
    fun setCaretFollowing(enabled: Boolean)
}
