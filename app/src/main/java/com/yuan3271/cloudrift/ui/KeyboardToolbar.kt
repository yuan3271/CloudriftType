package com.yuan3271.cloudrift.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.yuan3271.cloudrift.data.ThemeMode
import com.yuan3271.cloudrift.ime.ImeUiState
import com.yuan3271.cloudrift.input.KeyboardLayouts
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons
import com.yuan3271.cloudrift.theme.InternalSurfaceTone
import com.yuan3271.cloudrift.theme.LiquidGlassConfig
import com.yuan3271.cloudrift.theme.bestContentColor
import com.yuan3271.cloudrift.theme.LocalKeyboardOuterRadius
import com.yuan3271.cloudrift.theme.concentricInnerRadius
import com.yuan3271.cloudrift.theme.internalSurface
import com.yuan3271.cloudrift.theme.isLiquidGlassTheme
import com.yuan3271.cloudrift.theme.liquidControlGlass
import com.yuan3271.cloudrift.voice.VoiceState

/**
 * The strip above the keys: language, theme, dictation, settings, dismiss.
 *
 * The dictation button is the only filled button in the strip, because Material 3
 * Expressive reserves the strongest container for the primary action of a surface.
 */
@Composable
fun KeyboardToolbar(
    state: ImeUiState,
    onLanguageClick: () -> Unit,
    onLanguageLongClick: () -> Unit,
    onThemeClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHideClick: () -> Unit,
    onClipboardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recording = state.voice is VoiceState.Recording
    val busy = state.voice is VoiceState.Transcribing || state.voice is VoiceState.Correcting

    // A slow turn while dictating is the cheapest possible "we are listening" cue.
    val spin by animateFloatAsState(
        targetValue = if (busy) 360f else 0f,
        label = "voice-busy",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TOOLBAR_HEIGHT)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LanguageChip(
            label = KeyboardLayouts.languageLabel(state.layout),
            onClick = onLanguageClick,
            onLongClick = onLanguageLongClick,
        )
        SmallIconButton(
            icon = when (state.themeMode) {
                ThemeMode.System -> CloudriftIcons.AutoMode
                ThemeMode.Light -> CloudriftIcons.Sun
                ThemeMode.Dark -> CloudriftIcons.Moon
            },
            description = "主题",
            onClick = onThemeClick,
            width = LiquidGlassConfig.LANGUAGE_THEME_CONTROL_WIDTH_DP.dp,
        )
        if (state.update != null && state.showUpdateDot) {
            UpdateMark(replayKey = state.keyboardShows, onClick = onUpdateClick)
        }

        Spacer(Modifier.weight(1f))

        val voiceIcon = when {
            recording -> CloudriftIcons.Stop
            busy -> CloudriftIcons.Refresh
            else -> CloudriftIcons.Mic
        }
        if (isLiquidGlassTheme()) {
            LiquidIconButton(
                onClick = onVoiceClick,
                active = true,
                width = LiquidGlassConfig.TOOLBAR_CONTROL_HEIGHT_DP.dp,
                height = LiquidGlassConfig.TOOLBAR_CONTROL_HEIGHT_DP.dp,
                tint = if (recording) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (recording) {
                    MaterialTheme.colorScheme.error.bestContentColor()
                } else {
                    MaterialTheme.colorScheme.primary.bestContentColor()
                },
            ) { tint ->
                Icon(
                    imageVector = voiceIcon,
                    contentDescription = "语音输入",
                    tint = tint,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (busy) spin else 0f),
                )
            }
        } else {
            FilledIconButton(
                onClick = onVoiceClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = when {
                        recording -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                ),
                modifier = Modifier.size(LiquidGlassConfig.TOOLBAR_CONTROL_HEIGHT_DP.dp),
            ) {
                Icon(
                    imageVector = voiceIcon,
                    contentDescription = "语音输入",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (busy) spin else 0f),
                )
            }
        }
        SmallIconButton(
            icon = CloudriftIcons.Clipboard,
            description = "剪贴板",
            onClick = onClipboardClick,
            active = state.clipboardVisible,
        )
        SmallIconButton(
            icon = CloudriftIcons.Settings,
            description = "设置",
            onClick = onSettingsClick,
            active = state.quickSettingsVisible,
        )
        SmallIconButton(
            icon = CloudriftIcons.KeyboardHide,
            description = "收起键盘",
            onClick = onHideClick,
            width = LiquidGlassConfig.HIDE_CONTROL_WIDTH_DP.dp,
            shape = RoundedCornerShape(
                concentricInnerRadius(
                    LocalKeyboardOuterRadius.current,
                    LiquidGlassConfig.HIDE_CONTROL_RADIUS_INSET_DP.dp,
                ),
            ),
        )
    }
}

/**
 * The quiet half of the update notice: a small yellow dot beside the theme key. It carries no text,
 * so it never pushes the keys around, and the settings screen is where the version and the download
 * button live.
 */
