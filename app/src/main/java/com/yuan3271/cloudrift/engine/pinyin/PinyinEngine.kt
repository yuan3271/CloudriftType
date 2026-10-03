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

    /**
     * Cached segmentations; typing is repetitive enough for this to pay off. The branch decision
     * and the sentence decoder read the same plan, so they cannot disagree about how much of the
     * buffer is a run of syllables.
     */
    private val splitCache = HashMap<String, SplitPlan>(64)

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
        if (syllables < 2) return rawLast(candidates)
        if (orderProvider() == CandidateOrder.CharacterFirst) {
            // Characters in front, everything else behind: a stable sort, so the engine's own
            // ranking inside each group survives.
            return rawLast(candidates.sortedBy { if (it.text.length == 1) 0 else 1 })
        }
        return rawLast(
            candidates.sortedWith(
                compareBy(
                    { candidate ->
                        when {
                            // 首字母 (and 首字母 + 全拼 mixed) readings are a way of reading the
                            // buffer, not the buffer's own reading, so they never outrank a
                            // spelled out one. They do stay ahead of the candidates that only eat
                            // part of the buffer, which is where a mixed run would otherwise lose
                            // to its own first word ("wojintianqlbj" -> 我今天 used to sit in
                            // front of 我今天去了北京).
                            (candidate.kind == CandidateKind.Initials ||
                                candidate.kind == CandidateKind.Mixed) &&
                                candidate.consumed >= buffer.length -> 2
                            candidate.consumed >= buffer.length && candidate.unmatchedFrom == -1 -> 0
                            candidate.consumed >= buffer.length -> 1
                            else -> 3
                        }
                    },
                    // Inside the "whole buffer, something still to type" group the closest match
                    // wins: 试试看 (one character away) beats 实时控制 (four away).
                    { candidate -> untypedCharacters(candidate) },
                    // Inside the "only part of the buffer" group the longest reach wins, which is
                    // what puts the words that make up the sentence before its single characters.
                    { candidate -> -candidate.consumed },
                    // Spelled out readings follow the sentence-first rule (a longer reading is more
                    // of what the user typed). 首字母 readings keep the order the lattice gave them
                    // (fewest steps first, then score), because how many guesses a reading needed is
                    // not visible from its text.
                    { candidate ->
                        if (candidate.kind == CandidateKind.Initials || candidate.kind == CandidateKind.Mixed) {
                            0
                        } else {
                            -candidate.text.length
                        }
                    },
                    // Last, so it only ever breaks a tie between readings of the same length:
                    // 好吧 has to come out in front of 毫巴 and 是吧 in front of 十八, which no
                    // frequency can say - both are real words and one of them is simply not what
                    // anyone means. See [SPOKEN_TAIL_PHRASES].
                    { candidate -> if (candidate.text == SPOKEN_TAIL_PHRASES[buffer]) 0 else 1 },
                ),
            ),
        )
    }

    /**
     * The raw letters are what the keyboard shows when nothing else fits. A run that was partly
     * spelled out is evidence that the user is typing pinyin, so the raw letters drop behind those
     * readings ("nhao" leads with 你好, "nhao" itself stays underneath). A run that only fits 简拼
     * guesses keeps them in front, which is the "zzz -> 之正在 is noise, the letters are the answer"
     * rule - and the letters never disappear either way.
     */
    private fun rawLast(candidates: List<Candidate>): List<Candidate> {
        if (candidates.none { it.kind == CandidateKind.Raw }) return candidates
        // 原样字母永远排在真读音后面。这里原来留了一个例外：一串字母只读成简拼（候选全是 Initials）
        // 时把原样字母留在最前，理由是"zzz → 在在 是噪声，字母才是答案"。广谱回归台（573 句自撰
        // 语料）量出来这个例外代价太大——它让 **520/573** 句的首字母输入第一名变成一个谁都不要的
        // 字母串；噪声那一半现在由解码器自己挡掉了（只由单字拼凑的路径根本不进候选），所以例外可以
        // 撤掉：字母串还在列表里，随时可以整串上屏。
        val ordered = ArrayList<Candidate>(candidates.size)
        ordered.addAll(candidates.filter { it.kind != CandidateKind.Raw })
        ordered.addAll(candidates.filter { it.kind == CandidateKind.Raw })
        return ordered
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
            // A wider pool than the strip will show, because the 句末语气词 are lifted inside it:
            // 可以了 and 可以吗 are what someone typing 可以 usually wants next, and the corpus has
            // both - just below 可以接受 and 可以想象, which are what a *sentence* corpus writes.
            val words = dictionary.nextWords(context, maxOf(limit * 3, limit))
            if (words.isEmpty()) continue
            val ranked = words.sortedWith(
                compareByDescending<WordEntry> { tailParticleScore(it) }.thenBy { it.word },
            ).take(limit)
            return ranked.map { entry ->
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

    /**
     * What a prediction is worth in the 联想 strip.
     *
     * A 句末语气词 the corpus has seen at all is raised to [PREDICTION_PARTICLE_FLOOR] - a floor,
     * not a bonus, so it lands in the visible part of the strip without ever overtaking a
     * continuation the corpus is actually more sure about (今天→天气 stays ahead of 今天→吗, and
     * 什么→时候 stays ahead of 什么→呢). A particle with no evidence stays at 0 and stays out.
     */
    private fun tailParticleScore(entry: WordEntry): Int {
        val word = entry.word
        if (entry.score <= 0 || word.length != 1 || word[0] !in PREDICTION_PARTICLES) return entry.score
        return maxOf(entry.score, PREDICTION_PARTICLE_FLOOR)
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
            return attested(decodeTop(buffer, bounds, SENTENCE_LIMIT))
                .map { it.text }
                .filter { it != buffer }
                .map { sentence(it, buffer, unmatchedFrom = -1) }
        }
        val prefix = decode(buffer.substring(0, split.covered), bounds) ?: return emptyList()
        // Candidates are compared on the *extended* reading, so the fragment itself is paid for by
        // every one of them; comparing against the covered reading instead would let the fragment
        // ride along for free and always win.
        val alternatives = ArrayList<Reading>()
        for (syllable in dictionary.syllablesStartingWith(fragment, PARTIAL_SYLLABLE_LIMIT)) {
            if (syllable == fragment) continue
            val extended = buffer + syllable.substring(fragment.length)
            val extendedBounds = syllableSplit(extended)?.bounds ?: continue
            alternatives.addAll(decodeTop(extended, extendedBounds, SENTENCE_LIMIT))
        }
        if (alternatives.isEmpty()) return emptyList()
        return attested(alternatives.sortedByDescending { it.score })
            .map { it.text }
            .filter { it != buffer }
            .distinct()
            .take(SENTENCE_LIMIT)
            .map { sentence(it, buffer, unmatchedFrom = prefix.length) }
    }

    /**
     * 把"语料里从来没这样连过"的一整串读法收敛掉。
     *
     * 组合解码能拼出无穷多读法，而大多数读法不是中文：`毫无|青年`、`辛苦|里`、`不及|慢慢|来`、
     * `转账|一|不错` 里的每一段都是词典里的词或常用字，相邻两段却在语料里从来没一起出现过
     * （证据 0）。这类读法凑在一起就是用户眼里的"不成句的候选"。
     *
     * 但只在**一整串读法一条证据都没有**时收敛：那种情况下剩下的全是同一串音节的同音字洗牌，
     * 留解码器最看好的那一条就够。只要有一条读法带证据，一条都不删——语料没覆盖、却确实是用户
     * 想打的句子（`那|挺|好|的`、`票|买|好|了|吗`）和噪声在证据这一维上完全一样，多删一条就少
     * 一句正常话（广谱回归台实测）。词组与专名是词典里的词，走另一条候选来源，不受影响。
     */
    private fun attested(readings: List<Reading>): List<Reading> {
        // 没有搭配模型时"证据"恒为 0，那不是"语料里没连过"，是"无从知道"（测试里的词典就是
        // 这种），照删会把每条读法都删掉。
        if (!dictionary.hasAssociationModel) return readings
        if (readings.size <= UNBACKED_READINGS) return readings
        // 只要有一条读法带着语料证据，就一条都不删。
        //
        // 这一条是量出来的，不是保守：同一个字形骨架下常常有真也有假（`那|挺|好|的` 对
        // `那|听|好|的`、`票|买|好|了|吗` 对 `票|买|好|了|马`），"删掉没证据的"分不出谁是谁，
        // 广谱回归台实测每删一条就少一句正常话。所以真正被删的只有另一种情况——一整串读法**一条
        // 证据都没有**：那不是"挑了错的"，而是"这串音节根本没被语料读到过"，剩下的全是同音字洗牌
        // （`毫无|青年`、`辛苦|里`、`不及|慢慢|来`、`转账|一|不错`），留一条最好的猜测就够，铺满
        // 八条只会让候选栏看起来全是"不成句的结果"。
        if (readings.any { it.evidence > 0 }) return readings
        return readings.take(UNBACKED_READINGS)
    }

    /**
     * 一条接缝的搭配证据：句首边界（[SENTENCE_START]）不是接缝。
     *
     * `^今天` 说明"今天"能开句，它没有说这条读法左右两段能不能连。把它算进去以后，几乎每条读法
     * 都能凑出一分"证据"，[attested] 于是形同虚设——`辛苦|里`、`毫无|青年`、`转账|一|不错` 全部
     * 照样留在候选里。
     */
    private fun internalEvidence(lastUnit: String, pair: Int): Int =
        if (lastUnit == SENTENCE_START) 0 else pair

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
    private fun decodeTop(buffer: String, bounds: IntArray, count: Int): List<Reading> {
        val syllables = bounds.size - 1
        val paths = Array(syllables + 1) { ArrayList<Path>(count) }
        // The first unit is scored against the sentence boundary, not against nothing: without it
        // position 0 is the one place the decoder has no association evidence at all, which is how
        // 吗 (the leading character of "ma") could open a sentence. See [SENTENCE_START].
        paths[0].add(Path(0f, "", SENTENCE_START))
        for (start in 0 until syllables) {
            if (paths[start].isEmpty()) continue
            val syllable = buffer.substring(bounds[start], bounds[start + 1])
            // The last syllable is the only place a sentence-final particle can be, and the only
            // place it is worth reading past the top few characters of the table: 呗 is the 16th
            // character of "bei" and 嘞 the 18th of "lei", yet 好呗 / 走嘞 are exactly what someone
            // typing those letters means.
            val isTail = start + 1 == syllables
            // The rule exists to beat the *ordinary* character of the same syllable (压 for 呀, 拉
            // for 啦, 把 for 吧, 被 for 呗). It deliberately does nothing for a syllable whose own
            // first character is already a particle - 吗/嘛, 哦/噢/喔, 哟/唷 - because choosing
            // between two particles is the pair model's business. Which of 干吗 / 干嘛 wins is
            // therefore the corpus's call, not this rule's (the everyday-word layer later gave
            // 干嘛 the corpus evidence, see build_dict.py's TATOEBA_FLOOR_BASE).
            val particles = if (isTail) {
                val tail = TAIL_PARTICLES[syllable].orEmpty()
                val best = dictionary.charsFor(syllable, 1)
                if (best.isNotEmpty() && best[0] !in tail) tail else ""
            } else {
                ""
            }
            // Not just the single best character: letters that read the same cover homophones
            // (ba is 把 and 吧), and which one is right is not a per-character fact - it is what
            // the pair model knows (走 is followed by 吧, never by 把). Ranking them by the table
            // order with a small decay lets the association decide, which is how 我们走吧 stops
            // coming out as 我们走把.
            val characters = dictionary.charsFor(
                syllable,
                if (particles.isEmpty()) SENTENCE_CHAR_LIMIT else TAIL_CHAR_LIMIT,
            )
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
                    // 一句话不会以句末语气词开头（"吗/吧/呢/啦/呀/嘛…"；能当叹词的 啊/哦/哇/哟
                    // 不算在这一类里）。ma 的首选字恰好是 吗，不挡掉它，"mashangle" 就只出
                    // 吗上了——而 马上 就在隔壁。
                    if (start == 0 && character in FINAL_ONLY_PARTICLES) continue
                    // The association model conditions on the last *unit*, character or word: after
                    // a lone 我 the next word should be scored just like after a word, otherwise a
                    // sentence decoded mostly into single characters gets no association at all.
                    // At the start of the run the boundary evidence is deliberately *not* used for
                    // single characters: which **word** opens a sentence is information (马上,
                    // 下雨, 实施), which *character* opens one mostly means "this character is
                    // common" - 是, 那, 好 - and the lattice then uses it to prefer 事|是|这个 over
                    // the word 实施. The word steps still get it, see [SENTENCE_START].
                    val pair = if (path.lastToken == SENTENCE_START) {
                        0
                    } else {
                        dictionary.bigramScore(path.lastToken, character.toString())
                    }
                    // A 句末语气词 needs no evidence and pays no rank penalty at the end of a
                    // sentence: it is what the user typed, and it is the one thing the corpus
                    // cannot be asked about (好呀 is spelled 好压 in a news corpus, 下雨啦 as
                    // 下雨拉). Everywhere else the ordinary rule stands.
                    val particle = particles.indexOf(character) >= 0
                    if (particle) {
                        push(
                            paths[start + 1],
                            path.score + CHARACTER_SCORE - UNIT_PENALTY +
                                pair * BIGRAM_WEIGHT + TAIL_PARTICLE_BONUS,
                            path.text + character,
                            count,
                            lastToken = character.toString(),
                            evidence = path.evidence + pair,
                        )
                        continue
                    }
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
                        evidence = path.evidence + pair,
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
                            // The sentence boundary is not an internal join: "^今天" says 今天 can
                            // open a sentence, it says nothing about whether the units of this
                            // reading belong together. Counting it made almost every reading look
                            // attested (see [attested]).
                            evidence = path.evidence + internalEvidence(path.lastToken, pair),
                        )
                    }
                }
            }
        }
        return paths[syllables]
            .sortedByDescending { it.score }
            .filter { it.text.isNotEmpty() }
            .map { Reading(it.text, it.score, it.evidence) }
    }

    /** 一条读法：文本、路径分数，以及它拿到的搭配证据（0 = 语料里没有这个连法）。 */
    private data class Reading(val text: String, val score: Float, val evidence: Int)

    private fun push(
        paths: ArrayList<Path>,
        score: Float,
        text: String,
        count: Int,
        lastToken: String = "",
        evidence: Int = 0,
    ) {
        // 去重要连上下文一起看：一条路径的分数只取决于（到哪了、文本、最后一段是什么），两条
        // 文本相同、切分不同的路径不是同一条。`今天|天气` 与词典词 `今天天气` 都写"今天天气"，
        // 以前按文本去重把后者挤掉，于是续在后面的搭配分是拿 `今天天气` 去算的，正确读法
        // `今天|天气|很好` 永远排不上来。加上 lastToken 以后两条各走各的，最好的那条赢。
        if (paths.any { it.text == text && it.lastToken == lastToken }) return
        if (paths.size >= count && paths.last().score >= score) return
        paths.add(Path(score, text, lastToken, evidence))
        paths.sortByDescending { it.score }
        while (paths.size > count) paths.removeAt(paths.size - 1)
    }

    private data class Path(
        val score: Float,
        val text: String,
        val lastToken: String = "",
        /**
         * 这条读法到目前为止拿到的搭配证据之和。0 表示它是由几个互不相干的单元拼出来的——
         * `毫无|青年`、`辛苦|里`、`不及|慢慢|来` 都是这种，语料里从来没人这样连过。
         */
        val evidence: Int = 0,
    )

    /** The single best reading of the run of syllables, or null when none exists. */
    private fun decode(buffer: String, bounds: IntArray): String? =
        decodeTop(buffer, bounds, 1).firstOrNull()?.text

    private data class SyllableSplit(val bounds: IntArray, val covered: Int, val fragment: String)

    /**
     * How the buffer is cut into syllables: how far [covered] the cut reaches, and the syllable
     * length [steps] taken at every index on the way there.
     */
    private data class SplitPlan(val covered: Int, val steps: IntArray)

    /**
     * Syllable split of the part that can be read, plus whatever is left over. The leftover is not
     * an error any more: it is the syllable the user is in the middle of typing.
     */
    private fun syllableSplit(buffer: String): SyllableSplit? {
        val plan = splitPlan(buffer)
        if (plan.covered == 0) return null
        val bounds = ArrayList<Int>(8)
        bounds.add(0)
        var index = 0
        while (index < plan.covered) {
            val step = plan.steps[index]
            if (step == 0) break
            index += step
            bounds.add(index)
        }
        return SyllableSplit(bounds.toIntArray(), index, buffer.substring(index))
    }

    /**
     * Cuts the buffer into syllables the way the reader typed them, with one correction.
     *
     * Longest match is the rule, and it stays the rule - but on its own it can walk into a syllable
     * the dictionary has nothing behind. "jidangeng" then cuts as ji + dang + eng, and because
     * "eng" is a syllable of the table with no character and no word of its own, the sentence
     * decoder's lattice dead-ends on the last position and the whole sentence disappears: the bar
     * showed 激荡 and single characters where 鸡蛋羹 belongs. It bites hardest on long buffers,
     * where the chance that one syllable of the run is unusable is that much higher.
     *
     * So every position is planned from the end backwards. The syllable that covers the most
     * letters wins; between ways of covering the same letters, the one with fewer syllables the
     * dictionary cannot convert, then the longer first syllable. Nothing else moves: a plan only
     * differs from longest match where longest match would dead-end.
     */
    private fun splitPlan(buffer: String): SplitPlan {
        splitCache[buffer]?.let { return it }
        val size = buffer.length
        val reach = IntArray(size + 1) { it }
        val dead = IntArray(size + 1)
        val steps = IntArray(size + 1)
        val maxLength = if (nineKey) MAX_T9_SYLLABLE_LENGTH else PinyinSyllables.maxLength
        for (start in size - 1 downTo 0) {
            var bestReach = start
            var bestDead = 0
            var bestStep = 0
            val upper = minOf(size, start + maxLength)
            // Longest first, so an equally good shorter syllable never displaces a longer one.
            for (end in upper downTo start + 1) {
                if (!isSyllableToken(buffer, start, end)) continue
                val candidateReach = reach[end]
                if (candidateReach < bestReach) continue
                val candidateDead = dead[end] + if (converts(buffer, start, end)) 0 else 1
                if (candidateReach > bestReach || candidateDead < bestDead) {
                    bestReach = candidateReach
                    bestDead = candidateDead
                    bestStep = end - start
                }
            }
            reach[start] = bestReach
            dead[start] = bestDead
            steps[start] = bestStep
        }
        val plan = SplitPlan(reach[0], steps)
        if (splitCache.size > COVER_CACHE_LIMIT) splitCache.clear()
        splitCache[buffer] = plan
        return plan
    }

    /**
     * Whether the dictionary can turn this syllable into text at all. A syllable that is only in
     * the table - "eng", "shei", "fiao" - has nothing to show, and a split that ends on one is a
     * split the decoder cannot finish.
     */
    private fun converts(buffer: String, start: Int, end: Int): Boolean {
        val token = buffer.substring(start, end)
        return if (nineKey) {
            dictionary.t9CharsFor(token, 1).isNotEmpty()
        } else {
            dictionary.charsFor(token, 1).isNotEmpty()
        }
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
        val budget = syllableBudget(buffer)
        for (word in words) {
            // One character per syllable, and no more; see [syllableBudget]. Typing "kan" means 看,
            // and 看到 belongs to "kand" - where the d is the 到's own letter. Without this the
            // bar buried the character the user was typing under every word that happens to begin
            // with the same syllable.
            if (word.word.length > budget) continue
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

    /**
     * 首字母 candidates, and the 混合输入 (initials mixed with full pinyin) that shares their
     * decoding: every character is covered by at least its initial, so the whole buffer is
     * consumed and nothing is left to type. "nh" gives 你好 without typing a single full syllable,
     * and "nhao" gives it with the 好 spelled out.
     *
     * A run is decoded as a sequence of steps, not looked up as one string: "jtzmy" is
     * 今天 + 怎么样 (jt | zmy), and the same letters also reach 今天怎么 / 今天这么 through
     * 今天 + 怎么 (jt | zm). A step may be
     *  - one letter standing for one character (简拼),
     *  - two to four letters standing for a whole word (简拼),
     *  - a syllable typed out in full, or
     *  - a word typed out in full (全拼).
     * The last two are what make a mixed run work: "wojt" is 我 + 今天, "wojintianqlbj" is
     * 我 + 今天 + 去 + 了 + 北京. Partial paths are kept on purpose with their own `consumed`, so
     * picking one leaves the rest of the buffer to carry on typing.
     */
    private fun addInitialCandidates(buffer: String, out: MutableList<Candidate>) {
        if (nineKey || buffer.length < INITIALS_MIN_LENGTH) return

        val letters = buffer.lowercase()
        if (letters.any { it !in 'a'..'z' }) return
        // position -> the paths that reach it, best first
        val paths = Array(letters.length + 1) { ArrayList<InitialPath>(INITIALS_PATHS) }
        // 首字母/混合那半套解码同样从句子开头起步，两套解码对"第一个词"的判断才是一致的。
        paths[0].add(InitialPath(consumed = 0, text = "", score = 0, lastWord = SENTENCE_START))

        for (start in 0 until letters.length) {
            if (paths[start].isEmpty()) continue
            // A single letter may stand for one character (我, 去, 了 ...). Without this step the
            // run can never be cut the way it is actually typed - 简拼 is 我|今天|去|了|北京 - and
            // the initials index has no single character words in it at all.
            val letter = letters.substring(start, start + 1)
            val tail = start + 1 == letters.length
            // 收尾那一步额外把语气词带上：单字母那一步只给"这个音节里最常用的字"，`l` 底下是 里/来/路,
            // 了 排不进这几个，于是 `xinkule` 只能出 辛苦里。语气词是句子最可能的结尾，见 addInitialStep。
            val tailParticles = if (tail) {
                TAIL_PARTICLES.filterKeys { it.startsWith(letter) }.values.flatMap { it.map(Char::toString) }
            } else {
                emptyList()
            }
            addInitialStep(
                paths = paths,
                start = start,
                length = 1,
                texts = dictionary.initialCharacters(letter, INITIALS_CHAR_LIMIT).map { it.toString() } + tailParticles,
                scoreOf = { CHARACTER_SCORE.toInt() },
                tail = tail,
            )
            // The 全拼 half of a mixed run: a syllable spelled out in full.
            addSpelledSyllableSteps(paths, letters, start)
            // Keys go up to MIXED_WORD_LENGTH letters: an all-initials key is as long as its word
            // (two to four), a mixed one is longer by however much of the word was spelled out.
            val longest = minOf(MIXED_WORD_LENGTH, letters.length - start)
            for (length in MIN_INITIALS..longest) {
                val key = letters.substring(start, start + length)
                // The whole bucket, not the top few: the ranking below moves words up by how
                // natural their syllables are and whether they are common, and a word the lookup
                // already dropped can never come back.
                val words = dictionary.wordsForInitials(key, INITIALS_LOOKUP)
                if (words.isEmpty()) continue
                for (word in words) {
                    // A key longer than the word means the first syllable was given as its initial
                    // and the rest was spelled out ("nhao" -> 你好): that half is 全拼, and saying so
                    // is what lets the decoder prefer it over 你好 + 奥.
                    val mixed = dictionary.abbreviatesFirstSyllable(key, word.word)
                    for (path in paths[start]) {
                        val next = paths[start + length]
                        val text = path.text + word.word
                        // 同上：文本相同的两条读法切分可能不同，续在后面的搭配分也不同。
                        if (next.any { it.text == text && it.lastWord == word.word }) continue
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
                                usedInitials = true,
                                usedSyllable = path.usedSyllable || mixed,
                                units = path.units + 1,
                                evidence = path.evidence + internalEvidence(path.lastWord, pair),
                            ),
                        )
                        next.sortByDescending { it.score }
                        while (next.size > INITIALS_PATHS) next.removeAt(next.size - 1)
                    }
                }
            }
            // The other 全拼 half: a whole word spelled out in full (今天 for "jintian").
            addSpelledWordSteps(paths, letters, start)
        }

        // 每条读法连同它拿到的搭配证据一起收集，最后按"有证据的在前、没有的只在一条都没有时
        // 兜底"过滤——和整句解码同一条规矩（见 [attested]）。
        val ranked = ArrayList<Pair<Candidate, Int>>(INITIALS_LIMIT)
        for (consumed in letters.length downTo MIN_INITIALS) {
            // Fewer steps first: a reading that eats the same letters as a single word ("wsm" ->
            // 为什么) beats one that needed two ("w(sm)" -> 无什么), even though the two-step one
            // scores higher on word frequency alone. Ties keep the score order the lattice built.
            for (path in paths[consumed].sortedWith(compareBy({ it.units }, { -it.score }))) {
                if (path.text.length < 2) continue
                // A reading made only of lone characters is not 简拼, it is noise: every run of
                // letters fits one ("women" -> 无藕木耳南, "zzz" -> 在在), and offering it is how
                // the feature used to bury the word the user actually typed. Real 简拼 always
                // touches a word - 你好 for "nh", 今天|怎么样 for "jtzmy" - so a path that used
                // neither the word index nor a syllable spelled out in full is dropped.
                if (!path.usedWord && !path.usedSyllable) continue
                val mixed = path.usedInitials && path.usedSyllable
                // The whole buffer is exactly one word ("nh" -> 你好, "nhao" -> 你好): that is a
                // dictionary hit, not a 首字母 guess, and it ranks like the spelled-out words do.
                val exactWord = path.units == 1 && path.usedWord && consumed == letters.length
                ranked.add(
                    Candidate(
                        text = path.text,
                        consumed = consumed,
                        kind = when {
                            exactWord -> CandidateKind.Conversion
                            mixed -> CandidateKind.Mixed
                            path.usedInitials -> CandidateKind.Initials
                            // A run that was spelled out and still does not reach the end of the
                            // buffer is an ordinary partial reading ("wojintianqlbj" -> 我今天),
                            // not a 首字母 one.
                            else -> CandidateKind.Conversion
                        },
                        score = path.score,
                        annotation = when {
                            mixed -> MIXED_ANNOTATION
                            path.usedInitials -> INITIALS_ANNOTATION
                            else -> letters.substring(0, consumed)
                        },
                        // Every character was given at least its initial, so nothing is dimmed.
                        unmatchedFrom = -1,
                    ) to path.evidence,
                )
            }
            if (ranked.size >= INITIALS_LIMIT) break
        }
        // 和整句解码同一条规矩（见 [attested]）：只要有一条带证据的读法就一条都不删；一条证据都
        // 没有时，那串字母只是把同音字洗来洗去，留最好的那条。
        val selected = if (!dictionary.hasAssociationModel || ranked.any { it.second > 0 }) {
            ranked
        } else {
            ranked.take(UNBACKED_READINGS)
        }
        out.addAll(selected.map { it.first }.take(INITIALS_LIMIT))
    }

    /**
     * The 全拼 half of a mixed run: the letters at [start] are one syllable typed out in full, so
     * they produce that syllable's characters - "nhao" is n + hao, and the hao is what makes 好
     * an answer.
     *
     * Ranked like the sentence decoder ranks its characters: the syllable's best character is
     * always a path, and a lower ranked homophone only joins in when the pair model expects it
     * here, so the lattice does not fill with 我门/我闷-style variants.
     */
    private fun addSpelledSyllableSteps(
        paths: Array<ArrayList<InitialPath>>,
        letters: String,
        start: Int,
    ) {
        val upper = minOf(letters.length, start + PinyinSyllables.maxLength)
        for (end in start + 2..upper) {
            val syllable = letters.substring(start, end)
            if (!PinyinSyllables.isSyllable(syllable)) continue
            val chars = dictionary.charsFor(syllable, SPELLED_CHAR_LIMIT)
            val next = paths[end]
            for (rank in chars.indices) {
                val character = chars[rank].toString()
                for (path in paths[start]) {
                    val combined = path.text + character
                    if (next.any { it.text == combined && it.lastWord == character }) continue
                    val pair = dictionary.bigramScore(path.lastWord, character)
                    if (rank > 0 && pair <= 0) continue
                    next.add(
                        InitialPath(
                            consumed = end,
                            text = combined,
                            score = path.score + (
                                CHARACTER_SCORE - rank * SENTENCE_CHAR_DECAY - UNIT_PENALTY +
                                    pair * BIGRAM_WEIGHT
                                ).toInt(),
                            lastWord = character,
                            usedWord = path.usedWord,
                            usedInitials = path.usedInitials,
                            usedSyllable = true,
                            units = path.units + 1,
                            evidence = path.evidence + internalEvidence(path.lastWord, pair),
                        ),
                    )
                }
            }
            next.sortByDescending { it.score }
            while (next.size > INITIALS_PATHS) next.removeAt(next.size - 1)
        }
    }

    /**
     * The 全拼 half as a whole word: the letters at [start] spell a reading the dictionary has
     * ("jintian" -> 今天). Without this step a mixed run would have to fall back to the syllables'
     * single characters, and 我今天|去了|北京 would lose to 我|进|天|去了|北京.
     */
    private fun addSpelledWordSteps(
        paths: Array<ArrayList<InitialPath>>,
        letters: String,
        start: Int,
    ) {
        val upper = minOf(letters.length, start + SPELLED_WORD_LETTERS)
        for (end in start + 2..upper) {
            val reading = letters.substring(start, end)
            val words = dictionary.wordsFor(reading, SPELLED_WORDS_PER_STEP)
            if (words.isEmpty()) continue
            val next = paths[end]
            for (word in words) {
                for (path in paths[start]) {
                    val combined = path.text + word.word
                    if (next.any { it.text == combined && it.lastWord == word.word }) continue
                    val pair = dictionary.bigramScore(path.lastWord, word.word)
                    next.add(
                        InitialPath(
                            consumed = end,
                            text = combined,
                            score = path.score +
                                (word.score - UNIT_PENALTY + pair * BIGRAM_WEIGHT).toInt(),
                            lastWord = word.word,
                            usedWord = true,
                            usedInitials = path.usedInitials,
                            // The word was typed out in full, so this half of the run is 全拼.
                            usedSyllable = true,
                            units = path.units + 1,
                            evidence = path.evidence + internalEvidence(path.lastWord, pair),
                        ),
                    )
                }
            }
            next.sortByDescending { it.score }
            while (next.size > INITIALS_PATHS) next.removeAt(next.size - 1)
        }
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
        /** True for the one letter step that ends the run, see the 句末语气词 note below. */
        tail: Boolean = false,
    ) {
        val end = start + length
        if (texts.isEmpty() || end >= paths.size) return
        val next = paths[end]
        for (text in texts) {
            for (path in paths[start]) {
                val combined = path.text + text
                if (next.any { it.text == combined && it.lastWord == text }) continue
                // 一句话的最后**一个字母**也遵守"句末语气词优先"：`xinkule` 的 l 应该是 了，而单字母那一步
                // 按音节常用度给字（里/来），搭配模型根本没参与——573 句的自撰语料里，`辛苦了 → 辛苦里`
                // 就是这么来的。只有收尾那一步、只有语气词字，其余照旧不带搭配分。
                val particle = tail && text.length == 1 && text[0] in TAIL_PARTICLE_CHARS
                val pair = if (particle) dictionary.bigramScore(path.lastWord, text) else 0
                next.add(
                    InitialPath(
                        consumed = end,
                        text = combined,
                        // No association term for a lone letter in general: one letter is not
                        // evidence of what follows anything, and feeding every step the 联想 score
                        // only let lone characters outrank real words. The word step above keeps it.
                        score = path.score + scoreOf(text) - INITIALS_UNIT_PENALTY +
                            (if (particle) pair * INITIALS_BIGRAM_WEIGHT + PARTICLE_STEP_BONUS else 0),
                        lastWord = text,
                        usedWord = path.usedWord,
                        usedInitials = true,
                        units = path.units + 1,
                        evidence = path.evidence + internalEvidence(path.lastWord, pair),
                    ),
                )
            }
        }
        next.sortByDescending { it.score }
        while (next.size > INITIALS_PATHS) next.removeAt(next.size - 1)
    }

    /** One way of reading a run of initials (or a mix of initials and full syllables). */
    private data class InitialPath(
        val consumed: Int,
        val text: String,
        val score: Int,
        val lastWord: String = "",
        /** True once the path has touched the word index, i.e. it is 简拼 and not lone characters. */
        val usedWord: Boolean = false,
        /** True once a single letter has stood for a character, i.e. the path is 首字母 at all. */
        val usedInitials: Boolean = false,
        /**
         * True once a syllable has been typed out in full. That alone makes a path a real reading:
         * "zhongguo" cut into two spelled syllables is not the lone character noise [usedWord]
         * guards against, and a mixed run needs it to be offered at all.
         */
        val usedSyllable: Boolean = false,
        /** How many steps the reading is built from; one means a single dictionary word. */
        val units: Int = 0,
        /** 搭配证据之和，0 表示这条读法是几个互不相干的单元拼出来的（见 [attested]）。 */
        val evidence: Int = 0,
    )

    /** Ranking key for one completion, see [addCompletionCandidates] for the order. */
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
     * How many characters the buffer has room for: one per syllable it spells, plus one for the
     * syllable it is in the middle of ("kand" is kan + the start of a second one, so two).
     *
     * This is the bound on 码前缀补全. A completion is a guess about what the user has *not*
     * typed yet, and the only honest amount to guess is "one character per syllable you gave me":
     * with "kan" the user has asked for one character (看), and 看到 is not more of that answer,
     * it is a different reading of input that has not been given (kandao). The letters are the
     * evidence - "kand" has the d of 到 in it, "kan" does not.
     */
    private fun syllableBudget(buffer: String): Int {
        val split = syllableSplit(buffer) ?: return 0
        val whole = split.bounds.size - 1
        return whole + if (split.fragment.isEmpty()) 0 else 1
    }

    /**
     * Length of the longest prefix of [buffer] that is a sequence of complete syllables.
     * Returns 0 when even the first character cannot start a syllable.
     */
    private fun longestCover(buffer: String): Int = splitPlan(buffer).covered

    private fun firstSyllableLength(buffer: String): Int = matchSyllable(buffer, 0)

    /**
     * Tries to consume one syllable at [index]: the longest token that can stand as a syllable
     * there. See [isSyllableToken] for what "can stand" means.
     */
    private fun matchSyllable(buffer: String, index: Int): Int {
        if (index >= buffer.length) return 0
        val maxLength = if (nineKey) MAX_T9_SYLLABLE_LENGTH else PinyinSyllables.maxLength
        val upper = minOf(buffer.length, index + maxLength)
        for (end in upper downTo index + 1) {
            if (isSyllableToken(buffer, index, end)) return end - index
        }
        return 0
    }

    /**
     * Whether `buffer[start, end)` reads as one syllable here. Latin input matches the table
     * directly; nine key input matches the syllable's keypad signature, either a table syllable or
     * one with a nine key reading in the dictionary.
     *
     * One letter syllables (a/e/o/m/n) are interjections: nobody types "nhao" meaning 嗯好.
     * Counting them as a syllable makes a 首字母+全拼 run look like full pinyin (n | hao) and hides
     * the reading the user meant (n + hao -> 你好), so a single letter only stands on its own at
     * the end of the buffer, where it cannot be the initial of the syllable that follows it.
     */
    private fun isSyllableToken(buffer: String, start: Int, end: Int): Boolean {
        val token = buffer.substring(start, end)
        val match = if (nineKey) matchesNineKey(token) else PinyinSyllables.isSyllable(token)
        return match && (nineKey || end - start > 1 || end == buffer.length)
    }

    private fun matchesNineKey(digits: String): Boolean {
        if (!T9.isDigits(digits)) return false
        return dictionary.hasNineKeyReading(digits)
    }

    private fun hasInitial(letter: Char): Boolean = PinyinSyllables.hasInitial(letter)

    /**
     * 句末语气词, grouped by their syllable. A sentence ends on one of these far more often than the
     * news corpus suggests: 好呀, 下雨啦, 好吧, 是吗 are things people type all day and things a
     * written corpus writes as 好压 / 下雨拉 / 毫巴 / 十八. So a character in this table is a
     * first-class reading of its syllable - but only in the last position, which is the one place
     * it cannot be confused with the ordinary word that happens to share its sound (把/八 for 吧,
     * 压 for 呀, 拉 for 啦).
     *
     * The interjections that *open* a sentence (哎, 唉, 诶, 嗯, 喂, 嗨, 嘿) are deliberately not
     * here: they are 叹词, and they are already the leading character of their syllable.
     */
    private val TAIL_PARTICLES: Map<String, String> = mapOf(
        "a" to "啊",
        "ba" to "吧",
        "bei" to "呗",
        "de" to "的",
        "la" to "啦",
        "le" to "了",
        "lei" to "嘞",
        "lie" to "咧",
        "lou" to "喽",
        "luo" to "啰",
        "ma" to "吗嘛",
        "na" to "哪",
        "ne" to "呢",
        "o" to "哦噢喔",
        "wa" to "哇",
        "ya" to "呀",
        "yo" to "哟唷",
    )

    /**
     * What the 联想 strip is allowed to lift: the endings a written corpus under-represents
     * (好**吧**, 是**吗**, 走**啦**), not 的 and 了 - those two are already the first thing the
     * corpus says after most words (知道→了, 好→的), so a bonus would only push them in front of
     * the *content* the person is actually reaching for (今天→天气).
     */
    private val PREDICTION_PARTICLES: Set<Char> = "吧吗呢啊呀啦哦嘛哇哟呗咯喽咧噢喔唷".toSet()

    /** [TAIL_PARTICLES] as characters: the 26 key tail rule and the 简拼 tail step both need it. */
    private val TAIL_PARTICLE_CHARS: Set<Char> = TAIL_PARTICLES.values.joinToString("").toSet()

    /**
     * 只能收尾、不能开头的语气词。和 [PREDICTION_PARTICLES] 差在 啊/哦/哇/哟：那四个既是句末
     * 语气词又是叹词，`啊，你说什么` 是正常的开头，所以它们不在禁止之列；剩下的（吧/吗/呢/啦/
     * 呀/嘛/呗/咯/喽/咧/喔/唷）没有人会拿来开头。
     */
    private val FINAL_ONLY_PARTICLES: Set<Char> = "吧吗呢啦呀嘛呗咯喽咧喔唷".toSet()

    /**
     * 口语句末短语: reading -> what someone typing those letters means.
     *
     * The two character 语气词 phrases are the one place where the word list is actively wrong for
     * a keyboard: 毫巴, 十八 and 号码 are all real words, all three are what the corpus ranks first
     * for "haoba", "shiba" and "haoma", and not one of them is what the person typing means. The
     * sentence decoder does read all three correctly (好|吧 is a better pair than 毫|巴), it just
     * arrives after the dictionary word because a word outranks a sentence of the same length.
     *
     * Curated, in the same spirit as [PinyinDictionary.COMMON_WORDS]: a short product statement
     * about what a Chinese keyboard is expected to offer first, not a statistic. Only the exact
     * reading is lifted, and only as the last tie breaker, so it cannot move anything else.
     */
    private val SPOKEN_TAIL_PHRASES: Map<String, String> = mapOf(
        "haoba" to "好吧",
        "haoma" to "好吗",
        "shima" to "是吗",
        "shiba" to "是吧",
        "duiba" to "对吧",
        "xingba" to "行吧",
        "zouba" to "走吧",
        "laiba" to "来吧",
        "haoya" to "好呀",
        "shiya" to "是呀",
        "duiya" to "对呀",
        "haola" to "好啦",
        "laila" to "来啦",
        "zoula" to "走啦",
        "xingma" to "行吗",
    )

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
         * them, it does not score them, so a rank wide enough to reach the character the *pair
         * model* likes is what this buys.
         *
         * 3 was too narrow: 杯 is the 6th character of "bei" (被背北備倍**杯**), so with 3 the
         * decoder could not even build 我|想|喝|杯|咖啡 and the correct reading was never on the
         * bar to begin with - no amount of filtering can remove a wrong candidate if the right one
         * was never generated. 16 is where the 573 句广谱台 stops improving (496 vs 432 at 3);
         * the `rank > 0 && pair <= 0` rule above still keeps the bar from filling with 我门/我闷,
         * and a 46 letter buffer still decodes in 0.1 ms.
         */
        private const val SENTENCE_CHAR_LIMIT = 16
        /**
         * Characters tried for the *last* syllable of a run, when that syllable can close a
         * sentence. The 句末语气词 sit deep in their syllable's table - 呗 is the 16th character of
         * "bei", 嘞 the 18th of "lei" - and they are the one class of character that a sentence can
         * end on with nothing in the corpus to back it up. Only the particles are read that far;
         * for every other character the pair rule below still applies.
         */
        private const val TAIL_CHAR_LIMIT = 20
        /**
         * What a 句末语气词 is worth at the end of the run, on top of the rank-0 score it is given.
         * Small next to the unit penalty, but it is what decides 好啦 over 好拉 and 好呀 over 好压:
         * there the pair model is silent about both, so the tie has to be broken deliberately.
         */
        private const val TAIL_PARTICLE_BONUS = 250f
        /**
         * 首字母解码收尾那一步给语气词的加分。和 [TAIL_PARTICLE_BONUS] 同一件事、同一量级，
         * 只是那条路径的分数是整数。
         */
        private const val PARTICLE_STEP_BONUS = 250
        /**
         * Where an observed 句末语气词 sits in the 联想 strip. 800 is "clearly visible, below the
         * words the corpus is most sure about": 可以吗 keeps its place in the strip next to
         * 可以接受, and 今天吗 still does not come out ahead of 今天天气.
         */
        private const val PREDICTION_PARTICLE_FLOOR = 800
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
        /**
         * How many characters one letter of 首字母 may stand for. Wider than it looks like it needs
         * to be: the syllables that start with a letter are ordered by how much text they carry, not
         * by how likely a single character is, so 我 (wo) sits seventh under "w" (无/为/网/问/完/外
         * first). Offering the whole run lets the pair model choose - 我|知道 scores on the
         * 我 -> 知道 pair, while 无|知道 has nothing behind it.
         */
        private const val INITIALS_CHAR_LIMIT = 8
        /** Characters per syllable the mixed (首字母 + 全拼) decoder tries; see its sibling above. */
        private const val SPELLED_CHAR_LIMIT = 3
        /** Words per spelled out reading the mixed decoder tries. */
        private const val SPELLED_WORDS_PER_STEP = 6
        /** Longest reading the mixed decoder looks up as a whole word, in letters. */
        private const val SPELLED_WORD_LETTERS = 12
        /** Longest 首字母 key: an all-initials word is 2-4 letters, a mixed one is spelled out longer. */
        private const val MIXED_WORD_LENGTH = 12
        /** Labels the candidate bar shows under a 首字母 / mixed reading. */
        private const val INITIALS_ANNOTATION = "首字母"
        private const val MIXED_ANNOTATION = "混合"
        /**
         * 句子开头的左侧上下文，与 `tools/dictgen/build_bigram.py` 的 `BOUNDARY` 同一个键。
         */
        private const val SENTENCE_START = "^"
        /**
         * Per-unit cost of the 首字母 path, the same role [UNIT_PENALTY] plays in the sentence
         * decoder: without it every letter could be covered by its own character and the longest
         * run of lone characters would always win.
         */
        private const val INITIALS_UNIT_PENALTY = 1200
        /** Segmentations cache, bounded so a long session cannot grow it without limit. */
        private const val COVER_CACHE_LIMIT = 256
        /**
         * 没有任何语料证据时，最多还留几条读法。1 是量出来的：删光会让新词/专名/生僻说法失去
         * 整句候选，多留则同音字洗牌又铺满候选栏；详见 [attested]。
         */
        private const val UNBACKED_READINGS = 1
        /** Nine key signatures never get longer than the longest syllable's digit count. */
        private const val MAX_T9_SYLLABLE_LENGTH = 6
        /**
         * How many partial readings one position of a 首字母/混合 run may keep.
         *
         * This is a beam width, and it has to be wide enough that a correct reading is not
         * evicted at an intermediate position by a *different* segmentation of the same letters
         * that happens to score higher there. With the hand-written corpus alone 8 was enough;
         * with Tatoeba's pairs the mixed run "jintianwsm" lost 今天|为什么 to 及|那天|晚上|吗
         * (那天|晚上 is a real pair) because eight higher-scoring partial paths filled the beam
         * first. The cost of the wider beam is a few dozen objects per keystroke.
         */
        private const val INITIALS_PATHS = 24
    }
}
