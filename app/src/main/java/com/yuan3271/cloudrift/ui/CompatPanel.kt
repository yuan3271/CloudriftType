package com.yuan3271.cloudrift.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuan3271.cloudrift.ime.ImeController
import com.yuan3271.cloudrift.ime.ImeUiState
import com.yuan3271.cloudrift.input.CompatPanelKind
import com.yuan3271.cloudrift.input.KeyCode
import com.yuan3271.cloudrift.input.KeyboardLayouts
import com.yuan3271.cloudrift.input.KeyboardPage
import com.yuan3271.cloudrift.theme.CloudriftTheme
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons
import com.yuan3271.cloudrift.voice.VoiceState

/**
 * 键鼠兼容面板：外接键鼠在场时取代整块虚拟键盘的那两块——**候选词面板**与**工具面板**。
 *
 * 两块面板各是**一个独立窗口**（见 `CloudriftImeService.applyCompatPanel`）：各自能拖、各自按
 * 内容撑开。这里只负责画，位置交给服务。
 *
 * ```
 *   ╭───────────────────────────╮
 *   │  ▬▬▬▬   [你好][拟合][你]  │  候选词面板：跟着光标，**默认不出现**，
 *   ╰───────────────────────────╯  打字（或有候选 / 联想 / 提示）时才露出来
 *   ╭───────────────────────────╮
 *   │  ▬▬▬▬  [中][符] [🎤][📋]  │  工具面板：独立窗口，拖到哪停在哪
 *   ╰───────────────────────────╯
 * ```
 *
 * 框与横屏那台悬浮键盘是同一套（见 [FloatingPanel] 与 [FloatingGripStrip]）：同样 24dp 圆角、
 * 同样的边距、顶部同样一条小横条，抓它就拖着走，没有阴影也没有多余的手柄。
 *
 * 窗口开不出来时（没有「显示在其他应用上层」权限）退回 [CompatPanel]：两块画进输入法窗口里，
 * 功能一件不少，只是不能各拖各的——那时会在面板上给出授权入口。
 */

/** 候选词面板（它自己的那个窗口）。 */
@Composable
fun CompatContentWindow(controller: ImeController) {
    val state by controller.state.collectAsStateWithLifecycle()
    CompatWindowTheme(state) {
        CompatCard(
            state = state,
            controller = controller,
            kind = CompatPanelKind.Content,
            maxWidth = state.contentMaxWidth(),
        ) {
            CompatContentBody(state = state, controller = controller)
        }
    }
}

/**
 * 工具面板（它自己的那个窗口）：一块**紧凑**的悬浮卡片（把手 + 一排工具，不留空）。
 *
 * 它单独成一个 composable，是因为它要由服务放进自己的窗口，而不是画在候选词那个窗口里；两个
 * 窗口各有各的大小：候选词窗口按候选排，工具窗口收紧到这一排。
 */
@Composable
fun CompatToolbarWindowContent(controller: ImeController) {
    val state by controller.state.collectAsStateWithLifecycle()
    CompatWindowTheme(state) {
        CompatCard(
            state = state,
            controller = controller,
            kind = CompatPanelKind.Toolbar,
            maxWidth = state.toolbarMaxWidth(),
        ) {
            CompatToolbar(state = state, controller = controller)
        }
    }
}

/**
 * 退路：两块面板都画进输入法窗口里。
 *
 * 输入法窗口的宽度是系统说了算的（这台机器上就是整屏宽），所以卡片只能靠自己的 `widthIn` 收窄；
 * 两块也就只能一起被拖（同一个窗口）。这是个降级形态：面板上会写明怎么让它恢复成两个窗口。
 */
