package com.yuan3271.cloudrift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.input.LayoutId
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons

/**
 * In window sheet for choosing a layout. A real ModalBottomSheet would open a second
 * window, which an input method cannot reliably own, so the sheet is drawn inside the
 * keyboard surface with a scrim instead.
 */
@Composable
fun LayoutPickerOverlay(
    current: LayoutId,
    available: List<LayoutId> = LayoutId.enabled(japaneseEnabled = false),
    onSelect: (LayoutId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
                .padding(8.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "输入方式",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
                available.forEach { layout ->
                    LayoutRow(
                        layout = layout,
                        selected = layout == current,
                        onClick = { onSelect(layout) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutRow(layout: LayoutId, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = layoutIcon(layout),
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = layoutName(layout),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(
                imageVector = CloudriftIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(2.dp))
    }
}

private fun layoutIcon(layout: LayoutId) = when (layout) {
    LayoutId.Pinyin26, LayoutId.Pinyin9 -> CloudriftIcons.Keyboard
    LayoutId.JapaneseRomaji -> CloudriftIcons.Spellcheck
    LayoutId.English -> CloudriftIcons.Keyboard
}

internal fun layoutName(layout: LayoutId): String = when (layout) {
    LayoutId.Pinyin26 -> "中文 · 26 键拼音"
    LayoutId.Pinyin9 -> "中文 · 9 键拼音"
    LayoutId.JapaneseRomaji -> "日本語 · ローマ字入力"
    LayoutId.English -> "English · QWERTY"
}
