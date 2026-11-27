package com.yuan3271.cloudrift.ime

import com.yuan3271.cloudrift.input.LayoutId
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageCycleTest {

    @Test
    fun `nine key goes to english first instead of jumping to the 26 key layout`() {
        assertEquals(
            LayoutId.English,
            ImeController.nextLanguageLayout(
                current = LayoutId.Pinyin9,
                japaneseEnabled = false,
                chineseReturn = LayoutId.Pinyin9,
            ),
        )
    }

    @Test
    fun `coming back from english restores the chinese layout the user was on`() {
        assertEquals(
            LayoutId.Pinyin9,
            ImeController.nextLanguageLayout(
                current = LayoutId.English,
                japaneseEnabled = false,
                chineseReturn = LayoutId.Pinyin9,
            ),
        )
        assertEquals(
            LayoutId.Pinyin26,
            ImeController.nextLanguageLayout(
                current = LayoutId.English,
                japaneseEnabled = false,
                chineseReturn = LayoutId.Pinyin26,
            ),
        )
    }

    @Test
    fun `japanese only joins the rotation when it is enabled`() {
        assertEquals(
            LayoutId.JapaneseRomaji,
            ImeController.nextLanguageLayout(
                current = LayoutId.English,
                japaneseEnabled = true,
                chineseReturn = LayoutId.Pinyin26,
            ),
        )
        assertEquals(
            LayoutId.Pinyin26,
            ImeController.nextLanguageLayout(
                current = LayoutId.JapaneseRomaji,
                japaneseEnabled = true,
                chineseReturn = LayoutId.Pinyin26,
            ),
        )
    }

    @Test
    fun `a stale non chinese return layout falls back to the 26 key layout`() {
        assertEquals(
            LayoutId.Pinyin26,
            ImeController.nextLanguageLayout(
                current = LayoutId.English,
                japaneseEnabled = false,
                chineseReturn = LayoutId.English,
            ),
        )
    }
}
