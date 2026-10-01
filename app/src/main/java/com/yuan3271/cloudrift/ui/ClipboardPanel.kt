package com.yuan3271.cloudrift.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.data.ClipEntry
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The clipboard panel: a screen of its own, sized like the key area so opening it does not resize
 * the keyboard. The list is the whole point, so it scrolls and keeps as many entries as the store
 * remembers; tapping inserts, long pressing deletes.
 */
@Composable
fun ClipboardPanel(
    entries: List<ClipEntry>,
    onPick: (ClipEntry) -> Unit,
    onDelete: (ClipEntry) -> Unit,
    onCopy: (ClipEntry) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    keyHeight: Dp,
    modifier: Modifier = Modifier,
) {
    var copied by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CloudriftIcons.Clipboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "剪贴板",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    entries.isEmpty() -> "暂无记录"
                    copied -> "已复制到剪贴板"
                    else -> "${entries.size} 条 · 左滑删除 · 右滑复制 · 长按展开"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("清空") }
            }
            TextButton(onClick = onClose) {
                Icon(CloudriftIcons.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("收起")
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keyHeight * LIST_ROWS + KEY_GAP * (LIST_ROWS - 1)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "复制过的文字会出现在这里，可以上下滑动查看",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keyHeight * LIST_ROWS + KEY_GAP * (LIST_ROWS - 1)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp),
                verticalArrangement = Arrangement.spacedBy(KEY_GAP),
            ) {
                items(entries, key = { "${it.at}-${it.text.hashCode()}" }) { entry ->
                    SwipeActionsRow(
                        onCopy = {
                            onCopy(entry)
                            copied = true
                        },
                        onDelete = { onDelete(entry) },
                    ) { armedColor ->
                        ClipRow(
                            entry = entry,
                            onPick = { onPick(entry) },
                            armedColor = armedColor,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClipRow(entry: ClipEntry, onPick: () -> Unit, armedColor: Color?) {
    var expanded by remember { mutableStateOf(false) }
    val container = armedColor ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (armedColor == null) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onPick, onLongClick = { expanded = !expanded })
            .background(container)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            // Long press opens the entry, so nothing has to be committed blindly.
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = formatTimestamp(entry.at),
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.6f),
        )
    }
}

/**
 * Swipe left to delete, swipe right to put the entry back on the clipboard. Copying springs the row
 * back into place; deleting does not, because the entry is gone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionsRow(
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable (armedColor: Color?) -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        // Nothing happens while the finger is down. Swiping only moves the row and lights it up;
        // the action runs once the row has settled, which is the release the user was promised.
        confirmValueChange = { it != SwipeToDismissBoxValue.Settled },
    )
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.EndToStart -> onDelete()
            SwipeToDismissBoxValue.StartToEnd -> {
                onCopy()
                // Copying is not destructive, so the row comes back.
                state.reset()
            }

            else -> Unit
        }
    }
    // The state flips its target as soon as the finger is far enough that releasing would trigger
    // the action, which is exactly the moment the row should say "let go now".
    val armedColor = when (state.targetValue) {
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
        else -> null
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 16.dp),
            ) {
                // Right swipe reveals the copy hint from the start edge, left swipe the delete
                // hint from the end edge; the row itself covers both while it rests.
                Icon(
                    imageVector = CloudriftIcons.Clipboard,
                    contentDescription = "复制",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(20.dp),
                )
                Icon(
                    imageVector = CloudriftIcons.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(20.dp),
                )
            }
        },
    ) {
        content(armedColor)
    }
}

/** Today shows the clock, anything older shows the date too. */
private fun formatTimestamp(millis: Long): String {
    if (millis <= 0L) return ""
    val now = System.currentTimeMillis()
    val dayMillis = 24 * 60 * 60 * 1000L
    val pattern = if (now - millis < dayMillis) "HH:mm" else "MM-dd HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}

/** Rows of history visible at once; matches the key area so the keyboard keeps its height. */
private const val LIST_ROWS = 4
