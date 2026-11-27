package com.yuan3271.cloudrift.input

import java.text.BreakIterator

/** 文本末尾的一个"字"（用户眼里的一个字符）在字符串里占的区间。 */
internal data class GraphemeCluster(val start: Int, val end: Int) {
    val utf16Length: Int get() = end - start
}

/**
 * 按**字素簇**取字，而不是按 UTF-16 单元。
 *
 * 退格删一个"字"时，emoji（代理对）、带组合附加符号的字母、家庭 emoji（ZWJ 连接）、
 * 国旗（两个区域指示符）都必须作为一个整体删掉：只删掉半个代理对，输入框里会留下一个
 * 渲染成方块的孤立字符，而且用户还得再按一次才删得干净。
 *
 * 用 BreakIterator 的字符实例：Android 上它是 ICU 的实现，JVM 上（单元测试）是标准库的实现，
 * 两边都按 Unicode 的字符边界走。
 */
internal object Graphemes {

    /** [text] 末尾那一个字素簇；空串返回 null。 */
    fun lastCluster(text: CharSequence): GraphemeCluster? {
        if (text.isEmpty()) return null
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text.toString())
        val end = iterator.last()
        if (end <= 0) return null
        val start = iterator.previous()
        if (start < 0 || start >= end) return null
        return GraphemeCluster(start, end)
    }

    /** [text] 末尾那一个字素簇占几个码点（[InputConnection][android.view.inputmethod.InputConnection] 的按码点删除接口用它）。 */
    fun lastClusterCodePoints(text: CharSequence): Int {
        val cluster = lastCluster(text) ?: return 0
        val slice = text.subSequence(cluster.start, cluster.end).toString()
        return slice.codePointCount(0, slice.length)
    }
}