@Composable
internal fun UpdateMark(replayKey: Int, onClick: () -> Unit) {
    // 打开键盘时先说人话，再收成一枚点：黄色长条写着黑字「有更新」，约一秒后收成黄点，
    // 点里是向上的箭头。长条期间点它同样有效；收成点之后它不再挪动任何键。
    //
    // [replayKey] 每次开键盘都会变（ImeUiState.keyboardShows）：IME 的 view 是复用的，光靠
    // remember 只会播开头那一次，拿开键盘上（onStartInputView）的信号当钥匙才会每次都播。
    var expanded by remember { mutableStateOf(true) }
    LaunchedEffect(replayKey) {
        expanded = true
        delay(UpdateMarkPillMs)
        expanded = false
    }
    val width by animateDpAsState(if (expanded) 54.dp else 22.dp, label = "updateMarkWidth")
    val fill by animateColorAsState(
        if (expanded) UpdateAmber else Color.Transparent,
        label = "updateMarkFill",
    )
    Row(
        modifier = Modifier
            .height(22.dp)
            .width(width)
            .then(
                if (isLiquidGlassTheme()) {
                    Modifier.liquidControlGlass(
                        shape = RoundedCornerShape(50),
                        color = fill,
                        active = expanded,
                    )
                } else {
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(fill)
                },
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (expanded) {
            Text(
                text = "有更新",
                color = Color.Black,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        } else {
            Box(
        modifier = Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(50))
            .background(UpdateAmber),
        contentAlignment = Alignment.Center,
    ) {
            // A filled amber circle with an up arrow in it, so it reads as "there is something to
            // take" rather than as a plain notification dot.
            Icon(
                imageVector = CloudriftIcons.ExpandMore,
                contentDescription = "有新版本",
                tint = UpdateArrow,
                modifier = Modifier
                    .size(11.dp)
                    .rotate(180f),
            )
        }
        }
    }
}

/** 「有更新」长条在键盘上停留的时间。 */
private const val UpdateMarkPillMs = 1200L

/**
 * 工具栏上的胶囊键：语言那颗是「地球 + 当前语言」，符号那颗没有图标，只有一个字（`符` /
 * `?123`）。两颗共用同一个壳：同一个圆角、同一档底色、同一个内边距——工具栏里出现两个尺寸不一的
 * 胶囊，看起来就像两套控件。
 *
 * [onClick] / [onLongClick] 都为 null 时它就是一颗**只用来提示**的胶囊（大写锁那颗）：壳、
 * 圆角、字号、内边距和能点的两颗完全一样，只是不接点击。[active] 是工具栏自己的"正在生效"画法
 * （见 [ToolbarButton]）：同样的壳，换成 primaryContainer 底。
 */
@Composable
internal fun ToolbarChip(
    label: String,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val glass = isLiquidGlassTheme()
    val container = if (active) {
        if (glass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (active) {
        if (glass) {
            MaterialTheme.colorScheme.primary.bestContentColor()
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .then(
                if (glass) {
                    Modifier.liquidControlGlass(
                        shape = RoundedCornerShape(50),
                        active = active,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            null
                        },
                    )
                } else {
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(container)
                },
            )
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) content else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = content,
        )
    }
}

/** 语言那颗：地球图标 + 当前语言（中 / En / 日）。 */
@Composable
internal fun LanguageChip(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ToolbarChip(
        label = label,
        onClick = onClick,
        onLongClick = onLongClick,
        icon = CloudriftIcons.Globe,
        modifier = Modifier.size(
            width = LiquidGlassConfig.LANGUAGE_THEME_CONTROL_WIDTH_DP.dp,
            height = LiquidGlassConfig.TOOLBAR_CONTROL_HEIGHT_DP.dp,
        ),
    )
}

/**
 * 工具栏上一颗圆按钮的外壳：38dp、圆形、选中时 `primaryContainer` 底。图标键与文字键（符号那颗
 * 写的是「符」）共用它，免得同一个工具栏里出现两种按钮尺寸。
 */
@Composable
internal fun ToolbarButton(
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    width: Dp = LiquidGlassConfig.TOOLBAR_CONTROL_HEIGHT_DP.dp,
    height: Dp = LiquidGlassConfig.TOOLBAR_CONTROL_HEIGHT_DP.dp,
    shape: Shape = RoundedCornerShape(50),
    content: @Composable (Color) -> Unit,
) {
    if (isLiquidGlassTheme()) {
        LiquidIconButton(
            onClick = onClick,
            modifier = modifier,
            active = active,
            width = width,
            height = height,
            shape = shape,
        ) { tint ->
            content(tint)
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier
                .size(width = width, height = height)
                .clip(shape)
                .background(
                    if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                ),
        ) {
            content(
                if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
internal fun SmallIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    width: Dp = LiquidGlassConfig.TOOLBAR_CONTROL_HEIGHT_DP.dp,
    shape: Shape = RoundedCornerShape(50),
) {
    // description 同时当无障碍标签与图标的替代文字：这一颗在哪儿都是"按下去做什么"。
    ToolbarButton(
        description = description,
        onClick = onClick,
        active = active,
        width = width,
        shape = shape,
    ) { tint ->
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

private val TOOLBAR_HEIGHT = 52.dp

/** Amber, so the mark reads as "something to look at" without shouting. */
private val UpdateAmber = Color(0xFFFFB300)

/** Dark brown on amber, which keeps the arrow legible on both light and dark keyboards. */
private val UpdateArrow = Color(0xFF3A2A00)
