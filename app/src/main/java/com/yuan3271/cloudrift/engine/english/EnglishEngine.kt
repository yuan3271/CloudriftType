package com.yuan3271.cloudrift.engine.english

import com.yuan3271.cloudrift.engine.Candidate
import com.yuan3271.cloudrift.engine.CandidateKind
import com.yuan3271.cloudrift.engine.EngineKind
import com.yuan3271.cloudrift.engine.EngineOutput
import com.yuan3271.cloudrift.engine.InputEngine

/**
 * English needs no conversion, so this engine only supplies word completion from a compact
 * built in list. Everything else (capitalisation, double space to period) is keyboard
 * behaviour and lives in the input controller.
 */
class EnglishEngine : InputEngine {

    override val kind: EngineKind = EngineKind.Latin

    override fun evaluate(raw: String, limit: Int): EngineOutput {
        if (raw.length < 2) return EngineOutput(literalCandidates(raw))
        val lower = raw.lowercase()
        val matches = ArrayList<Candidate>(limit)
        for (word in EnglishWords.ALL) {
            if (word.startsWith(lower) && word != lower) {
                matches.add(
                    Candidate(
                        text = EnglishWords.matchCase(raw, word),
                        consumed = raw.length,
                        kind = CandidateKind.Prediction,
                        score = 1_000 - word.length,
                    ),
                )
                if (matches.size >= limit) break
            }
        }
        if (matches.isEmpty()) return EngineOutput(literalCandidates(raw))
        // The literal buffer is always offered first so the user is never forced to accept a
        // completion.
        return EngineOutput(literalCandidates(raw) + matches)
    }

    private fun literalCandidates(raw: String): List<Candidate> =
        if (raw.isEmpty()) {
            emptyList()
        } else {
            listOf(Candidate(raw, raw.length, CandidateKind.Raw, score = Int.MAX_VALUE))
        }
}
