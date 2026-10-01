package com.yuan3271.cloudrift.engine.pinyin

/**
 * Mapping between Latin letters and the phone keypad digits used by the nine key layouts.
 * `v` carries the ü reading and therefore shares the TUV key.
 */
object T9 {
    private val MAP = IntArray(26)

    init {
        "abc".forEach { MAP[it - 'a'] = 2 }
        "def".forEach { MAP[it - 'a'] = 3 }
        "ghi".forEach { MAP[it - 'a'] = 4 }
        "jkl".forEach { MAP[it - 'a'] = 5 }
        "mno".forEach { MAP[it - 'a'] = 6 }
        "pqrs".forEach { MAP[it - 'a'] = 7 }
        "tuv".forEach { MAP[it - 'a'] = 8 }
        "wxyz".forEach { MAP[it - 'a'] = 9 }
    }

    fun digitOf(letter: Char): Char {
        val index = letter.lowercaseChar() - 'a'
        return if (index in 0..25) ('0' + MAP[index]) else letter
    }

    fun encode(reading: String): String = buildString(reading.length) {
        for (ch in reading) append(digitOf(ch))
    }

    fun isDigits(text: String): Boolean = text.isNotEmpty() && text.all { it in '0'..'9' }
}
