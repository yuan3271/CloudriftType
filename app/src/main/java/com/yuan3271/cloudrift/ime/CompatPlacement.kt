package com.yuan3271.cloudrift.ime

/**
 * 键鼠兼容面板的候选词那一块该摆在哪儿。
 *
 * 两块面板各有各的窗口、各自的默认位置（见 `CloudriftImeService.placeCompatWindow`），所以这块
 * 算术单独放在这里：它决定的是用户眼睛看到的那件事——"候选在光标的下面""符号页压在工具栏上
 * 面"，用**屏幕像素**说话，好按用户的原话写用例（`CompatPlacementTest`）。
 *
 * 位置一律是窗口左上角在屏幕上的坐标。窗口里那圈 10dp / 6dp 的透明边距是卡片自己的事，这里
 * 不管。
 */

/** 一块面板的落点（屏幕像素，左上角）。 */
internal data class PanelPosition(val x: Float, val y: Float)

/**
 * 候选那一排跟随光标时的横向落点：**光标在屏幕的哪一段，就往哪边对齐**（用户点名"具体左下、
 * 中下、右下看情况"）。
 *
 * 为什么要分三段，而不是一律"从光标往右铺"：候选词是一排长短不一的字，光标贴到屏幕右边时
 * 往右铺会直接顶出屏幕，然后被夹回来——看起来就是"面板跑到别处去了，和光标没关系"。贴着右边
 * 的光标要让面板往**左**铺（右下），左半屏的光标才往右铺（左下），中间两样都放得下、居中最好看
 * （中下）。
 *
 * @param gapPx 面板边缘与光标让开的距离。负数表示压过光标一点点，让光标那一列落在面板里，
 *   而不是紧贴着面板外缘。
 */
internal fun caretPanelX(
    caretX: Float,
    panelWidth: Float,
    screenWidth: Float,
    gapPx: Float,
): Float {
    val screen = screenWidth.coerceAtLeast(1f)
    val raw = when {
        // 左下：光标在左三分之一，面板左缘对着光标往右铺。
        caretX <= screen / LEFT_ZONE_DIVISOR -> caretX + gapPx
        // 右下：光标在右三分之一，面板右缘对着光标往左铺。
        caretX >= screen * RIGHT_ZONE_FRACTION -> caretX - panelWidth - gapPx
        // 中下：两边都放得下，面板对着光标居中。
        else -> caretX - panelWidth / 2f
    }
    return clampToScreen(raw, panelWidth, screen)
}

/**
 * 候选那一排跟随光标时的落点：默认**在光标下面**，下面放不下（光标已经贴着屏幕底部）才翻到
 * 光标上面那一行。
 */
internal fun panelBelowCaret(
    caretX: Float,
    caretY: Float,
    panelWidth: Float,
    panelHeight: Float,
    screenWidth: Float,
    screenBottomLimit: Float,
    gapX: Float,
    gapY: Float,
    lineHeight: Float,
): PanelPosition {
    val x = caretPanelX(caretX, panelWidth, screenWidth, gapX)
    val below = caretY + gapY
    val above = caretY - panelHeight - lineHeight
    val flipped = panelHeight > 0f && below + panelHeight > screenBottomLimit
    val y = (if (flipped) above else below).coerceIn(
        0f,
        (screenBottomLimit - panelHeight).coerceAtLeast(0f),
    )
    return PanelPosition(x, y)
}

/**
 * 从工具栏里弹出来的那一块（符号页 / 数字页 / 语音 / 剪贴板）的落点：**压在工具面板上面**
 * （用户点名"默认在工具栏的上面"）。
 *
 * 横向对着工具面板居中：弹出的一层通常比工具栏宽，居中最不容易在窄屏上歪到一边去。
 */
internal fun panelAboveToolbar(
    toolbarX: Float,
    toolbarY: Float,
    toolbarWidth: Float,
    panelWidth: Float,
    panelHeight: Float,
    screenWidth: Float,
    screenBottomLimit: Float,
    gapPx: Float,
): PanelPosition {
    val centered = toolbarX + toolbarWidth / 2f - panelWidth / 2f
    val x = clampToScreen(centered, panelWidth, screenWidth.coerceAtLeast(1f))
    val y = (toolbarY - panelHeight - gapPx).coerceIn(
        0f,
        (screenBottomLimit - panelHeight).coerceAtLeast(0f),
    )
    return PanelPosition(x, y)
}

/** 夹进屏幕：面板左右都不该探出去。 */
private fun clampToScreen(x: Float, panelWidth: Float, screenWidth: Float): Float =
    x.coerceIn(0f, (screenWidth - panelWidth).coerceAtLeast(0f))

/** 左三分之一 / 右三分之一的分界：光标落在这两段里就贴边对齐，中间那段居中。 */
private const val LEFT_ZONE_DIVISOR = 3f
private const val RIGHT_ZONE_FRACTION = 2f / 3f
