package com.yuan3271.cloudrift.ime

/** A word the user spelled out one single character at a time, with the reading it was typed as. */
data class LearnedWord(val reading: String, val word: String)

/**
 * The run of single characters committed back to back.
 *
 * 一个字一个字确认是"词典里还没有的词"唯一的输入方式：张、伟、来 三个字分开选，中间没有一次是
 * 三条一起选的。整串攒起来才会变成 张伟来（读音 zhangweilai）——只记相邻两个字的话，下次再打
 * 这三个字的拼音时出来的只会是 张伟 / 伟来，用户刚拼好的那条还是不在候选里。
 *
 * 这个类是纯逻辑（没有 Android 依赖），因为"哪一步该记、记什么"正是要单测的东西；键盘那边只负责
 * 在"又来了一个单字"时喂给它，在别的任何提交里清掉它。
 */
class CharacterChain(
    /** How long two characters may be apart and still belong to the same run. */
    private val windowMillis: Long,
    /** Longest run that is still remembered as one word. */
    private val maxChars: Int,
) {

    private var reading = ""
    private var word = ""
    private var at = 0L

    /**
     * Adds one character to the run.
     *
     * @param syllable the reading of the character that was just picked ("zhang" for 张).
     * @param character the character itself.
     * @param now the commit time, in the same clock as the previous calls.
     * @return the word the run spells out so far, or null while it is still a single character.
     */
    fun append(syllable: String, character: String, now: Long): LearnedWord? {
        if (word.isEmpty() || now - at > windowMillis) {
            reading = syllable
            word = character
        } else {
            reading += syllable
            word += character
        }
        at = now
        if (word.length >= maxChars) {
            val finished = LearnedWord(reading, word)
            clear()
            return finished
        }
        return if (word.length > 1) LearnedWord(reading, word) else null
    }

    /** Breaks the run: the next character starts a word of its own. */
    fun clear() {
        reading = ""
        word = ""
        at = 0L
    }
}
