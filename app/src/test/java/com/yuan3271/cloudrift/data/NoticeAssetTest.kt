package com.yuan3271.cloudrift.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * App 内的第三方资源声明必须和仓库里的 NOTICE.md 一字不差.
 *
 * The keyboard ships data derived from a CC BY corpus, so the attribution has to travel inside
 * the APK: the settings screen reads `assets/NOTICE.md`, not the file in the repository. Keeping
 * one copy in the repository and one in the assets would drift the first time a source is added,
 * so the asset is checked here rather than trusted.
 */
class NoticeAssetTest {

    @Test
    fun `the shipped notice is the repository notice`() {
        val asset = File("src/main/assets/NOTICE.md")
        val repository = File("../NOTICE.md")

        assertTrue("缺少 $asset", asset.exists())
        assertTrue("缺少 $repository", repository.exists())
        assertEquals(
            "app/src/main/assets/NOTICE.md 与仓库根目录的 NOTICE.md 不一致；" +
                "改完 NOTICE.md 后跑 cp NOTICE.md app/src/main/assets/NOTICE.md",
            repository.readText(),
            asset.readText(),
        )
    }

    @Test
    fun `the notice still credits the corpora the assets are built from`() {
        val notice = File("src/main/assets/NOTICE.md").readText()

        // 语料来源一旦从声明里掉出去，署名就没有了 —— 这里当成构建失败而不是文档问题。
        for (credit in listOf("Tatoeba", "CC BY 2.0", "pypinyin", "jieba", "THUOCL", "pinyin-data")) {
            assertTrue("NOTICE.md 里缺少 $credit", notice.contains(credit))
        }
    }
}
