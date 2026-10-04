package com.yuan3271.cloudrift.input

import android.view.InputDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外接键鼠的判定。
 *
 * 这些用例回答的是两件事：
 *
 * 1. **为什么不能只看 `Configuration.keyboard`**：那一位在不少 ROM 上常年是 QWERTY（投屏、桌面
 *    模式、出厂配置），一台什么都没插的手机也会被判成"接了外接键盘"——用户报的就是这个。
 *    现在判定只看设备表，见 `detects nothing on a phone whose configuration claims a keyboard`。
 * 2. **为什么判定不能只比一位**：SOURCE_MOUSE 是"指针类 + MOUSE 位"的组合值，触摸屏与它共享
 *    指针类这一位；遥控器与游戏手柄也带键盘类位，但打不出字母；电源键那台 `gpio-keys` 与各家
 *    ROM 的虚拟设备同样是"键盘类设备"却不是能打字的键盘。
 */
class ExternalInputsTest {

    private fun device(
        sources: Int,
        keyboardType: Int = InputDevice.KEYBOARD_TYPE_NONE,
        name: String = "device",
        virtual: Boolean = false,
    ) = InputDeviceFacts(
        name = name,
        sources = sources,
        keyboardType = keyboardType,
        virtual = virtual,
    )

    private fun touchscreen() = device(InputDevice.SOURCE_TOUCHSCREEN, name = "touchscreen")

    private fun alphabeticKeyboard() =
        device(
            InputDevice.SOURCE_KEYBOARD,
            InputDevice.KEYBOARD_TYPE_ALPHABETIC,
            name = "Logitech K380",
        )

    @Test
    fun `a phone with nothing attached has no external input`() {
        val snapshot = detectExternalInputs(listOf(touchscreen()))
        assertFalse("触摸屏不是外接键盘", snapshot.keyboard)
        assertFalse("触摸屏不是鼠标", snapshot.pointing)
        assertFalse(snapshot.present)
    }

    @Test
    fun `detects nothing on a phone whose configuration claims a keyboard`() {
        // 判定里已经没有 Configuration.keyboard 这一位了：哪怕机器自报 QWERTY，只要设备表里
        // 没有真键盘，就不该弹面板。这条用例是用户报的"没接键盘也被判定成外接键盘"的钉子。
        val snapshot = detectExternalInputs(listOf(touchscreen()))
        assertFalse(snapshot.keyboard)
        assertFalse(snapshot.present)
    }

    @Test
    fun `a real alphabetic keyboard is detected by name`() {
        val snapshot = detectExternalInputs(listOf(touchscreen(), alphabeticKeyboard()))
        assertTrue(snapshot.keyboard)
        assertTrue(snapshot.present)
        assertEquals("Logitech K380", snapshot.keyboardName)
    }

    @Test
    fun `system made up devices are not keyboards`() {
        // gpio-keys（电源 / 音量）、uinput、ROM 自己的虚拟设备都是键盘类设备，但不是能打字的键盘。
        for (name in listOf("gpio-keys", "uinput", "Virtual", "MTK Virtual Keyboard")) {
            val snapshot = detectExternalInputs(
                listOf(
                    touchscreen(),
                    device(InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_ALPHABETIC, name),
                ),
            )
            assertFalse("$name 不该被当成外接键盘", snapshot.keyboard)
        }
        // isVirtual 为真的设备同样不算，哪怕名字看不出问题。
        val virtual = device(
            InputDevice.SOURCE_KEYBOARD,
            InputDevice.KEYBOARD_TYPE_ALPHABETIC,
            name = "Some Keyboard",
            virtual = true,
        )
        assertFalse(detectExternalInputs(listOf(virtual)).keyboard)
    }

    @Test
    fun `remote controls and gamepads are not keyboards`() {
        val remote = device(InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC)
        val gamepad = device(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD)
        val snapshot = detectExternalInputs(listOf(remote, gamepad))
        assertFalse("打不出字母的键盘类设备换不来面板", snapshot.keyboard)
    }

    @Test
    fun `mice touchpads and trackballs are pointing devices`() {
        for (sources in listOf(
            InputDevice.SOURCE_MOUSE,
            InputDevice.SOURCE_TOUCHPAD,
            InputDevice.SOURCE_TRACKBALL,
        )) {
            val snapshot = detectExternalInputs(
                listOf(touchscreen(), device(sources, name = "pointer")),
            )
            assertFalse("只有指针设备，没有键盘", snapshot.keyboard)
            assertTrue("sources=$sources", snapshot.pointing)
            assertTrue(snapshot.present)
        }
    }

    @Test
    fun `describe says what was found`() {
        assertEquals("", ExternalInputSnapshot().describe)
        assertEquals("已检测到外接键盘", ExternalInputSnapshot(keyboard = true).describe)
        assertEquals("已检测到外接鼠标 / 触控板", ExternalInputSnapshot(pointing = true).describe)
        assertEquals(
            "已检测到外接键盘与鼠标",
            ExternalInputSnapshot(keyboard = true, pointing = true).describe,
        )
        assertEquals(
            "键盘：Logitech K380",
            ExternalInputSnapshot(keyboard = true, keyboardName = "Logitech K380").describeDevices,
        )
    }
}
