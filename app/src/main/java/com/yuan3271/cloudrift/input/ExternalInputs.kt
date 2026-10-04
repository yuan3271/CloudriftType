package com.yuan3271.cloudrift.input

import android.content.Context
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice

/**
 * 场上有没有能顶替软键盘的输入设备，以及是哪一类。
 *
 * 键盘与指针分开记，是为了在设置页里说清楚"检测到了什么"：两者任何一个在场，键鼠兼容面板都
 * 有意义（一个能打字、一个能点候选）。
 */
data class ExternalInputSnapshot(
    val keyboard: Boolean = false,
    val pointing: Boolean = false,
) {
    val present: Boolean get() = keyboard || pointing

    /** 设置页里那句话用的短描述；没有外接设备时是空串。 */
    val describe: String
        get() = when {
            keyboard && pointing -> "已检测到外接键盘与鼠标"
            keyboard -> "已检测到外接键盘"
            pointing -> "已检测到外接鼠标 / 触控板"
            else -> ""
        }
}

/**
 * 一台输入设备里与"这块屏上的软键盘还有没有用"有关的两项事实。
 *
 * 抽成纯数据是为了让判定逻辑脱离 Android 单测（见 `ExternalInputsTest`）；[detectExternalInputs]
 * 本身不碰框架，取值的那一步在 [ExternalInputMonitor] 里。
 */
data class InputDeviceFacts(
    val sources: Int,
    val keyboardType: Int,
)

/**
 * 判定外接键鼠是否在场。
 *
 * `configurationKeyboard` 传 `Configuration.keyboard`：那是框架自己维护的"机器上挂着硬件键盘"
 * 状态，也是系统决定要不要弹软键盘的依据（中文输入法里它还包括平板键盘壳、Chromebook 自带的
 * 键盘，这些设备同样不需要屏上按键）。设备表是补充——ROM 更新那个值有早有晚，而 InputManager
 * 的设备表在热插拔时是当场变的。
 */
fun detectExternalInputs(
    configurationKeyboard: Int,
    devices: List<InputDeviceFacts>,
): ExternalInputSnapshot {
    val keyboard = configurationKeyboard != Configuration.KEYBOARD_NOKEYS ||
        devices.any { it.isAlphabeticKeyboard() }
    val pointing = devices.any { it.isPointingDevice() }
    return ExternalInputSnapshot(keyboard = keyboard, pointing = pointing)
}

/**
 * 能打字的键盘：必须是键盘类设备，而且框架认它是字母键盘。
 *
 * 遥控器、游戏手柄、扫描枪也带 `SOURCE_KEYBOARD` 位，但它们要么打不出字母，要么是
 * `KEYBOARD_TYPE_NON_ALPHABETIC`——那种设备在场不该把虚拟键盘换成兼容面板。
 */
private fun InputDeviceFacts.isAlphabeticKeyboard(): Boolean =
    (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
        keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC

/**
 * 能点候选词的设备：鼠标 / 触控板 / 轨迹球。
 *
 * 比较用的是**整个常量**而不是单独一位：`SOURCE_MOUSE` 是 `指针类 | MOUSE 位` 的组合值，
 * 只比 `sources and SOURCE_MOUSE != 0` 的话，触摸屏（`SOURCE_TOUCHSCREEN`）也会因为共享
 * `SOURCE_CLASS_POINTER` 位而被算成鼠标——那样每台手机都会"检测到外接鼠标"。
 */
private fun InputDeviceFacts.isPointingDevice(): Boolean =
    (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
        (sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD ||
        (sources and InputDevice.SOURCE_TRACKBALL) == InputDevice.SOURCE_TRACKBALL

/**
 * 盯着设备表的变化。
 *
 * 热插拔时 InputManager 会回调（这是主路径）；ROM 不发这个回调时还有输入法服务自己的
 * `onConfigurationChanged` 兜底（见 `CloudriftImeService`），两条路最后都走 [refresh]。
 */
class ExternalInputMonitor(
    private val context: Context,
    private val onChanged: (ExternalInputSnapshot) -> Unit,
) {
    private val inputManager: InputManager? =
        context.getSystemService(Context.INPUT_SERVICE) as? InputManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var registered = false

    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            refresh()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            refresh()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            refresh()
        }
    }

    fun start() {
        if (registered) return
        registered = runCatching {
            inputManager?.registerInputDeviceListener(listener, mainHandler)
            true
        }.getOrDefault(false)
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { inputManager?.unregisterInputDeviceListener(listener) }
    }

    /** 读一次当前状态；[notify] 为 true 时把结果推给回调。 */
    fun refresh(notify: Boolean = true): ExternalInputSnapshot {
        val snapshot = snapshot()
        if (notify) onChanged(snapshot)
        return snapshot
    }

    fun snapshot(): ExternalInputSnapshot = detectExternalInputs(
        configurationKeyboard = context.resources.configuration.keyboard,
        devices = deviceFacts(),
    )

    /**
     * 读设备表。读不到就返回空表（Android 12 起部分 InputDevice 取值对普通应用收紧，某些 ROM
     * 也会直接抛 SecurityException）——那样判定退化成"只看 Configuration.keyboard"，功能不塌。
     */
    private fun deviceFacts(): List<InputDeviceFacts> {
        val manager = inputManager ?: return emptyList()
        return runCatching {
            val facts = mutableListOf<InputDeviceFacts>()
            for (id in manager.inputDeviceIds) {
                val device = manager.getInputDevice(id) ?: continue
                facts += InputDeviceFacts(
                    sources = device.sources,
                    keyboardType = device.keyboardType,
                )
            }
            facts
        }.getOrDefault(emptyList())
    }
}
