package com.yuan3271.cloudrift.ime

import android.view.KeyEvent
import com.yuan3271.cloudrift.data.ExternalKeyFlavor
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
        meta: Boolean = false,
        down: Boolean = true,
        flavor: ExternalKeyFlavor = ExternalKeyFlavor.Windows,
    ) = hardwareKeyAction(keyCode, unicode, ctrl, alt, meta, down, flavor)

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
        assertNull(key(KeyEvent.KEYCODE_CTRL_LEFT))
        assertNull(key(KeyEvent.KEYCODE_ALT_LEFT))
        assertNull(key(KeyEvent.KEYCODE_TAB, 0x09))
    }

    @Test
    fun `shift and caps lock are modifiers, and both phases matter`() {
        // 单击 Shift 切中英、短按 Caps Lock 切中英 / 长按锁大小写，都要看按下与抬起两次事件。
        assertEquals(HardwareKeyAction.Modifier(ModifierKey.Shift, true), key(KeyEvent.KEYCODE_SHIFT_LEFT))
        assertEquals(
            HardwareKeyAction.Modifier(ModifierKey.Shift, false),
            key(KeyEvent.KEYCODE_SHIFT_RIGHT, down = false),
        )
        assertEquals(
            HardwareKeyAction.Modifier(ModifierKey.CapsLock, true),
            key(KeyEvent.KEYCODE_CAPS_LOCK),
        )
        assertEquals(
            HardwareKeyAction.Modifier(ModifierKey.CapsLock, false),
            key(KeyEvent.KEYCODE_CAPS_LOCK, down = false),
        )
        assertNull(modifierKey(KeyEvent.KEYCODE_A))
    }

    @Test
    fun `command combinations only mean something in the mac flavour`() {
        // Mac 档：⌘C/⌘V/⌘X/⌘A 翻成编辑器动作。Windows 档：Win 键不是复制粘贴的修饰键，交回系统。
        val mac = ExternalKeyFlavor.Mac
        assertEquals(
            HardwareKeyAction.Edit(EditAction.Copy),
            key(KeyEvent.KEYCODE_C, meta = true, flavor = mac),
        )
        assertEquals(
            HardwareKeyAction.Edit(EditAction.Paste),
            key(KeyEvent.KEYCODE_V, meta = true, flavor = mac),
        )
        assertEquals(
            HardwareKeyAction.Edit(EditAction.Cut),
            key(KeyEvent.KEYCODE_X, meta = true, flavor = mac),
        )
        assertEquals(
            HardwareKeyAction.Edit(EditAction.SelectAll),
            key(KeyEvent.KEYCODE_A, meta = true, flavor = mac),
        )
        assertNull(key(KeyEvent.KEYCODE_C, meta = true))
        // 没有对应编辑器动作的 ⌘ 组合（⌘Z、⌘Q…）一律放行，别被输入法悄悄吃掉。
        assertNull(key(KeyEvent.KEYCODE_Z, meta = true, flavor = mac))
        assertNull(key(KeyEvent.KEYCODE_SPACE, ' '.code, meta = true, flavor = mac))
    }
}
