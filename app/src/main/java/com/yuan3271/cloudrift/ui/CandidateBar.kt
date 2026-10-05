package com.yuan3271.cloudrift.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import com.yuan3271.cloudrift.engine.Candidate
import com.yuan3271.cloudrift.engine.CandidateKind
import com.yuan3271.cloudrift.ime.ImeUiState
import com.yuan3271.cloudrift.input.KeyboardPage
import com.yuan3271.cloudrift.input.LayoutId
import com.yuan3271.cloudrift.ui.icons.CloudriftIcons

/**
 * A fixed height strip so the keyboard never changes size while typing. When there is
 * nothing to convert it shows the current layout instead of collapsing.
 */
@Composable
fun CandidateBar(
    state: ImeUiState,
    onCandidate: (Int) -> Unit,
    onExpand: () -> Unit,
    onPasteClipboardOffer: () -> Unit,
    onDismissClipboardOffer: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 什么都没有正在拼时这一条显示什么。虚拟键盘上是当前布局名；键鼠兼容面板上换一句"直接打字，
     * 候选显示在这里"（外接键盘的人还不知道候选会不会出来）。
     */
    idle: @Composable () -> Unit = { IdleStrip(state = state) },
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CANDIDATE_BAR_HEIGHT)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        CandidateStrip(
            state = state,
            onCandidate = onCandidate,
            onExpand = onExpand,
            onPasteClipboardOffer = onPasteClipboardOffer,
            onDismissClipboardOffer = onDismissClipboardOffer,
            idle = idle,
        )
    }
}

/**
 * 这条栏里装的东西：候选词、剪贴板提示，或者空闲时那行字。
 *
 * 单独抽出来是因为键鼠兼容面板要的是**一模一样**的一条（同样的候选胶囊、同样把没打完的部分
 * 画淡、候选前面同样标着 1-9），只是外面多套一个圆角外框；两边各写一份迟早会长歪。
 * 唯一的差别是那条一次性剪贴板提示：键鼠模式下它不出现（见 [allowClipboardOffer]）。
 */
