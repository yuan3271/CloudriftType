package com.yuan3271.cloudrift.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.BuildConfig
import com.yuan3271.cloudrift.data.ApiEndpoint
import com.yuan3271.cloudrift.data.ApiStyle
import com.yuan3271.cloudrift.data.CandidateOrder
import com.yuan3271.cloudrift.data.AppSettings
import com.yuan3271.cloudrift.data.KeyBackground
import com.yuan3271.cloudrift.data.KeyboardFrame
import com.yuan3271.cloudrift.data.ThemeMode
import com.yuan3271.cloudrift.data.ThemeSource
import com.yuan3271.cloudrift.data.UserStats
import com.yuan3271.cloudrift.data.UpdateInfo
import com.yuan3271.cloudrift.data.UpdateInterval
import com.yuan3271.cloudrift.input.KeyboardLayouts
import com.yuan3271.cloudrift.theme.hsvToColor
import com.yuan3271.cloudrift.ui.KeyPreviewRow
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    imeEnabled: Boolean = false,
    userStats: UserStats = UserStats(),
    update: UpdateInfo? = null,
    actions: SettingsActions,
) {
    val grantState = rememberMicrophoneGranted()
    var hasMicrophone by remember { mutableStateOf(grantState) }
    var confirmClearLearning by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasMicrophone = granted }

    LaunchedEffect(actions.requestMicrophoneOnStart) {
        if (actions.requestMicrophoneOnStart && !hasMicrophone) {
            permissionLauncher.launch(SettingsActivity.MICROPHONE_PERMISSION)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "云隙输入", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Cloudrift Type",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.finish) {
                        Icon(CloudriftIcons.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SetupCard(
                imeEnabled = imeEnabled,
                hasMicrophone = hasMicrophone,
                onEnableKeyboard = actions.openSystemKeyboardSettings,
                onPickKeyboard = actions.showKeyboardPicker,
                onGrantMicrophone = {
                    permissionLauncher.launch(SettingsActivity.MICROPHONE_PERMISSION)
                },
            )

            UpdateCard(
                update = update,
                interval = settings.updateCheckInterval,
                showDot = settings.showUpdateDot,
                onInterval = { value -> onUpdate { it.copy(updateCheckInterval = value) } },
                onShowDot = { value -> onUpdate { it.copy(showUpdateDot = value) } },
                onCheckNow = actions.checkForUpdate,
                onDownload = actions.downloadUpdate,
                onDownloadMirror = actions.downloadUpdateViaMirror,
                onOpenRelease = actions.openRelease,
            )

            SectionTitle("外观", CloudriftIcons.Palette)
            SettingsCard {
                Text(
                    text = "主题",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                ThemeModeSelector(
                    current = settings.themeMode,
                    onSelect = { mode -> onUpdate { it.copy(themeMode = mode) } },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "跟随系统时，键盘会随系统深浅色状态自动切换。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "配色来源",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                ThemeSourceSelector(
                    current = settings.themeSource,
                    onSelect = { source -> onUpdate { it.copy(themeSource = source) } },
                )
                if (settings.themeSource == ThemeSource.Custom) {
                    Spacer(Modifier.height(10.dp))
                    AccentPicker(
                        hue = settings.accentHue,
                        saturation = settings.accentSaturation,
                        onHue = { value -> onUpdate { it.copy(accentHue = value) } },
                        onSaturation = { value -> onUpdate { it.copy(accentSaturation = value) } },
                    )
                }
            }

            SectionTitle("键盘外观", CloudriftIcons.Settings)
            SettingsCard {
                // Same preview as the in-keyboard sheet, so a height or radius change is
                // visible here without having to switch to the keyboard to look at it.
                val screenHeight = LocalConfiguration.current.screenHeightDp
                val previewHeight = (
                    if (settings.keyHeightDp > 0) {
                        settings.keyHeightDp.toFloat()
                    } else {
                        KeyboardLayouts.autoKeyHeight(screenHeight)
                    }
                    ).dp
                Text(
                    text = "外观预览",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    KeyPreviewRow(
                        rows = listOf(KeyboardLayouts.previewRow()),
                        keyHeight = previewHeight,
                        cornerRadius = settings.keyCornerRadiusDp.dp,
                        keyBackground = settings.keyBackground,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "按键背景",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                KeyBackgroundSelector(
                    current = settings.keyBackground,
                    onSelect = { style -> onUpdate { it.copy(keyBackground = style) } },
                )
                Spacer(Modifier.height(14.dp))
                ValueSlider(
                    title = "按键圆角",
                    value = settings.keyCornerRadiusDp,
                    range = 4..26,
                    unit = "dp",
                    onChange = { value -> onUpdate { it.copy(keyCornerRadiusDp = value) } },
                )
                ValueSlider(
                    title = "按键字母大小",
                    value = settings.keyLabelScalePercent,
                    range = 80..140,
                    unit = "%",
                    onChange = { value -> onUpdate { it.copy(keyLabelScalePercent = value) } },
                )
                ValueSlider(
                    title = "键盘高度",
                    value = settings.keyHeightDp,
                    range = 0..72,
                    unit = "dp",
                    zeroLabel = "自动",
                    onChange = { value -> onUpdate { it.copy(keyHeightDp = value) } },
                )
                ValueSlider(
                    title = "距屏幕底部",
                    value = settings.bottomGapDp,
                    range = 0..48,
                    unit = "dp",
                    onChange = { value -> onUpdate { it.copy(bottomGapDp = value) } },
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "横屏键盘",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val frames = listOf(
                        KeyboardFrame.Floating to "悬浮",
                        KeyboardFrame.Full to "全屏",
                    )
                    frames.forEachIndexed { index, (frame, label) ->
                        SegmentedButton(
                            selected = settings.landscapeFrame == frame,
                            onClick = { onUpdate { it.copy(landscapeFrame = frame) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = frames.size),
                        ) {
                            Text(label)
                        }
                    }
                }
                if (settings.landscapeFrame == KeyboardFrame.Floating) {
                    ValueSlider(
                        title = "悬浮键盘宽度",
                        value = settings.floatingWidthPercent,
                        range = 45..100,
                        unit = "%",
                        onChange = { value -> onUpdate { it.copy(floatingWidthPercent = value) } },
                    )
                    ValueSlider(
                        title = "悬浮键盘按键高度",
                        value = settings.floatingKeyHeightDp,
                        range = 28..72,
                        unit = "dp",
                        onChange = { value -> onUpdate { it.copy(floatingKeyHeightDp = value) } },
                    )
                }
                Text(
                    text = "手机横屏时上方空间很少，所以默认弹出悬浮小键盘；拖卡片顶部可以移动、" +
                        "拖右下角可以缩放。悬浮尺寸与上面的「键盘高度 / 距屏幕底部」互不影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("键盘行为", CloudriftIcons.Keyboard)
            SettingsCard {
                SwitchRow(
                    title = "顶部数字行",
                    subtitle = "在字母行上方常驻一行数字",
                    checked = settings.showNumberRow,
                    onCheckedChange = { value -> onUpdate { it.copy(showNumberRow = value) } },
                )
                SwitchRow(
                    title = "上滑输入符号",
                    subtitle = "在字母键上向上滑动，输入它对应的符号",
                    checked = settings.swipeUpSymbols,
                    onCheckedChange = { value -> onUpdate { it.copy(swipeUpSymbols = value) } },
                )
                SwitchRow(
                    title = "空格滑动移动光标",
                    subtitle = "按住空格左右拖动可以移动光标",
                    checked = settings.spaceCursorControl,
                    onCheckedChange = { value -> onUpdate { it.copy(spaceCursorControl = value) } },
                )
                SwitchRow(
                    title = "长按空格语音输入",
                    subtitle = "按住空格说话，松手结束并开始识别",
                    checked = true,
                    onCheckedChange = { },
                    enabled = false,
                )
                SwitchRow(
                    title = "启用日文输入",
                    subtitle = "开启后语言键会在 中 → 英 → 日 之间循环；关闭时只在 中 ⇄ 英 之间切换",
                    checked = settings.japaneseEnabled,
                    onCheckedChange = { value -> onUpdate { it.copy(japaneseEnabled = value) } },
                )
            }

            SectionTitle("候选词顺序", CloudriftIcons.Spellcheck)
            SettingsCard {
                Text(
                    text = "一句话里有多个词时，候选怎么排。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val orders = listOf(
                        CandidateOrder.LongFirst to "长句优先",
                        CandidateOrder.CharacterFirst to "单字优先",
                    )
                    orders.forEachIndexed { index, (order, label) ->
                        SegmentedButton(
                            selected = settings.candidateOrder == order,
                            onClick = { onUpdate { it.copy(candidateOrder = order) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = orders.size),
                        ) {
                            Text(label)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (settings.candidateOrder == CandidateOrder.LongFirst) {
                        "先给整句（shishizhege → 实施这个），再给组成它的词（实施 / 试试），最后才是单字。"
                    } else {
                        "先给单字（shizhege → 是），再给同音的词组与整句。两个模式随时可切。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("自学习", CloudriftIcons.Spellcheck)
            SettingsCard {
                Text(
                    text = "键盘只观察你实际选了哪些候选：同一个码选过两次以上，下次它就排在前面；" +
                        "逐字拼出的新词（比如张 + 伟）会被记住。全部在手机本地计算，不联网。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = "自学习输入习惯",
                    subtitle = "关闭后不再记录新的习惯，已有记录仍然生效",
                    checked = settings.learningEnabled,
                    onCheckedChange = { value -> onUpdate { it.copy(learningEnabled = value) } },
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "已学习 ${userStats.learnedCommits} 次上屏 · " +
                            "${userStats.habits} 条习惯 · ${userStats.inventedWords} 个自造词",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { confirmClearLearning = true }) { Text("清除记录") }
                }
                Spacer(Modifier.height(10.dp))
                LearningTransferRows(
                    exportLearning = actions.exportLearning,
                    importLearning = actions.importLearning,
                )
            }

            SectionTitle("输入体验", CloudriftIcons.Keyboard)
            SettingsCard {
                SwitchRow(
                    title = "按键震动",
                    subtitle = "每次按键给出轻微触感反馈",
                    checked = settings.hapticFeedback,
                    onCheckedChange = { value -> onUpdate { it.copy(hapticFeedback = value) } },
                )
                SwitchRow(
                    title = "按键音",
                    subtitle = "使用系统的点击音效",
                    checked = settings.soundFeedback,
                    onCheckedChange = { value -> onUpdate { it.copy(soundFeedback = value) } },
                )
            }

            SectionTitle("语音输入", CloudriftIcons.Mic)
            SettingsCard {
                Text(
                    text = "录音会先发送到语音 API 转成文字，再交给聊天 API 做纠错。" +
                        "聊天 API 被要求不得改写、润色或翻译，返回结果超出纠错范围时会被丢弃。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    title = "启用聊天 API 纠错",
                    subtitle = "关闭后只使用语音 API 的原始结果",
                    checked = settings.voiceCorrection,
                    onCheckedChange = { value -> onUpdate { it.copy(voiceCorrection = value) } },
                )
                SwitchRow(
                    title = "自动补充句末标点",
                    subtitle = "缺句末标点时补一个；句末句号是否合适由 AI 纠错判断，不合适会去掉",
                    checked = settings.autoPunctuation,
                    onCheckedChange = { value -> onUpdate { it.copy(autoPunctuation = value) } },
                )
                ValueSlider(
                    title = "识别结果自动上屏",
                    value = settings.voiceAutoApplyDelayMs / 1000,
                    range = 0..5,
                    unit = " 秒",
                    zeroLabel = "手动",
                    onChange = { value ->
                        onUpdate { it.copy(voiceAutoApplyDelayMs = value * 1000) }
                    },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "纠错完成后停顿这么久再自动上屏；期间点「取消 / 重说 / 上屏」都会接管。" +
                        "选「手动」则一直等确认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SpeechEndpointCard(
                endpoint = settings.speech,
                hotWords = settings.hotWords,
                onHotWords = { value -> onUpdate { it.copy(hotWords = value) } },
                onChange = { endpoint -> onUpdate { it.copy(speech = endpoint) } },
            )

            ChatEndpointCard(
                endpoint = settings.chat,
                onChange = { endpoint -> onUpdate { it.copy(chat = endpoint) } },
            )

            SectionTitle("关于", CloudriftIcons.Info)
            SettingsCard {
                LabelValue("版本", BuildConfig.VERSION_NAME)
                LabelValue("图标", "云隙图标集（自绘）")
                LabelValue("中文词库", "jieba 词频 + THUOCL 领域词 + pinyin-data 读音 + pypinyin 词条读音（均 MIT）")
                LabelValue("联想语料", "自撰口语语料 + Tatoeba 中文句子（CC BY 2.0 FR）")
                LabelValue("界面", "Material 3 Expressive")
                ThirdPartyNoticeRow()
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClearLearning) {
        // 学习记录是几千次上屏攒出来的，清掉不可恢复；一次误触就抹掉它，代价和"多点一下"不成
        // 比例，所以这里要一次明确的确认。
        AlertDialog(
            onDismissRequest = { confirmClearLearning = false },
            title = { Text("清除学习记录？") },
            text = {
                Text(
                    "将删除全部 ${userStats.learnedCommits} 次上屏的学习结果、" +
                        "${userStats.habits} 条习惯和 ${userStats.inventedWords} 个自造词，无法恢复。" +
                        "建议先导出备份。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.clearLearning()
                        confirmClearLearning = false
                    },
                ) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearLearning = false }) { Text("取消") }
            },
        )
    }
}

/**
 * The loud half of the update notice: the version that was found, directly under the enable card
 * where the eye already is, with the two buttons and the check frequency.
 */
@Composable
private fun UpdateCard(
    update: UpdateInfo?,
    interval: UpdateInterval,
    showDot: Boolean,
    onInterval: (UpdateInterval) -> Unit,
    onShowDot: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onDownload: () -> Unit,
    onDownloadMirror: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (update != null) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = CloudriftIcons.Refresh,
                    contentDescription = null,
                    tint = if (update != null) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (update != null) {
                        "检测到新版本 ${update.versionName}"
                    } else {
                        "已是最新版本"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "当前 ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (update != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "覆盖安装即可，设置、自学习记录和剪贴板历史都会保留。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onDownload) { Text("下载更新") }
                    OutlinedButton(onClick = onDownloadMirror) { Text("加速下载") }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenRelease) { Text("查看发布页") }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "GitHub 直连慢或打不开时，用「加速下载」经 ghproxy 镜像取同一个安装包。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "检测频率",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    UpdateInterval.Never to "不检测",
                    UpdateInterval.Daily to "每天",
                    UpdateInterval.Weekly to "每周",
                    UpdateInterval.Monthly to "每月",
                )
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = interval == value,
                        onClick = { onInterval(value) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            SwitchRow(
                title = "在键盘上显示黄色提示",
                subtitle = "关掉后只在设置页提示新版本",
                checked = showDot,
                onCheckedChange = onShowDot,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCheckNow) { Text("立即检测") }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "版本来自 GitHub Releases",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SetupCard(
    imeEnabled: Boolean,
    hasMicrophone: Boolean,
    onEnableKeyboard: () -> Unit,
    onPickKeyboard: () -> Unit,
    onGrantMicrophone: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (imeEnabled) "输入法启用成功" else "启用云隙输入",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (imeEnabled) {
                    "当前正在使用云隙输入。下面可以调整外观、按键和语音接口。"
                } else {
                    "在系统设置里勾选「云隙输入」，然后切换过去即可使用。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEnableKeyboard, modifier = Modifier.weight(1f)) {
                    Text("去启用")
                }
                OutlinedButton(onClick = onPickKeyboard, modifier = Modifier.weight(1f)) {
                    Text("切换输入法")
                }
            }
            if (!hasMicrophone) {
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "语音输入需要麦克风权限",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onGrantMicrophone) { Text("授权") }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ThemeSourceSelector(current: ThemeSource, onSelect: (ThemeSource) -> Unit) {
    val options = listOf(
        Triple(ThemeSource.Dynamic, "跟壁纸", CloudriftIcons.Palette),
        Triple(ThemeSource.Cloudrift, "云隙蓝", CloudriftIcons.Cloud),
        Triple(ThemeSource.Custom, "自定义", CloudriftIcons.Palette),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (source, label, icon) ->
            SegmentedButton(
                selected = source == current,
                onClick = { onSelect(source) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            ) {
                Text(label)
            }
        }
    }
}

/** Hue and saturation, previewed live; the schemes themselves are generated in theme/Color.kt. */
@Composable
private fun AccentPicker(
    hue: Int,
    saturation: Int,
    onHue: (Int) -> Unit,
    onSaturation: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = hue.toFloat(),
            onValueChange = { onHue(it.roundToInt()) },
            valueRange = 0f..359f,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(hsvToColor(hue.toFloat(), saturation / 100f, 0.55f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "鲜艳度",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Slider(
            value = saturation.toFloat(),
            onValueChange = { onSaturation(it.roundToInt()) },
            valueRange = 10f..85f,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KeyBackgroundSelector(current: KeyBackground, onSelect: (KeyBackground) -> Unit) {
    val options = listOf(
        KeyBackground.Filled to "填充",
        KeyBackground.Outlined to "描边",
        KeyBackground.Ghost to "无底",
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (style, label) ->
            SegmentedButton(
                selected = style == current,
                onClick = { onSelect(style) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun ValueSlider(
    title: String,
    value: Int,
    range: IntRange,
    unit: String,
    zeroLabel: String? = null,
    onChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (value == 0 && zeroLabel != null) zeroLabel else "$value$unit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun ThemeModeSelector(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        Triple(ThemeMode.System, "跟随系统", CloudriftIcons.AutoMode),
        Triple(ThemeMode.Light, "浅色", CloudriftIcons.Sun),
        Triple(ThemeMode.Dark, "深色", CloudriftIcons.Moon),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label, icon) ->
            SegmentedButton(
                selected = mode == current,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun SpeechEndpointCard(
    endpoint: ApiEndpoint,
    hotWords: String,
    onHotWords: (String) -> Unit,
    onChange: (ApiEndpoint) -> Unit,
) {
    SectionTitle("语音识别 API", CloudriftIcons.Mic)
    SettingsCard {
        Text(
            text = "阿里云百炼的 OpenAI 兼容模式只有对话接口，语音识别走千问的 " +
                "多模态接口：音频以 Base64 内联上传，不需要公网地址。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        ProviderSelector(
            current = endpoint.style,
            onSelect = { style -> onChange(endpoint.copy(style = style)) },
        )
        Spacer(Modifier.height(10.dp))
        ConfirmablePresetRow(
            endpoint = endpoint,
            presets = listOf(
                "OpenAI" to AppSettings.openAiSpeechPreset(),
                "阿里云千问" to AppSettings.aliyunSpeechPreset(),
            ),
            onChange = onChange,
        )
        Spacer(Modifier.height(12.dp))

        when (endpoint.style) {
            ApiStyle.OpenAiCompatible -> {
                EndpointField(
                    value = endpoint.baseUrl,
                    label = "Base URL",
                    placeholder = AppSettings.DEFAULT_SPEECH_BASE_URL,
                    onChange = { onChange(endpoint.copy(baseUrl = it)) },
                )
                SecretField(
                    value = endpoint.apiKey,
                    label = "API Key",
                    onChange = { onChange(endpoint.copy(apiKey = it)) },
                )
                EndpointField(
                    value = endpoint.model,
                    label = "模型",
                    placeholder = AppSettings.DEFAULT_SPEECH_MODEL,
                    onChange = { onChange(endpoint.copy(model = it)) },
                )
            }

            ApiStyle.AliyunQianwen -> {
                EndpointField(
                    value = endpoint.baseUrl,
                    label = "Base URL",
                    placeholder = AppSettings.QIANWEN_BASE_URL,
                    onChange = { onChange(endpoint.copy(baseUrl = it)) },
                )
                SecretField(
                    value = endpoint.apiKey,
                    label = "API Key",
                    onChange = { onChange(endpoint.copy(apiKey = it)) },
                )
                EndpointField(
                    value = endpoint.model,
                    label = "模型",
                    placeholder = AppSettings.QIANWEN_DEFAULT_SPEECH_MODEL,
                    onChange = { onChange(endpoint.copy(model = it)) },
                )
            }
        }

        EndpointField(
            value = endpoint.languageHint,
            label = "语言提示（可选）",
            placeholder = "zh / ja / en",
            onChange = { onChange(endpoint.copy(languageHint = it)) },
        )

        if (endpoint.style == ApiStyle.AliyunQianwen) {
            OutlinedTextField(
                value = hotWords,
                onValueChange = onHotWords,
                label = { Text("即时热词（可选）") },
                placeholder = { Text("云隙输入:5, 张三:3") },
                supportingText = {
                    Text("一行一个或用逗号分隔；权重 1–5，写 50 表示超级热词（最多 50 个）")
                },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Key / Token 只保存在本机应用私有目录，并已排除在自动备份与换机迁移之外。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatEndpointCard(endpoint: ApiEndpoint, onChange: (ApiEndpoint) -> Unit) {
    SectionTitle("文本修正 API", CloudriftIcons.Spellcheck)
    SettingsCard {
        Text(
            text = "OpenAI 兼容的 /chat/completions，只用来纠正识别错字。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        ConfirmablePresetRow(
            endpoint = endpoint,
            presets = listOf(
                "OpenAI" to AppSettings.openAiChatPreset(),
                "阿里云百炼" to AppSettings.dashScopeChatPreset(),
            ),
            onChange = onChange,
        )
        Spacer(Modifier.height(12.dp))
        EndpointField(
            value = endpoint.baseUrl,
            label = "Base URL",
            placeholder = AppSettings.DEFAULT_CHAT_BASE_URL,
            onChange = { onChange(endpoint.copy(baseUrl = it)) },
        )
        SecretField(
            value = endpoint.apiKey,
            label = "API Key",
            onChange = { onChange(endpoint.copy(apiKey = it)) },
        )
        EndpointField(
            value = endpoint.model,
            label = "模型",
            placeholder = AppSettings.DEFAULT_CHAT_MODEL,
            onChange = { onChange(endpoint.copy(model = it)) },
        )
    }
}

@Composable
private fun ProviderSelector(current: ApiStyle, onSelect: (ApiStyle) -> Unit) {
    val options = listOf(
        ApiStyle.OpenAiCompatible to "OpenAI 兼容",
        ApiStyle.AliyunQianwen to "阿里云千问",
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (style, label) ->
            SegmentedButton(
                selected = style == current,
                onClick = { onSelect(style) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label)
            }
        }
    }
}

/**
 * 服务商预设按一下就会把 Base URL 和模型换掉，所以先问一句再动手.
 *
 * 之所以必须确认：这两个按钮长得像"切换显示"（旁边就是一模一样的两格选择器），实际却是**写入**
 * 操作。手滑点到另一家，正在用的地址和模型就换了，而换了以后键盘不会报错，只会在你下次说话时
 * 用了错的接口——这类误操作必须挡在点击和生效之间。
 *
 * 文案说的是实话：ApiEndpoint.merge 只覆盖 Base URL 和模型，用户自己填的 API Key 不受影响
 * （见 merge 的实现），所以这里不吓唬人说 Key 会丢。
 */
@Composable
private fun ConfirmablePresetRow(
    endpoint: ApiEndpoint,
    presets: List<Pair<String, ApiEndpoint>>,
    onChange: (ApiEndpoint) -> Unit,
) {
    var pending by remember { mutableStateOf<Pair<String, ApiEndpoint>?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { (label, preset) ->
            OutlinedButton(onClick = { pending = label to preset }) { Text(label) }
        }
    }
    pending?.let { (label, preset) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("切换到 $label 预设？") },
            text = {
                Text(
                    "会把 Base URL 和模型换成 $label 的预设，已经填好的 API Key 会保留。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(endpoint.merge(preset))
                        pending = null
                    },
                ) {
                    Text("切换")
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EndpointField(
    value: String,
    label: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.trim()) },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

@Composable
private fun SecretField(value: String, label: String, onChange: (String) -> Unit) {
    var reveal by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.trim()) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (reveal) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { reveal = !reveal }) {
                Icon(
                    imageVector = if (reveal) CloudriftIcons.Info else CloudriftIcons.Key,
                    contentDescription = if (reveal) "隐藏" else "显示",
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

/** Presets never clobber credentials the user already typed. */
private fun ApiEndpoint.merge(preset: ApiEndpoint) = copy(
    baseUrl = preset.baseUrl,
    model = preset.model.ifBlank { model },
    style = preset.style,
)

@Composable
private fun LabelValue(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 第三方资源声明.
 *
 * The licence text ships *inside* the APK (`assets/NOTICE.md`) and is shown here, because the
 * attribution a CC BY corpus requires has to travel with the app that uses it - a NOTICE.md in
 * the repository does not reach anyone who installs the keyboard. The asset is a copy of the
 * repository's NOTICE.md; NoticeAssetTest fails the build if the two drift apart.
 */
@Composable
private fun ThirdPartyNoticeRow() {
    val context = LocalContext.current
    var notice by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                notice = runCatching {
                    context.assets.open("NOTICE.md").bufferedReader().use { it.readText() }
                }.getOrNull().orEmpty()
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "第三方资源声明",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = "查看完整许可与语料来源",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
    }

    notice?.let { text ->
        AlertDialog(
            onDismissRequest = { notice = null },
            confirmButton = {
                TextButton(onClick = { notice = null }) { Text("关闭") }
            },
            title = { Text("第三方资源声明") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}
