package com.yuan3271.cloudrift.engine.emoji

import android.content.res.AssetManager
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * One standard emoji category: the Unicode group name it is filed under, the Chinese label and
 * rail icon the keyboard shows, and the emoji themselves.
 */
data class EmojiGroup(
    val key: String,
    val label: String,
    val icon: String,
    val emoji: List<String>,
)

/**
 * `assets/emoji.txt`（由 `tools/dictgen/build_emoji.py` 生成，数据源 iamcal/emoji-data，MIT）。
 *
 * 分组用的是 Unicode 自己的 emoji 分组，不另造一套——分类是标准的一部分，用户脑子里那张表
 * （表情 / 人物 / 动物 / 食物 / 出行 / 活动 / 物品 / 符号 / 旗帜）就是它。中文标签与轨道上那个
 * 小图标属于界面文案，所以留在这里，assets 里只有标准数据本身。
 *
 * 与拼音词典同样的约定：构造函数收 reader 而不是 Context，测试才能直接读仓库里那份 assets。
 */
class EmojiCatalog(groups: List<EmojiGroup>) {

    val groups: List<EmojiGroup> = groups

    fun group(key: String): EmojiGroup? = groups.firstOrNull { it.key == key }

    /** 第一个分类的键，UI 在没有选择时的落点。 */
    val firstKey: String? get() = groups.firstOrNull()?.key

    val isEmpty: Boolean get() = groups.isEmpty()

    companion object {
        /**
         * 标准分类：键与 `emoji.txt` 第二列的字面量一致，顺序就是 rail 里从上到下的顺序。
         */
        private val STANDARD = listOf(
            StandardGroup("Smileys & Emotion", "表情", "😀"),
            StandardGroup("People & Body", "人物", "🧑"),
            StandardGroup("Animals & Nature", "动物", "🐻"),
            StandardGroup("Food & Drink", "食物", "🍔"),
            StandardGroup("Travel & Places", "出行", "✈️"),
            StandardGroup("Activities", "活动", "⚽"),
            StandardGroup("Objects", "物品", "💡"),
            StandardGroup("Symbols", "符号", "❤️"),
            StandardGroup("Flags", "旗帜", "🏁"),
        )

        private data class StandardGroup(val key: String, val label: String, val icon: String)

        /**
         * 读 assets；读到坏行就跳过那一行。**不抛异常**：一份表情表读不出来，键盘该少一页，
         * 不该整个输入法起不来（词库那条踩过同一个坑，见 EngineRegistry.warmUp）。
         */
        fun fromAssets(assets: AssetManager): EmojiCatalog = fromReader {
            BufferedReader(InputStreamReader(assets.open(ASSET), Charsets.UTF_8))
        }

        fun fromReader(reader: () -> BufferedReader): EmojiCatalog {
            val buckets = LinkedHashMap<String, MutableList<String>>()
            runCatching {
                reader().use { buffered ->
                    buffered.lineSequence().forEach { line ->
                        if (line.isEmpty() || line.startsWith("#")) return@forEach
                        val tab = line.indexOf('\t')
                        if (tab <= 0 || tab == line.lastIndex) return@forEach
                        val emoji = line.substring(0, tab)
                        val group = line.substring(tab + 1)
                        buckets.getOrPut(group) { mutableListOf() }.add(emoji)
                    }
                }
            }
            val groups = STANDARD.mapNotNull { standard ->
                val emoji = buckets[standard.key]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                EmojiGroup(standard.key, standard.label, standard.icon, emoji)
            }
            return EmojiCatalog(groups)
        }

        const val ASSET = "emoji.txt"
    }
}
