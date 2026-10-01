package com.yuan3271.cloudrift.voice

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 千问 response shape is unusual - the text is nested at `output.output.sentence.text`
 * with a second copy at `output.text`, and there is no `choices` array - so it gets its own
 * contract test.
 */
class QianwenAsrClientTest {

    private val client = QianwenAsrClient()

    @Test
    fun `reads the nested sentence text`() {
        val response = JSONObject(
            """
            {"output":{"output":{"sentence":{"text":"你好，世界"}},"text":"你好，世界"},
             "request_id":"abc"}
            """.trimIndent(),
        )

        assertEquals("你好，世界", client.extractText(response))
    }

    @Test
    fun `falls back to the flat text field`() {
        val response = JSONObject("""{"output":{"text":"另一种结构"}}""")

        assertEquals("另一种结构", client.extractText(response))
    }

    @Test
    fun `returns null when the payload carries no text`() {
        assertNull(client.extractText(JSONObject("""{"output":{}}""")))
        assertNull(client.extractText(JSONObject("""{"message":"bad key"}""")))
    }

    @Test
    fun `hot words default to the normal weight band`() {
        val vocabulary = client.parseHotWords("云隙输入")

        assertEquals(1, vocabulary.length())
        assertEquals(4, vocabulary.getInt("云隙输入"))
    }

    @Test
    fun `hot words accept explicit weights and the super weight`() {
        val vocabulary = client.parseHotWords("张三:2, 李四:50, 语音实验室:9")

        assertEquals(2, vocabulary.getInt("张三"))
        // 50 is the documented "super hot word" value and must survive untouched, while an
        // out of range 9 is clamped back into 1..5.
        assertEquals(50, vocabulary.getInt("李四"))
        assertEquals(5, vocabulary.getInt("语音实验室"))
    }

    @Test
    fun `hot words split on newlines and full width punctuation`() {
        val vocabulary = client.parseHotWords("一个\n两个，三个;四个")

        assertEquals(4, vocabulary.length())
        assertTrue(vocabulary.has("一个"))
        assertTrue(vocabulary.has("四个"))
    }

    @Test
    fun `blank input produces no vocabulary block`() {
        assertFalse(client.parseHotWords("   ").length() > 0)
        assertFalse(client.parseHotWords("").length() > 0)
    }
}
