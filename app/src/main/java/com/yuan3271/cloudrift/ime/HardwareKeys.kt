package com.yuan3271.cloudrift.ime

import android.view.KeyEvent
import com.yuan3271.cloudrift.data.ExternalKeyFlavor

/**
 * 实体键盘上的一颗键，翻译成输入法内部的一个动作。
 *
 * 这里只回答"这颗键是什么意思"（纯函数，见 `HardwareKeysTest`）；按下之后发生什么由
 * [ImeController.onHardwareKeyDown] 决定。
 */
sealed interface HardwareKeyAction {
    /** 往读音缓冲里添一个字符：字母、数字、标点。 */
    data class Type(val text: String) : HardwareKeyAction

    /** 退格：删一个字符（有选区时删选区，走的是和屏上退格键同一条路）。 */
    data object Backspace : HardwareKeyAction

    data object Enter : HardwareKeyAction

    /** 空格：正在拼写就是"选首选"（不额外补空格），没在拼写就是普通的空格。 */
    data object Space : HardwareKeyAction

    /** 打断当前输入：把还没上屏的读音丢掉（Esc）。 */
    data object Cancel : HardwareKeyAction

    /**
     * 修饰键的按下 / 抬起。Shift 与 Caps Lock 都不产生字符，但它们要**看两次事件**才有意思：
     * 单独按一下 Shift 是切中英，短按 Caps Lock 也是切中英、长按才是锁大小写。具体怎么判由
     * [ImeController] 按时间决定。
     */
    data class Modifier(val key: ModifierKey, val down: Boolean) : HardwareKeyAction

    /**
     * 编辑器动作（复制 / 粘贴 / 剪切 / 全选）：Mac 档下 ⌘C 这一套要翻成 Android 应用认的动作。
     */
    data class Edit(val action: EditAction) : HardwareKeyAction
}

enum class ModifierKey { Shift, CapsLock }

enum class EditAction { Copy, Cut, Paste, SelectAll }

/**
 * @param unicode 已经过 Meta 状态换算的字符（`KeyEvent.getUnicodeChar(metaState)`）；0 表示这颗
 *   键打不出字符。
 * @param ctrlPressed / altPressed 组合键状态。
 * @param metaPressed ⌘ / Win 按着（`META_META_ON`）。
 * @param down 这一次是按下还是抬起；修饰键只有靠这一对事件才分得出短按与长按。
 * @param flavor 用户在设置里选的那一档（见 [ExternalKeyFlavor]）。
 * @return null 表示"这颗键不归输入法管"，原样交回系统。
 */
fun hardwareKeyAction(
    keyCode: Int,
    unicode: Int,
    ctrlPressed: Boolean,
    altPressed: Boolean,
    metaPressed: Boolean = false,
    down: Boolean = true,
    flavor: ExternalKeyFlavor = ExternalKeyFlavor.Windows,
): HardwareKeyAction? {
    // 修饰键先看一眼：它们没有字符、也会被上面的 Ctrl/Alt 早退挡掉，但短按 / 长按正是输入法要
    // 用的东西。Shift 的按下与抬起都返回动作，控制器再决定"单独按了一下才算数"。
    modifierKey(keyCode)?.let { return HardwareKeyAction.Modifier(it, down) }
    // ⌘ 组合只在 Mac 档下翻成编辑器动作。Windows 档放行，因为 Win 键在 Android 上不是复制粘贴
    // 的修饰键，抢过来只会让别的快捷键失灵。
    if (metaPressed && !ctrlPressed && !altPressed) {
        if (flavor == ExternalKeyFlavor.Mac) metaEditAction(keyCode)?.let { return HardwareKeyAction.Edit(it) }
        return null
    }
    // Ctrl / Alt 组合是应用自己的快捷键（Ctrl+C、Ctrl+W、Alt 菜单），也可能是在用 AltGr 打
    // 特殊字符——输入法一律不接，免得把快捷键和欧文布局吃掉。
    if (ctrlPressed || altPressed) return null
    return when (keyCode) {
        // 退格、回车、空格在屏上都各有对应的键，物理键走同一条链路。
        KeyEvent.KEYCODE_DEL -> HardwareKeyAction.Backspace
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> HardwareKeyAction.Enter
        KeyEvent.KEYCODE_SPACE -> HardwareKeyAction.Space
        // Esc 在中文输入里没有别的含义，正好当"取消当前拼写"。
        KeyEvent.KEYCODE_ESCAPE -> HardwareKeyAction.Cancel
        else -> unicode.toTypeAction()
    }
}

/** Shift / Caps Lock 这两颗修饰键；其余一概不是。 */
fun modifierKey(keyCode: Int): ModifierKey? = when (keyCode) {
    KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> ModifierKey.Shift
    KeyEvent.KEYCODE_CAPS_LOCK -> ModifierKey.CapsLock
    else -> null
}

/**
 * Mac 档下 ⌘ 组合对应的编辑器动作。只认最常用的四颗：⌘Z 之类 Android 编辑器根本没有对应动作的
 * 键不在表里，会原样交回系统，不至于被输入法悄悄吃掉。
 */
private fun metaEditAction(keyCode: Int): EditAction? = when (keyCode) {
    KeyEvent.KEYCODE_C -> EditAction.Copy
    KeyEvent.KEYCODE_X -> EditAction.Cut
    KeyEvent.KEYCODE_V -> EditAction.Paste
    KeyEvent.KEYCODE_A -> EditAction.SelectAll
    else -> null
}

/**
 * 能打印的字符才算输入。这里排掉的是控制字符（Tab、方向键、Delete、功能键都会给出 0 或者
 * 小于空格的码位），它们该由系统自己处理：Tab 换焦点、方向键移动光标、F1-F12 是应用的事。
 */
private fun Int.toTypeAction(): HardwareKeyAction? = when (this) {
    0 -> null
    // 用 Character.toChars 而不是 Int.toChar：前者对码位做完整换算，也不会踩 Kotlin 对
    // Int→Char 转换的截断警告。两个区间合起来正好排掉代理区。
    in 0x20..0xD7FF, in 0xE000..0xFFFD ->
        HardwareKeyAction.Type(String(Character.toChars(this)))
    else -> null
}