@Composable
fun CompatPanel(
    state: ImeUiState,
    controller: ImeController,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            // 悬浮键盘同款外边距：卡片与屏幕边靠它对齐。
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
        CompatPermissionStrip(state = state, controller = controller)
        // 候选词卡片默认不出现：没有正在打的字、没有候选、没有提示时，屏幕上只剩工具面板那一条
        // （用户点名要的就是这个）。
        if (state.compatContentVisible) {
            FloatPanelWithGrip(
                controller = controller,
                kind = CompatPanelKind.Content,
                maxWidth = state.contentMaxWidth(),
            ) {
                CompatContentBody(state = state, controller = controller)
            }
        }
        FloatPanelWithGrip(
            controller = controller,
            kind = CompatPanelKind.Toolbar,
            maxWidth = state.toolbarMaxWidth(),
        ) {
            CompatToolbar(state = state, controller = controller)
        }
    }
}

// ---- 卡片 ---------------------------------------------------------------------------

/**
 * 一块面板的窗口内容：悬浮卡片（圆角 + `surfaceContainer` 底 + 顶部一条把手）。
 *
 * 位置由窗口决定，这里只画卡片本身，并把宽度上限交给内容——卡片是"按内容撑开"的
 * （`WRAP_CONTENT` 的独立窗口里，`fillMaxWidth` 会顺着"至多整屏宽"这个上限长到整屏，
 * 正是用户报过的"面板突然铺满整个宽度"）。
 */
@Composable
private fun CompatWindowTheme(state: ImeUiState, content: @Composable () -> Unit) {
    CloudriftTheme(
        themeMode = state.themeMode,
        themeSource = state.themeSource,
        accentHue = state.accentHue,
        accentSaturation = state.accentSaturation,
    ) {
        Box(
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(horizontal = PANEL_MARGIN, vertical = PANEL_MARGIN_VERTICAL),
        ) {
            content()
        }
    }
}

@Composable
private fun CompatCard(
    state: ImeUiState,
    controller: ImeController,
    kind: CompatPanelKind,
    maxWidth: Dp,
    content: @Composable () -> Unit,
) {
    FloatPanelWithGrip(controller = controller, kind = kind, maxWidth = maxWidth) {
        CompatPermissionStrip(state = state, controller = controller)
        content()
    }
}

@Composable
private fun FloatPanelWithGrip(
    controller: ImeController,
    kind: CompatPanelKind,
    maxWidth: Dp,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(PANEL_CORNER),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FloatingGripStrip(
                onMove = { dx, dy -> controller.moveCompatPanelBy(kind, dx, dy) },
                // 面板按内容排好了，没有可缩放的余地：这一条不留缩放手柄，拖动区也收在中间，
                // 卡片宽度才由内容说了算。
                onResize = { _, _ -> },
                onResizeCommitted = { controller.commitCompatPanelPosition(kind) },
                showResizeHandle = false,
                expand = false,
            )
            Box(modifier = Modifier.widthIn(max = maxWidth)) {
                Column(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(PANEL_GAP),
                ) {
                    content()
                }
            }
        }
    }
}

// ---- 面板里的东西 -------------------------------------------------------------------

/** 候选词面板的内容：候选栏，或展开的第二层。 */
@Composable
private fun CompatContentBody(state: ImeUiState, controller: ImeController) {
    // 提示条（语音出错、要麦克风权限）压在这块面板的最上面：键鼠模式下没有别的地方能显示它。
    state.notice?.let { notice ->
        NoticeStrip(
            message = notice,
            actionLabel = if (state.noticeNeedsMicrophonePermission) "授权" else null,
            onAction = controller::openMicrophonePermissionSettings,
            onDismiss = controller::dismissNotice,
        )
    }
    when {
        hasExpandedPanel(state) -> CompatExpandedPanel(state = state, controller = controller)
        // 候选栏只在"真有东西"时出现（候选、联想、剪贴板提示）。空着的时候这条不画——用户点名：
        // 候选栏默认不出现，别在屏幕上留一条空栏。
        state.candidates.isEmpty() && state.clipboardOffer == null -> Unit
        else -> CandidateStrip(
            state = state,
            onCandidate = controller::selectCandidate,
            onExpand = controller::toggleCandidatesExpanded,
            onPasteClipboardOffer = controller::pasteClipboardOffer,
            onDismissClipboardOffer = controller::dismissClipboardOffer,
            idle = {},
            // 按内容撑开：两三个候选就是一张窄卡片，候选多了才长到上限再滚动。
            wrapContent = true,
            // 物理键盘在手：按 2 选第二颗，那颗前面就得写着 2。
            numbered = true,
        )
    }
}

