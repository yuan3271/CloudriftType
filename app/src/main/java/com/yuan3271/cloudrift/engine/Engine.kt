package com.yuan3271.cloudrift.engine

/** What the engine is allowed to do with the raw buffer the keyboard has collected. */
enum class EngineKind {
    /** Chinese, QWERTY pinyin. */
    Pinyin26,

    /** Chinese, nine key T9 pinyin. */
    Pinyin9,

    /** Japanese, romaji typed on a QWERTY layout. */
    Romaji,

    /** English / Latin, no composition beyond capitalisation. */
    Latin,
}

enum class CandidateKind {
    /** A real dictionary hit. */
    Conversion,

    /** A single character standing in for a syllable. */
    Character,

    /**
     * A reading reached by 首字母: every syllable was given only its initial letter ("nh" -> 你好).
     */
    Initials,

    /**
     * 首字母 and 全拼 mixed in one run: "nhao" is n + hao -> 你好, "wojt" is wo + jt -> 我今天.
     */
    Mixed,

    /** Something the engine suggests before any conversion (kana, latin words). */
    Prediction,

    /** The raw buffer committed verbatim. */
    Raw,
}

/**
 * @param text what will be inserted into the editor.
 * @param consumed how many characters of the raw buffer this candidate eats. Committing
 *   "你好" out of "nihao" consumes all five letters; committing the character 你 next to a
 *   still-unconverted "hao" only consumes two.
 */
data class Candidate(
    val text: String,
    val consumed: Int,
    val kind: CandidateKind = CandidateKind.Conversion,
    val score: Int = 0,
    /** Secondary line under the candidate, usually the reading it came from. */
    val annotation: String = "",
    /**
     * Index in [text] from which the characters were not spelled out by the input yet - the 好
     * of 你好 offered while only "nih" is on screen. -1 means the whole candidate is covered by
     * what has been typed. Display hint only: picking the candidate commits all of [text].
     */
    val unmatchedFrom: Int = -1,
) {
    val display: String get() = text
}

data class EngineOutput(
    val candidates: List<Candidate> = emptyList(),
    /**
     * Text the engine wants the editor to show as composing. Engines that translate as you
     * type (kana, romaji) fill this in; dictionary engines leave it empty and let the
     * keyboard show the raw buffer.
     */
    val composingPreview: String = "",
)

interface InputEngine {
    val kind: EngineKind

    /** Called on every keystroke with the full raw buffer. Must be cheap enough for typing. */
    fun evaluate(raw: String, limit: Int = DEFAULT_CANDIDATE_LIMIT): EngineOutput

    /** The text that gets committed when the user presses space or enter mid-composition. */
    fun literal(raw: String): String = raw

    /**
     * Words that tend to follow [text], offered as a 联想 strip once a word has gone in and the
     * reading buffer is empty again. Engines without an association model return nothing, which the
     * keyboard reads as "show the layout instead".
     */
    fun associations(text: String, limit: Int = DEFAULT_ASSOCIATION_LIMIT): List<Candidate> = emptyList()

    companion object {
        const val DEFAULT_CANDIDATE_LIMIT = 48
        const val DEFAULT_ASSOCIATION_LIMIT = 16
    }
}
