package com.yuan3271.cloudrift.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 逐字确认的自学习：用户一个字一个字选完三个字之后，整串要能被记下来。
 *
 * 这条测试回答的是"为什么整串重要"：只记相邻两个字的话，下次再打这三个字的拼音时出来的只会是
 * 张伟 / 伟来 两个片段，用户刚拼好的那条组法还是不在候选里——那正是要修掉的行为。
 */
class CharacterChainTest {

    private val chain = CharacterChain(windowMillis = 3000L, maxChars = 12)

    @Test
    fun `three characters confirmed one at a time become one word`() {
        assertNull("单个字还不算一个词", chain.append("zhang", "张", 0L))

        val pair = chain.append("wei", "伟", 500L)
        assertEquals(LearnedWord("zhangwei", "张伟"), pair)

        val whole = chain.append("lai", "来", 900L)
        assertEquals("第三个字要把前两个一起带上", LearnedWord("zhangweilai", "张伟来"), whole)
    }

    @Test
    fun `a pause starts a new word instead of extending the old one`() {
        chain.append("zhang", "张", 0L)
        chain.append("wei", "伟", 500L)

        // Longer than the window: 来 starts a new word, it is not the third character of 张伟.
        val afterPause = chain.append("lai", "来", 500L + 3001L)
        assertNull(afterPause)
        assertEquals(LearnedWord("lailai", "来来"), chain.append("lai", "来", 500L + 3002L))
    }

    @Test
    fun `clearing breaks the run`() {
        chain.append("zhang", "张", 0L)
        chain.clear()

        assertNull(chain.append("wei", "伟", 100L))
    }

    @Test
    fun `the run stops growing at the learned word length`() {
        val short = CharacterChain(windowMillis = 3000L, maxChars = 3)
        short.append("a", "阿", 0L)
        short.append("b", "波", 1L)
        assertEquals(LearnedWord("abc", "阿波次"), short.append("c", "次", 2L))
        // 满了以后这一串就结束了，下一个字重新起头（而不是把后面的字都粘成一条）。
        assertNull(short.append("d", "得", 3L))
    }
}
