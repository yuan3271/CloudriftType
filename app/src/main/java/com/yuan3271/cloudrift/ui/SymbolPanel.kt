package com.yuan3271.cloudrift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuan3271.cloudrift.data.KeyBackground
import com.yuan3271.cloudrift.data.SymbolWidth
import com.yuan3271.cloudrift.engine.emoji.EmojiGroup
import com.yuan3271.cloudrift.input.KeyDef
import com.yuan3271.cloudrift.ime.SymbolSheet

/**
 * The symbol page, drawn as a scrollable bar of symbol keys instead of a fixed grid: the whole
 * inventory is available and the user flicks through it the same way they flick through the
 * candidate strip.
 *
 * The left rail is the page's table of contents: 全角 / 半角 punctuation, which is the one decision
 * a Chinese typist makes constantly and previously had to be guessed by the keyboard, and 表情,
 * whose nine entries are the Unicode emoji groups (in the rail rather than in a strip across the
 * top, because the page may not grow taller than the letter page).
 *
 * The function row keeps using [KeyCanvas] so backspace repeat, space and enter behave exactly
 * like they do on the letter page.
 */
@Composable
fun SymbolPanel(
    symbols: List<KeyDef>,
    emojiGroups: List<EmojiGroup>,
    sheet: SymbolSheet,
    emojiGroup: String,
    functionRow: List<KeyDef>,
    width: SymbolWidth,
    onWidthChange: (SymbolWidth) -> Unit,
    onSheetChange: (SymbolSheet) -> Unit,
    onEmojiGroupChange: (String) -> Unit,
    keyHeight: Dp,
    cornerRadius: Dp,
    keyBackground: KeyBackground,
    callbacks: KeyCallbacks,
    labelScale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val gridHeight = keyHeight * VISIBLE_ROWS + KEY_GAP * (VISIBLE_ROWS - 1)
    // 空串表示"还没选过"：落到第一个分类，而不是给出一页空白。
    val activeGroup = emojiGroups.firstOrNull { it.key == emojiGroup } ?: emojiGroups.firstOrNull()
    val showEmoji = sheet == SymbolSheet.Emoji && activeGroup != null

    Row(modifier = modifier.fillMaxWidth()) {
        SymbolRail(
            width = width,
            sheet = if (activeGroup == null) SymbolSheet.Punctuation else sheet,
            emojiGroups = emojiGroups,
            activeEmojiGroup = activeGroup?.key,
            height = gridHeight,
            onWidthChange = onWidthChange,
            onSheetChange = onSheetChange,
            onEmojiGroupChange = onEmojiGroupChange,
        )
        Column(modifier = Modifier.weight(1f)) {
            if (showEmoji && activeGroup != null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(SYMBOL_COLUMNS),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight),
                    contentPadding = PaddingValues(horizontal = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
                    verticalArrangement = Arrangement.spacedBy(KEY_GAP),
                ) {
                    // key 就是 emoji 本身：表里每个 emoji 只属于一个分类，重复会让懒合成抛异常。
                    items(activeGroup.emoji, key = { it }) { glyph ->
                        val key = KeyDef.immediate(glyph)
                        KeyTile(
                            key = key,
                            height = keyHeight,
                            cornerRadius = cornerRadius,
                            keyBackground = keyBackground,
                            onClick = { callbacks.onKey(key) },
                            // 表情按同样的字号画会显得比标点小一圈（标点是窄字形，emoji 是方块）。
                            labelScale = labelScale * EMOJI_LABEL_SCALE,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(SYMBOL_COLUMNS),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gridHeight),
                    contentPadding = PaddingValues(horizontal = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(KEY_GAP),
                    verticalArrangement = Arrangement.spacedBy(KEY_GAP),
                ) {
                    items(symbols, key = { it.output }) { symbol ->
                        KeyTile(
                            key = symbol,
                            height = keyHeight,
                            cornerRadius = cornerRadius,
                            keyBackground = keyBackground,
                            onClick = { callbacks.onKey(symbol) },
                            labelScale = labelScale,
                        )
                    }
                }
            }
            Spacer(Modifier.height(KEY_GAP))
            KeyCanvas(
                rows = listOf(functionRow),
                keyHeight = keyHeight,
                cornerRadius = cornerRadius,
                keyBackground = keyBackground,
                callbacks = callbacks,
            )
        }
    }
}

/**
 * 左栏：标点页时是「全角 / 半角 / 表情」三颗，表情页时是「标点」加九个标准分类。
 *
 * 它是一条 [LazyColumn]，不是固定三格的 Column：九分类塞进三格高必须能滚——和数字页那条滑动
 * 竖条是同一套做法（三颗可见，往下滑出其余的）。
 */
@Composable
private fun SymbolRail(
    width: SymbolWidth,
    sheet: SymbolSheet,
    emojiGroups: List<EmojiGroup>,
    activeEmojiGroup: String?,
    height: Dp,
    onWidthChange: (SymbolWidth) -> Unit,
    onSheetChange: (SymbolSheet) -> Unit,
    onEmojiGroupChange: (String) -> Unit,
) {
    val chipHeight = (height - KEY_GAP * (VISIBLE_CHIPS - 1)) / VISIBLE_CHIPS
    LazyColumn(
        modifier = Modifier
            .width(RAIL_WIDTH)
            .height(height)
            .padding(start = 3.dp),
        verticalArrangement = Arrangement.spacedBy(KEY_GAP),
    ) {
        if (sheet == SymbolSheet.Emoji) {
            item(key = "punctuation") {
                RailChip(
                    icon = "符",
                    label = "标点",
                    selected = false,
                    height = chipHeight,
                ) { onSheetChange(SymbolSheet.Punctuation) }
            }
            items(emojiGroups, key = { it.key }) { group ->
                RailChip(
                    icon = group.icon,
                    label = group.label,
                    selected = group.key == activeEmojiGroup,
                    height = chipHeight,
                ) { onEmojiGroupChange(group.key) }
            }
        } else {
            item(key = "full") {
                RailChip(
                    label = "全角",
                    selected = width == SymbolWidth.Full,
                    height = chipHeight,
                ) { onWidthChange(SymbolWidth.Full) }
            }
            item(key = "half") {
                RailChip(
                    label = "半角",
                    selected = width == SymbolWidth.Half,
                    height = chipHeight,
                ) { onWidthChange(SymbolWidth.Half) }
            }
            if (emojiGroups.isNotEmpty()) {
                item(key = "emoji") {
                    RailChip(
                        icon = "😀",
                        label = "表情",
                        selected = false,
                        height = chipHeight,
                    ) { onSheetChange(SymbolSheet.Emoji) }
                }
            }
        }
    }
}

@Composable
private fun RailChip(
    icon: String = "",
    label: String,
    selected: Boolean,
    height: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (icon.isNotEmpty()) {
                Text(text = icon, fontSize = 15.sp, maxLines = 1)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontSize = if (icon.isEmpty()) 14.sp else 10.sp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private val RAIL_WIDTH = 52.dp

private const val SYMBOL_COLUMNS = 7

/** Rows of symbols visible before scrolling; the bar keeps the height of the letter page. */
private const val VISIBLE_ROWS = 3

/** 左栏一次看得见几颗（多出来的靠滚动）。 */
private const val VISIBLE_CHIPS = 3

/** 表情字形比标点大一号画，屏幕上才是同一个视觉大小。 */
private const val EMOJI_LABEL_SCALE = 1.35f