@Composable
internal fun CandidateStrip(
    state: ImeUiState,
    onCandidate: (Int) -> Unit,
    onExpand: () -> Unit,
    onPasteClipboardOffer: () -> Unit,
    onDismissClipboardOffer: () -> Unit,
    idle: @Composable () -> Unit,
    /**
     * 键鼠兼容面板里的那条：卡片按内容撑开，两三个候选就是一张窄卡片，候选多了才长到上限再滚动。
     *
     * 虚拟键盘上这条必须撑满窗口宽度（它顶着整块键盘的顶边），所以默认是 false。
     */
    wrapContent: Boolean = false,
    /**
     * 每颗候选前面标上 1-9。
     *
     * 键鼠兼容面板上是给**物理键盘**看的："按 2 选第二颗"这件事只有号码写在候选前面才成立
     * （见 ImeController.selectCandidateByDigit）。虚拟键盘上手指直接点候选，标号只是噪声。
     */
    numbered: Boolean = false,
    /**
     * 刚复制进来的那段内容要不要占这条栏（一次性的"要不要粘贴"）。
     *
     * 键鼠兼容面板上传 false（用户点名）：那边剪贴板只有一个入口——工具栏上的 📋，刚复制的内容
     * 不该自己把面板顶出来。虚拟键盘上照旧，点一下就是贴上。
     */
    allowClipboardOffer: Boolean = true,
) {
    if (state.candidates.isEmpty()) {
        // 刚复制进来的内容优先占这条栏：它是一次性的"要不要粘贴"，而 layout 名随时都在。
        // 一旦开始打字（有候选）就让位给候选，不用用户自己关。
        val offer = state.clipboardOffer.takeIf { allowClipboardOffer }
        if (offer != null) {
            ClipboardOfferStrip(
                text = offer.text.replace(OFFER_WHITESPACE, " ").trim(),
                onPaste = onPasteClipboardOffer,
                onDismiss = onDismissClipboardOffer,
                wrapContent = wrapContent,
            )
        } else {
            idle()
        }
    } else {
        val pills: @Composable () -> Unit = {
            state.candidates.take(MAX_VISIBLE).forEachIndexed { index, candidate ->
                CandidatePill(
                    candidate = candidate,
                    // 标号从 1 开始，和物理键盘上的数字键一一对应。
                    number = if (numbered && index < MAX_NUMBERED) index + 1 else null,
                    // The first candidate is the one space/enter would take - but only
                    // while something is being composed. A 联想 strip has no default, so
                    // its pills all carry the prediction tint.
                    emphasised = index == 0 && state.isComposing,
                    onClick = { onCandidate(index) },
                )
            }
        }
        val expandButton: @Composable () -> Unit = {
            // 箭头下面垫一层卡片底色：候选行被截断时，最后一颗候选会在箭头右边露出一条边，
            // 盖上底色之后"候选到这儿为止"才是干净的，箭头也正好贴着卡片右缘。
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(start = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onExpand, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = CloudriftIcons.ExpandMore,
                        contentDescription = "展开候选",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (wrapContent) {
            // 键鼠面板那条：卡片多宽由内容说了算，装不下就截断并把剩下的收进展开列表
            // （见 [WrapCandidateRow]）。
            WrapCandidateRow(expand = expandButton, pills = pills)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(PILL_GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(state.candidates.take(MAX_VISIBLE)) { index, candidate ->
                        CandidatePill(
                            candidate = candidate,
                            emphasised = index == 0 && state.isComposing,
                            onClick = { onCandidate(index) },
                        )
                    }
                }
                if (state.candidates.size > MAX_VISIBLE || state.candidatesExpanded) {
                    expandButton()
                }
            }
        }
    }
}

/**
 * 按内容撑开的候选行：候选少时卡片就这么窄；装不下时**自动截断**，并在右端留一颗「展开」，
 * 点它把剩下的候选摊开成下拉列表（[ExpandedCandidates]）。
 *
 * 为什么不用 `LazyRow`：Lazy 布局**总是把自己撑到可用宽度**（可用宽度就是卡片上限），于是"内容
 * 到底多宽"量不出来——卡片永远顶满上限，展开按钮还会被挤到可视区外，看起来正是"多余的候选词
 * 既没有截断、也没有下拉列表"。横向滚动容器同样不能用无限约束去量（Compose 会直接抛异常）。
 *
 * 所以这里只做两件事：先用**不限宽**的约束把候选那一行量一遍拿到真实内容宽度（普通 Row 允许无限
 * 约束），再决定这一帧是"内容宽，没有箭头"还是"上限宽，裁掉超出部分，右端放箭头"。
 */
@Composable
private fun WrapCandidateRow(
    expand: @Composable () -> Unit,
    pills: @Composable () -> Unit,
) {
    Layout(
        content = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PILL_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pills()
            }
            expand()
        },
        // 卡片的上限由外面那层 widthIn 给（窗口是按内容收紧的独立窗口），这里不再重复；
        // clipToBounds 把超出上限的候选真的裁掉——截断就该是截断，不是让卡片继续长。
        modifier = Modifier.clipToBounds(),
    ) { measurables, constraints ->
        val unbounded = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Int.MAX_VALUE)
        val content = measurables.firstOrNull()?.measure(unbounded)
        val contentWidth = content?.width ?: 0
        val height = (content?.height ?: 0).coerceAtLeast(constraints.minHeight)
        // 箭头按候选行的高度量（它自己用 fillMaxHeight 铺满，才好盖住被截断的那一截候选）。
        val button = measurables.getOrNull(1)?.measure(
            Constraints(minWidth = 0, maxWidth = Int.MAX_VALUE, minHeight = height, maxHeight = height),
        )
        val buttonWidth = button?.width ?: 0
        val available = constraints.maxWidth
        if (contentWidth + buttonWidth <= available) {
            // 全装得下：卡片宽度就取内容宽度，箭头不出现（它只是"还有别的候选"的入口）。
            layout(contentWidth, height) {
                content?.place(0, Alignment.CenterVertically.align(content.height, height))
            }
        } else {
            // 装不下：给箭头留出位置，超出的候选被裁掉，箭头永远贴着卡片右端可见。
            val pillsWidth = (available - buttonWidth).coerceAtLeast(0)
            layout(available, height) {
                content?.place(0, Alignment.CenterVertically.align(content.height, height))
                button?.place(pillsWidth, Alignment.CenterVertically.align(button.height, height))
            }
        }
    }
}

