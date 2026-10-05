package com.yuan3271.cloudrift.ime

import com.yuan3271.cloudrift.data.ClipEntry
import com.yuan3271.cloudrift.engine.Candidate
import com.yuan3271.cloudrift.input.ExternalInputSnapshot
import com.yuan3271.cloudrift.input.KeyboardPage
import com.yuan3271.cloudrift.voice.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 键鼠兼容模式读的那几个派生状态。用户报的两条 bug 都出在同一个状态上：**选完一个候选之后
 * 缓冲没有清空**，于是物理键盘的下一次空格 / 数字仍然被当成"还在拼写"，把同一个词又上屏一次。
 */
class ImeUiStateTest {

    private val composing = ImeUiState(
        raw = "nihao",
        preview = "你好",
        candidates = listOf(
            Candidate(text = "你好", consumed = 5),
            Candidate(text = "你", consumed = 2),
        ),
        candidatesExpanded = true,
        externalInputs = ExternalInputSnapshot(keyboard = true),
    )

    @Test
    fun `a committed candidate leaves nothing behind to commit again`() {
        val after = composing.clearedBuffer()

        assertFalse("选完候选就不再是拼写状态：下一次空格 / 数字不该再上屏一次", after.isComposing)
        assertEquals(emptyList<Candidate>(), after.candidates)
        assertFalse("展开的候选列表也跟着收掉", after.candidatesExpanded)
    }

    @Test
    fun `the candidate window collapses once the buffer is cleared`() {
        assertTrue("打字时候选栏才露出来", composing.compatContentVisible)
        assertFalse("选完就收起来，屏幕上不留一条空栏", composing.clearedBuffer().compatContentVisible)
    }

    @Test
    fun `the toolbar stays hidden until the user is actually typing`() {
        val idle = ImeUiState(externalInputs = ExternalInputSnapshot(keyboard = true))
        assertFalse("空闲时屏幕上什么都不留", idle.compatToolbarVisible)
        assertTrue("一开始打字，两块面板一起出现", composing.compatToolbarVisible)
        assertTrue(
            "符号页开着的时候工具栏不能跑：它正是从工具栏点开的",
            ImeUiState(
                page = KeyboardPage.Symbols,
                externalInputs = ExternalInputSnapshot(keyboard = true),
            ).compatToolbarVisible,
        )
    }

    @Test
    fun `a mouse-only setup keeps the toolbar on screen`() {
        val mouseOnly = ImeUiState(
            externalInputs = ExternalInputSnapshot(pointing = true, pointingName = "蓝牙鼠标"),
        )

        assertTrue("只有鼠标时工具栏是屏幕上唯一的入口，藏起来就没有键盘可用了", mouseOnly.compatToolbarVisible)
        assertFalse("没有键盘就没有正在打的字，候选那一块还是收着", mouseOnly.compatContentVisible)
    }

    @Test
    fun `fresh clipboard content does not summon anything`() {
        val copied = ImeUiState(
            externalInputs = ExternalInputSnapshot(keyboard = true),
            clipboardOffer = ClipEntry(text = "刚复制的一段话", at = 0L),
        )

        assertFalse("""
            有内容了也隐藏（用户点名）：键鼠模式下剪贴板只从工具栏的 📋 进，
            刚复制的东西不许自己把面板顶出来
        """.trimIndent(), copied.compatContentVisible)
        assertFalse(copied.compatToolbarVisible)
    }

    @Test
    fun `opening the clipboard panel is what brings it up`() {
        val opened = ImeUiState(
            externalInputs = ExternalInputSnapshot(keyboard = true),
            clipboardVisible = true,
        )

        assertTrue("从工具栏点开它才出现", opened.compatContentVisible)
        assertTrue("而且它压在工具栏上面", opened.compatToolbarMenu)
    }

    @Test
    fun `a popup from the toolbar is the kind that sits above the toolbar`() {
        val voice = ImeUiState(
            externalInputs = ExternalInputSnapshot(keyboard = true),
            voice = VoiceState.Recording(elapsedMs = 0L, level = 0f, cancelArmed = false),
        )
        val symbols = ImeUiState(
            page = KeyboardPage.Symbols,
            externalInputs = ExternalInputSnapshot(keyboard = true),
        )
        val clipboard = ImeUiState(
            clipboardVisible = true,
            externalInputs = ExternalInputSnapshot(keyboard = true),
        )

        assertTrue(voice.compatToolbarMenu)
        assertTrue(symbols.compatToolbarMenu)
        assertTrue(clipboard.compatToolbarMenu)
    }

    @Test
    fun `the expanded candidate list stays with the caret`() {
        val expanded = composing.copy(candidatesExpanded = true, raw = "", preview = "")

        assertTrue("「更多候选」还是第二层", expanded.compatExpandedPanel)
        assertFalse("但它是候选栏那一排的延续，位置照旧跟着光标", expanded.compatToolbarMenu)
    }
}
