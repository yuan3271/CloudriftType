package com.yuan3271.cloudrift.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.data.ThemeMode
import com.yuan3271.cloudrift.ime.ImeUiState
import com.yuan3271.cloudrift.input.KeyboardLayouts
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons
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
        )
        if (state.update != null && state.showUpdateDot) {
            UpdateMark(onClick = onUpdateClick)
        }

        Spacer(Modifier.weight(1f))

        FilledIconButton(
            onClick = onVoiceClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = when {
                    recording -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            ),
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                imageVector = when {
                    recording -> CloudriftIcons.Stop
                    busy -> CloudriftIcons.Refresh
                    else -> CloudriftIcons.Mic
                },
                contentDescription = "语音输入",
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (busy) spin else 0f),
            )
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
        )
    }
}

/**
 * The quiet half of the update notice: a small yellow dot beside the theme key. It carries no text,
 * so it never pushes the keys around, and the settings screen is where the version and the download
 * button live.
 */
@Composable
private fun UpdateMark(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(RoundedCornerShape(50))
                .background(UpdateAmber),
        )
    }
}

@Composable
private fun LanguageChip(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = CloudriftIcons.Globe,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SmallIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

private val TOOLBAR_HEIGHT = 46.dp

/** Amber, so the mark reads as "something to look at" without shouting. */
private val UpdateAmber = Color(0xFFFFB300)
