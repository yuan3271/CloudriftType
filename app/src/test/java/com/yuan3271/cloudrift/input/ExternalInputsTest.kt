package com.yuan3271.cloudrift.input

import android.content.res.Configuration
import android.view.InputDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 外接键鼠的判定。
 *
 * 这些用例回答的是"为什么判定不能只比一位"：SOURCE_MOUSE 是"指针类 + MOUSE 位"的组合值，
 * 触摸屏与它共享指针类这一位，只比一位的话每台手机都会"检测到外接鼠标"，虚拟键盘会在用户
 * 什么都没插的时候被换成面板。同理，遥控器与游戏手柄也带键盘类位，但它们打不出字母。
 */
class ExternalInputsTest {

    private fun device(sources: Int, keyboardType: Int = InputDevice.KEYBOARD_TYPE_NONE) =
        InputDeviceFacts(sources = sources, keyboardType = keyboardType)

    private fun touchscreen() = device(InputDevice.SOURCE_TOUCHSCREEN)

    private fun alphabeticKeyboard() =
        device(InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_ALPHABETIC)

    @Test
    fun `a phone with nothing attached has no external input`() {
        val snapshot = detectExternalInputs(
            configurationKeyboard = Configuration.KEYBOARD_NOKEYS,
            devices = listOf(touchscreen()),
        )
        assertFalse("触摸屏不是外接键盘", snapshot.keyboard)
        assertFalse("触摸屏不是鼠标", snapshot.pointing)
        assertFalse(snapshot.present)
    }

    @Test
    fun `an alphabetic keyboard counts even when the configuration has not caught up`() {
        // ROM 更新 Configuration.keyboard 有早有晚，设备表那一刻已经是新的。
        val snapshot = detectExternalInputs(
            configurationKeyboard = Configuration.KEYBOARD_NOKEYS,
            devices = listOf(touchscreen(), alphabeticKeyboard()),
        )
        assertTrue(snapshot.keyboard)
        assertTrue(snapshot.present)
    }

    @Test
    fun `the configuration flag alone is enough for a built in keyboard`() {
        // 平板键盘壳、Chromebook 自带的键盘都是"机器自己的键盘"：设备表里可能读不到，但系统配置
        // 会说有键盘，而这种设备同样不需要屏上按键。
        val snapshot = detectExternalInputs(
            configurationKeyboard = Configuration.KEYBOARD_QWERTY,
            devices = listOf(touchscreen()),
        )
        assertTrue(snapshot.keyboard)
    }

    @Test
    fun `remote controls and gamepads are not keyboards`() {
        val remote = device(InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC)
        val gamepad = device(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD)
        val snapshot = detectExternalInputs(
            configurationKeyboard = Configuration.KEYBOARD_NOKEYS,
            devices = listOf(remote, gamepad),
        )
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
                configurationKeyboard = Configuration.KEYBOARD_NOKEYS,
                devices = listOf(touchscreen(), device(sources)),
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
    }
}
