package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test

class DraftPolicyTest {
    @Test fun scopeSeparatesAccountsEndpointsAndLegacyLogins() {
        val a = draftScope("https://relay.example/", "alice", "old-token")
        assertEquals(a, draftScope(" https://relay.example ", "alice", "new-token"))
        assertNotEquals(a, draftScope("https://relay.example", "bob", "old-token"))
        assertNotEquals(a, draftScope("https://other.example", "alice", "old-token"))
        assertNotEquals(draftScope("https://relay.example", "", "a"), draftScope("https://relay.example", "", "b"))
        assertNotEquals(a, draftScope("https://relay.example", "", "old-token"))
        assertTrue(a.matches(Regex("[a-f0-9]{64}")))
        assertFalse(a.contains("alice"))
    }

    @Test fun previewFlattensWhitespaceButNeverChangesRawText() {
        val raw = "\n  第一行\r\n\r\n第二行\t 内容\u2028第三行\u3000结束  "
        assertEquals("第一行 第二行 内容 第三行 结束", draftPreview(raw))
        assertTrue(raw.startsWith("\n  "))
        assertEquals("", draftPreview(" \r\n\t"))
    }

    @Test fun longEmojiPreviewsAreBoundedAndCannotSplitASurrogate() {
        val preview = draftPreview("😀".repeat(1000))
        assertEquals(81, preview.codePointCount(0, preview.length))
        assertTrue(preview.endsWith("…"))
        assertFalse(draftPreview(" ".repeat(511) + "😀").any { it.isSurrogate() })
    }
}
