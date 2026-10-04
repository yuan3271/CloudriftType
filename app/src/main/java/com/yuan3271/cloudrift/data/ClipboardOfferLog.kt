package com.yuan3271.cloudrift.data

/**
 * 决定"刚看到的这段剪贴板文字要不要再提示一次"。
 *
 * 只记**最近提示过的那一段**，不是"提示过的所有内容"：
 *
 *  - 复制 A、B、再复制 A —— 三次都给提示。第二次 A 和上一次的 B 不是同一段，用户确实又复制了
 *    一次，提示就该回来；
 *  - 复制 A、又复制 A —— 第二次不给。剪贴板里还是同一段文字，提示已经给过了。
 *
 * 之所以要记，是因为"这段文字还在剪贴板里"这件事会**反复出现**：进程重启时 [ClipboardStore]
 * 会再读一遍剪贴板，候选项清空时提示又回到候选栏，于是同一段内容一天里能冒出来很多次——用户
 * 报的"显示多次、怎么都关不掉"就是这个。纯逻辑、无 Android 依赖，方便把这条规则单测钉死。
 */
internal class ClipboardOfferLog(private var lastOffered: String? = null) {

    /** 记下"这段文字提示过了"，返回这一次该不该提示。 */
    fun offer(text: String): Boolean {
        if (text == lastOffered) return false
        lastOffered = text
        return true
    }

    /** 上次提示过的那段文字。写进 prefs 才能跨进程存活（见 [ClipboardStore]）。 */
    fun lastOfferedText(): String? = lastOffered
}
