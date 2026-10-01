package com.yuan3271.cloudrift.engine.pinyin

import com.yuan3271.cloudrift.engine.Candidate
import com.yuan3271.cloudrift.engine.CandidateKind
import com.yuan3271.cloudrift.engine.EngineKind
import com.yuan3271.cloudrift.engine.EngineOutput
import com.yuan3271.cloudrift.engine.InputEngine
import com.yuan3271.cloudrift.data.UserProfile
import com.yuan3271.cloudrift.data.CandidateOrder

/**
 * Dictionary driven pinyin engine shared by the 26 key and the 9 key layouts.
 *
 * The raw buffer is either Latin letters (26 key) or digits (9 key). Everything else about
 * the algorithm is identical: split the buffer into syllables, look the reading up, and
 * offer word hits first, then per syllable characters.
 */
class PinyinEngine(
    private val dictionary: PinyinDictionary,
    nineKey: Boolean,
    /** What this user has typed before; null in tests and before the profile is wired up. */
    private val profile: UserProfile? = null,
    /** Single characters first, or whole sentences first; read on every evaluation. */
    private val orderProvider: () -> CandidateOrder = { CandidateOrder.LongFirst },
) : InputEngine {

    override val kind: EngineKind = if (nineKey) EngineKind.Pinyin9 else EngineKind.Pinyin26

    private val nineKey = nineKey

    /** Cached segmentation results; typing is repetitive enough for this to pay off. */
    private val coverCache = HashMap<String, Int>(64)

    override fun evaluate(raw: String, limit: Int): EngineOutput {
        if (!dictionary.isReady || raw.isEmpty()) return EngineOutput()

        val buffer = normalize(raw)
        if (buffer.isEmpty()) return EngineOutput()

        val candidates = ArrayList<Candidate>(limit)
        val covered = longestCover(buffer)

        if (covered == buffer.length) {
            // The buffer ends on a syllable boundary. Finished words come first, but the single
            // most likely character of the syllable in progress still outranks the completions,
            // which is what Chinese keyboards do: "ni" leads with 你 and follows with 你好.
            val topRank = topRankSize(buffer)
            addWordCandidates(buffer, buffer.length, candidates)
            // A reading that no single word covers is decoded as a whole sentence, and that
            // sentence is the answer for anything longer than a word.
            candidates.addAll(sentenceCandidates(buffer))
            // No 首字母 here. The whole buffer already reads as full pinyin, and the alternative
            // "one letter stands for one character" reading of it (wo -> 无藕) is not something
            // anyone typed: showing it right next to 我 is how 简拼 used to bury the answer.
            // 简拼 is consulted only in the two branches below, where full pinyin leaves part of
            // the buffer unread - which is exactly the shape of a run of initials ("nh", "jtzmy").
            addSyllableCandidates(buffer, buffer.length, candidates, from = 0, until = topRank)
            addCompletionCandidates(buffer, head = buffer, out = candidates)
            addSyllableCandidates(buffer, buffer.length, candidates, from = topRank)
            if (buffer.length == 1) addLeadingSyllableCandidates(buffer, candidates)
            if (candidates.size < limit && buffer.length > 1) {
                addPrefixCandidates(buffer, candidates, limit)
            }
        } else if (covered > 0) {
            // Mid syllable, e.g. "nih". The user is clearly on their way to 你好, so the
            // completions of the whole buffer are the first thing worth showing - they are what
            // makes the word appear before the last two letters are typed.
            val head = buffer.substring(0, covered)
            addCompletionCandidates(buffer, head = head, out = candidates)
            candidates.addAll(sentenceCandidates(buffer))
            // "nh" cannot be read as syllables at all, but that is exactly how 你好 is typed in
            // jianpin, so the initials index is consulted before falling back to characters.
            addInitialCandidates(buffer, candidates)
            addWordCandidates(head, head.length, candidates)
            addSyllableCandidates(head, head.length, candidates)
            if (candidates.size < limit) {
                addPrefixCandidates(head, candidates, limit)
            }
        } else {
            // Nothing complete yet: offer syllables that would complete the trailing fragment.
            addPartialCandidates(buffer, candidates, limit)
            addInitialCandidates(buffer, candidates)
            // Letters that read as nothing must still be committable, whatever the initials index
            // had to say about them.
            if (candidates.none { it.kind == CandidateKind.Raw }) {
                candidates.add(Candidate(buffer, buffer.length, CandidateKind.Raw))
            }
        }

        if (candidates.isEmpty()) {
            candidates.add(Candidate(buffer, buffer.length, CandidateKind.Raw))
        }
        val ordered = orderCandidates(
            buffer = buffer,
            syllables = syllableSplit(buffer)?.bounds?.size?.minus(1) ?: 1,
            candidates = dedupe(candidates, limit),
        )
        return EngineOutput(candidates = personalize(buffer, ordered, limit))
    }

    /**
     * Long first: a whole sentence, then the words that make it up, then single characters.
     *
     * Chinese keyboards show it that way because that is the order of usefulness - the sentence is
     * what the user typed, the words are its parts, and the characters are the fallback. Only the
     * groups are reordered, never the ranking inside a group, and a single syllable input keeps the
     * order it was built in (你 before its completions, which is what typing one syllable wants).
     */
    private fun orderCandidates(
        buffer: String,
        syllables: Int,
        candidates: List<Candidate>,
    ): List<Candidate> {
        if (syllables < 2) return candidates
        if (orderProvider() == CandidateOrder.CharacterFirst) {
            // Characters in front, everything else behind: a stable sort, so the engine's own
            // ranking inside each group survives.
            return candidates.sortedBy { if (it.text.length == 1) 0 else 1 }
        }
        return candidates.sortedWith(
            compareBy(
                { candidate ->
                    when {
                        candidate.consumed >= buffer.length && candidate.unmatchedFrom == -1 -> 0
                        candidate.consumed >= buffer.length -> 1
                        else -> 2
                    }
                },
                // Inside the "whole buffer, something still to type" group the closest match wins:
                // 试试看 (one character away) beats 实时控制 (four away).
                { candidate -> untypedCharacters(candidate) },
                // Inside the "only part of the buffer" group the longest reach wins, which is what
                // puts the words that make up the sentence before its single characters.
                { candidate -> -candidate.consumed },
                { candidate -> -candidate.text.length },
            ),
        )
    }

    private fun untypedCharacters(candidate: Candidate): Int =
        if (candidate.unmatchedFrom < 0) 0 else candidate.text.length - candidate.unmatchedFrom

    override fun literal(raw: String): String = normalize(raw)

    /**
     * 联想: what the association table says tends to follow what was just committed.
     *
     * The context is the committed text itself, backing off to its tail, because a commit is not
     * always one word: picking the sentence candidate 我今天去了北京 has to end up asking about
     * 北京, not about the whole string, or there would be nothing to look up.
     */
    override fun associations(text: String, limit: Int): List<Candidate> {
        if (!dictionary.isReady || text.isEmpty()) return emptyList()
        for (context in associationContexts(text)) {
            val words = dictionary.nextWords(context, limit)
            if (words.isEmpty()) continue
            return words.map { entry ->
                Candidate(
                    text = entry.word,
                    consumed = 0,
                    kind = CandidateKind.Prediction,
                    score = entry.score,
                )
            }
        }
        return emptyList()
    }

    /** Longest first: the committed text, then its 4/3/2 character tails. */
    private fun associationContexts(text: String): List<String> {
        val contexts = LinkedHashSet<String>(4)
        contexts.add(text)
        for (length in 4 downTo 2) {
            if (text.length > length) contexts.add(text.takeLast(length))
        }
        return contexts.toList()
    }

    /**
     * Reorders the dictionary result with what this person actually does: a candidate they have
     * already picked twice for this code (or for a prefix of it) becomes the first suggestion, and
     * a word they assembled character by character is offered even though no dictionary has it.
     *
     * Order is otherwise preserved, so the engine's own ranking still decides between candidates
     * the user has no history with.
     */
    private fun personalize(code: String, ranked: List<Candidate>, limit: Int): List<Candidate> {
        val profile = profile ?: return ranked

        val habits = ranked.map { it to profile.habit(code, it.text) }
        val promoted = habits.filter { it.second >= UserProfile.HABIT_THRESHOLD }
            .sortedByDescending { it.second }
            .map { it.first }
        val invented = profile.inventedWord(code)
            ?.takeIf { word -> ranked.none { it.text == word } }
            ?.let { word ->
                Candidate(
                    text = word,
                    consumed = code.length,
                    kind = CandidateKind.Conversion,
                    annotation = code,
                )
            }
        if (promoted.isEmpty() && invented == null) return ranked

        val ordered = ArrayList<Candidate>(ranked.size + 1)
        invented?.let(ordered::add)
        ordered.addAll(promoted)
        ordered.addAll(habits.map { it.first })
        return dedupe(ordered, limit)
    }

    // ---- candidate sources --------------------------------------------------------

    /**
     * Decodes the whole buffer as one utterance: the run of syllables is walked left to right and
     * every position may be covered either by single characters or by a dictionary word, with the
     * cheapest total winning. That is what turns "wojintianqulebeijing" into 我今天去了北京 instead
     * of just 我.
     *
     * The cost model is a unigram one: every unit contributes its log-frequency, and every unit
     * also pays [UNIT_PENALTY], which is what makes one word beat several characters. Single
     * characters get a fixed score since the asset only ranks them, it does not score them.
     *
     * Returns null for anything that is not a whole run of syllables (partial readings are still
     * handled the old way) and for the nine key pad, where one keystroke stands for several
     * syllables and the lattice would explode.
     */
    private fun sentenceCandidates(buffer: String): List<Candidate> {
        if (nineKey || buffer.length < 4) return emptyList()
        val split = syllableSplit(buffer) ?: return emptyList()
        val bounds = split.bounds
        if (bounds.size < 3) return emptyList()

        val decoded = decode(buffer, bounds) ?: return emptyList()
        if (decoded.isEmpty()) return emptyList()

        // Trailing half syllable: "baidubaik" is already 百度百 plus the start of 科, and the user
        // should see the whole word rather than three single characters. Each plausible syllable
        // the fragment can still become is appended and decoded; the best total wins, and only the
        // characters that came from the fragment are left dimmed.
        val fragment = split.fragment
        if (fragment.isEmpty()) {
            return decodeTop(buffer, bounds, SENTENCE_LIMIT)
                .map { it.first }
                .filter { it != buffer }
                .map { sentence(it, buffer, unmatchedFrom = -1) }
        }
        val prefix = decode(buffer.substring(0, split.covered), bounds) ?: return emptyList()
        // Candidates are compared on the *extended* reading, so the fragment itself is paid for by
        // every one of them; comparing against the covered reading instead would let the fragment
        // ride along for free and always win.
        val alternatives = ArrayList<Pair<String, Float>>()
        for (syllable in dictionary.syllablesStartingWith(fragment, PARTIAL_SYLLABLE_LIMIT)) {
            if (syllable == fragment) continue
            val extended = buffer + syllable.substring(fragment.length)
            val extendedBounds = syllableSplit(extended)?.bounds ?: continue
            alternatives.addAll(decodeTop(extended, extendedBounds, SENTENCE_LIMIT))
        }
        if (alternatives.isEmpty()) return emptyList()
        return alternatives
            .sortedByDescending { it.second }
            .map { it.first }
            .filter { it != buffer }
            .distinct()
            .take(SENTENCE_LIMIT)
            .map { sentence(it, buffer, unmatchedFrom = prefix.length) }
    }

    private fun sentence(text: String, reading: String, unmatchedFrom: Int) = Candidate(
        text = text,
        consumed = reading.length,
        kind = CandidateKind.Conversion,
        score = Int.MAX_VALUE,
        annotation = reading,
        unmatchedFrom = unmatchedFrom,
    )

    /**
     * The [count] best readings of the whole buffer, best first. Keeping more than one is what
     * lets 实施这个 *and* 试试这个 both be offered for "shishizhege": they are different paths
     * through the same lattice with almost the same cost.
     */
    private fun decodeTop(buffer: String, bounds: IntArray, count: Int): List<Pair<String, Float>> {
        val syllables = bounds.size - 1
        val paths = Array(syllables + 1) { ArrayList<Path>(count) }
        paths[0].add(Path(0f, "", ""))
        for (start in 0 until syllables) {
            if (paths[start].isEmpty()) continue
            val syllable = buffer.substring(bounds[start], bounds[start + 1])
            // Not just the single best character: letters that read the same cover homophones
            // (ba is 把 and 吧), and which one is right is not a per-character fact - it is what
            // the pair model knows (走 is followed by 吧, never by 把). Ranking them by the table
            // order with a small decay lets the association decide, which is how 我们走吧 stops
            // coming out as 我们走把.
            val characters = dictionary.charsFor(syllable, SENTENCE_CHAR_LIMIT)
            val last = minOf(syllables, start + MAX_SENTENCE_WORD_SYLLABLES)
            // Up to WORDS_PER_STEP words per span, so the second best reading of a span ("试试"
            // next to "实施") is a path of its own rather than being lost to the best one.
            val words = HashMap<Int, List<WordEntry>>(4)
            for (end in start + 2..last) {
                val reading = buffer.substring(bounds[start], bounds[end])
                val entries = dictionary.wordsFor(reading, WORDS_PER_STEP)
                if (entries.isNotEmpty()) words[end] = entries
            }
            for (path in paths[start]) {
                for ((rank, character) in characters.withIndex()) {
                    // The association model conditions on the last *unit*, character or word: after
                    // a lone 我 the next word should be scored just like after a word, otherwise a
                    // sentence decoded mostly into single characters gets no association at all.
                    val pair = dictionary.bigramScore(path.lastToken, character.toString())
                    // The best character of a syllable is always a path. A lower-ranked homophone
                    // only joins in when the pair model actually expects it here (走 -> 吧), so the
                    // bar does not fill with 我门/我闷-style variants of a word that is already right.
                    if (rank > 0 && pair <= 0) continue
                    push(
                        paths[start + 1],
                        path.score + CHARACTER_SCORE - rank * SENTENCE_CHAR_DECAY - UNIT_PENALTY +
                            pair * BIGRAM_WEIGHT,
                        path.text + character,
                        count,
                        lastToken = character.toString(),
                    )
                }
                for ((end, entries) in words) {
                    for (entry in entries) {
                        // Word frequency says 向河北 and 我想装 alike are real words; the pair score is
                        // what knows 我 is followed by 想 far more often than by 向.
                        val pair = dictionary.bigramScore(path.lastToken, entry.word)
                        push(
                            paths[end],
                            path.score + entry.score - UNIT_PENALTY + pair * BIGRAM_WEIGHT,
                            path.text + entry.word,
                            count,
                            lastToken = entry.word,
                        )
                    }
                }
            }
        }
        return paths[syllables]
            .sortedByDescending { it.score }
            .map { it.text to it.score }
            .filter { it.first.isNotEmpty() }
    }

    private fun push(
        paths: ArrayList<Path>,
        score: Float,
        text: String,
        count: Int,
        lastToken: String = "",
    ) {
        if (paths.any { it.text == text }) return
        if (paths.size >= count && paths.last().score >= score) return
        paths.add(Path(score, text, lastToken))
        paths.sortByDescending { it.score }
        while (paths.size > count) paths.removeAt(paths.size - 1)
    }

    private data class Path(val score: Float, val text: String, val lastToken: String = "")

    /** The single best reading of the run of syllables, or null when none exists. */
    private fun decode(buffer: String, bounds: IntArray): String? =
        decodeTop(buffer, bounds, 1).firstOrNull()?.first

    private data class SyllableSplit(val bounds: IntArray, val covered: Int, val fragment: String)

    /**
     * Greedy syllable split of the part that can be read, plus whatever is left over. The leftover
     * is not an error any more: it is the syllable the user is in the middle of typing.
     */
    private fun syllableSplit(buffer: String): SyllableSplit? {
        val bounds = ArrayList<Int>(buffer.length + 1)
        bounds.add(0)
        var index = 0
        while (index < buffer.length) {
            val step = matchSyllable(buffer, index)
            if (step == 0) break
            index += step
            bounds.add(index)
        }
        if (bounds.size < 2) return null
        return SyllableSplit(bounds.toIntArray(), index, buffer.substring(index))
    }

    private fun addWordCandidates(reading: String, consumed: Int, out: MutableList<Candidate>) {
        val words = if (nineKey) dictionary.t9WordsFor(reading, WORD_LIMIT) else dictionary.wordsFor(reading, WORD_LIMIT)
        for (word in words) {
            out.add(
                Candidate(
                    text = word.word,
                    consumed = consumed,
                    kind = CandidateKind.Conversion,
                    score = word.score + consumed * 4,
                    annotation = word.reading,
                ),
            )
        }
    }

    private fun addSyllableCandidates(
        syllable: String,
        consumed: Int,
        out: MutableList<Candidate>,
        from: Int = 0,
        until: Int = CHAR_LIMIT,
    ) {
        val chars = if (nineKey) {
            dictionary.t9CharsFor(syllable, CHAR_LIMIT)
        } else {
            dictionary.charsFor(syllable, CHAR_LIMIT)
        }
        for ((index, ch) in chars.withIndex()) {
            if (index < from || index >= until) continue
            out.add(
                Candidate(
                    text = ch.toString(),
                    consumed = consumed,
                    kind = CandidateKind.Character,
                    // Later entries in the table are rarer, so decay the score with the index.
                    score = 10_000 - index * 3,
                    annotation = if (nineKey) "" else syllable,
                ),
            )
        }
    }

    /**
     * Words the typed code can still grow into: every reading that starts with the whole buffer
     * and is longer than it. This is the candidate source that turns "nih" into 你好, and the
     * `unmatchedFrom` index is what lets the bar draw the 好 as not-yet-typed.
     *
     * Order matters more than the raw word frequency here, so the pool is ranked in three
     * steps: first the words that continue a syllable the user has already finished, ahead of the
     * ones that would need a different ending for the syllable in progress (牛奶 is a guess about
     * "niu", 你们 is not); then the shortest completions, because every extra character is text
     * the engine is inventing; and only then word frequency, with a nudge for completions that
     * start with the character we would have offered on its own (你好 before 拟合).
     */
    private fun addCompletionCandidates(buffer: String, head: String, out: MutableList<Candidate>) {
        if (buffer.length < COMPLETION_MIN_INPUT) return
        val words = if (nineKey) {
            dictionary.t9CompletionWords(buffer, COMPLETION_POOL_LIMIT)
        } else {
            dictionary.completionWords(buffer, COMPLETION_POOL_LIMIT)
        }
        if (words.isEmpty()) return
        val expected = hintCharacters(head)
        val completions = ArrayList<Ranked>(words.size)
        for (word in words) {
            val matched = uncoveredCharacters(buffer, word.reading)
                .coerceIn(0, word.word.length)
            val untyped = word.word.length - matched
            completions.add(
                Ranked(
                    continuesTypedSyllable = matched > 0,
                    untyped = untyped,
                    agreesWithBestCharacter = word.word.firstOrNull() in expected,
                    candidate = Candidate(
                        text = word.word,
                        consumed = buffer.length,
                        kind = CandidateKind.Conversion,
                        score = word.score,
                        annotation = word.reading,
                        unmatchedFrom = matched,
                    ),
                ),
            )
        }
        completions.sortWith(
            compareBy(
                { !it.continuesTypedSyllable },
                { it.untyped },
                { !it.agreesWithBestCharacter },
                { -it.candidate.score },
                { it.candidate.text },
            ),
        )
        for (entry in completions.take(COMPLETION_LIMIT)) out.add(entry.candidate)
    }

    /** Ranking key for one completion, see [addCompletionCandidates] for the order. */
    /**
     * 首字母 candidates: every character is represented by its initial, so the whole buffer is
     * consumed and nothing is left to type. "nh" gives 你好 without typing a single full syllable.
     *
     * A run of initials is decoded as a sequence, not looked up as one string: "jtzmy" is
     * 今天 + 怎么样 (jt | zmy), and the same letters also reach 今天怎么 / 今天这么 through
     * 今天 + 怎么 (jt | zm). Partial paths are kept on purpose with their own `consumed`, so
     * picking one leaves the rest of the initials in the buffer to carry on typing.
     */
    private fun addInitialCandidates(buffer: String, out: MutableList<Candidate>) {
        if (nineKey || buffer.length < INITIALS_MIN_LENGTH) return

        val letters = buffer.lowercase()
        if (letters.any { it !in 'a'..'z' }) return
        // position -> the paths that reach it, best first
        val paths = Array(letters.length + 1) { ArrayList<InitialPath>(INITIALS_PATHS) }
        paths[0].add(InitialPath(consumed = 0, text = "", score = 0))

        for (start in 0 until letters.length) {
            if (paths[start].isEmpty()) continue
            // A single letter may stand for one character (我, 去, 了 ...). Without this step the
            // run can never be cut the way it is actually typed - 简拼 is 我|今天|去|了|北京 - and
            // the initials index has no single character words in it at all.
            addInitialStep(
                paths = paths,
                start = start,
                length = 1,
                texts = dictionary.initialCharacters(letters.substring(start, start + 1), INITIALS_CHAR_LIMIT)
                    .map { it.toString() },
                scoreOf = { CHARACTER_SCORE.toInt() },
            )
            val longest = minOf(MAX_INITIALS, letters.length - start)
            for (length in MIN_INITIALS..longest) {
                val key = letters.substring(start, start + length)
                // The whole bucket, not the top few: the ranking below moves words up by how
                // natural their syllables are and whether they are common, and a word the lookup
                // already dropped can never come back.
                val words = dictionary.wordsForInitials(key, INITIALS_LOOKUP)
                if (words.isEmpty()) continue
                for (word in words) {
                    for (path in paths[start]) {
                        val next = paths[start + length]
                        val text = path.text + word.word
                        if (next.any { it.text == text }) continue
                        // The same association model the sentence decoder uses, and it is what this
                        // path was missing: "wjtqlbj" reads as 我今天|去了|北京 rather than
                        // 伪静态|权利|比较 not because either word is more frequent on its own, but
                        // because one pair actually occurs in the corpus and the other does not.
                        val pair = dictionary.bigramScore(path.lastWord, word.word)
                        next.add(
                            InitialPath(
                                consumed = start + length,
                                text = text,
                                score = path.score + word.score + pair * INITIALS_BIGRAM_WEIGHT -
                                    INITIALS_UNIT_PENALTY,
                                lastWord = word.word,
                                usedWord = true,
                            ),
                        )
                        next.sortByDescending { it.score }
                        while (next.size > INITIALS_PATHS) next.removeAt(next.size - 1)
                    }
                }
            }
        }

        val ranked = ArrayList<Candidate>(INITIALS_LIMIT)
        for (consumed in letters.length downTo MIN_INITIALS) {
            for (path in paths[consumed]) {
                if (path.text.length < 2) continue
                // A reading made only of lone characters is not 简拼, it is noise: every run of
                // letters fits one ("women" -> 无藕木耳南, "zzz" -> 在在), and offering it is how
                // the feature used to bury the word the user actually typed. Real 简拼 always
                // touches a word - 你好 for "nh", 今天|怎么样 for "jtzmy" - so a path that never
                // used the word index is dropped rather than ranked.
                if (!path.usedWord) continue
                ranked.add(
                    Candidate(
                        text = path.text,
                        consumed = consumed,
                        kind = CandidateKind.Conversion,
                        score = path.score,
                        annotation = "首字母",
                        // Every character was given at least its initial, so nothing is dimmed.
                        unmatchedFrom = -1,
                    ),
                )
            }
            if (ranked.size >= INITIALS_LIMIT) break
        }
        out.addAll(ranked.take(INITIALS_LIMIT))
    }

    /**
     * Extends every path that reaches [start] by one step covering [length] letters and producing
     * one of [texts]. Both the single character step and the word step go through here so they are
     * scored on the same scale: the unit penalty is what stops a long run of lone characters from
     * beating a real word that covers the same letters, and the association score is what makes
     * 我|今天 win over two unrelated words that happen to be frequent.
     */
    private fun addInitialStep(
        paths: Array<ArrayList<InitialPath>>,
        start: Int,
        length: Int,
        texts: List<String>,
        scoreOf: (String) -> Int,
    ) {
        val end = start + length
        if (texts.isEmpty() || end >= paths.size) return
        val next = paths[end]
        for (text in texts) {
            for (path in paths[start]) {
                val combined = path.text + text
                if (next.any { it.text == combined }) continue
                next.add(
                    InitialPath(
                        consumed = end,
                        text = combined,
                        // No association term here on purpose: a one letter step is not evidence of
                        // what follows anything, and feeding it the 联想 score only let lone
                        // characters outrank real words. The word step above keeps it.
                        score = path.score + scoreOf(text) - INITIALS_UNIT_PENALTY,
                        lastWord = text,
                        usedWord = path.usedWord,
                    ),
                )
            }
        }
        next.sortByDescending { it.score }
        while (next.size > INITIALS_PATHS) next.removeAt(next.size - 1)
    }

    /** One way of reading a run of initials: how much it ate, what it produced, how good it is. */
    private data class InitialPath(
        val consumed: Int,
        val text: String,
        val score: Int,
        val lastWord: String = "",
        /** True once the path has touched the word index, i.e. it is 简拼 and not lone characters. */
        val usedWord: Boolean = false,
    )

    private data class Ranked(
        val continuesTypedSyllable: Boolean,
        val untyped: Int,
        val agreesWithBestCharacter: Boolean,
        val candidate: Candidate,
    )

    /**
     * The characters the engine would offer for the syllable the user is inside. A completion
     * that starts with one of them is much likelier to be what was meant than one that starts
     * with a different character from the same reading.
     */
    private fun hintCharacters(head: String): Set<Char> {
        if (head.isEmpty()) return emptySet()
        val chars = if (nineKey) {
            dictionary.t9CharsFor(head, topRankSize(head))
        } else {
            dictionary.charsFor(head, 1)
        }
        return chars.toSet()
    }

    /**
     * How many characters of the syllable in progress are offered before the word completions.
     * On the 26 key board one code means one syllable, so the single best character is enough;
     * on the nine key pad several syllables share a signature, and the user needs one character
     * from each of them before word guesses are useful.
     */
    private fun topRankSize(syllable: String): Int =
        if (nineKey) dictionary.t9TopRankSize(syllable) else 1

    /** Offers to convert only the first syllable, leaving the rest of the buffer untouched. */
    private fun addPrefixCandidates(buffer: String, out: MutableList<Candidate>, limit: Int) {
        val firstBoundary = firstSyllableLength(buffer)
        if (firstBoundary <= 0 || firstBoundary >= buffer.length) return
        val head = buffer.substring(0, firstBoundary)
        // The longest pair of syllables first: 实施 is a better answer for "shishizhege" than 是 is,
        // and it has to be in the list before the character table can fill the budget - a syllable
        // contributes up to 48 characters, which used to leave no room for anything behind it.
        val second = firstSyllableLength(buffer.substring(firstBoundary))
        if (second > 0) {
            val two = buffer.substring(0, firstBoundary + second)
            addWordCandidates(two, two.length, out)
        }
        addWordCandidates(head, firstBoundary, out)
        addSyllableCandidates(head, firstBoundary, out, until = PREFIX_CHAR_LIMIT)
    }

    /** The buffer cannot be fully split yet, so complete the trailing fragment. */
    private fun addPartialCandidates(buffer: String, out: MutableList<Candidate>, limit: Int) {
        val partialStart = hasInitial(buffer.first())
        val syllables = if (nineKey) {
            dictionary.t9SyllablesStartingWith(buffer, PARTIAL_SYLLABLE_LIMIT)
        } else {
            dictionary.syllablesStartingWith(buffer, PARTIAL_SYLLABLE_LIMIT)
        }
        for (syllable in syllables) {
            if (syllable == buffer) {
                addSyllableCandidates(syllable, buffer.length, out)
            } else {
                val chars = if (nineKey) dictionary.t9CharsFor(syllable, 2) else dictionary.charsFor(syllable, 2)
                for (ch in chars) {
                    out.add(
                        Candidate(
                            text = ch.toString(),
                            consumed = buffer.length,
                            kind = CandidateKind.Character,
                            score = 5_000,
                            annotation = syllable,
                        ),
                    )
                }
            }
            if (out.size >= limit) break
        }
        if (out.isEmpty() && partialStart) {
            out.add(Candidate(buffer, buffer.length, CandidateKind.Raw))
        }
    }

    /**
     * One letter is the opening of a word, not a word: after "n" the user wants 那/你/能, so the
     * most used syllables starting with that letter each contribute their best character.
     */
    private fun addLeadingSyllableCandidates(letter: String, out: MutableList<Candidate>) {
        val syllables = if (nineKey) {
            dictionary.t9SyllablesStartingWith(letter, PARTIAL_SYLLABLE_LIMIT)
        } else {
            dictionary.syllablesStartingWith(letter, PARTIAL_SYLLABLE_LIMIT)
        }
        for (syllable in syllables) {
            if (syllable == letter) continue
            val chars = if (nineKey) {
                dictionary.t9CharsFor(syllable, 1)
            } else {
                dictionary.charsFor(syllable, 1)
            }
            for (ch in chars) {
                out.add(
                    Candidate(
                        text = ch.toString(),
                        consumed = letter.length,
                        kind = CandidateKind.Character,
                        annotation = syllable,
                    ),
                )
            }
        }
    }

    // ---- segmentation -------------------------------------------------------------

    /**
     * How many characters of a word the buffer already spells out in full. "nih" against 你好
     * (reading "nihao") covers 你 only - the h has started 好's syllable but not finished it -
     * so the candidate is drawn with 好 dimmed.
     *
     * The reading is aligned against the buffer rather than split on its own, because a reading
     * such as "xian" is 先 on its own but 西 + 安 in a two character word: splitting the reading
     * first would call the whole word untyped after "xi" and grey out a character the user has in
     * fact finished.
     */
    private fun uncoveredCharacters(buffer: String, reading: String): Int {
        // The split of a reading is not unique ("xian" is 先 or 西 + 安), and the split decides how
        // many characters count as typed. So the best split wins: the one that spells out the most
        // characters with the buffer the user has actually produced.
        return coverCharacters(buffer, 0, reading, 0, HashMap())
    }

    /**
     * Most characters of [reading] from [index] that the buffer from [typedLength] spells out in
     * full. A syllable that is only half typed stops the count: everything behind it is exactly
     * the part the keyboard is filling in.
     */
    private fun coverCharacters(
        buffer: String,
        typedLength: Int,
        reading: String,
        index: Int,
        memo: HashMap<Long, Int>,
    ): Int {
        if (index >= reading.length) return 0
        val key = (index.toLong() shl 32) or typedLength.toLong()
        memo[key]?.let { return it }

        val upper = minOf(reading.length, index + PinyinSyllables.maxLength)
        var best = 0
        for (end in index + 1..upper) {
            val syllable = reading.substring(index, end)
            if (!PinyinSyllables.isSyllable(syllable)) continue
            val signature = typedForm(syllable)
            if (typedLength + signature.length > buffer.length) continue
            if (!buffer.regionMatches(typedLength, signature, 0, signature.length)) continue
            val rest = coverCharacters(buffer, typedLength + signature.length, reading, end, memo)
            best = maxOf(best, 1 + rest)
        }
        memo[key] = best
        return best
    }

    /** A syllable's form in the current layout: letters on the 26 key board, digits on the pad. */
    private fun typedForm(syllable: String): String = if (nineKey) T9.encode(syllable) else syllable

    /**
     * Length of the longest prefix of [buffer] that is a sequence of complete syllables.
     * Returns 0 when even the first character cannot start a syllable.
     */
    private fun longestCover(buffer: String): Int {
        coverCache[buffer]?.let { return it }
        val result = coverFrom(buffer, 0)
        if (coverCache.size > COVER_CACHE_LIMIT) coverCache.clear()
        coverCache[buffer] = result
        return result
    }

    private fun coverFrom(buffer: String, start: Int): Int {
        var index = start
        var lastBoundary = start
        while (index < buffer.length) {
            val step = matchSyllable(buffer, index)
            if (step == 0) break
            index += step
            lastBoundary = index
        }
        return lastBoundary - start
    }

    private fun firstSyllableLength(buffer: String): Int = matchSyllable(buffer, 0)

    /**
     * Tries to consume one syllable at [index]. Latin input matches the table directly; nine
     * key input matches the syllable's keypad signature, preferring the longest candidate
     * that is either a table syllable or has a nine key reading in the dictionary.
     */
    private fun matchSyllable(buffer: String, index: Int): Int {
        if (index >= buffer.length) return 0
        val maxLength = if (nineKey) MAX_T9_SYLLABLE_LENGTH else PinyinSyllables.maxLength
        val upper = minOf(buffer.length, index + maxLength)
        var best = 0
        for (end in index + 1..upper) {
            val token = buffer.substring(index, end)
            val match = if (nineKey) matchesNineKey(token) else PinyinSyllables.isSyllable(token)
            if (match) best = end - index
        }
        return best
    }

    private fun matchesNineKey(digits: String): Boolean {
        if (!T9.isDigits(digits)) return false
        return dictionary.hasNineKeyReading(digits)
    }

    private fun hasInitial(letter: Char): Boolean = PinyinSyllables.hasInitial(letter)

    private fun normalize(raw: String): String {
        val builder = StringBuilder(raw.length)
        for (ch in raw) {
            when {
                ch in 'a'..'z' -> builder.append(ch)
                ch in 'A'..'Z' -> builder.append(ch.lowercaseChar())
                ch in '0'..'9' -> builder.append(ch)
                else -> Unit
            }
        }
        return builder.toString()
    }

    private fun dedupe(candidates: List<Candidate>, limit: Int): List<Candidate> {
        val seen = HashSet<String>(candidates.size)
        val result = ArrayList<Candidate>(minOf(limit, candidates.size))
        for (candidate in candidates) {
            if (!seen.add(candidate.text)) continue
            result.add(candidate)
            if (result.size >= limit) break
        }
        return result
    }

    companion object {
        private const val WORD_LIMIT = 24
        private const val CHAR_LIMIT = 48
        private const val PARTIAL_SYLLABLE_LIMIT = 12
        /** How many "words this code can still become" to offer. */
        private const val COMPLETION_LIMIT = 12
        /**
         * Pool asked of the dictionary before ranking. It has to be generous: the technical and
         * medical word lists put long, rare words high up the frequency order, so a small pool
         * would drop 你好 before the engine ever got to rank it.
         */
        private const val COMPLETION_POOL_LIMIT = 256
        /** One letter is too little to guess a word from; the single characters win there. */
        private const val COMPLETION_MIN_INPUT = 2
        /** Longest word the sentence decoder will try to fit in one step. */
        private const val MAX_SENTENCE_WORD_SYLLABLES = 6
        /**
         * Characters the sentence decoder tries per syllable. The character table only ranks
         * them, it does not score them, so this is about homophones that the pair model can tell
         * apart (吧/把 after 走) - not about the long tail.
         */
        private const val SENTENCE_CHAR_LIMIT = 3
        /** Charged per rank of character, so a lower-ranked homophone needs the pair model to win. */
        private const val SENTENCE_CHAR_DECAY = 60f
        /**
         * Words per span the decoder keeps as separate paths. Six is what it takes for "shishi" to
         * offer 试试 next to 实施, 事实 and 逝世: sentence candidates are paths, so a word that is
         * not in this list can never become one.
         */
        private const val WORDS_PER_STEP = 6
        /** How many whole-sentence readings to offer. */
        private const val SENTENCE_LIMIT = 8
        /** Characters the "convert only the first syllable" fallback may add. */
        private const val PREFIX_CHAR_LIMIT = 12
        /** Shortest buffer treated as 首字母; a single letter stays a character lookup. */
        private const val INITIALS_MIN_LENGTH = 2
        /** Shortest word initials step; single letters have no word to stand for. */
        private const val MIN_INITIALS = 2
        private const val MAX_INITIALS = 4
        /** Words kept per initials step, and how many readings of a run are offered. */
        /**
         * How many 首字母 readings to offer. Generous on purpose: the corpus ranks 你好 below a
         * dozen place names for "nh", and the candidate list has to still contain it - the bar
         * shows the first few and the expand button the rest.
         */
        private const val INITIALS_LIMIT = 30
        /** How many bucket entries a single 首字母 step may consider before ranking. */
        private const val INITIALS_LOOKUP = 60
        /**
         * Unigram cost of one unit in the sentence decoder, on the dictionary's log-frequency
         * scale: a unit scores its log frequency minus this, so one word beats several characters.
         * Single characters get a fixed score because the asset ranks them but does not score them.
         */
        private const val UNIT_PENALTY = 1200f
        private const val CHARACTER_SCORE = 905f
        /** How much a seen word pair (score 1..1000) is worth next to word frequencies. */
        private const val BIGRAM_WEIGHT = 2f
        /**
         * The same idea for the 首字母 path, where the competition is between ways of cutting a
         * run of initials into words. Word frequencies are close together there, so the pair has
         * to outweigh a mild frequency difference to pick 我今天|去了 over 伪静态|权利.
         */
        private const val INITIALS_BIGRAM_WEIGHT = 3
        /** How many characters one letter of 首字母 may stand for. */
        private const val INITIALS_CHAR_LIMIT = 6
        /**
         * Per-unit cost of the 首字母 path, the same role [UNIT_PENALTY] plays in the sentence
         * decoder: without it every letter could be covered by its own character and the longest
         * run of lone characters would always win.
         */
        private const val INITIALS_UNIT_PENALTY = 1200
        /** Segmentations cache, bounded so a long session cannot grow it without limit. */
        private const val COVER_CACHE_LIMIT = 256
        /** Nine key signatures never get longer than the longest syllable's digit count. */
        private const val MAX_T9_SYLLABLE_LENGTH = 6
        private const val INITIALS_PATHS = 8
    }
}
