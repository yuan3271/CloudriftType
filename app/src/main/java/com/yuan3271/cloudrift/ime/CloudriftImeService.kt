package com.yuan3271.cloudrift.ime

import android.util.Log
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.core.view.WindowCompat
import androidx.core.graphics.drawable.toDrawable
import com.yuan3271.cloudrift.data.AppGraph
import com.yuan3271.cloudrift.input.CompatPanelKind
import com.yuan3271.cloudrift.input.ExternalInputMonitor
import com.yuan3271.cloudrift.input.InputWindowHost
import com.yuan3271.cloudrift.theme.CloudriftTheme
import com.yuan3271.cloudrift.ui.CompatContentWindow
import com.yuan3271.cloudrift.ui.CompatToolbarWindowContent
import com.yuan3271.cloudrift.ui.KeyboardRoot
import kotlin.math.roundToInt

/**
 * The input method itself. It owns nothing but the controller and the Compose surface; all
 * keyboard behaviour lives in [ImeController].
 */
class CloudriftImeService : LifecycleInputMethodService(), InputWindowHost {

    /**
     * Null only when construction blew up. An input method that fails to start simply never
     * appears, which is impossible to diagnose from the outside, so the failure is turned
     * into a visible message and a logcat entry instead.
     */
    private var controller: ImeController? = null

    /**
     * Where the floating keyboard sits, in window offsets; reset whenever the frame changes.
     * Kept as floats and rounded only when the window is updated: a slow drag moves a fraction of
     * a pixel per event, and truncating each event to an Int would drop those fractions - the card
     * would sit still and then jump, which reads as "the keyboard does not follow the finger".
     */
    private var floatingOffsetX = 0f
    private var floatingOffsetY = 0f
    private var floating = false
    private var floatingWidthPercent = DEFAULT_FLOATING_WIDTH

    /**
     * 键鼠兼容面板的两块面板各自的窗口（见 [createCompatWindow]）；开不出来的那块不在表里。
     *
     * 用户点名两块面板要"两窗分离"：候选词面板跟着光标、工具面板停在自己被拖到的地方，各自
     * 按内容撑开。所以它们不能画在输入法窗口里——输入法那个窗口的宽度是系统说了算的。
     */
    private val compatWindows = mutableMapOf<CompatPanelKind, CompatWindow>()

    /** 键鼠兼容面板：候选词窗口要不要跟着光标走，以及当前拿到的光标锚点（屏幕像素）。 */
    private var caretFollowing = false
    private var caretAnchor: android.graphics.PointF? = null
    /** 光标锚点每来一条就重摆一次窗口太吵，只记前几条，用来核对坐标空间对不对。 */
    private var caretLogs = 0

