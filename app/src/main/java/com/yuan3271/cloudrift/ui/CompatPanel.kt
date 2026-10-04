package com.yuan3271.cloudrift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.ime.ImeController
import com.yuan3271.cloudrift.ime.ImeUiState
import com.yuan3271.cloudrift.input.KeyCode
import com.yuan3271.cloudrift.input.KeyboardLayouts
import com.yuan3271.cloudrift.input.KeyboardPage
import com.yuan3271.cloudrift.theme.CloudriftTheme
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons
import com.yuan3271.cloudrift.voice.VoiceState

/**
 * 键鼠兼容面板：外接键鼠在场时取代整块虚拟键盘的那两块。
 *
 * 它不是另起一套界面——**框就是横屏那台悬浮键盘的框**（见 KeyboardSurface 的 floating 分支与
 * [FloatingGripStrip]）：同样 24dp 圆角的卡片、同样 10dp / 6dp 的外边距、顶部同样一条 20dp 的
 * 把手（中间一根小白条＝拖着走），窗口同样是那块"能拖到任何地方"的悬浮窗。区别只在卡片里装的
 * 东西：虚拟键盘装的是按键，这里装的是候选词栏与工具栏。
 *
 * 两块面板是**两个窗口**（用户点名：工具面板与候选词面板是分开的，各是一个窗口），各自带自己的
 * 把手、各自能拖：
 *
 * ```
 *   ╭──────────────────────────────────────────╮
 *   │            ▬▬▬▬          ⌄               │  把手
 *   │  [你好][拟合][你][内][拟]                │  候选词面板（虚拟键盘那条候选栏）
 *   ╰──────────────────────────────────────────╯
 *   ╭──────────────────────────────────────────╮
 *   │            ▬▬▬▬                          │  把手
 *   │  [🌐 中][符][🎤][📋]                     │  工具面板（紧凑排布，不留空）
 *   ╰──────────────────────────────────────────╯
 * ```
 *
 * 候选词面板在输入法自己的那个窗口里，工具面板是服务另开的一个窗口（见
 * `CloudriftImeService.applyToolbarWindow`）；开不出来时（个别 ROM 不给第二个窗口）退回把工具
 * 面板画进候选词那个窗口，功能一件不少，只是不能各拖各的。
 *
 * 工具栏**不是**新画的一排按钮：语言那颗就是 [LanguageChip]，语音与剪贴板就是
 * [SmallIconButton] / [FilledIconButton]，尺寸与配色和虚拟键盘工具栏一模一样；符号那颗仓库里
 * 没有图标，就用键盘上同一个键的字（[KeyboardLayouts.symbolKeyLabel]：中文 `符`、英文 `?123`），
 * 外壳仍是同一颗 [ToolbarButton]。
 */
