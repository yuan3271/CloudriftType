package com.yuan3271.cloudrift.input

import android.content.Context
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
    /** 判定成键盘 / 指针的那台设备名字，设置页里显示出来，好跟现场对账。 */
    val keyboardName: String? = null,
    val pointingName: String? = null,
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

    /** 名字那行小字；两边都认不出来时是空串。 */
    val describeDevices: String
        get() = listOfNotNull(
            keyboardName?.let { "键盘：$it" },
            pointingName?.let { "指针：$it" },
        ).joinToString(" · ")
}

/**
 * 一台输入设备里与"这块屏上的软键盘还有没有用"有关的几项事实。
 *
 * 抽成纯数据是为了让判定逻辑脱离 Android 单测（见 `ExternalInputsTest`）；[detectExternalInputs]
 * 本身不碰框架，取值的那一步在 [ExternalInputMonitor] 里。
 */
data class InputDeviceFacts(
    val name: String = "",
    val sources: Int,
    val keyboardType: Int,
    /** 系统自己造出来的设备（`InputDevice.isVirtual`）；读不到时按 true 处理，宁可不误报。 */
    val virtual: Boolean = false,
)

/**
 * 判定外接键鼠是否在场：**只看设备表**。
 *
 * 以前这里还看 `Configuration.keyboard != KEYBOARD_NOKEYS`，那是错的：那一位在不少 ROM 上
 * 常年是 `QWERTY`（有的机器出厂就带着，有的桌面模式/投屏也会把它点亮），于是**一台什么都没插的
 * 手机也会被判成"接了外接键盘"**，键鼠兼容面板就自己冒出来了。现在这一位不再参与判定——
 * 设备表就在手边，而且热插拔当场就变。
 */
fun detectExternalInputs(devices: List<InputDeviceFacts>): ExternalInputSnapshot {
    val keyboard = devices.firstOrNull { it.isAlphabeticKeyboard() }
    val pointing = devices.firstOrNull { it.isPointingDevice() }
    return ExternalInputSnapshot(
        keyboard = keyboard != null,
        pointing = pointing != null,
        keyboardName = keyboard?.name?.takeIf { it.isNotBlank() },
        pointingName = pointing?.name?.takeIf { it.isNotBlank() },
    )
}

/**
 * 能打字的键盘：必须是键盘类设备、框架认它是字母键盘、**不是系统自己造出来的设备**。
 *
 * 遥控器、游戏手柄、扫描枪也带 `SOURCE_KEYBOARD` 位，但它们要么打不出字母，要么是
 * `KEYBOARD_TYPE_NON_ALPHABETIC`——那种设备在场不该把虚拟键盘换成兼容面板。
 */
private fun InputDeviceFacts.isAlphabeticKeyboard(): Boolean =
    !virtual &&
        (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
        keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
        !looksLikePlatformDevice(name)

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
 * 系统/内核造出来的那几类"键盘"，名字是有共性的：`gpio-keys`（电源、音量按键走的就是它）、
 * `uinput`、以及各家 ROM 给虚拟设备起的 `virtual` 名字。它们不该把面板叫出来。
 *
 * 按名字认设备当然不优雅，但设备表里能拿到的判据只有这些（`getDescriptor` / `getVendorId`
 * 从 Android 12 起对普通应用收紧）。真碰上一台名字里带这些字的外接键盘，用户可以把它插在
 * 别的设备上试，或者告诉我，我再换成"必须 `isExternal`"那条更严的规则。
 */
private fun looksLikePlatformDevice(name: String): Boolean {
    val lower = name.lowercase()
    return PLATFORM_DEVICE_HINTS.any { it in lower }
}

private val PLATFORM_DEVICE_HINTS = listOf("gpio", "uinput", "virtual")

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
        devices = deviceFacts(),
    )

    /**
     * 读设备表。读不到就返回空表（Android 12 起部分 InputDevice 取值对普通应用收紧，某些 ROM
     * 也会直接抛 SecurityException）——那样判定成"没有外接设备"，用户仍可在设置里把
     * 「外接键鼠」切成虚拟键盘正常用。
     *
     * 名字与 `isVirtual` 都单独包一层：任何一个读不到（收紧的 ROM）时，`isVirtual` 记成 true ——
     * 宁可不认这台设备，也不能让电源键那种系统键盘把面板叫出来。
     */
    private fun deviceFacts(): List<InputDeviceFacts> {
        val manager = inputManager ?: return emptyList()
        return runCatching {
            val facts = mutableListOf<InputDeviceFacts>()
            for (id in manager.inputDeviceIds) {
                val device = manager.getInputDevice(id) ?: continue
                facts += InputDeviceFacts(
                    name = runCatching { device.name }.getOrDefault(""),
                    sources = device.sources,
                    keyboardType = device.keyboardType,
                    virtual = runCatching { device.isVirtual }.getOrDefault(true),
                )
            }
            facts
        }.getOrDefault(emptyList())
    }
}
