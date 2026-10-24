package com.yuan3271.cloudrift.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuan3271.cloudrift.data.AppTheme
import com.yuan3271.cloudrift.theme.CloudriftSurface
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons

/**
 * A blocking choice shown once on fresh installs and once for users upgrading from a release
 * without an explicit theme preference. There is deliberately no close action or implicit default:
 * tapping outside and the system back gesture are both disabled.
 */
@Composable
fun AppThemeChoiceDialog(onSelect: (AppTheme) -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = null,
                    onClick = {},
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppThemeChoiceCard(
                onSelect = onSelect,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
            )
        }
    }
}

/**
 * The IME cannot reliably own a second platform dialog window, so its blocking choice is drawn
 * as an in-window modal. It consumes taps on the scrim and swallows Back while visible.
 */
@Composable
fun AppThemeChoiceOverlay(
    onSelect: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) {}
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(
                interactionSource = null,
                indication = null,
                role = null,
                onClick = {},
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppThemeChoiceCard(
            onSelect = onSelect,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
        )
    }
}

@Composable
private fun AppThemeChoiceCard(
    onSelect: (AppTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    CloudriftSurface(
        modifier = modifier.heightIn(max = 520.dp),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "选择界面主题",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "先选一个。之后仍可在「外观 > 界面主题」中随时切换。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ThemeChoiceOption(
                title = "Material 3 Expressive",
                subtitle = "熟悉、克制、跟随系统配色",
                icon = CloudriftIcons.Palette,
                onClick = { onSelect(AppTheme.Material3Expressive) },
            )
            ThemeChoiceOption(
                title = "液态玻璃",
                subtitle = "折射、模糊与流动高光",
                icon = CloudriftIcons.Cloud,
                onClick = { onSelect(AppTheme.LiquidGlass) },
            )
        }
    }
}

@Composable
private fun ThemeChoiceOption(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
