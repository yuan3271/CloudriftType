package com.yuan3271.cloudrift.ui

import android.view.View
import android.content.res.Configuration
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuan3271.cloudrift.data.KeyBackground
import com.yuan3271.cloudrift.data.KeyboardFrame
import com.yuan3271.cloudrift.ime.ImeController
import com.yuan3271.cloudrift.ime.ImeUiState
import com.yuan3271.cloudrift.input.KeyCode
import com.yuan3271.cloudrift.input.KeyboardPage
import com.yuan3271.cloudrift.input.KeyboardLayouts
import com.yuan3271.cloudrift.input.LayoutId
import com.yuan3271.cloudrift.theme.CloudriftTheme
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons
import com.yuan3271.cloudrift.voice.VoiceState

/**
 * Root of the keyboard window. Everything the input view shows is composed from here.
 *
 * The whole keyboard lives inside one [Box] and every overlay uses `matchParentSize()`. That
 * matters: an overlay that simply asked for `fillMaxSize()` used to stretch the IME window to
 * the whole screen, which is why opening the layout picker made the keyboard jump to the top.
 */
@Composable
fun KeyboardRoot(controller: ImeController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    // Landscape framing: the UI knows the orientation, the service owns the window.
    val floating = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        state.landscapeFrame == KeyboardFrame.Floating

    LaunchedEffect(floating, state.floatingWidthPercent) {
        controller.applyInputFrame(floating = floating, widthPercent = state.floatingWidthPercent)
    }

    // The appearance sliders change the content height, and an IME window is not always
    // re-measured just because its content shrank or grew. Asking for a layout keeps the
    // window in step with the slider instead of only catching up after the keyboard has been
    // hidden and shown again.
    LaunchedEffect(state.keyHeightDp, state.bottomGapDp, state.keyCornerRadiusDp) {
        view.requestLayout()
        (view.parent as? View)?.requestLayout()
    }

    CloudriftTheme(
        themeMode = state.themeMode,
        themeSource = state.themeSource,
        accentHue = state.accentHue,
        accentSaturation = state.accentSaturation,
    ) {
        var layoutPickerVisible by remember { mutableStateOf(false) }

        Box(modifier = modifier.fillMaxWidth()) {
            KeyboardSurface(
                state = state,
                controller = controller,
                floating = floating,
                onOpenLayoutPicker = {
                    controller.setQuickSettingsVisible(false)
                    layoutPickerVisible = true
                },
                onOpenClipboard = {
                    layoutPickerVisible = false
                    controller.toggleClipboard()
                },
                onToggleQuickSettings = {
                    layoutPickerVisible = false
                    controller.toggleQuickSettings()
                },
            )

            if (layoutPickerVisible) {
                LayoutPickerOverlay(
                    current = state.layout,
                    available = LayoutId.enabled(state.japaneseEnabled),
                    onSelect = { layout ->
                        controller.selectLayout(layout)
                        layoutPickerVisible = false
                    },
                    onDismiss = { layoutPickerVisible = false },
                    modifier = Modifier.matchParentSize(),
                )
            }

        }
    }
}

