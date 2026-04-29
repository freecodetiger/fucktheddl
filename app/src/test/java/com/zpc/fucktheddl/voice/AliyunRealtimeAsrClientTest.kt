package com.zpc.fucktheddl.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class AliyunRealtimeAsrClientTest {
    @Test
    fun extractsSentenceTextFromFunAsrResultJson() {
        val raw = """
            {
              "payload": {
                "output": {
                  "sentence": {
                    "text": "明天截止完成真机测试"
                  }
                }
              }
            }
        """.trimIndent()

        assertEquals("明天截止完成真机测试", extractAsrText(raw))
    }

    @Test
    fun keepsPlainTextFallbackForUnexpectedPayloads() {
        assertEquals("plain text", extractAsrText("plain text"))
    }
}
