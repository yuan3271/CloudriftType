package com.yuan3271.cloudrift.engine.pinyin

import org.junit.Assert.assertEquals
import org.junit.Test

class T9Test {

    @Test
    fun `letters map onto the phone keypad`() {
        assertEquals("2", T9.encode("a"))
        assertEquals("64426", T9.encode("nihao"))
        assertEquals("94664", T9.encode("zhong"))
        assertEquals("8", T9.encode("v"))
    }
}
