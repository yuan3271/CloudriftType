package com.yuan3271.cloudrift.engine

import android.content.Context
import android.util.Log
import com.yuan3271.cloudrift.data.UserProfile
import com.yuan3271.cloudrift.data.CandidateOrder
import com.yuan3271.cloudrift.engine.english.EnglishEngine
import com.yuan3271.cloudrift.engine.japanese.JapaneseEngine
import com.yuan3271.cloudrift.engine.pinyin.PinyinDictionary
import com.yuan3271.cloudrift.engine.pinyin.PinyinEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the dictionary and hands out one engine per layout. The Chinese dictionary is loaded
 * off the main thread; [dictionaryReady] lets the keyboard show a subtle loading state
 * instead of blocking the first keystroke.
 */
class EngineRegistry(
    private val context: Context,
    private val profile: UserProfile? = null,
    /** Read on every evaluation: the user can flip the order without rebuilding the engine. */
    private val orderProvider: () -> CandidateOrder = { CandidateOrder.LongFirst },
) {

    /**
     * 词库加载这条协程以前**没有兜底**：`dictionary.load()` 任何一次抛出（资源读失败、低内存设备
     * 上的 OOM、进程被系统回收时读不到 assets）都会从协程里逃出去，被当成未捕获异常**直接杀掉
     * 输入法进程**。而 `warmUp()` 每次打开键盘都会调一次，于是表现就是"打开键盘反复闪退"。
     * 现在两层兜住：具体那步有 runCatching（见 [warmUp]），这里再挂一个 handler 作为兜底。
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "engine scope failed", error)
        },
    )
    private val dictionary = PinyinDictionary.fromAssets(context.assets)

    private val _dictionaryReady = MutableStateFlow(dictionary.isReady)
    val dictionaryReady: StateFlow<Boolean> = _dictionaryReady.asStateFlow()

    /** 词库没能加载起来（读 assets 失败 / 内存不够）。UI 用它显示提示，下一次会话会再试一次。 */
    private val _dictionaryFailed = MutableStateFlow(false)
    val dictionaryFailed: StateFlow<Boolean> = _dictionaryFailed.asStateFlow()

    /** 同一时刻只允许一个加载协程：反复打开键盘不再叠加加载。 */
    private val loading = AtomicBoolean(false)

    private val pinyin26 by lazy {
        PinyinEngine(dictionary, nineKey = false, profile = profile, orderProvider = orderProvider)
    }
    private val pinyin9 by lazy {
        PinyinEngine(dictionary, nineKey = true, profile = profile, orderProvider = orderProvider)
    }
    private val romaji by lazy { JapaneseEngine(EngineKind.Romaji) }
    private val latin by lazy { EnglishEngine() }

    fun engineFor(kind: EngineKind): InputEngine = when (kind) {
        EngineKind.Pinyin26 -> pinyin26
        EngineKind.Pinyin9 -> pinyin9
        EngineKind.Romaji -> romaji
        EngineKind.Latin -> latin
    }

    /** Idempotent; safe to call from every input view creation. */
    fun warmUp() {
        if (dictionary.isReady) {
            _dictionaryReady.value = true
            return
        }
        if (!loading.compareAndSet(false, true)) return
        scope.launch {
            val outcome = runCatching { withContext(Dispatchers.IO) { dictionary.load() } }
            outcome.onFailure {
                _dictionaryFailed.value = true
                Log.e(TAG, "dictionary load failed", it)
            }
            _dictionaryReady.value = dictionary.isReady
            loading.set(false)
        }
    }

    companion object {
        private const val TAG = "CloudriftEngines"
    }
}
