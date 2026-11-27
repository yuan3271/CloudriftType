package com.yuan3271.cloudrift.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GraphemesTest {

    @Test
    fun `a single chinese character is one cluster`() {
        val cluster = Graphemes.lastCluster("你好")
        assertEquals(1, cluster?.start)
        assertEquals(2, cluster?.end)
        assertEquals(1, Graphemes.lastClusterCodePoints("你好"))
    }

    @Test
    fun `an ascii letter is one cluster`() {
        assertEquals(1, Graphemes.lastCluster("abc")?.utf16Length)
        assertEquals(1, Graphemes.lastClusterCodePoints("abc"))
    }

    @Test
    fun `an emoji is one cluster even though it is two utf-16 units`() {
        val text = "a😀"
        val cluster = Graphemes.lastCluster(text)
        assertEquals(1, cluster?.start)
        assertEquals(3, cluster?.end)
        // 按码点删的接口一次删掉整个 emoji，而不是半个代理对。
        assertEquals(1, Graphemes.lastClusterCodePoints(text))
    }

    @Test
    fun `a combining accent stays with its base letter`() {
        val text = "e\u0301"
        assertEquals(2, Graphemes.lastCluster(text)?.utf16Length)
        assertEquals(2, Graphemes.lastClusterCodePoints(text))
    }

    @Test
    fun `empty text has no cluster`() {
        assertNull(Graphemes.lastCluster(""))
        assertEquals(0, Graphemes.lastClusterCodePoints(""))
    }
}
