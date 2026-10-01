package com.yuan3271.cloudrift.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.BuildConfig
import com.yuan3271.cloudrift.data.ApiEndpoint
import com.yuan3271.cloudrift.data.ApiStyle
import com.yuan3271.cloudrift.data.AppSettings
import com.yuan3271.cloudrift.data.KeyBackground
import com.yuan3271.cloudrift.data.KeyboardFrame
import com.yuan3271.cloudrift.data.ThemeMode
import com.yuan3271.cloudrift.data.ThemeSource
import com.yuan3271.cloudrift.data.UserStats
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
    actions: SettingsActions,
) {
    val grantState = rememberMicrophoneGranted()
    var hasMicrophone by remember { mutableStateOf(grantState) }
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
                    OutlinedButton(onClick = actions.clearLearning) { Text("清除记录") }
                }
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
                LabelValue("中文词库", "jieba 词频（MIT）+ pinyin4j 读音（BSD）")
                LabelValue("界面", "Material 3 Expressive")
            }

            Spacer(Modifier.height(24.dp))
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
        PresetRow(
            presets = listOf(
                "OpenAI" to { onChange(endpoint.merge(AppSettings.openAiSpeechPreset())) },
                "阿里云千问" to { onChange(endpoint.merge(AppSettings.aliyunSpeechPreset())) },
            ),
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
        PresetRow(
            presets = listOf(
                "OpenAI" to { onChange(endpoint.merge(AppSettings.openAiChatPreset())) },
                "阿里云百炼" to { onChange(endpoint.merge(AppSettings.dashScopeChatPreset())) },
            ),
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

@Composable
private fun PresetRow(presets: List<Pair<String, () -> Unit>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { (label, apply) ->
            OutlinedButton(onClick = apply) { Text(label) }
        }
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
        )
    }
}
