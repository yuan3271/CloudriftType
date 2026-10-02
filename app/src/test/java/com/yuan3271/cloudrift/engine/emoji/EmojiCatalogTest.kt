package com.yuan3271.cloudrift.engine.emoji

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 表情表按 Unicode 的标准分组分发，而且每一项都得能画出来。
 *
 * 这三条都不是"文档"：分组是用户找表情的唯一线索（分类错了就是找不到），不重复是
 * LazyVerticalGrid 的硬要求（key 冲突会抛异常，符号页已经因为重复 key 闪退过一次），
 * 非空是"轨道的分类名点了有东西"。
 */
class EmojiCatalogTest {

    private fun catalog() = EmojiCatalog.fromReader {
        BufferedReader(
            InputStreamReader(FileInputStream(File("src/main/assets", EmojiCatalog.ASSET)), Charsets.UTF_8),
        )
    }

    @Test
    fun `every standard group ships, in the standard order`() {
        val groups = catalog().groups
        val keys = groups.map { it.key }

        assertEquals(
            listOf(
                "Smileys & Emotion",
                "People & Body",
                "Animals & Nature",
                "Food & Drink",
                "Travel & Places",
                "Activities",
                "Objects",
                "Symbols",
                "Flags",
            ),
            keys,
        )
        for (group in groups) {
            assertTrue("${group.key} 是空的", group.emoji.isNotEmpty())
            assertTrue("${group.key} 缺中文标签", group.label.isNotEmpty())
            assertTrue("${group.key} 缺轨道图标", group.icon.isNotEmpty())
        }
        // 1900 上下：少一大截说明生成脚本只读到半张表。
        assertTrue("表情总数偏少: ${groups.sumOf { it.emoji.size }}", groups.sumOf { it.emoji.size } > 1500)
    }

    @Test
    fun `an emoji belongs to exactly one group`() {
        val seen = mutableSetOf<String>()
        val duplicated = mutableListOf<String>()
        for (group in catalog().groups) {
            for (emoji in group.emoji) {
                if (!seen.add(emoji)) duplicated.add(emoji)
            }
        }

        assertTrue("表情表里有重复项: $duplicated", duplicated.isEmpty())
    }

    @Test
    fun `the well known faces sit where a user looks for them`() {
        val catalog = catalog()

        assertTrue("😀 不在表情里", catalog.group("Smileys & Emotion")!!.emoji.contains("😀"))
        assertTrue("🏁 不在旗帜里", catalog.group("Flags")!!.emoji.contains("🏁"))
    }
}
