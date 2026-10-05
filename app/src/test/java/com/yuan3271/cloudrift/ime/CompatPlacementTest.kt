package com.yuan3271.cloudrift.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 键鼠兼容面板里候选词那一块的位置，锁的是用户点名的三件事：
 *
 * 1. 候选栏默认在**光标下面**（下面放不下才翻到上面一行）；
 * 2. 横向按光标所在的位置分**左下 / 中下 / 右下**，别让一排候选顶出屏幕；
 * 3. 从工具栏弹出来的那一块（符号页 / 语音 / 剪贴板）默认在**工具栏上面**。
 */
class CompatPlacementTest {

    /** 一台常见的手机竖屏：1080x2400 像素。 */
    private val screenWidth = 1080f
    private val screenHeight = 2400f

    /** 跟服务里那三个常量一样：往右下让一点、光标那一行的厚度。 */
    private val gapX = -16f
    private val gapY = 10f
    private val lineHeight = 8f

    private val panelWidth = 300f
    private val panelHeight = 60f

    private fun belowCaret(caretX: Float, caretY: Float = 600f) = panelBelowCaret(
        caretX = caretX,
        caretY = caretY,
        panelWidth = panelWidth,
        panelHeight = panelHeight,
        screenWidth = screenWidth,
        screenBottomLimit = screenHeight,
        gapX = gapX,
        gapY = gapY,
        lineHeight = lineHeight,
    )

    @Test
    fun `the panel sits below the caret`() {
        val position = belowCaret(caretX = 300f, caretY = 600f)

        assertEquals(600f + gapY, position.y, 0.01f)
    }

    @Test
    fun `a caret on the left of the screen puts the panel to its lower right`() {
        val position = belowCaret(caretX = 100f)

        assertEquals("左下：面板左缘对着光标往右铺", 100f + gapX, position.x, 0.01f)
    }

    @Test
    fun `a caret in the middle puts the panel centred under it`() {
        val position = belowCaret(caretX = screenWidth / 2f)

        assertEquals(
            "中下：面板对着光标居中",
            screenWidth / 2f - panelWidth / 2f,
            position.x,
            0.01f,
        )
    }

    @Test
    fun `a caret on the right of the screen puts the panel to its lower left`() {
        val position = belowCaret(caretX = screenWidth - 40f)

        assertEquals(
            "右下：面板右缘对着光标往左铺，整排候选才留在屏幕里",
            screenWidth - 40f - panelWidth - gapX,
            position.x,
            0.01f,
        )
        assertTrue("整块面板都在屏幕里", position.x + panelWidth <= screenWidth)
    }

    @Test
    fun `a panel wider than the screen never starts outside it`() {
        val position = panelBelowCaret(
            caretX = 900f,
            caretY = 600f,
            panelWidth = screenWidth + 200f,
            panelHeight = panelHeight,
            screenWidth = screenWidth,
            screenBottomLimit = screenHeight,
            gapX = gapX,
            gapY = gapY,
            lineHeight = lineHeight,
        )

        assertEquals(0f, position.x, 0.01f)
    }

    @Test
    fun `a caret near the bottom flips the panel to the line above it`() {
        val position = belowCaret(caretX = 500f, caretY = screenHeight - 12f)

        assertEquals(
            "下面放不下就翻到光标上面一行（再往上让开光标那一行的高度）",
            screenHeight - 12f - panelHeight - lineHeight,
            position.y,
            0.01f,
        )
    }

    @Test
    fun `a popup from the toolbar sits right above it`() {
        val toolbarX = 300f
        val toolbarY = 2210f
        val toolbarWidth = 480f
        val gap = 6f * 2.75f

        val position = panelAboveToolbar(
            toolbarX = toolbarX,
            toolbarY = toolbarY,
            toolbarWidth = toolbarWidth,
            panelWidth = 700f,
            panelHeight = 420f,
            screenWidth = screenWidth,
            screenBottomLimit = screenHeight,
            gapPx = gap,
        )

        assertEquals("压在工具栏上面", toolbarY - 420f - gap, position.y, 0.01f)
        assertEquals(
            "横向对着工具栏居中",
            toolbarX + toolbarWidth / 2f - 700f / 2f,
            position.x,
            0.01f,
        )
    }

    @Test
    fun `a toolbar pushed against the right edge keeps the popup on screen`() {
        val position = panelAboveToolbar(
            toolbarX = screenWidth - 480f,
            toolbarY = 2210f,
            toolbarWidth = 480f,
            panelWidth = 700f,
            panelHeight = 420f,
            screenWidth = screenWidth,
            screenBottomLimit = screenHeight,
            gapPx = 16f,
        )

        assertEquals(screenWidth - 700f, position.x, 0.01f)
        assertTrue("弹出层不探出屏幕", position.x >= 0f && position.x + 700f <= screenWidth)
    }
}
