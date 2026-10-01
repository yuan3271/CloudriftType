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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.data.KeyBackground
import com.yuan3271.cloudrift.data.SymbolWidth
import com.yuan3271.cloudrift.input.KeyDef

/**
 * The symbol page, drawn as a scrollable bar of symbol keys instead of a fixed grid: the whole
 * inventory is available and the user flicks through it the same way they flick through the
 * candidate strip.
 *
 * The left rail switches the inventory between 全角 and 半角 punctuation, which is the one
 * decision a Chinese typist makes constantly and previously had to be guessed by the keyboard.
 *
 * The function row keeps using [KeyCanvas] so backspace repeat, space and enter behave exactly
 * like they do on the letter page.
 */
@Composable
fun SymbolPanel(
    symbols: List<KeyDef>,
    functionRow: List<KeyDef>,
    width: SymbolWidth,
    onWidthChange: (SymbolWidth) -> Unit,
    keyHeight: Dp,
    cornerRadius: Dp,
    keyBackground: KeyBackground,
    callbacks: KeyCallbacks,
    modifier: Modifier = Modifier,
) {
    val gridHeight = keyHeight * VISIBLE_ROWS + KEY_GAP * (VISIBLE_ROWS - 1)

    Row(modifier = modifier.fillMaxWidth()) {
        WidthRail(
            width = width,
            onSelect = onWidthChange,
            height = gridHeight,
        )
        Column(modifier = Modifier.weight(1f)) {
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
                    )
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

/** 全角 / 半角, stacked so the choice sits beside the symbols it changes. */
@Composable
private fun WidthRail(width: SymbolWidth, onSelect: (SymbolWidth) -> Unit, height: Dp) {
    Column(
        modifier = Modifier
            .width(RAIL_WIDTH)
            .height(height)
            .padding(start = 3.dp),
        verticalArrangement = Arrangement.spacedBy(KEY_GAP),
    ) {
        RailButton(
            label = "全角",
            selected = width == SymbolWidth.Full,
            modifier = Modifier.weight(1f),
        ) { onSelect(SymbolWidth.Full) }
        RailButton(
            label = "半角",
            selected = width == SymbolWidth.Half,
            modifier = Modifier.weight(1f),
        ) { onSelect(SymbolWidth.Half) }
    }
}

@Composable
private fun RailButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
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
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

private val RAIL_WIDTH = 50.dp

private const val SYMBOL_COLUMNS = 7

/** Rows of symbols visible before scrolling; the bar keeps the height of the letter page. */
private const val VISIBLE_ROWS = 3
