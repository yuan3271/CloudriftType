package com.yuan3271.cloudrift.ime

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 物理键盘上的键怎么翻译成输入法的动作。
 *
 * 重点在"什么不碰"：Ctrl / Alt 组合是应用的快捷键（Ctrl+C、Ctrl+W、Alt 菜单、AltGr 打特殊
 * 字符），Tab 与方向键管焦点和光标，Delete 删的是光标右边——这些一旦被输入法吃掉，用户在
 * 外接键盘上就会觉得"快捷键坏了、光标不动了"。所以它们必须原样交回系统。
 */
class HardwareKeysTest {

    private fun key(
        keyCode: Int,
        unicode: Int = 0,
        ctrl: Boolean = false,
        alt: Boolean = false,
    ) = hardwareKeyAction(keyCode, unicode, ctrl, alt)

    @Test
    fun `letters digits and punctuation go into the reading buffer`() {
        assertEquals(HardwareKeyAction.Type("n"), key(KeyEvent.KEYCODE_N, 'n'.code))
        assertEquals(HardwareKeyAction.Type("7"), key(KeyEvent.KEYCODE_7, '7'.code))
        assertEquals(HardwareKeyAction.Type("，"), key(KeyEvent.KEYCODE_COMMA, '，'.code))
    }

    @Test
    fun `the meta state has already turned shift and caps lock into a capital`() {
        // 服务传进来的是 getUnicodeChar(metaState) 的结果，所以大写在这里就是大写——输入法
        // 不必再猜 Shift 有没有按下。
        assertEquals(HardwareKeyAction.Type("H"), key(KeyEvent.KEYCODE_H, 'H'.code))
    }

    @Test
    fun `delete enter space and escape have their own actions`() {
        assertEquals(HardwareKeyAction.Backspace, key(KeyEvent.KEYCODE_DEL))
        assertEquals(HardwareKeyAction.Enter, key(KeyEvent.KEYCODE_ENTER))
        assertEquals(HardwareKeyAction.Enter, key(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertEquals(HardwareKeyAction.Space, key(KeyEvent.KEYCODE_SPACE, ' '.code))
        assertEquals(HardwareKeyAction.Cancel, key(KeyEvent.KEYCODE_ESCAPE))
    }

    @Test
    fun `ctrl and alt combinations are left to the application`() {
        assertNull(key(KeyEvent.KEYCODE_C, 0x03, ctrl = true))
        assertNull(key(KeyEvent.KEYCODE_W, 0x17, ctrl = true))
        assertNull(key(KeyEvent.KEYCODE_Q, 0x40, alt = true))
    }

    @Test
    fun `tab arrows and function keys are not ours`() {
        assertNull(key(KeyEvent.KEYCODE_TAB, 0x09))
        assertNull(key(KeyEvent.KEYCODE_DPAD_LEFT))
        assertNull(key(KeyEvent.KEYCODE_DPAD_DOWN))
        assertNull(key(KeyEvent.KEYCODE_FORWARD_DEL))
        assertNull(key(KeyEvent.KEYCODE_F1))
        assertNull(key(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun `a key that produces no character is not ours`() {
        assertNull(key(KeyEvent.KEYCODE_SHIFT_LEFT))
        assertNull(key(KeyEvent.KEYCODE_CTRL_LEFT))
    }
}
