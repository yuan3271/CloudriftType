package com.yuan3271.cloudrift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.data.AppSettings
import com.yuan3271.cloudrift.data.KeyBackground
import com.yuan3271.cloudrift.data.ThemeMode
import com.yuan3271.cloudrift.data.ThemeSource
import com.yuan3271.cloudrift.ime.ImeUiState
import com.yuan3271.cloudrift.input.KeyboardLayouts
import com.yuan3271.cloudrift.theme.hsvToColor
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons
import kotlin.math.roundToInt

/**
 * The settings the user actually reaches for while typing, kept inside the keyboard so that
 * leaving the current app is only ever one deliberate tap away.
 */
@Composable
fun QuickSettingsPanel(
    state: ImeUiState,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onClose: () -> Unit,
    onOpenFullSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val autoHeight = KeyboardLayouts.autoKeyHeight(screenHeight)
    val keyHeight = (if (state.keyHeightDp > 0) state.keyHeightDp.toFloat() else autoHeight).dp

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = CloudriftIcons.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "键盘设置",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.padding(end = 6.dp),
                ) {
                    Icon(CloudriftIcons.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("收起")
                }
            }

            // The height, radius, background and colour sliders all act on the keys that this
            // preview draws, so their effect is visible while the finger is still on the slider
            // even though the keyboard itself is behind the sheet.
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                LabeledRow("外观预览") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    ) {
                        KeyPreviewRow(
                            rows = listOf(KeyboardLayouts.previewRow()),
                            keyHeight = keyHeight,
                            cornerRadius = state.keyCornerRadiusDp.dp,
                            keyBackground = state.keyBackground,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = SCROLL_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ---- theme ---------------------------------------------------------
                LabeledRow("主题") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val modes = listOf(
                            ThemeMode.System to "跟随系统",
                            ThemeMode.Light to "浅色",
                            ThemeMode.Dark to "深色",
                        )
                        modes.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = state.themeMode == mode,
                                onClick = { onUpdate { it.copy(themeMode = mode) } },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                LabeledRow("配色") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val sources = listOf(
                            ThemeSource.Dynamic to "跟壁纸",
                            ThemeSource.Cloudrift to "云隙蓝",
                            ThemeSource.Custom to "自定义",
                        )
                        sources.forEachIndexed { index, (source, label) ->
                            SegmentedButton(
                                selected = state.themeSource == source,
                                onClick = { onUpdate { it.copy(themeSource = source) } },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = sources.size),
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                if (state.themeSource == ThemeSource.Custom) {
                    ColorPicker(
                        hue = state.accentHue,
                        saturation = state.accentSaturation,
                        onHue = { value -> onUpdate { it.copy(accentHue = value) } },
                        onSaturation = { value -> onUpdate { it.copy(accentSaturation = value) } },
                    )
                }

                // ---- key appearance -------------------------------------------------
                LabeledRow("按键背景") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val styles = listOf(
                            KeyBackground.Filled to "填充",
                            KeyBackground.Outlined to "描边",
                            KeyBackground.Ghost to "无底",
                        )
                        styles.forEachIndexed { index, (style, label) ->
                            SegmentedButton(
                                selected = state.keyBackground == style,
                                onClick = { onUpdate { it.copy(keyBackground = style) } },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = styles.size),
                            ) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                SliderRow(
                    title = "按键圆角",
                    value = state.keyCornerRadiusDp.toFloat(),
                    range = 4f..26f,
                    steps = 10,
                    suffix = "dp",
                    onChange = { value -> onUpdate { it.copy(keyCornerRadiusDp = value.roundToInt()) } },
                )

                SliderRow(
                    title = "键盘高度",
                    value = state.keyHeightDp.toFloat(),
                    range = 0f..72f,
                    steps = 17,
                    suffix = if (state.keyHeightDp == 0) "自动" else "dp",
                    onChange = { value -> onUpdate { it.copy(keyHeightDp = value.roundToInt()) } },
                )

                SliderRow(
                    title = "距屏幕底部",
                    value = state.bottomGapDp.toFloat(),
                    range = 0f..48f,
                    steps = 11,
                    suffix = "dp",
                    onChange = { value -> onUpdate { it.copy(bottomGapDp = value.roundToInt()) } },
                )

                // ---- behaviour ------------------------------------------------------
                // 「顶部数字行」搬回完整设置页：键盘里的开关要留给打字当口会想改的东西，
                // 数字行是"装好就不动"的外观偏好。空出来的位置给语音的文本修正 API ——
                // 纠错不可用或不想让文字出网时，这里一按就不用再进设置页。
                SwitchRow(
                    title = "文本修正 API",
                    checked = state.voiceCorrection,
                    onCheckedChange = { value -> onUpdate { it.copy(voiceCorrection = value) } },
                )
                SwitchRow(
                    title = "上滑输入符号",
                    checked = state.swipeUpSymbols,
                    onCheckedChange = { value -> onUpdate { it.copy(swipeUpSymbols = value) } },
                )
                SwitchRow(
                    title = "空格滑动移动光标",
                    checked = state.spaceCursorControl,
                    onCheckedChange = { value -> onUpdate { it.copy(spaceCursorControl = value) } },
                )
                SwitchRow(
                    title = "按键震动",
                    checked = state.hapticFeedback,
                    onCheckedChange = { value -> onUpdate { it.copy(hapticFeedback = value) } },
                )
            }

            // Two explicit ways out: back to the keys, or on to the full settings screen.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(CloudriftIcons.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("完成")
                }
                Button(
                    onClick = onOpenFullSettings,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(CloudriftIcons.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("更多设置")
                }
            }
        }
    }
}

private val SCROLL_MAX_HEIGHT = 236.dp

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    suffix: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (suffix == "自动") "自动" else "${value.roundToInt()}$suffix",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.height(28.dp),
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Hue wheel plus saturation, previewed as a live swatch. */
@Composable
private fun ColorPicker(
    hue: Int,
    saturation: Int,
    onHue: (Int) -> Unit,
    onSaturation: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "色相",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp),
            )
            Slider(
                value = hue.toFloat(),
                onValueChange = { onHue(it.roundToInt()) },
                valueRange = 0f..359f,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(hsvToColor(hue.toFloat(), saturation / 100f, 0.55f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "鲜艳度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp),
            )
            Slider(
                value = saturation.toFloat(),
                onValueChange = { onSaturation(it.roundToInt()) },
                valueRange = 10f..85f,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
            )
            Spacer(Modifier.width(28.dp))
        }
    }
}
