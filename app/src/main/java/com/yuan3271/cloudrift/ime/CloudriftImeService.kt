package com.yuan3271.cloudrift.ime

import android.util.Log
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.view.inputmethod.EditorInfo
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
import androidx.core.view.WindowCompat
import com.yuan3271.cloudrift.data.AppGraph
import com.yuan3271.cloudrift.input.InputWindowHost
import com.yuan3271.cloudrift.theme.CloudriftTheme
import com.yuan3271.cloudrift.ui.KeyboardRoot

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

    /** Where the floating keyboard sits, in window offsets; reset whenever the frame changes. */
    private var floatingOffsetX = 0
    private var floatingOffsetY = 0
    private var floating = false
    private var floatingWidthPercent = DEFAULT_FLOATING_WIDTH

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        controller = runCatching { ImeController(this) }
            .onFailure { Log.e(TAG, "输入法初始化失败，键盘将显示错误提示", it) }
            .getOrNull()
    }

    override fun onCreateInputView(): View {
        AppGraph.init(this)
        // Must run before the view is attached: Compose looks the owners up from the window
        // content root when the input view is added to the window.
        attachComposeOwnersToWindow()
        // The keyboard paints its own surface. Without this the window's own background shows as a
        // thick frame around the rounded card (and around the floating card's margins).
        window?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
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
            floatingOffsetX = 0
            floatingOffsetY = 0
        }
        val previousWidth = floatingWindowWidth(this.floatingWidthPercent)
        val nextWidth = floatingWindowWidth(percent)
        if (floating && this.floating && previousWidth != nextWidth) {
            // The card is centred, so half of the growth happens on each side. Shifting by half the
            // difference keeps the left edge where it is, which is what a corner drag should do.
            floatingOffsetX -= (nextWidth - previousWidth) / 2
        }
        this.floating = floating
        this.floatingWidthPercent = percent
        applyWindowLayout()
    }

    private fun floatingWindowWidth(percent: Int): Int =
        (resources.displayMetrics.widthPixels * percent / 100f).toInt()

    override fun moveInputWindowBy(dx: Float, dy: Float) {
        if (!floating) return
        floatingOffsetX += dx.toInt()
        floatingOffsetY -= dy.toInt()
        applyWindowLayout()
    }

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
        attributes.gravity = if (floating) {
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        } else {
            Gravity.BOTTOM
        }
        attributes.x = if (floating) floatingOffsetX else 0
        attributes.y = if (floating) floatingOffsetY else 0
        val decorView = dialog.window?.decorView ?: return
        runCatching {
            (getSystemService(WINDOW_SERVICE) as? android.view.WindowManager)
                ?.updateViewLayout(decorView, attributes)
        }.onFailure { Log.w(TAG, "无法调整输入法窗口布局", it) }
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
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller?.onFinishInputView()
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        controller?.onWindowHidden()
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

    override fun onEvaluateInputViewShown(): Boolean = true

    override fun onDestroy() {
        controller?.dispose()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "CloudriftIme"
        const val DEFAULT_FLOATING_WIDTH = 78
        const val MIN_FLOATING_WIDTH = 45
        const val MAX_FLOATING_WIDTH = 100
    }
}
