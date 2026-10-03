package com.yuan3271.cloudrift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.data.KeyBackground
import com.yuan3271.cloudrift.input.KeyDef

/** 数字页的行数：滑条三行 + 底下那颗 `符`。 */
private const val STRIP_ROWS = 4

/**
 * 数字页：左边一条能滑的竖条 + 左下角固定的 `符` + 右边四列按键。总共四行，和 26 键一样高。
 *
 * 三件事是用户盯着要的，都落在这一张图里：
 *
 * 1. **不能比 26 键高**：四行封死，滑条占三行、`符` 占第四行。
 * 2. **左边是滑动，不是切换**：竖条用 LazyColumn 真滚，手指滑到哪儿符号就跟到哪儿，松手还有
 *    惯性，而不是"划一下就换一组"。
 * 3. **键面要和 26 键一套**：右边的键一律走 [KeyTile]（KeyCanvas 里那套 KeyFace），所以圆角、
 *    字号、底色和字母键完全一致——以前那套"拨号盘大字 + 胶囊圆角"已经删掉。
 * 4. **滑条里的符号不带键框**（用户要求）：一行行小方块叠在一起太吵，改成整条滑动区**一个统一
 *    外框**，里面只有符号本身，滑动也是整条一起滑。
 */
@Composable
fun NumberPanel(
    strip: List<KeyDef>,
    symbolKey: KeyDef,
    rows: List<List<KeyDef>>,
    keyHeight: Dp,
    cornerRadius: Dp,
    keyBackground: KeyBackground,
    callbacks: KeyCallbacks,
    modifier: Modifier = Modifier,
    labelScale: Float = 1f,
) {
    // 四行键 + 三条缝：左右两边共用这个高度，行才对得齐。
    val gridHeight = keyHeight * STRIP_ROWS + KEY_GAP * (STRIP_ROWS - 1)
    val stripHeight = keyHeight * (STRIP_ROWS - 1) + KEY_GAP * (STRIP_ROWS - 2)
    val stripState = rememberLazyListState()

    Row(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stripHeight)
                    .padding(horizontal = KEY_GAP / 2)
                    .clip(RoundedCornerShape(cornerRadius))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(cornerRadius)),
            ) {
                LazyColumn(
                    state = stripState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(KEY_GAP),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(),
                ) {
                    items(strip, key = { "strip:${it.output}" }) { key ->
                        StripSymbol(
                            key = key,
                            height = keyHeight,
                            labelScale = labelScale,
                            onClick = { callbacks.onKey(key) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(KEY_GAP))
            KeyTile(
                key = symbolKey,
                height = keyHeight,
                cornerRadius = cornerRadius,
                keyBackground = keyBackground,
                onClick = { callbacks.onKey(symbolKey) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KEY_GAP / 2),
                labelScale = labelScale,
            )
        }

        Column(
            modifier = Modifier.weight(4f),
        ) {
            // 右边四列交给 KeyCanvas，而不是一颗颗 KeyTile：这样退格键和 26 键上的退格是**同一套
            // 逻辑**——按住连删、长按删一个词、按住上滑点亮「清空」并松手清空，全都自动一致。
            KeyCanvas(
                rows = rows,
                keyHeight = keyHeight,
                cornerRadius = cornerRadius,
                keyBackground = keyBackground,
                callbacks = callbacks,
                labelScale = labelScale,
            )
        }
    }
}

/**
 * 滑条里的一格符号：**只有符号，没有键框**——整条滑动区共用外面那一个框。
 *
 * 按下去只有一点点缩放（和键一样的按压手感），不加底色，免得看起来又像一个个小方块。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StripSymbol(
    key: KeyDef,
    height: Dp,
    labelScale: Float,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "strip-symbol",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KeyLabelOnly(key = key, labelScale = labelScale)
    }
}
