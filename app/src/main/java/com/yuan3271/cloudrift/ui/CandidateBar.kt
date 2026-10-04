package com.yuan3271.cloudrift.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
 * 画淡、同样的一次性剪贴板提示），只是外面多套一个圆角外框；两边各写一份迟早会长歪。
 */
@Composable
internal fun CandidateStrip(
    state: ImeUiState,
    onCandidate: (Int) -> Unit,
    onExpand: () -> Unit,
    onPasteClipboardOffer: () -> Unit,
    onDismissClipboardOffer: () -> Unit,
    idle: @Composable () -> Unit,
) {
    if (state.candidates.isEmpty()) {
        // 刚复制进来的内容优先占这条栏：它是一次性的"要不要粘贴"，而 layout 名随时都在。
        // 一旦开始打字（有候选）就让位给候选，不用用户自己关。
        val offer = state.clipboardOffer
        if (offer != null) {
            ClipboardOfferStrip(
                text = offer.text.replace(OFFER_WHITESPACE, " ").trim(),
                onPaste = onPasteClipboardOffer,
                onDismiss = onDismissClipboardOffer,
            )
        } else {
            idle()
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(state.candidates.take(MAX_VISIBLE)) { index, candidate ->
                    CandidatePill(
                        candidate = candidate,
                        // The first candidate is the one space/enter would take - but only
                        // while something is being composed. A 联想 strip has no default, so
                        // its pills all carry the prediction tint.
                        emphasised = index == 0 && state.isComposing,
                        onClick = { onCandidate(index) },
                    )
                }
            }
            if (state.candidates.size > MAX_VISIBLE || state.candidatesExpanded) {
                IconButton(onClick = onExpand, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = CloudriftIcons.ExpandMore,
                        contentDescription = "更多候选",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
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
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
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
private fun CandidatePill(candidate: Candidate, emphasised: Boolean, onClick: () -> Unit) {
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
private val OFFER_WHITESPACE = Regex("\\s+")