@Composable
fun CompatPanel(
    state: ImeUiState,
    controller: ImeController,
    modifier: Modifier = Modifier,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val screenWidth = LocalConfiguration.current.screenWidthDp.toFloat()
    val density = LocalDensity.current
    val keyHeight = (
        if (state.keyHeightDp > 0) {
            state.keyHeightDp.toFloat()
        } else {
            KeyboardLayouts.autoKeyHeight(screenHeight)
        }
        ).dp
    val cornerRadius = state.keyCornerRadiusDp.dp
    val labelScale = state.keyLabelScalePercent / 100f
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
    // 面板里没有按键，"缩放"就只剩宽度这一件事（高度那半截按 0 交回去）。换算与悬浮键盘同一套：
    // 横向位移 / 屏宽 = 宽度百分比。
    val onResize: (Float, Float) -> Unit = { dx, _ ->
        controller.resizeFloatingKeyboard(
            widthDeltaPercent = (dx / density.density) / screenWidth.coerceAtLeast(1f) * 100f,
            keyHeightDeltaDp = 0f,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // 悬浮键盘同款外边距：卡片与屏幕边、卡片与卡片之间都靠它对齐。
            .padding(horizontal = PANEL_MARGIN, vertical = PANEL_MARGIN_VERTICAL)
            // 窗口自己不吃系统栏（decorFitsSystemWindows=false），面板替它吃：手势条不能压在
            // 工具面板上。
            .windowInsetsPadding(
                WindowInsets.systemBars.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ),
        verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
    ) {
        state.notice?.let { notice ->
            NoticeStrip(
                message = notice,
                actionLabel = if (state.noticeNeedsMicrophonePermission) "授权" else null,
                onAction = controller::openMicrophonePermissionSettings,
                onDismiss = controller::dismissNotice,
            )
        }

        FloatingPanel {
            FloatingGripStrip(
                onMove = controller::moveFloatingKeyboard,
                onResize = onResize,
                onResizeCommitted = controller::commitFloatingKeyboardSize,
                showResizeHandle = false,
            )
            CandidateBar(
                state = state,
                onCandidate = controller::selectCandidate,
                onExpand = controller::toggleCandidatesExpanded,
                onPasteClipboardOffer = controller::pasteClipboardOffer,
                onDismissClipboardOffer = controller::dismissClipboardOffer,
                idle = { CompatIdleStrip(state = state) },
            )
        }

        // 展开的第二层（语音 / 剪贴板 / 更多候选 / 符号页 / 数字页）：同样是悬浮卡片，夹在
        // 候选面板与工具面板之间，与虚拟键盘里"按键区被面板替换"是同一个规矩。
        if (hasExpandedPanel(state)) {
            FloatingPanel {
                FloatingGripStrip(
                    onMove = controller::moveFloatingKeyboard,
                    onResize = onResize,
                    onResizeCommitted = controller::commitFloatingKeyboardSize,
                    showResizeHandle = false,
                )
                when {
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

                    // 符号页：**先保证每颗符号看得清**，再谈排布。列数按面板当前宽度算（一颗至少
                    // 约 56dp），窄了就少几列、多几行，滚动着取——绝不为了"一排放 7 个"把字挤扁。
                    state.page == KeyboardPage.Symbols -> BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SymbolPanel(
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
                            columns = (maxWidth / SYMBOL_TILE_MIN_WIDTH).toInt()
                                .coerceIn(3, 8),
                        )
                    }

                    // 符号页底部那颗 `123` 通向这里。物理键盘打数字是直接上屏的，但只用鼠标的
                    // 用户没有键盘可打——面板里留着这条数字页，两条路都不落空。
                    else -> NumberPanel(
                        strip = KeyboardLayouts.mathStrip(),
                        symbolKey = KeyboardLayouts.numberSymbolKey(),
                        rows = KeyboardLayouts.numberRows(state.enterLabel),
                        keyHeight = keyHeight,
                        cornerRadius = cornerRadius,
                        keyBackground = state.keyBackground,
                        callbacks = callbacks,
                        labelScale = labelScale,
                    )
                }
            }
        }

        // 工具面板正常是另一个窗口（见 CompatToolbarWindow）。只有第二个窗口开不出来时，才退回
        // 画在这里——功能一件不少，只是两块面板不能各拖各的。
        if (state.compatToolbarInMainWindow) {
            FloatingPanel {
                FloatingGripStrip(
                    onMove = controller::moveFloatingKeyboard,
                    onResize = onResize,
                    onResizeCommitted = controller::commitFloatingKeyboardSize,
                    showResizeHandle = false,
                )
                CompatToolbar(state = state, controller = controller)
            }
        }
    }
}

/**
 * 工具面板那个窗口里装的东西：一块**紧凑**的悬浮卡片（把手 + 一排工具，不留空）。
 *
 * 它单独成一个 composable，是因为它要由服务放进自己的窗口（见 `CloudriftImeService`），而不是画
 * 在输入法窗口里；两个窗口各有各的大小：候选词窗口按候选排，工具窗口收紧到这一排。
 */
@Composable
fun CompatToolbarWindowContent(controller: ImeController) {
    val state by controller.state.collectAsStateWithLifecycle()
    CloudriftTheme(
        themeMode = state.themeMode,
        themeSource = state.themeSource,
        accentHue = state.accentHue,
        accentSaturation = state.accentSaturation,
    ) {
        FloatingPanel(
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(horizontal = PANEL_MARGIN, vertical = PANEL_MARGIN_VERTICAL),
            fillWidth = false,
        ) {
            FloatingGripStrip(
                onMove = controller::moveCompatToolbar,
                onResize = { _, _ -> },
                onResizeCommitted = controller::commitCompatToolbarPosition,
                showResizeHandle = false,
                expand = false,
            )
            CompatToolbar(state = state, controller = controller)
        }
    }
}

private fun hasExpandedPanel(state: ImeUiState): Boolean = when {
    state.voice !is VoiceState.Idle && !state.holdToTalk -> true
    state.clipboardVisible -> true
    state.candidatesExpanded -> true
    state.page != KeyboardPage.Letters -> true
    else -> false
}

