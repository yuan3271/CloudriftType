package com.yuan3271.cloudrift.ui

import androidx.compose.material3.Slider
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

/**
 * 设置页与键盘内快捷设置共用的滑杆。
 *
 * 用 SliderState 版本（旧的 value 重载已经废弃）：值从设置仓库来、拖动时回写；外面改了值也要
 * 同步回滑杆——否则在键盘里的快捷设置改完，设置页还停在旧位置。
 */
@Composable
fun CloudriftSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    val state = rememberSliderState(value, steps, valueRange)
    LaunchedEffect(value) {
        if (state.value != value) state.value = value
    }
    Slider(state = state, onValueChange = onValueChange, modifier = modifier)
}
