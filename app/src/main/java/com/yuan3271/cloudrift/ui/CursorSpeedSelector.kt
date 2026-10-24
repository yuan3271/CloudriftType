package com.yuan3271.cloudrift.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        speeds.forEachIndexed { index, speed ->
            SegmentedButton(
                selected = current == speed,
                onClick = { onSelect(speed) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = speeds.size),
            ) {
                Text(speed.label(), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
