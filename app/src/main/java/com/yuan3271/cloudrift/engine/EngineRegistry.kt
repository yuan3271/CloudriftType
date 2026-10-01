package com.yuan3271.cloudrift.engine

import android.content.Context
import com.yuan3271.cloudrift.data.UserProfile
import com.yuan3271.cloudrift.data.CandidateOrder
import com.yuan3271.cloudrift.engine.english.EnglishEngine
import com.yuan3271.cloudrift.engine.japanese.JapaneseEngine
import com.yuan3271.cloudrift.engine.pinyin.PinyinDictionary
import com.yuan3271.cloudrift.engine.pinyin.PinyinEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dictionary = PinyinDictionary.fromAssets(context.assets)

    private val _dictionaryReady = MutableStateFlow(dictionary.isReady)
    val dictionaryReady: StateFlow<Boolean> = _dictionaryReady.asStateFlow()

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
        scope.launch {
            val ready = withContext(Dispatchers.IO) {
                dictionary.load()
                true
            }
            _dictionaryReady.value = ready
        }
    }
}