    /**
     * 外接键鼠的在场状态。它的回调只做一件事：把快照交给控制器，由控制器决定窗口里画虚拟键盘
     * 还是键鼠兼容面板（见 ImeController.onExternalInputs）。
     */
    private val externalInputs: ExternalInputMonitor by lazy {
        ExternalInputMonitor(this) { snapshot -> controller?.onExternalInputs(snapshot) }
    }

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        controller = runCatching { ImeController(this) }
            .onFailure { Log.e(TAG, "输入法初始化失败，键盘将显示错误提示", it) }
            .getOrNull()
        // 先读一次再挂监听：窗口第一次画出来的时候就得知道场上有没有键鼠，否则面板要等到用户
        // 下一次插拔才出现。
        controller?.onExternalInputs(externalInputs.refresh(notify = false))
        externalInputs.start()
    }

    override fun onCreateInputView(): View {
        AppGraph.init(this)
        // Must run before the view is attached: Compose looks the owners up from the window
        // content root when the input view is added to the window.
        attachComposeOwnersToWindow()
        // The keyboard paints its own surface. Without this the window's own background shows as a
        // thick frame around the rounded card (and around the floating card's margins).
        window?.window?.let { dialogWindow ->
            dialogWindow.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            // 悬浮键盘不会铺满窗口（卡片四周留着 10dp / 6dp 的边距），那些边距必须露出底下的
            // 应用。窗口格式要是 `PixelFormat.TRANSPARENT`（"没有 alpha 位、按不透明算"），
            // 没画过的地方就会按窗口自己的底色算——横屏时看起来就是卡片四周一圈黑底。
            // 明确要 TRANSLUCENT（带 alpha 的那种），这些边距才是真的透明。
            dialogWindow.setFormat(PixelFormat.TRANSLUCENT)
        }
        // The keyboard owns its own bottom inset, which is what lets the rounded surface sit
        // flush against a gesture navigation bar.
        window?.window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        return super.onCreateInputView()
    }

    /**
     * Frames the input window. A full width keyboard is a bar along the bottom; a floating one is a
     * narrower card that can be dragged, which is what landscape needs since a phone turned sideways
     * has almost no height left above a full width keyboard.
     */
    override fun applyInputFrame(floating: Boolean, widthPercent: Int) {
        val percent = widthPercent.coerceIn(MIN_FLOATING_WIDTH, MAX_FLOATING_WIDTH)
        if (this.floating != floating) {
            // Entering or leaving the floating frame starts centred; a resize must not, or the card
            // would jump back to the middle on every drag frame.
            floatingOffsetX = 0f
            floatingOffsetY = 0f
        }
        val previousWidth = floatingWindowWidth(this.floatingWidthPercent)
        val nextWidth = floatingWindowWidth(percent)
        if (floating && this.floating && previousWidth != nextWidth) {
            // The card is centred, so half of the growth would land on each side. The corner the
            // user is dragging is the *right* one, so the opposite (left) edge has to stay put:
            // that takes a shift of +half the difference. (It was minus, which pinned the right
            // edge instead - so the handle did not move at all when dragged sideways.)
            floatingOffsetX += (nextWidth - previousWidth) / 2f
        }
        this.floating = floating
        this.floatingWidthPercent = percent
        applyWindowLayout()
    }

    private fun floatingWindowWidth(percent: Int): Int =
        (resources.displayMetrics.widthPixels * percent / 100f).toInt()

    override fun moveInputWindowBy(dx: Float, dy: Float) {
        if (!floating) return
        floatingOffsetX += dx
        floatingOffsetY -= dy
        applyWindowLayout()
    }

    // ---- 键鼠兼容面板的两个窗口 --------------------------------------------------------

    /**
     * 键鼠兼容面板的两块（候选词、工具）各是**一个独立窗口**，不是画在输入法窗口里的两行。
     *
     * 为什么不能画在输入法窗口里：那个窗口的宽度是系统说了算的——这台机器上它是 `MATCH_PARENT`，
     * 画在里面的卡片只能跟着铺满整屏（用户报的"窗屏莫名其妙占满整个横屏"），而且两块卡片共用
     * 一个窗口，抓住一块拖另一块也跟着走（用户报的"没分成两个窗口"）。
     *
     * 窗口类型只能是 `TYPE_APPLICATION_OVERLAY`：
     * - `TYPE_INPUT_METHOD_DIALOG`（2012）走不通，它要 `INTERNAL_SYSTEM_WINDOW`（系统签名权限），
     *   日志里就是 `permission denied for window type 2012`；
     * - 再开一个 `TYPE_INPUT_METHOD`（2011）会把"这个显示的输入法窗口"顶掉，输入法 inset 跟着乱，
     *   代价比收益大。
     * 悬浮窗要用户授一次「显示在其他应用上层」（`SYSTEM_ALERT_WINDOW`）。没授权时不硬撑：返回
     * false，界面把两块面板画回输入法窗口里（功能一件不少，只是不能各拖各的），并给出授权入口。
     */
    override fun applyCompatPanel(
        kind: CompatPanelKind,
        visible: Boolean,
        xPercent: Int,
        yPercent: Int,
    ): Boolean {
        val window = compatWindows[kind] ?: run {
            // 没建过就现在建——**哪怕这次不用显示**。两块面板能不能各占一个窗口只有建了才知道，
            // 而这个答案决定界面走"两块独立窗口"还是"画回输入法窗口"那条路；藏着不建等于把
            // 候选词面板的第一次出现押在一次 addView 上。
            val created = createCompatWindow(kind, startHidden = !visible) ?: return false
            compatWindows[kind] = created
            created
        }
        window.hintXPercent = xPercent
        window.hintYPercent = yPercent
        if (window.visible != visible) {
            window.visible = visible
            // 只是收起来，不拆窗口：候选词面板在键鼠模式下是"打字才出现、打完就收"，每次重建
            // 都要重新量一遍、重新合成一遍，出现得慢半拍。
            window.view.visibility = if (visible) View.VISIBLE else View.GONE
        }
        if (!visible) return true
        placeCompatWindow(window)
        return true
    }

    /**
     * 拖动把手。
     *
     * 两块面板的"位置"是两种东西，所以位移落在两个地方：
     * - 候选词面板跟着光标走，所以拖出来的是**相对光标**的偏移（拖开一点，光标再走它还在旁边，
     *   不是"脱开光标"）；
     * - 工具面板没有光标可跟，拖出来的是屏幕上的绝对位置，拖到哪停在哪、下次开键盘还认得。
     */
    override fun moveCompatPanelBy(kind: CompatPanelKind, dx: Float, dy: Float) {
        val window = compatWindows[kind] ?: return
        if (kind == CompatPanelKind.Content) {
            window.nudgeX += dx
            window.nudgeY += dy
            placeCompatWindow(window)
        } else {
            window.x += dx
            window.y += dy
            applyCompatWindowPosition(window)
        }
    }

    override fun commitCompatPanelPosition(kind: CompatPanelKind) {
        // 只有工具面板需要记住位置（候选词面板的位置由光标决定，拖出来的是相对偏移，不值得记）。
        if (kind != CompatPanelKind.Toolbar) return
        val window = compatWindows[kind] ?: return
        val metrics = resources.displayMetrics
        controller?.onCompatToolbarMoved(
            (window.x / metrics.widthPixels * 100f).roundToInt().coerceIn(0, 100),
            (window.y / metrics.heightPixels * 100f).roundToInt().coerceIn(0, 100),
        )
    }

    override fun removeCompatPanels() {
        compatWindows.values.forEach { window ->
            runCatching { window.windowManager.removeViewImmediate(window.view) }
                .onFailure { Log.w(TAG, "键鼠面板窗口拆不掉", it) }
        }
        compatWindows.clear()
    }

    private fun createCompatWindow(kind: CompatPanelKind, startHidden: Boolean): CompatWindow? {
        val active = controller ?: return null
        val context = overlayContext()
        val manager = context.getSystemService(WINDOW_SERVICE) as? WindowManager ?: run {
            Log.w(TAG, "拿不到悬浮窗的 WindowManager，键鼠面板退回输入法窗口")
            return null
        }
        val view = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@CloudriftImeService)
            setViewTreeViewModelStoreOwner(this@CloudriftImeService)
            setViewTreeSavedStateRegistryOwner(this@CloudriftImeService)
            if (startHidden) visibility = View.GONE
        }
        view.setContent {
            when (kind) {
                CompatPanelKind.Content -> CompatContentWindow(active)
                CompatPanelKind.Toolbar -> CompatToolbarWindowContent(active)
            }
        }
        // WRAP_CONTENT：窗口自己收紧到面板大小——卡片按内容撑开、没有可以点空的区域，这正是
        // "窗口自适应内容"。位置由 applyCompatWindowPosition 逐像素摆，所以重力固定成左上角。
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                // 面板以外的地方点击照旧落到下面的应用（输入法不该接管整屏触摸）。
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        val added = runCatching {
            manager.addView(view, params)
            true
        }.getOrElse {
            Log.w(TAG, "键鼠面板窗口开不出来（$kind），退回画在输入法窗口里", it)
            false
        }
        if (!added) return null
        val window = CompatWindow(
            kind = kind,
            view = view,
            windowManager = manager,
            params = params,
            visible = !startHidden,
        )
        // 面板按内容撑开，宽度高度会随候选多少变。量一次、变一次就重摆一次，窗口才不会在
        // 内容变高之后半个身子探到屏幕外（或者压住光标那一行）。
        view.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val width = (right - left).toFloat()
            val height = (bottom - top).toFloat()
            if (width <= 0f || height <= 0f) return@addOnLayoutChangeListener
            if (width == window.width && height == window.height) return@addOnLayoutChangeListener
            window.width = width
            window.height = height
            if (window.visible) placeCompatWindow(window)
        }
        return window
    }

    /**
     * 悬浮窗要一个"窗口上下文"，而且类型必须与窗口类型一致：拿服务的上下文（窗口类型是输入法的
     * 2011）去加 2038 的窗口，会被 `IncorrectContextUseViolation` 拦下。
     *
     * API 30 以下没有 `createWindowContext`：那时也还没有这条类型检查，直接用服务上下文。
     */
    private fun overlayContext(): Context =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
            this
        }

    /**
     * 把一块面板摆到它该在的地方。
     *
     * 候选词面板：跟着光标（编辑器每报一次锚点就走一遍这条路），光标下面放不下就翻到上面；
     * 拿不到锚点就退回贴屏幕底部中间。工具面板：第一次按记住的位置（没记住＝底部居中），之后
     * 用户拖到哪就在哪——所以它不走光标那条路。
     */
    private fun placeCompatWindow(window: CompatWindow) {
        // 面板还没量出来时不能摆：默认位置（底部居中）与"下面放不下就翻到上面"都要用尺寸，
        // 拿 0 去算会把它放在屏幕正中间那条线上。等第一帧布局完，尺寸自然就来了——
        // 那一刻 createCompatWindow 里挂的布局监听会再调一次这里。
        if (window.width <= 0f || window.height <= 0f) {
            // 最多补几次：面板的尺寸是布局给的，正常情况一帧就有；万一某台机器上一直量不出来，
            // 也不能在这儿无限重投消息。
            if (window.placementAttempts < COMPAT_PLACEMENT_ATTEMPTS) {
                window.placementAttempts++
                window.view.post { if (window.visible) placeCompatWindow(window) }
            }
            return
        }
        window.placementAttempts = 0
        val metrics = resources.displayMetrics
        val inset = systemBarBottomInset()
        val bottomLimit = (metrics.heightPixels - inset).toFloat()
        val anchor = caretAnchor.takeIf {
            caretFollowing && window.kind == CompatPanelKind.Content
        }
        when {
            anchor != null -> {
                val below = anchor.y + CARET_GAP_Y_PX + window.nudgeY
                val above = anchor.y - window.height - CARET_LINE_PX + window.nudgeY
                window.x = anchor.x + CARET_GAP_X_PX + window.nudgeX
                window.y = if (window.height > 0f && below + window.height > bottomLimit) above else below
            }

            window.kind == CompatPanelKind.Content -> {
                window.x = (metrics.widthPixels - window.width) / 2f + window.nudgeX
                window.y = bottomLimit - window.height - FALLBACK_MARGIN_PX + window.nudgeY
            }

            !window.positioned -> {
                window.x = if (window.hintXPercent >= 0) {
                    metrics.widthPixels * window.hintXPercent / 100f
                } else {
                    (metrics.widthPixels - window.width) / 2f
                }
                window.y = if (window.hintYPercent >= 0) {
                    metrics.heightPixels * window.hintYPercent / 100f
                } else {
                    bottomLimit - window.height - FALLBACK_MARGIN_PX
                }
                window.positioned = true
            }
            // 已经被拖过：位置归用户，不要因为一次同步把它拽回记住的那个百分比。
        }
        window.x = window.x.coerceIn(0f, (metrics.widthPixels - window.width).coerceAtLeast(0f))
        window.y = window.y.coerceIn(0f, (bottomLimit - window.height).coerceAtLeast(0f))
        applyCompatWindowPosition(window)
    }

    private fun applyCompatWindowPosition(window: CompatWindow) {
        window.params.gravity = Gravity.TOP or Gravity.START
        window.params.x = window.x.roundToInt()
        window.params.y = window.y.roundToInt()
        runCatching { window.windowManager.updateViewLayout(window.view, window.params) }
            .onFailure { Log.w(TAG, "键鼠面板摆位失败（${window.kind}）", it) }
    }

    /** 光标锚点变了：候选词面板要跟着走（工具面板不动）。 */
    private fun placeCompatContentWindow() {
        val window = compatWindows[CompatPanelKind.Content] ?: return
        if (!window.visible) return
        placeCompatWindow(window)
    }

    /**
     * 键鼠兼容面板里的一块的窗口，连同它自己的位置。
     *
     * 尺寸在**窗口层**量（`WRAP_CONTENT` 下窗口的宽高就是面板的宽高），Compose 那边只管画，
     * 这样"窗口自适应内容"这件事只有一处真相。
     */
    private class CompatWindow(
        val kind: CompatPanelKind,
        val view: ComposeView,
        val windowManager: WindowManager,
        val params: WindowManager.LayoutParams,
        visible: Boolean,
    ) {
        var visible = visible
        /** 左上角在屏幕上的位置（像素）。 */
        var x = 0f
        var y = 0f
        /** 相对默认位置（候选词＝光标，工具面板＝记住的位置）拖出来的偏移。 */
        var nudgeX = 0f
        var nudgeY = 0f
        /** 量到的面板尺寸；0 表示还没量到。 */
        var width = 0f
        var height = 0f
        /** 工具面板：位置已经定过一次（记住的位置或默认位置），之后归用户拖。 */
        var positioned = false
        /** 最近一次同步给过来的位置提示（屏幕百分比，负数＝没记住）。 */
        var hintXPercent = -1
        var hintYPercent = -1
        /** 面板还没量到时补摆位置的次数。 */
        var placementAttempts = 0
    }

    private fun systemBarBottomInset(): Int =
        runCatching {
            val decor = window?.window?.decorView ?: return 0
            decor.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.systemBars())?.bottom ?: 0
        }.getOrDefault(0)

    private val windowManager: WindowManager
        get() = getSystemService(WINDOW_SERVICE) as WindowManager

    private fun applyWindowLayout() {
        // 键鼠兼容模式下窗口里只有一块 1dp 的透明壳（两块面板各自在 [compatWindows] 里），
        // 这里的宽度、位置都只跟虚拟键盘有关：壳本身不占屏，摆在哪都一样。
        val dialog = window ?: return
        val attributes = dialog.window?.attributes ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        attributes.width = if (floating) {
            (screenWidth * floatingWidthPercent / 100f).toInt()
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }
        attributes.height = ViewGroup.LayoutParams.WRAP_CONTENT
        val anchor = caretAnchor.takeIf { caretFollowing && floating }
        attributes.gravity = if (anchor != null) {
            // 跟随光标：窗口按光标定位，左上角＝锚点 + 用户拖出来的那点偏移（拖动是"相对光标的挪动"，
            // 不是"脱离光标"，所以下次光标一动它仍然跟着）。
            Gravity.TOP or Gravity.START
        } else if (floating) {
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        } else {
            Gravity.BOTTOM
        }
        val metrics = resources.displayMetrics
        if (anchor != null) {
            val decorHeight = dialog.window?.decorView?.height ?: 0
            val maxX = (metrics.widthPixels - attributes.width).coerceAtLeast(0)
            attributes.x = (anchor.x + CARET_GAP_X_PX + floatingOffsetX)
                .roundToInt()
                .coerceIn(0, maxX)
            // 光标下面放得下就放下面，放不下（贴着屏幕底部）就翻到光标上面一行。
            val below = anchor.y + CARET_GAP_Y_PX + floatingOffsetY
            val above = anchor.y - decorHeight - CARET_LINE_PX
            val limit = metrics.heightPixels - systemBarBottomInset()
            attributes.y = (
                if (decorHeight > 0 && below + decorHeight > limit) above else below
                )
                .roundToInt()
                .coerceIn(0, (limit - decorHeight).coerceAtLeast(0))
        } else {
            attributes.x = if (floating) floatingOffsetX.roundToInt() else 0
            attributes.y = if (floating) floatingOffsetY.roundToInt() else 0
        }
        val decorView = dialog.window?.decorView ?: return
        runCatching {
            (getSystemService(WINDOW_SERVICE) as? android.view.WindowManager)
                ?.updateViewLayout(decorView, attributes)
        }.onFailure { Log.w(TAG, "无法调整输入法窗口布局", it) }
    }

    // ---- 候选词面板跟随光标 ------------------------------------------------------------

    /**
     * 键鼠兼容面板出现时开始向编辑器要光标锚点（`CURSOR_UPDATE_MONITOR`），收起时停掉并回到
     * "贴屏幕底部"。
     *
     * 编辑器不支持（`requestCursorUpdates` 返回 false / 从不回调）时什么都不会发生：窗口继续待在
     * 底部，与接这块之前完全一样——跟随光标是加分项，不是前提。
     */
    override fun setCaretFollowing(enabled: Boolean) {
        if (caretFollowing == enabled) return
        caretFollowing = enabled
        caretLogs = 0
        if (!enabled) {
            caretAnchor = null
            // 停下监听：有的编辑器会一直算锚点，输入法不该在这次输入里继续要它。
        }
        val requested = runCatching {
            val mode = if (enabled) InputConnection.CURSOR_UPDATE_MONITOR else 0
            currentInputConnection?.requestCursorUpdates(mode) ?: false
        }
            .onFailure { Log.w(TAG, "无法请求光标位置更新", it) }
            .getOrDefault(false)
        if (enabled) {
            // 编辑器（或它的输入连接）没接这条：面板就按"不知道光标在哪"处理，贴屏幕底部。
            if (requested) {
                Log.i(TAG, "已向编辑器订阅光标位置，候选词面板将跟着光标走")
            } else {
                Log.i(TAG, "编辑器不接受光标位置订阅，候选词面板贴屏幕底部")
            }
        }
        applyWindowLayout()
    }

    /**
     * 编辑器报来了光标位置。
     *
     * 坐标空间：`insertionMarker*` 是编辑器**局部坐标**，"渲染到屏幕上时要先过 `getMatrix()`"
     * （官方文档原话），所以先换算再当屏幕坐标用。`getMatrix()` 按实现永远不会是 null（构造器里
     * 没设就是单位阵），单位阵换算等于没换算——正好是"编辑器没给矩阵"时的正确行为。
     */
    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)
        if (!caretFollowing) return
        val point = floatArrayOf(
            cursorAnchorInfo.insertionMarkerHorizontal,
            cursorAnchorInfo.insertionMarkerBottom,
        )
        if (point[0].isNaN() || point[1].isNaN()) return
        cursorAnchorInfo.matrix?.let { matrix -> runCatching { matrix.mapPoints(point) } }
        val metrics = resources.displayMetrics
        if (caretLogs < CARET_LOG_LIMIT) {
            caretLogs++
            Log.i(
                TAG,
                "光标锚点 原始=(${cursorAnchorInfo.insertionMarkerHorizontal}, " +
                    "${cursorAnchorInfo.insertionMarkerBottom}) 换算后=(${point[0]}, ${point[1]}) " +
                    "屏幕=${metrics.widthPixels}x${metrics.heightPixels}",
            )
        }
        // 换算完落在屏幕外的点照样收下，只是夹进屏幕范围：面板会贴着光标那一侧停住，而不是
        // 直接放弃跟随、永远贴回屏幕底部（宁可位置差一点，也好过一直不跟）。
        caretAnchor = android.graphics.PointF(
            point[0].coerceIn(0f, metrics.widthPixels.toFloat()),
            point[1].coerceIn(0f, metrics.heightPixels.toFloat()),
        )
        placeCompatContentWindow()
        // 两块面板都在自己的窗口里时，输入法窗口只是个 1dp 的壳，没什么好重摆的；退回把面板画进
        // 输入法窗口的那条路上，窗口本身才需要跟着光标走。
        if (compatWindows[CompatPanelKind.Content] == null) applyWindowLayout()
    }

    @Composable
    override fun KeyboardContent() {
        val active = controller
        if (active != null) {
            KeyboardRoot(active)
        } else {
            InitializationFailedPanel()
        }
    }

    @Composable
    private fun InitializationFailedPanel() {
        CloudriftTheme {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Column {
                        Text(
                            text = "云隙输入初始化失败",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = "请在 logcat 中过滤 CloudriftIme 查看原因。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        controller?.onStartInput(attribute, restarting)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Cheap insurance: the decor can be recreated if the IME process is reused.
        attachComposeOwnersToWindow()
        controller?.onStartInputView()
        // 有些 ROM 在热插拔时不给服务发设备回调，每次弹出键盘再核对一次。
        externalInputs.refresh()
    }

    /**
     * 桌面模式 / 键盘壳 / 蓝牙键盘接入或拔出时，系统配置里的 keyboard 会变，这是设备表回调之外
     * 的第二条路（见 ExternalInputMonitor）。
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        externalInputs.refresh()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller?.onFinishInputView()
        removeCompatPanels()
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        controller?.onWindowHidden()
        removeCompatPanels()
        super.onWindowHidden()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd,
        )
        // The selection itself is what a backspace has to act on when the user has highlighted a
        // range, so the two ends are passed on rather than just "something changed".
        controller?.onSelectionChanged(selectionStart = newSelStart, selectionEnd = newSelEnd)
    }

    /**
     * Never take over the screen. A Chinese IME needs the app visible above the keyboard so
     * the user can see what is being typed.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /**
     * 输入视图永远显示。这个输入法存在的意义就是被显示：外接键鼠在旁边时也一样——只是那时窗口里
     * 画的是候选词栏 + 工具栏的键鼠兼容面板，而不是整块虚拟键盘（见 ImeUiState.showsCompatPanel）。
     * 所以结果不取自父类，但父类那一步仍然要走完（lint `MissingSuperCall`，也是框架更新状态的
     * 地方），只是它的返回值这里不采用。
     */
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    /**
     * 物理键盘的按键先进输入法。
     *
     * 只有真的有外接键鼠在场时控制器才会接管（见 ImeController.onHardwareKeyDown）：字母、数字、
     * 标点、退格、回车、空格、Esc 走输入法自己的拼写链路，中文才有候选可点；其余（Tab、方向键、
     * Ctrl/Alt 组合、功能键）一律交回系统，快捷键与焦点移动不受影响。
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val active = controller
        if (active != null &&
            active.onHardwareKeyDown(
                keyCode = keyCode,
                unicode = event.getUnicodeChar(event.metaState),
                ctrlPressed = event.isCtrlPressed,
                altPressed = event.isAltPressed,
            )
        ) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        removeCompatPanels()
        externalInputs.stop()
        controller?.dispose()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "CloudriftIme"
        /** 默认停靠位置与屏幕底边留出的空。 */
        const val FALLBACK_MARGIN_PX = 24
        /** 跟随光标时，候选词窗口相对光标往右、往下让开多少。 */
        const val CARET_GAP_X_PX = -16
        const val CARET_GAP_Y_PX = 10
        /** 翻到光标上方时，再往上让开一行的高度（光标本身在那一行里）。 */
        const val CARET_LINE_PX = 8
        /** 光标锚点只记前几条日志，用来核对坐标空间（真机排查用）。 */
        const val CARET_LOG_LIMIT = 3
        /** 面板还没量到尺寸时，补摆位置的次数上限。 */
        const val COMPAT_PLACEMENT_ATTEMPTS = 3
        const val DEFAULT_FLOATING_WIDTH = 78
        const val MIN_FLOATING_WIDTH = 45
        const val MAX_FLOATING_WIDTH = 100
    }
}
