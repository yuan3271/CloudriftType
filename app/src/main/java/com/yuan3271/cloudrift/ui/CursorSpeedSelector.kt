package com.yuan3271.cloudrift.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yuan3271.cloudrift.data.CursorSpeed

internal fun CursorSpeed.label(): String = when (this) {
    CursorSpeed.Slow -> "慢"
    CursorSpeed.Medium -> "中"
    CursorSpeed.Fast -> "快"
}

@Composable
internal fun CursorSpeedSelector(
    current: CursorSpeed,
    onSelect: (CursorSpeed) -> Unit,
    modifier: Modifier = Modifier,
) {
    val speeds = CursorSpeed.entries
    CloudriftSegmentedControl(
        options = speeds.map(CursorSpeed::label),
        selectedIndex = speeds.indexOf(current),
        onSelected = { onSelect(speeds[it]) },
        modifier = modifier,
    )
}
