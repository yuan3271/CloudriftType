package com.yuan3271.cloudrift.data

import android.content.Context
import com.yuan3271.cloudrift.engine.EngineRegistry
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
    lateinit var engines: EngineRegistry
        private set

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
            engines = EngineRegistry(appContext, profile)
            initialized = true
        }
    }

    fun requireContext(): Context {
        check(initialized) { "AppGraph.init() must run before use" }
        return appContext
    }
}
