package com.yuan3271.cloudrift.ime

import android.view.KeyEvent

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

    /** 空格：有候选就是"选首选 + 补一个空格"，没有候选就是普通的空格。 */
    data object Space : HardwareKeyAction

    /** 打断当前输入：把还没上屏的读音丢掉（Esc）。 */
    data object Cancel : HardwareKeyAction
}

/**
 * @param unicode 已经过 Meta 状态换算的字符（`KeyEvent.getUnicodeChar(metaState)`）；0 表示这颗
 *   键打不出字符。
 * @param ctrlPressed / altPressed 组合键状态。
 * @return null 表示"这颗键不归输入法管"，原样交回系统。
 */
fun hardwareKeyAction(
    keyCode: Int,
    unicode: Int,
    ctrlPressed: Boolean,
    altPressed: Boolean,
): HardwareKeyAction? {
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
