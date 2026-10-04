package com.yuan3271.cloudrift.ime

import android.util.Log
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
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
import com.yuan3271.cloudrift.input.ExternalInputMonitor
import com.yuan3271.cloudrift.input.InputWindowHost
import com.yuan3271.cloudrift.theme.CloudriftTheme
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

    /** 工具面板那个窗口（键鼠兼容面板时的第二个窗口）；开不出来时是 null。 */
    private var toolbarView: ComposeView? = null
    private var toolbarParams: WindowManager.LayoutParams? = null
    /** 工具面板左上角在屏幕上的位置（像素）；拖动时直接改它。 */
    private var toolbarOffsetX = 0f
    private var toolbarOffsetY = 0f
    /** 量出来的面板尺寸；默认位置（底部居中）要用它。 */
    private var toolbarWidthPx: Int? = null
    private var toolbarHeightPx: Int? = null
    private var toolbarPostAttempts = 0

    /** 键鼠兼容面板：候选词窗口要不要跟着光标走，以及当前拿到的光标锚点（屏幕像素）。 */
    private var caretFollowing = false
    private var caretAnchor: android.graphics.PointF? = null
    /** 这台机器给的光标锚点读不懂（换算后仍在屏幕外）：本机不再尝试跟随，免得面板跑到屏幕外。 */
    private var caretAnchorRejected = false
    /** 编辑器没给变换矩阵这件事只记一条日志。 */
    private var caretMatrixMissingLogged = false

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
        window?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
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

    // ---- 键鼠兼容面板的第二个窗口：工具面板 ------------------------------------------

    /**
     * 工具面板是**另一个窗口**，不是画在输入法窗口里的一行（用户点名：两个窗口）。
     *
     * 这样两块面板各自能拖、各自有位置：候选词面板是输入法自己的窗口（跟着光标放），工具面板是这里
     * 加的这一个。它比输入法窗口小得多，所以窗口自己就能收紧到面板大小，不用触摸区域那套。
     *
     * 窗口类型按可靠性顺序试：`TYPE_INPUT_METHOD_DIALOG`（输入法自己的对话框窗口）→ 带上输入法窗口
     * 的 token → `TYPE_APPLICATION_OVERLAY`（需要悬浮窗权限，没授权就失败）。全都开不出来时返回
     * false，界面把工具面板画进候选词那个窗口里——功能不缺，只是两块面板不能再分开摆。
     */
    override fun applyToolbarWindow(visible: Boolean, xPercent: Int, yPercent: Int): Boolean {
        if (!visible) {
            removeToolbarWindow()
            return false
        }
        if (toolbarView == null && !createToolbarWindow()) return false
        val view = toolbarView ?: return false
        val params = toolbarParams ?: return false
        // 第一次摆位置时面板可能还没量过（刚 addView）。量到了再摆一次，居中才是真的居中。
        if (toolbarWidthPx == null && view.width > 0) {
            toolbarWidthPx = view.width
            toolbarHeightPx = view.height
        }
        val metrics = resources.displayMetrics
        val insets = systemBarBottomInset()
        // 没拖过就放在底部居中：与候选词面板同一列，但各是各的窗口。
        val defaultX = (metrics.widthPixels - (toolbarWidthPx ?: 0)).toFloat() / 2f
        val defaultY = (
            metrics.heightPixels - insets - (toolbarHeightPx ?: 0) - FALLBACK_MARGIN_PX
            ).toFloat()
        toolbarOffsetX = if (xPercent >= 0) metrics.widthPixels * xPercent / 100f else defaultX
        toolbarOffsetY = if (yPercent >= 0) metrics.heightPixels * yPercent / 100f else defaultY
        params.gravity = Gravity.TOP or Gravity.START
        params.x = toolbarOffsetX.roundToInt()
        params.y = toolbarOffsetY.roundToInt()
        val applied = runCatching {
            windowManager.updateViewLayout(view, params)
            true
        }.getOrElse {
            Log.w(TAG, "工具面板窗口更新失败，退回画在键盘窗口里", it)
            false
        }
        if (applied && xPercent < 0 && toolbarWidthPx == null && toolbarPostAttempts < TOOLBAR_POSITION_ATTEMPTS) {
            toolbarPostAttempts++
            view.post { applyToolbarWindow(visible = true, xPercent = -1, yPercent = -1) }
        }
        return applied
    }

    override fun moveToolbarWindowBy(dx: Float, dy: Float) {
        val view = toolbarView ?: return
        val params = toolbarParams ?: return
        // 拖动量直接从窗口原来的位置加上去：这里记的是屏幕上的绝对位置（不是相对某个角），
        // 所以窗口不会因为"锚点动了"而跳。
        toolbarOffsetX += dx
        toolbarOffsetY += dy
        params.x = toolbarOffsetX.roundToInt()
        params.y = toolbarOffsetY.roundToInt()
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    override fun commitToolbarWindowPosition() {
        val metrics = resources.displayMetrics
        val xPercent = (toolbarOffsetX / metrics.widthPixels * 100f).roundToInt().coerceIn(0, 100)
        val yPercent = (toolbarOffsetY / metrics.heightPixels * 100f).roundToInt().coerceIn(0, 100)
        controller?.onCompatToolbarMoved(xPercent, yPercent)
    }

    private fun createToolbarWindow(): Boolean {
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@CloudriftImeService)
            setViewTreeViewModelStoreOwner(this@CloudriftImeService)
            setViewTreeSavedStateRegistryOwner(this@CloudriftImeService)
        }
        val active = controller ?: return false
        view.setContent { CompatToolbarWindowContent(active) }
        // WRAP_CONTENT：窗口自己收紧到面板大小（工具栏不留空，也没有可以点空的区域）。
        // WRAP_CONTENT：窗口自己收紧到面板大小（工具栏不留空，也没有可以点空的区域）。
        //
        // 窗口类型按可靠性顺序试：`TYPE_INPUT_METHOD_DIALOG`（输入法自己的对话框窗口，可能还要带上
        // 输入法窗口的 token）→ `TYPE_APPLICATION_OVERLAY`（要用户授权"显示在其他应用上层"）。
        // 全都开不出来时返回 false，界面把工具面板画进候选词那个窗口里。
        val candidates = listOf(
            toolbarWindowParams(WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG, withToken = false),
            toolbarWindowParams(WindowManager.LayoutParams.TYPE_INPUT_METHOD_DIALOG, withToken = true),
            toolbarWindowParams(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, withToken = false),
        )
        for (params in candidates) {
            val added = runCatching {
                windowManager.addView(view, params)
                true
            }.getOrElse {
                Log.w(TAG, "工具面板窗口开不出来（type=${params.type}）", it)
                false
            }
            if (added) {
                toolbarView = view
                toolbarParams = params
                return true
            }
        }
        return false
    }

    private fun toolbarWindowParams(type: Int, withToken: Boolean): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (withToken) token = window?.window?.attributes?.token
        }

    private fun removeToolbarWindow() {
        val view = toolbarView ?: return
        toolbarView = null
        toolbarParams = null
        toolbarWidthPx = null
        toolbarHeightPx = null
        toolbarPostAttempts = 0
        runCatching { windowManager.removeViewImmediate(view) }
    }

    private fun systemBarBottomInset(): Int =
        runCatching {
            val decor = window?.window?.decorView ?: return 0
            decor.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.systemBars())?.bottom ?: 0
        }.getOrDefault(0)

    private val windowManager: WindowManager
        get() = getSystemService(WINDOW_SERVICE) as WindowManager

    private fun applyWindowLayout() {
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
        caretAnchorRejected = false
        caretMatrixMissingLogged = false
        if (!enabled) caretAnchor = null
        runCatching {
            val mode = if (enabled) InputConnection.CURSOR_UPDATE_MONITOR else 0
            currentInputConnection?.requestCursorUpdates(mode)
        }.onFailure { Log.w(TAG, "无法请求光标位置更新", it) }
        applyWindowLayout()
    }

    /**
     * 编辑器报来了光标位置。
     *
     * 坐标按屏幕坐标用，`CursorAnchorInfo.matrix` 非空时先用它换算一次（编辑器自带缩放 / 滚动换算
     * 时会带上矩阵）。这两句是这套 API 里唯一需要真机核对的地方：坐标语义 Android 文档写得含糊，
     * 万一某台机器给的是编辑器局部坐标，锚点会落在屏幕左上角附近——那台机器上退回底部即可（把
     * "虚拟键盘 / 兼容面板"切成虚拟键盘，或直接不管它，窗口也不会跑到屏幕外）。
     */
    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo)
        if (!caretFollowing || caretAnchorRejected) return
        val point = floatArrayOf(
            cursorAnchorInfo.insertionMarkerHorizontal,
            cursorAnchorInfo.insertionMarkerBottom,
        )
        if (point[0].isNaN() || point[1].isNaN()) return
        // 坐标空间有官方说法：insertionMarker* 都是"编辑器局部坐标，渲染到屏幕时要先过 getMatrix()"
        // （developer.android.google.cn：in the local coordinates that will be transformed with
        // getMatrix() when rendered on the screen），所以这里照做——先拿矩阵，再换算。
        val matrix = cursorAnchorInfo.matrix
        if (matrix == null) {
            // 编辑器没给变换矩阵：那些值是局部坐标，我们没有任何依据换算到屏幕上。宁可不跟随
            // （面板继续贴在屏幕底部），也不猜一个位置把候选栏丢到别处。
            if (!caretMatrixMissingLogged) {
                caretMatrixMissingLogged = true
                Log.w(TAG, "编辑器没有提供 CursorAnchorInfo 的变换矩阵，本机不跟随光标")
            }
            return
        }
        runCatching { matrix.mapPoints(point) }
        val x = point[0]
        val y = point[1]
        val metrics = resources.displayMetrics
        if (x < -CARET_TOLERANCE_PX || x > metrics.widthPixels + CARET_TOLERANCE_PX ||
            y < -CARET_TOLERANCE_PX || y > metrics.heightPixels + CARET_TOLERANCE_PX
        ) {
            // 换算完还是屏幕外的点：这台机器给的东西我们读不懂。同样退回"贴底部"。
            caretAnchorRejected = true
            caretAnchor = null
            Log.w(TAG, "光标锚点落在屏幕外（$x, $y），这台机器上不跟随光标")
            applyWindowLayout()
            return
        }
        caretAnchor = android.graphics.PointF(x, y)
        applyWindowLayout()
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
        removeToolbarWindow()
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        controller?.onWindowHidden()
        removeToolbarWindow()
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
        removeToolbarWindow()
        externalInputs.stop()
        controller?.dispose()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "CloudriftIme"
        /** 面板还没量到时再摆几次位置的次数上限。 */
        const val TOOLBAR_POSITION_ATTEMPTS = 3
        /** 默认停靠位置与屏幕底边留出的空。 */
        const val FALLBACK_MARGIN_PX = 24
        /** 跟随光标时，候选词窗口相对光标往右、往下让开多少。 */
        const val CARET_GAP_X_PX = -16
        const val CARET_GAP_Y_PX = 10
        /** 翻到光标上方时，再往上让开一行的高度（光标本身在那一行里）。 */
        const val CARET_LINE_PX = 8
        /** 光标锚点允许超出屏幕多少像素；超出去就当这台机器的坐标空间读不懂。 */
        const val CARET_TOLERANCE_PX = 64
        const val DEFAULT_FLOATING_WIDTH = 78
        const val MIN_FLOATING_WIDTH = 45
        const val MAX_FLOATING_WIDTH = 100
    }
}
