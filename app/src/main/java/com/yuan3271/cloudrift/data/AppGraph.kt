package com.yuan3271.cloudrift.data

import android.content.Context
import com.yuan3271.cloudrift.engine.EngineRegistry
import com.yuan3271.cloudrift.engine.emoji.EmojiCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand rolled service locator. The app is small enough that a DI framework would be
 * more ceremony than value, but both the IME service and the settings activity need
 * the same singletons.
 */
object AppGraph {
    private lateinit var appContext: Context

    lateinit var settings: SettingsRepository
        private set
    lateinit var profile: UserProfile
        private set
    lateinit var updates: UpdateChecker
        private set
    lateinit var engines: EngineRegistry
        private set

    /**
     * 表情表（`assets/emoji.txt`）。**延迟加载**：大部分会话根本不翻符号页，没必要在启动时把
     * 1900 行读进内存；读失败也只是少一页（[EmojiCatalog.fromReader] 不抛异常）。
     */
    val emoji: EmojiCatalog by lazy { EmojiCatalog.fromAssets(requireContext().assets) }

    @Volatile
    private var initialized: Boolean = false

    /**
     * Idempotent and safe to call from any component. Every entry point calls it, because a
     * missing [init] is invisible until something throws deep inside the IME process, and an
     * input method that crashes on start simply never appears.
     */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            settings = SettingsRepository(appContext)
            profile = UserProfile(appContext, CoroutineScope(SupervisorJob() + Dispatchers.Default))
            updates = UpdateChecker(
                appContext,
                CoroutineScope(SupervisorJob() + Dispatchers.Default),
                settingsProvider = { settings.current },
            )
            engines = EngineRegistry(
                context = appContext,
                profile = profile,
                orderProvider = { settings.current.candidateOrder },
            )
            initialized = true
        }
    }

    fun requireContext(): Context {
        check(initialized) { "AppGraph.init() must run before use" }
        return appContext
    }
}
