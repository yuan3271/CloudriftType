package com.yuan3271.cloudrift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 剪贴板提示的"只提示一次"规则。这条规则错了的表现是提示**反复出现、怎么都关不掉**（用户报
 * 过的那个），做过头了的表现是新复制的内容反而提示不出来，两个方向都得钉住。
 */
class ClipboardOfferLogTest {

    /**
     * 用户点名要确认的那条：复制 A、复制 B、再复制 A——第二次 A 必须**照常提示**。
     * 记住的只能是"最近提示过的那一段"，不能是"提示过的所有内容"。
     */
    @Test
    fun `copying A then B then A offers A again`() {
        val offers = ClipboardOfferLog()

        assertTrue(offers.offer("A"))
        assertTrue(offers.offer("B"))
        assertTrue("第二次复制 A 仍然要给提示", offers.offer("A"))
        assertTrue("再复制 B 同样要给提示", offers.offer("B"))
    }

    /** 反过来的一半：同一段文字连着来两次，只提示第一次。 */
    @Test
    fun `the same text twice in a row is offered once`() {
        val offers = ClipboardOfferLog()

        assertTrue(offers.offer("A"))
        assertFalse(offers.offer("A"))
        assertFalse(offers.offer("A"))
    }

    /**
     * 键盘进程重启后会重新读一遍剪贴板，那时内容往往没变。记的那段文字是写进 prefs 的，所以
     * 重启（这里就是重新构造一个）不该让它又冒出来一次。
     */
    @Test
    fun `a restart does not forget which text has already been offered`() {
        val restarted = ClipboardOfferLog(lastOffered = "A")

        assertFalse(restarted.offer("A"))
        assertTrue(restarted.offer("B"))
        assertTrue(restarted.offer("A"))
    }

    /** 存下去的就是最近提示过的那一段，导入/导出与持久化都读它。 */
    @Test
    fun `the stored text is the last one offered`() {
        val offers = ClipboardOfferLog()
        offers.offer("A")
        offers.offer("B")

        assertEquals("B", offers.lastOfferedText())
    }
}
