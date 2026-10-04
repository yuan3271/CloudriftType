package com.yuan3271.cloudrift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons

/**
 * 悬浮卡片顶部的那一条。左边整块空白按住就是把卡片拖着走，右端那颗小图标是缩放手柄。
 *
 * 这一条的前身是 18dp 的拖拽粗边 **加上**一行为缩放手柄单独留的 26dp 空行——工具栏上方于是
 * 白留了一条差不多与工具栏等高的空白，里面一个工具都没有。现在两行并成一条
 * [GRIP_STRIP_HEIGHT]：中间那根小白条就是唯一的"这里能拖"提示，缩放手柄缩到右端一颗小图标。
 *
 * 横屏的悬浮键盘与键鼠兼容面板用的是**同一条**：两种悬浮卡片出现的方式、可拖的位置、缩放的手感
 * 都该是同一套，用户不需要学两遍。
 *
 * @param showResizeHandle 右端那颗缩放手柄。键鼠兼容面板的两张卡片都**没有**这一颗：候选栏与工具
 *   栏本来就是按内容排好的（工具栏不留空、候选栏有多少画多少），拖宽拖窄没有意义，留一颗能按的
 *   图标在那儿只会让人以为按下去会发生什么。
 */
@Composable
internal fun FloatingGripStrip(
    onMove: (Float, Float) -> Unit,
    onResize: (Float, Float) -> Unit,
    onResizeCommitted: () -> Unit,
    showResizeHandle: Boolean = true,
) {
    // pointerInput 里的手势协程只启动一次：直接用它捕获的 lambda，会一直用**第一次组合时**的
    // 那份（缩放要用的行数一开始还没量到，是 1，于是又变成 4 倍不跟手）。rememberUpdatedState
    // 让协程每次事件读到的都是最新的 lambda。
    val currentMove by rememberUpdatedState(onMove)
    val currentResize by rememberUpdatedState(onResize)
    val currentCommit by rememberUpdatedState(onResizeCommitted)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(GRIP_STRIP_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 有手柄时右边留出它的位置；没有手柄时两边各留同样的空，小白条就落在整条的正中间。
        if (!showResizeHandle) Spacer(Modifier.width(34.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        currentMove(dragAmount.x, dragAmount.y)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    // 白条只在深色卡片上有对比度：浅色卡片是 surfaceContainer（近白），白条压上去
                    // 几乎看不见——原来那条"能拖"的提示在浅色模式下等于没有。改成随主题的文字色，
                    // 两种模式下都是一条看得见的灰条。
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    )
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(50),
                    ),
            )
        }
        if (showResizeHandle) {
            // 缩放手柄：抓住它等于抓住卡片的右下角，被拖的那边跟着手指走（换算在 KeyboardSurface）。
            // 松手（或被系统抢走）才落盘，拖动过程中只改内存里的值。
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { currentCommit() },
                            onDragCancel = { currentCommit() },
                        ) { change, dragAmount ->
                            change.consume()
                            currentResize(dragAmount.x, dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CloudriftIcons.More,
                    contentDescription = "拖动调整大小",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(90f),
                )
            }
        } else {
            Spacer(Modifier.width(34.dp))
        }
    }
}

/** 悬浮卡片顶部把手的窄条高度。 */
internal val GRIP_STRIP_HEIGHT = 20.dp