/**
 * 剪贴板提示：图标 + 刚复制的那段内容，点一下直接贴到光标处；右边的叉只把这条提示收掉，
 * 历史里那条仍在。提示一条内容只出现一次——贴上、关掉、或者用户直接开始打字，它就不再回来
 * （见 `ClipboardStore.capture`）。
 *
 * 内容压成一行——复制的多半是一段带换行的文字，候选栏只有 44dp，摊开就什么都不剩了。
 */
@Composable
private fun ClipboardOfferStrip(
    text: String,
    onPaste: () -> Unit,
    onDismiss: () -> Unit,
    wrapContent: Boolean = false,
) {
    Row(
        modifier = if (wrapContent) Modifier else Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = (if (wrapContent) Modifier.widthIn(max = 380.dp) else Modifier.weight(1f))
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onPaste)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = CloudriftIcons.Clipboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = CloudriftIcons.Close,
                contentDescription = "关闭剪贴板提示",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IdleStrip(state: ImeUiState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = when (state.page) {
                KeyboardPage.Letters -> layoutName(state.layout)
                KeyboardPage.Symbols -> "符号 · 上下滑动查看更多"
                // 第一列能上下滑这件事没有别的地方会告诉用户，闲置时就在这儿说一句。
                KeyboardPage.Numbers -> "数字 · 第一列可上下滑动"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.dictionaryReady && state.layout.isChinese && state.page == KeyboardPage.Letters) {
            LoadingIndicator(
                modifier = Modifier.size(14.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "词库加载中",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CandidatePill(
    candidate: Candidate,
    emphasised: Boolean,
    onClick: () -> Unit,
    /** 这颗候选在物理键盘上对应的数字键（1-9）；null 表示这一条不标号。 */
    number: Int? = null,
) {
    val target = when {
        emphasised -> MaterialTheme.colorScheme.primaryContainer
        candidate.kind == CandidateKind.Prediction -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val container by animateColorAsState(target, label = "candidate-container")
    val content = if (emphasised) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (number != null) {
                // 号要小、要淡：它是给眼睛找"该按哪个数字键"用的，不该跟候选抢注意力。
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.5f),
                )
            }
            Text(
                text = candidateLabel(candidate, dim = content.copy(alpha = 0.45f)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
                color = content,
            )
            if (candidate.annotation.isNotEmpty() && !candidate.annotation.any { it in '0'..'9' }) {
                Text(
                    text = candidate.annotation,
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/**
 * The candidate text, with the part the user has not typed yet in a lighter tone: after "nih"
 * the 你好 candidate draws 你 normally and 好 faded, because only the first character is fully
 * spelled out. Picking the candidate still commits the whole word.
 */
internal fun candidateLabel(candidate: Candidate, dim: Color): AnnotatedString {
    val text = candidate.display
    val cut = candidate.unmatchedFrom
    return when {
        // -1 means "everything here was typed"; 0 means "none of it was". Getting those two the
        // wrong way round dimmed every ordinary candidate.
        cut < 0 -> AnnotatedString(text)
        cut == 0 && text.isNotEmpty() -> AnnotatedString(text, SpanStyle(color = dim))
        cut in 1 until text.length -> buildAnnotatedString {
            append(text.substring(0, cut))
            withStyle(SpanStyle(color = dim)) { append(text.substring(cut)) }
        }
        else -> AnnotatedString(text)
    }
}

private val CANDIDATE_BAR_HEIGHT = 44.dp
private const val MAX_VISIBLE = 12
/** 标号只到 9：物理键盘上能当快捷键用的就这九个。 */
private const val MAX_NUMBERED = 9
/** 候选胶囊之间、以及胶囊与「展开」之间的间距。 */
private val PILL_GAP = 6.dp
private val OFFER_WHITESPACE = Regex("\\s+")