/** 悬浮卡片：圆角、`surfaceContainer` 底、顶部一条 [FloatingGripStrip]。与横屏悬浮键盘同一个框。 */
@Composable
private fun FloatingPanel(
    modifier: Modifier = Modifier,
    /**
     * 卡片要不要撑满容器宽度。
     *
     * 候选词那张要（候选栏本来就按窗口宽度排），工具面板那张**不要**：它待在一个 `WRAP_CONTENT`
     * 的独立窗口里，而窗口给 Compose 的约束是"至多整屏宽"——卡片一旦 `fillMaxWidth()`，就会顺着
     * 这个上限长到整屏宽，看上去正是"工具面板突然铺满整个宽度"。所以工具面板靠内容自己撑开。
     */
    fillWidth: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(PANEL_CORNER),
    ) {
        // 不撑宽度的那张卡片（工具面板）里，孩子按内容量宽度，再靠这一列居中：把手与那一排工具
        // 都落在卡片中轴上，而卡片的宽度由内容决定（不会被"至多整屏宽"这个上限撑开）。
        Column(
            horizontalAlignment = if (fillWidth) Alignment.Start else Alignment.CenterHorizontally,
        ) { content() }
    }
}

/**
 * 键鼠模式下的空闲行：**只写语言**。
 *
 * 虚拟键盘那边写的是"中文 · 26 键拼音"，那个"26 键"是给手指看的布局名；物理键盘在手时它没有
 * 意义（用户点名要在键鼠模式下把它藏起来），所以这一行只剩语言，顺带回答"现在按哪个语言处理按键"。
 */
@Composable
private fun CompatIdleStrip(state: ImeUiState) {
    Text(
        text = KeyboardLayouts.languageName(state.layout),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 10.dp),
    )
}

/**
 * 工具面板那一行。左端是语言与符号两颗键，右端是语音（唯一的实心键，和虚拟键盘工具栏的规矩一样）
 * 与剪贴板——与 [KeyboardToolbar] 同一套组件、同一套间距，用户看到的是同一排工具的另一种排法。
 */
@Composable
private fun CompatToolbar(state: ImeUiState, controller: ImeController) {
    val recording = state.voice is VoiceState.Recording
    val busy = state.voice is VoiceState.Transcribing || state.voice is VoiceState.Correcting
    // 转写 / 纠错时那颗刷新图标自己转，这是最便宜的"还在忙"提示。
    val spin by animateFloatAsState(
        targetValue = if (busy) 360f else 0f,
        label = "compat-voice-busy",
    )

    Row(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // 有更新时先把这件事摆出来（和虚拟键盘工具栏那一枚是同一个：先"有更新"长条，再收成黄点）。
        // 键鼠模式下没有别的入口能看到它，所以这一枚不能省。
        if (state.update != null && state.showUpdateDot) {
            UpdateMark(replayKey = state.keyboardShows, onClick = controller::openUpdate)
        }
        LanguageChip(
            label = KeyboardLayouts.languageLabel(state.layout),
            onClick = controller::cycleLanguage,
            // 面板上没有布局面板（9 键那种手指布局在外接键盘下没有意义，控制器会把 9 键临时
            // 换成 26 键），长按就不再挂别的东西。
            onLongClick = controller::cycleLanguage,
        )
        // 符号那颗与语言同一颗胶囊：仓库里没有"符号"图标，而键盘上那颗符号键本来就是写字
        // （中文 符、英文布局 ?123），这里照抄同一个字，不另造图标。
        ToolbarChip(
            label = KeyboardLayouts.symbolKeyLabel(state.layout),
            onClick = controller::toggleSymbolPage,
            onLongClick = controller::toggleSymbolPage,
        )

        FilledIconButton(
            onClick = controller::toggleVoice,
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
            onClick = controller::toggleClipboard,
            active = state.clipboardVisible,
        )
    }
}

/** 悬浮卡片与屏幕边、卡片与卡片之间的距离：和悬浮键盘同一档。 */
private val PANEL_MARGIN = 10.dp
private val PANEL_MARGIN_VERTICAL = 6.dp
private val PANEL_GAP = 6.dp

/** 卡片圆角：与横屏悬浮键盘的 24dp 一致。 */
private val PANEL_CORNER = 24.dp

/** 符号页里一颗符号的最小宽度：低于这个数就把列数减一（先保证看得清，再谈排布）。 */
private val SYMBOL_TILE_MIN_WIDTH = 56.dp
