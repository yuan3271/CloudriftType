package com.yuan3271.cloudrift.engine.japanese

/**
 * Romaji to kana translation.
 *
 * Deliberately a hand written table rather than a rule engine: the table is small, the
 * ordering rules (sokuon, syllabic n, loanword digraphs) are the only real logic, and a
 * table is trivial to extend.
 */
object RomajiKana {

    data class Result(
        /** Kana produced so far, safe to show as composing text. */
        val kana: String,
        /**
         * Trailing romaji that cannot be translated yet, either because it is incomplete
         * ("k" on the way to "ka") or because it is not a valid sequence ("q").
         */
        val pending: String,
    ) {
        val composing: String get() = kana + pending
        val isEmpty: Boolean get() = kana.isEmpty() && pending.isEmpty()
    }

    private val TABLE: Map<String, String> = buildMap {
        // vowels
        put("a", "あ"); put("i", "い"); put("u", "う"); put("e", "え"); put("o", "お")
        // k
        put("ka", "か"); put("ki", "き"); put("ku", "く"); put("ke", "け"); put("ko", "こ")
        put("kya", "きゃ"); put("kyu", "きゅ"); put("kyo", "きょ")
        // s
        put("sa", "さ"); put("shi", "し"); put("si", "し"); put("su", "す"); put("se", "せ"); put("so", "そ")
        put("sha", "しゃ"); put("shu", "しゅ"); put("sho", "しょ"); put("sya", "しゃ"); put("syu", "しゅ"); put("syo", "しょ")
        put("she", "しぇ")
        // t
        put("ta", "た"); put("chi", "ち"); put("ti", "ち"); put("tsu", "つ"); put("tu", "つ")
        put("te", "て"); put("to", "と")
        put("cha", "ちゃ"); put("chu", "ちゅ"); put("cho", "ちょ"); put("cya", "ちゃ"); put("cyu", "ちゅ"); put("cyo", "ちょ")
        put("che", "ちぇ"); put("tya", "ちゃ"); put("tyu", "ちゅ"); put("tyo", "ちょ")
        put("thi", "てぃ"); put("the", "てぇ")
        // n
        put("na", "な"); put("ni", "に"); put("nu", "ぬ"); put("ne", "ね"); put("no", "の")
        put("nya", "にゃ"); put("nyu", "にゅ"); put("nyo", "にょ")
        // h
        put("ha", "は"); put("hi", "ひ"); put("fu", "ふ"); put("hu", "ふ"); put("he", "へ"); put("ho", "ほ")
        put("hya", "ひゃ"); put("hyu", "ひゅ"); put("hyo", "ひょ")
        put("fa", "ふぁ"); put("fi", "ふぃ"); put("fe", "ふぇ"); put("fo", "ふぉ"); put("fya", "ふゃ"); put("fyu", "ふゅ"); put("fyo", "ふょ")
        // m
        put("ma", "ま"); put("mi", "み"); put("mu", "む"); put("me", "め"); put("mo", "も")
        put("mya", "みゃ"); put("myu", "みゅ"); put("myo", "みょ")
        // y
        put("ya", "や"); put("yu", "ゆ"); put("yo", "よ"); put("ye", "いぇ")
        // r
        put("ra", "ら"); put("ri", "り"); put("ru", "る"); put("re", "れ"); put("ro", "ろ")
        put("rya", "りゃ"); put("ryu", "りゅ"); put("ryo", "りょ")
        // w
        put("wa", "わ"); put("wi", "うぃ"); put("wu", "う"); put("we", "うぇ"); put("wo", "を")
        put("wha", "うぁ"); put("whi", "うぃ"); put("whe", "うぇ"); put("who", "うぉ")
        // g
        put("ga", "が"); put("gi", "ぎ"); put("gu", "ぐ"); put("ge", "げ"); put("go", "ご")
        put("gya", "ぎゃ"); put("gyu", "ぎゅ"); put("gyo", "ぎょ"); put("gwa", "ぐぁ")
        // z
        put("za", "ざ"); put("ji", "じ"); put("zi", "じ"); put("zu", "ず"); put("ze", "ぜ"); put("zo", "ぞ")
        put("ja", "じゃ"); put("ju", "じゅ"); put("jo", "じょ"); put("jya", "じゃ"); put("jyu", "じゅ"); put("jyo", "じょ")
        put("je", "じぇ")
        // d
        put("da", "だ"); put("di", "ぢ"); put("du", "づ"); put("de", "で"); put("do", "ど")
        put("dya", "ぢゃ"); put("dyu", "ぢゅ"); put("dyo", "ぢょ"); put("dhi", "でぃ"); put("dhu", "でゅ")
        // b
        put("ba", "ば"); put("bi", "び"); put("bu", "ぶ"); put("be", "べ"); put("bo", "ぼ")
        put("bya", "びゃ"); put("byu", "びゅ"); put("byo", "びょ")
        // p
        put("pa", "ぱ"); put("pi", "ぴ"); put("pu", "ぷ"); put("pe", "ぺ"); put("po", "ぽ")
        put("pya", "ぴゃ"); put("pyu", "ぴゅ"); put("pyo", "ぴょ")
        // v (loanwords)
        put("va", "ゔぁ"); put("vi", "ゔぃ"); put("vu", "ゔ"); put("ve", "ゔぇ"); put("vo", "ゔぉ")
        // small kana and misc
        put("xa", "ぁ"); put("xi", "ぃ"); put("xu", "ぅ"); put("xe", "ぇ"); put("xo", "ぉ")
        put("xya", "ゃ"); put("xyu", "ゅ"); put("xyo", "ょ"); put("xwa", "ゎ")
        put("xtu", "っ"); put("xtsu", "っ"); put("ltu", "っ"); put("ltsu", "っ")
        put("xka", "ヶ"); put("lka", "ヶ")
        put("kya", "きゃ")
        put("-", "ー")
    }