/** 展开的第二层：语音 / 剪贴板 / 更多候选 / 符号页 / 数字页。 */
@Composable
private fun CompatExpandedPanel(state: ImeUiState, controller: ImeController) {
    val screenHeight = LocalConfiguration.current.screenHeightDp
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
    Box(modifier = Modifier.fillMaxWidth()) {
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

            // 符号页：**先保证每颗符号看得清**，再谈排布。列数按面板当前宽度算（一颗至少约
            // 56dp），窄了就少几列、多几行，滚动着取——绝不为了"一排放 7 个"把字挤扁。
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
                    columns = (maxWidth / SYMBOL_TILE_MIN_WIDTH).toInt().coerceIn(3, 8),
                )
            }

            // 符号页底部那颗 `123` 通向这里。物理键盘打数字是直接上屏的，但只用鼠标的用户没有
            // 键盘可打——面板里留着这条数字页，两条路都不落空。
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

/**
 * "两块面板需要「显示在其他应用上层」"这条提示。
 *
 * 只有用户自己能解决（去授权），所以它带一颗按钮；别的失败原因用户无能为力，不在这里说。
 */
@Composable
private fun CompatPermissionStrip(state: ImeUiState, controller: ImeController) {
    if (!state.compatNeedsOverlayPermission) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "候选栏与工具栏要分成两个窗口，需要「显示在其他应用上层」权限",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f, fill = false),
            )
            androidx.compose.material3.TextButton(onClick = controller::openOverlayPermissionSettings) {
                Text("去授权")
            }
        }
    }
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

private fun hasExpandedPanel(state: ImeUiState): Boolean = when {
    state.voice !is VoiceState.Idle && !state.holdToTalk -> true
    state.clipboardVisible -> true
    state.candidatesExpanded -> true
    state.page != KeyboardPage.Letters -> true
    else -> false
}

/**
 * 候选词卡片与展开面板的宽度上限。
 *
 * 上限存在的理由不是"排得下"，而是"别铺满整屏"：外接键鼠的面板浮在应用上面，一条横贯整个
 * 横屏的卡片既挡内容又和键盘腿一样宽，看着像键盘没关干净。窄内容（两三个候选）本来就撑不到
 * 上限，卡片按内容走。
 */
@Composable
private fun ImeUiState.contentMaxWidth(): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return (screenWidth * CONTENT_WIDTH_FRACTION).coerceIn(CONTENT_MIN_WIDTH, CONTENT_MAX_WIDTH)
}

@Composable
private fun ImeUiState.toolbarMaxWidth(): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return (screenWidth * TOOLBAR_WIDTH_FRACTION).coerceIn(TOOLBAR_MIN_WIDTH, TOOLBAR_MAX_WIDTH)
}

/** 悬浮卡片与屏幕边、卡片与卡片之间的距离：和悬浮键盘同一档。 */
private val PANEL_MARGIN = 10.dp
private val PANEL_MARGIN_VERTICAL = 6.dp
private val PANEL_GAP = 6.dp

/** 卡片圆角：与横屏悬浮键盘的 24dp 一致。 */
private val PANEL_CORNER = 24.dp

/** 符号页里一颗符号的最小宽度：低于这个数就把列数减一（先保证看得清，再谈排布）。 */
private val SYMBOL_TILE_MIN_WIDTH = 56.dp

/** 候选词卡片：内容多长就多长，最多占屏幕的这么多。 */
private const val CONTENT_WIDTH_FRACTION = 0.62f
private val CONTENT_MIN_WIDTH = 200.dp
private val CONTENT_MAX_WIDTH = 560.dp

/** 工具面板：一排工具，撑不到上限（上限只是兜底，免得某些字号下长出屏幕）。 */
private const val TOOLBAR_WIDTH_FRACTION = 0.9f
private val TOOLBAR_MIN_WIDTH = 160.dp
private val TOOLBAR_MAX_WIDTH = 420.dp