@Composable
private fun KeyboardSurface(
    state: ImeUiState,
    controller: ImeController,
    floating: Boolean,
    onOpenLayoutPicker: () -> Unit,
    onOpenClipboard: () -> Unit,
    onToggleQuickSettings: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val autoHeight = KeyboardLayouts.autoKeyHeight(screenHeight)
    // The floating card is sized by its own values: 键盘高度 and 距屏幕底部 are the upright
    // keyboard's business, and shrinking the upright one must not shrink the floating one.
    val keyHeight = if (floating) {
        state.floatingKeyHeightDp.dp
    } else {
        (if (state.keyHeightDp > 0) state.keyHeightDp.toFloat() else autoHeight).dp
    }
    val bottomGap = if (floating) 0.dp else state.bottomGapDp.dp
    val density = LocalDensity.current
    val labelScale = state.keyLabelScalePercent / 100f
    // The number page is a phone dial pad, so its keys stay round whatever the corner slider
    // says; every other page follows the user's choice.
    val cornerRadius = if (state.page == KeyboardPage.Numbers) {
        keyHeight / 2
    } else {
        state.keyCornerRadiusDp.dp
    }
    val callbacks = KeyCallbacks(
        onKey = controller::onKey,
        onAlternate = { _, alternate -> controller.onAlternateChosen(alternate) },
        onLongPress = { key ->
            if (key.code == KeyCode.Backspace) controller.onBackspaceLongPress()
        },
        onSwipeUp = controller::onSwipeUp,
        onSpaceCursorDrag = controller::onSpaceCursorDrag,
        onSpaceLongPress = controller::onSpaceLongPress,
        onSpaceRelease = controller::onSpaceRelease,
        onSpaceCancelChanged = controller::onSpaceCancelChanged,
        onClearAllArmedChanged = controller::onClearAllArmedChanged,
        onClearAll = controller::clearAllText,
    )
    val recording = state.voice as? VoiceState.Recording

    Surface(
        modifier = if (floating) {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        } else {
            Modifier.fillMaxWidth()
        },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = if (floating) {
            RoundedCornerShape(24.dp)
        } else {
            RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The keyboard owns the bottom of the screen, so it has to respect the
                // navigation bar itself once decorFitsSystemWindows is switched off.
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(bottom = bottomGap),
        ) {
            if (floating) {
                // Grab handle: dragging it moves the whole card, which the service does by
                // changing the window offsets.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { _, dragAmount ->
                                controller.moveFloatingKeyboard(dragAmount.x, dragAmount.y)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                }
                // Corner handle: dragging it resizes the card (width in % of the screen, key height
                // in dp). The values are only persisted when the finger lifts.
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(topStart = 10.dp, bottomEnd = 20.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                            .pointerInput(screenWidth) {
                                detectDragGestures(
                                    onDragEnd = { controller.commitFloatingKeyboardSize() },
                                ) { _, dragAmount ->
                                    val widthDelta = with(density) { dragAmount.x.toDp().value } /
                                        screenWidth.coerceAtLeast(1) * 100f
                                    // The card sits on the bottom edge, so it grows by dragging the
                                    // corner *up*; dragging right widens it.
                                    val heightDelta = -with(density) { dragAmount.y.toDp().value }
                                    controller.resizeFloatingKeyboard(widthDelta, heightDelta)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = CloudriftIcons.More,
                            contentDescription = "拖动调整大小",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(90f),
                        )
                    }
                }
            }
            KeyboardToolbar(
                state = state,
                onLanguageClick = {
                    controller.cycleLanguage()
                },
                onLanguageLongClick = onOpenLayoutPicker,
                onThemeClick = controller::cycleThemeMode,
                onUpdateClick = controller::openUpdate,
                onVoiceClick = controller::toggleVoice,
                onSettingsClick = onToggleQuickSettings,
                onHideClick = controller::hideKeyboard,
                onClipboardClick = onOpenClipboard,
            )

            state.notice?.let { notice ->
                NoticeStrip(
                    message = notice,
                    actionLabel = if (state.noticeNeedsMicrophonePermission) "授权" else null,
                    onAction = controller::openMicrophonePermissionSettings,
                    onDismiss = controller::dismissNotice,
                )
            }

            // Hold-to-talk borrows the candidate strip instead of the whole key area: the
            // space bar has to stay under the finger that is holding it.
            if (state.clearAllArmed) {
                ClearAllStrip()
            } else if (state.holdToTalk && recording != null) {
                ListeningStrip(
                    level = recording.level,
                    elapsedMs = recording.elapsedMs,
                    cancelArmed = recording.cancelArmed,
                )
            } else {
                CandidateBar(
                    state = state,
                    onCandidate = controller::selectCandidate,
                    onExpand = controller::toggleCandidatesExpanded,
                )
            }

            Box {
                when {
                    state.quickSettingsVisible -> QuickSettingsPanel(
                        state = state,
                        onUpdate = controller::updateSettings,
                        onClose = { controller.setQuickSettingsVisible(false) },
                        onOpenFullSettings = controller::openFullSettings,
                    )

                    // While the space bar is held down the keys stay: releasing the bar is the
                    // gesture that ends the recording.
                    state.voice !is VoiceState.Idle && !state.holdToTalk -> VoicePanel(
                        state = state.voice,
                        autoApplyDelayMs = state.voiceAutoApplyDelayMs,
                        autoApplyPending = state.autoApplyPending,
                        onStop = { controller.toggleVoice() },
                        onCancel = controller::dismissVoice,
                        onRetry = controller::retryVoice,
                        onCommit = controller::commitVoiceResult,
                        onOpenPermission = controller::openMicrophonePermissionSettings,
                        onCancelAutoApply = controller::cancelAutoApply,
                    )

                    state.clipboardVisible -> ClipboardPanel(
                        entries = state.clipboardEntries,
                        onPick = controller::pickClipboardEntry,
                        onDelete = controller::deleteClipboardEntry,
                        onCopy = controller::copyClipboardEntry,
                        onClear = controller::clearClipboard,
                        onClose = { controller.setClipboardVisible(false) },
                        keyHeight = keyHeight,
                    )

                    state.candidatesExpanded -> ExpandedCandidates(
                        state = state,
                        onCandidate = controller::selectCandidate,
                        onCollapse = controller::toggleCandidatesExpanded,
                    )

                    state.page == KeyboardPage.Symbols -> SymbolPanel(
                        symbols = KeyboardLayouts.symbolBar(state.symbolWidth),
                        functionRow = KeyboardLayouts.symbolFunctionRow(state.enterLabel),
                        width = state.symbolWidth,
                        onWidthChange = controller::setSymbolWidth,
                        keyHeight = keyHeight,
                        cornerRadius = cornerRadius,
                        keyBackground = state.keyBackground,
                        callbacks = callbacks,
                        labelScale = labelScale,
                    )

                    else -> KeyCanvas(
                        rows = KeyboardLayouts.rows(
                            layout = state.layout,
                            page = state.page,
                            shifted = state.shifted || state.capsLock,
                            enterLabel = state.enterLabel,
                            numberRow = state.showNumberRow,
                        ),
                        keyHeight = keyHeight,
                        cornerRadius = cornerRadius,
                        keyBackground = state.keyBackground,
                        activeKeyCode = if (state.capsLock) KeyCode.Shift else null,
                        callbacks = callbacks,
                        labelScale = labelScale,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 * Shown in place of the candidate strip while backspace is held and slid up: the option is lit, and
 * letting go clears the field. Drawn in the strip's own height, so lighting it up never moves the
 * keys that are still under the finger.
 */
@Composable
private fun ClearAllStrip() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(50),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = CloudriftIcons.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "松手清空全部",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedCandidates(
    state: ImeUiState,
    onCandidate: (Int) -> Unit,
    onCollapse: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "候选",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCollapse) {
                Icon(CloudriftIcons.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("收起")
            }
        }
        // Flow, not a grid: a candidate box is exactly as wide as the word in it, so 实施 this和
        // 百度百科 both get the room they need and a row packs as many as fit.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 210.dp)
                .verticalScroll(rememberScrollState())
                .padding(6.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.candidates.forEachIndexed { index, candidate ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onCandidate(index) }
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = candidateLabel(
                                candidate,
                                dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeStrip(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
        TextButton(onClick = onDismiss) {
            Icon(CloudriftIcons.Close, contentDescription = "关闭提示", modifier = Modifier.size(18.dp))
        }
    }
}