    private const val MAX_KEY_LENGTH = 3
    private val VOWELS = setOf('a', 'i', 'u', 'e', 'o')

    fun convert(input: String): Result {
        val kana = StringBuilder(input.length)
        var index = 0
        var pending = ""

        while (index < input.length) {
            val ch = input[index].lowercaseChar()

            // Syllabic n: complete unless the next character could start a new syllable.
            if (ch == 'n') {
                val next = input.getOrNull(index + 1)?.lowercaseChar()
                if (next == null) {
                    // Conversion always re-runs over the whole buffer, so emitting ん here is
                    // safe: typing a vowel next re-converts the buffer into "na" and friends.
                    kana.append('ん')
                    index++
                    continue
                }
                if (next == '\'') {
                    // The escape that forces ん before a vowel or y: "hon'yaku" -> ほんやく.
                    kana.append('ん')
                    index += 2
                    continue
                }
                if (next in VOWELS || next == 'y') {
                    // Fall through to the table lookup. Note that "ny" therefore wins over
                    // ん + や, which is the documented behaviour of Japanese IMEs.
                } else {
                    kana.append('ん')
                    index++
                    continue
                }
            }

            // Sokuon: a doubled consonant that is not the syllabic n.
            if (ch != 'n' && ch !in VOWELS && input.getOrNull(index + 1)?.lowercaseChar() == ch) {
                kana.append('っ')
                index++
                continue
            }

            val matched = longestMatch(input, index)
            if (matched == null) {
                pending = input.substring(index)
                break
            }
            val (romaji, kanaText) = matched
            kana.append(kanaText)
            index += romaji.length
        }

        return Result(kana.toString(), pending)
    }

    private fun longestMatch(input: String, index: Int): Pair<String, String>? {
        val max = minOf(MAX_KEY_LENGTH, input.length - index)
        for (length in max downTo 1) {
            val key = input.substring(index, index + length).lowercase()
            val kana = TABLE[key] ?: continue
            return key to kana
        }
        return null
    }

    /** Small kana and dakuten variants offered by the kana pad's long press. */
    val alternates: Map<String, List<String>> = mapOf(
        "あ" to listOf("ぁ", "ア", "ァ"),
        "い" to listOf("ぃ", "イ", "ィ"),
        "う" to listOf("ぅ", "ゔ", "ウ", "ゥ", "ヴ"),
        "え" to listOf("ぇ", "エ", "ェ"),
        "お" to listOf("ぉ", "オ", "ォ"),
        "か" to listOf("が", "カ", "ガ"),
        "き" to listOf("ぎ", "キ", "ギ"),
        "く" to listOf("ぐ", "ク", "グ"),
        "け" to listOf("げ", "ケ", "ゲ"),
        "こ" to listOf("ご", "コ", "ゴ"),
        "さ" to listOf("ざ", "サ", "ザ"),
        "し" to listOf("じ", "シ", "ジ"),
        "す" to listOf("ず", "ス", "ズ"),
        "せ" to listOf("ぜ", "セ", "ゼ"),
        "そ" to listOf("ぞ", "ソ", "ゾ"),
        "た" to listOf("だ", "タ", "ダ"),
        "ち" to listOf("ぢ", "チ", "ヂ"),
        "つ" to listOf("っ", "づ", "ツ", "ッ", "ヅ"),
        "て" to listOf("で", "テ", "デ"),
        "と" to listOf("ど", "ト", "ド"),
        "は" to listOf("ば", "ぱ", "ハ", "バ", "パ"),
        "ひ" to listOf("び", "ぴ", "ヒ", "ビ", "ピ"),
        "ふ" to listOf("ぶ", "ぷ", "フ", "ブ", "プ"),
        "へ" to listOf("べ", "ぺ", "ヘ", "ベ", "ペ"),
        "ほ" to listOf("ぼ", "ぽ", "ホ", "ボ", "ポ"),
        "や" to listOf("ゃ", "ヤ", "ャ"),
        "ゆ" to listOf("ゅ", "ユ", "ュ"),
        "よ" to listOf("ょ", "ヨ", "ョ"),
        "ら" to listOf("ラ"),
        "り" to listOf("リ"),
        "る" to listOf("ル"),
        "れ" to listOf("レ"),
        "ろ" to listOf("ロ"),
        "わ" to listOf("ゎ", "ヮ", "ワ"),
        "を" to listOf("ヲ"),
    )
}
