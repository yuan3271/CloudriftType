package com.yuan3271.cloudrift.engine.japanese

import com.yuan3271.cloudrift.engine.Candidate
import com.yuan3271.cloudrift.engine.CandidateKind
import com.yuan3271.cloudrift.engine.EngineKind
import com.yuan3271.cloudrift.engine.EngineOutput
import com.yuan3271.cloudrift.engine.InputEngine

/**
 * Japanese composition.
 *
 * The raw buffer is Latin romaji; it is translated to kana first, and that kana is then looked up
 * for kanji. The plain kana always stays the first candidate so the reading can be committed
 * verbatim. (The kana pad was dropped in 0.2.6: romaji covers the same ground with one layout.)
 */
class JapaneseEngine(override val kind: EngineKind = EngineKind.Romaji) : InputEngine {

    init {
        require(kind == EngineKind.Romaji) { "JapaneseEngine only handles Romaji" }
    }

    override fun evaluate(raw: String, limit: Int): EngineOutput {
        if (raw.isEmpty()) return EngineOutput()

        val converted = RomajiKana.convert(raw)
        val reading = converted.kana
        val preview = converted.composing

        val candidates = ArrayList<Candidate>(limit)
        if (reading.isNotEmpty()) {
            candidates.add(
                Candidate(
                    text = reading,
                    consumed = raw.length,
                    kind = CandidateKind.Raw,
                    score = Int.MAX_VALUE,
                ),
            )
            for (word in KanaDictionary.candidatesFor(reading, limit)) {
                if (word == reading) continue
                candidates.add(
                    Candidate(
                        text = word,
                        consumed = raw.length,
                        kind = CandidateKind.Conversion,
                        score = 10_000 - word.length,
                        annotation = reading,
                    ),
                )
            }
        }
        return EngineOutput(candidates = candidates, composingPreview = preview)
    }

    override fun literal(raw: String): String = RomajiKana.convert(raw).kana

    companion object {
        /** Kept for the romaji long press tables that the layout still shares. */
        fun alternatesFor(kana: String): List<String> = RomajiKana.alternates[kana].orEmpty()
    }
}
