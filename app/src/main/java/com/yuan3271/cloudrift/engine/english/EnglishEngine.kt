package com.yuan3271.cloudrift.engine.english

import com.yuan3271.cloudrift.data.UserProfile
import com.yuan3271.cloudrift.engine.Candidate
import com.yuan3271.cloudrift.engine.CandidateKind
import com.yuan3271.cloudrift.engine.EngineKind
import com.yuan3271.cloudrift.engine.EngineOutput
import com.yuan3271.cloudrift.engine.InputEngine

/**
 * English needs no conversion, so this engine only supplies word completion: the built in list,
 * plus the words this person actually types. Everything else (capitalisation, double space to
 * period) is keyboard behaviour and lives in the input controller.
 *
 * 自学习是中英**共用**的一份记录（[UserProfile]）：中文那边它调的是候选顺序，英文这边它同时
 * 干两件事——把用户为同一个码选过两次以上的词提到最前，以及把词表里根本没有的词（人名、
 * 术语、缩写：`cloudrift`、`astrearc`）补成候选。英文没有词频表也没有读音，习惯和"打过的
 * 词"就是它唯一能越用越准的地方。
 */
class EnglishEngine(
    /** What this user has typed before; null in tests and before the profile is wired up. */
    private val profile: UserProfile? = null,
) : InputEngine {

    override val kind: EngineKind = EngineKind.Latin

    override fun evaluate(raw: String, limit: Int): EngineOutput {
        if (raw.isEmpty()) return EngineOutput(emptyList())
        val literal = literalCandidates(raw)
        if (raw.length < 2) return EngineOutput(literal)
        val lower = raw.lowercase()
        val matches = ArrayList<Candidate>(limit)
        val seen = HashSet<String>(limit * 2)
        // 词表里没有的词要留出位置，否则常见前缀（`to`、`in`）会把它们挤到截断线外面。
        val learned = profile?.learnedWords(lower, LEARNED_LIMIT).orEmpty()
        val builtInCap = (limit - learned.size).coerceAtLeast(1)
        for (word in EnglishWords.ALL) {
            if (matches.size >= builtInCap) break
            if (word.startsWith(lower) && word != lower && seen.add(word)) {
                matches.add(candidate(raw, word))
            }
        }
        for (word in learned) if (seen.add(word)) matches.add(candidate(raw, word))
        if (matches.isEmpty()) return EngineOutput(literal)
        // The literal buffer is always offered first so the user is never forced to accept a
        // completion.
        return EngineOutput(literal + personalize(raw, matches, limit))
    }

    /**
     * 同一份学习记录：这个码（或它的前缀）选过两次以上的词直接提到最前，其余保持原顺序，
     * 也就是词表自己的优先序（日常词在前、课本词在后）。与中文 `PinyinEngine.personalize` 同口径。
     */
    private fun personalize(code: String, ranked: List<Candidate>, limit: Int): List<Candidate> {
        val profile = profile ?: return ranked.take(limit)
        val counts = profile.habitCounts(code, ranked.map { it.text })
        val promoted = ranked.indices
            .filter { counts[it] >= UserProfile.HABIT_THRESHOLD }
            .sortedByDescending { counts[it] }
            .map { ranked[it] }
        if (promoted.isEmpty()) return ranked.take(limit)

        val promotedTexts = promoted.mapTo(HashSet(promoted.size)) { it.text }
        val ordered = ArrayList<Candidate>(ranked.size)
        ordered.addAll(promoted)
        ordered.addAll(ranked.filter { it.text !in promotedTexts })
        return ordered.take(limit)
    }

    private fun candidate(raw: String, word: String): Candidate = Candidate(
        text = EnglishWords.matchCase(raw, word),
        consumed = raw.length,
        kind = CandidateKind.Prediction,
        score = 1_000 - word.length,
    )

    private fun literalCandidates(raw: String): List<Candidate> =
        if (raw.isEmpty()) {
            emptyList()
        } else {
            listOf(Candidate(raw, raw.length, CandidateKind.Raw, score = Int.MAX_VALUE))
        }

    private companion object {
        /** 自造词一次最多补这么多条，剩下的位置留给词表。 */
        const val LEARNED_LIMIT = 6
    }
}
