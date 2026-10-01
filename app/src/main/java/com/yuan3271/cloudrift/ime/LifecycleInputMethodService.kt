package com.yuan3271.cloudrift.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * [InputMethodService] is not a lifecycle owner, so Compose cannot host itself in the
 * input view without one. This base class backfills the three owners Compose asks for
 * and exposes [KeyboardContent] as the composable entry point of the keyboard window.
 */
abstract class LifecycleInputMethodService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    @Composable
    abstract fun KeyboardContent()

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onCreateInputView(): View {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LifecycleInputMethodService)
            setViewTreeViewModelStoreOwner(this@LifecycleInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@LifecycleInputMethodService)
            setContent { KeyboardContent() }
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    /**
     * Compose resolves its recomposer by walking up from the window's content view
     * (`android.R.id.content` -> `parentPanel`), not from the ComposeView itself. If the
     * owners only live on the ComposeView, that lookup fails and Compose throws
     * `IllegalStateException: ViewTreeLifecycleOwner not found from ... parentPanel` the
     * moment `InputMethodService.setInputView` attaches the view - which looks exactly like
     * "the keyboard never opens".
     *
     * So the owners have to sit on the decor view, above everything in the window.
     */
    protected fun attachComposeOwnersToWindow() {
        val decor = window?.window?.decorView ?: return
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
    }
}
