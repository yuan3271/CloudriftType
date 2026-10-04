package com.yuan3271.cloudrift.ui

import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import android.content.res.Configuration
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
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
import kotlin.math.abs

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
    //
    // 键鼠兼容面板走的也是这条悬浮框：它本来就是一块"能拖到任何地方"的卡片（外接键鼠多半在平板上，
    // 面板要能跟着光标、也能被拖走），所以它**总是**用悬浮框，不看横竖屏、也不看横屏那项设置，
    // 宽度沿用同一个"悬浮宽度"。
    val floating = state.showsCompatPanel ||
        (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            state.landscapeFrame == KeyboardFrame.Floating)

    LaunchedEffect(floating, state.floatingWidthPercent) {
        controller.applyInputFrame(floating = floating, widthPercent = state.floatingWidthPercent)
    }

    // 工具面板是**另一个窗口**（见 CloudriftImeService.applyToolbarWindow）：它该不该出现、在哪儿，
    // 要单独同步一次；开不出来时会退回画在候选词那个窗口里。
    LaunchedEffect(state.showsCompatPanel, state.compatToolbarXPercent, state.compatToolbarYPercent) {
        controller.syncCompatWindows()
    }

    // The appearance sliders change the content height, and an IME window is not always
    // re-measured just because its content shrank or grew. Asking for a layout keeps the
    // window in step with the slider instead of only catching up after the keyboard has been
    // hidden and shown again. 换到键鼠兼容面板（或换回来）是一次更大的高度变化，同样要主动
    // 请求一次布局，否则窗口会留着上一个形态的高度。
    LaunchedEffect(
        state.keyHeightDp,
        state.bottomGapDp,
        state.keyCornerRadiusDp,
        state.showsCompatPanel,
    ) {
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

        // 过渡动画：键盘整块出现时淡入并轻轻上移。窗口本身是系统弹出来的（改不了它的动画），
        // 但内容可以是"落下来"的；只在第一次合成时跑一次，打字过程里不会反复播。
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, tween(150)) }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = appear.value
                    translationY = (1f - appear.value) * 28.dp.toPx()
                },
        ) {
            // 外接键鼠在场且设置选了兼容面板时，窗口里只有候选词栏 + 工具栏；否则就是原来那
            // 整块键盘。两条路读的是同一份状态、同一套引擎，插上键鼠不会打断正在打的东西。
            if (state.showsCompatPanel) {
                CompatPanel(
                    state = state,
                    controller = controller,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
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
                        // 悬浮卡片是按按键尺寸开的窗，快捷设置那张整宽面板在它里面既放不下也点不准，
                        // 所以悬浮模式下齿轮改成直接进完整设置页（内容只多不少）。
                        if (floating) controller.openFullSettings() else controller.toggleQuickSettings()
                    },
                )
            }

            // 布局面板只属于按键那一侧：面板上没有按键，9 键这种手指布局也没意义（控制器在
            // 外接键鼠下会把 9 键临时换成 26 键），所以面板里不挂它。
            if (layoutPickerVisible && !state.showsCompatPanel) {
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

// 键盘高度是按**屏幕**高度算的（autoKeyHeight），不是按窗口高度——这里的窗口就是键盘自己，
// 拿 containerSize 当输入会绕成一个圈。Compose 的 ConfigurationScreenWidthHeight 检查针对的是
// "用 Configuration 尺寸去布局内容"，不适用；宽度只用来把拖动的像素换算成百分比。
@Suppress("ConfigurationScreenWidthHeight")
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
    // 屏幕宽度一律用 dp 参与缩放换算：拖动量也是 dp，两边单位一致，横拖与纵拖的手感才对得上。
    val screenWidth = LocalConfiguration.current.screenWidthDp.toFloat()
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
    // 按键区的实际高度，用来把「手指位移」换算成「按键高度变化量」（见 FloatingResize.kt）。
    // 只在真的变了才写回去，免得每次布局都触发一次重组。
    var keyAreaHeightPx by remember { mutableFloatStateOf(0f) }
    // 每一页都用同一个圆角：数字页以前是拨号盘（圆角固定成半高），看上去和 26 键不是一套键，
    // 现在跟着设置走，数字键和字母键长得一样。
    val cornerRadius = state.keyCornerRadiusDp.dp
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

    // 抓角落拖动 = 窗口管理器那样的调整：**被拖的那条边跟着手指走**（换算与理由见
    // FloatingResize.kt：卡片高度里工具栏、候选栏这些不随按键高度变，所以位移要先折成行数）。
    val keyHeightPx = with(density) { keyHeight.toPx() }
    val gapPx = with(density) { KEY_GAP.toPx() }
    val resizeRows = floatingResizeRows(
        keyAreaHeightPx = keyAreaHeightPx,
        keyHeightPx = keyHeightPx,
        gapPx = gapPx,
    )
    val onFloatingResize: (Float, Float) -> Unit = { dx, dy ->
        val (widthPercent, keyHeightDeltaDp) = floatingResizeDeltas(
            dragXPx = dx,
            dragYPx = dy,
            density = density.density,
            screenWidthDp = screenWidth,
            resizeRows = resizeRows,
        )
        controller.resizeFloatingKeyboard(widthPercent, keyHeightDeltaDp)
    }

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
                FloatingGripStrip(
                    onMove = controller::moveFloatingKeyboard,
                    onResize = onFloatingResize,
                    onResizeCommitted = controller::commitFloatingKeyboardSize,
                )
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
                    onPasteClipboardOffer = controller::pasteClipboardOffer,
                    onDismissClipboardOffer = controller::dismissClipboardOffer,
                )
            }

            // 量一下按键区的高度：悬浮卡片的缩放换算要用它（见 FloatingResize.kt）。
            Box(
                modifier = Modifier.onGloballyPositioned {
                    val measured = it.size.height.toFloat()
                    if (abs(measured - keyAreaHeightPx) > 0.5f) keyAreaHeightPx = measured
                },
            ) {
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
                        onSkipCorrection = controller::skipVoiceCorrection,
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
                        emojiGroups = controller.emojiGroups,
                        sheet = state.symbolSheet,
                        emojiGroup = state.emojiGroup,
                        functionRow = KeyboardLayouts.symbolFunctionRow(state.enterLabel),
                        width = state.symbolWidth,
                        onWidthChange = controller::setSymbolWidth,
                        onSheetChange = controller::setSymbolSheet,
                        onEmojiGroupChange = controller::setEmojiGroup,
                        keyHeight = keyHeight,
                        cornerRadius = cornerRadius,
                        keyBackground = state.keyBackground,
                        callbacks = callbacks,
                        labelScale = labelScale,
                    )

                    // 数字页有自己的排法：左边一条能滑的竖条，右边四列。
                    state.page == KeyboardPage.Numbers -> NumberPanel(
                        strip = KeyboardLayouts.mathStrip(),
                        symbolKey = KeyboardLayouts.numberSymbolKey(),
                        rows = KeyboardLayouts.numberRows(state.enterLabel),
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
internal fun ExpandedCandidates(
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
internal fun NoticeStrip(
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
