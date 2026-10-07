package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class ConversationTimeTest {
    private val task = MessageItem("u", "userMessage", "第一行\n\n第二行", "", "", "", "", null, emptyList())
    @Test fun imagePlaceholdersAndSingleLineHeaderDoNotChangeOriginalText() {
        assertEquals("第一行\n\n第二行\n[图片]", taskText(task.copy(imageCount = 2)))
        assertEquals("[图片]", taskText(task.copy(text = "", imageCount = 1)))
        assertEquals("[无文字内容]", taskText(task.copy(text = "")))
        assertEquals("共 57 轮 · 第一行 第二行", taskHeaderSubtitle(57, TaskReference(task)))
        assertEquals("轮数待升级 · [图片]", taskHeaderSubtitle(null, TaskReference(task.copy(text = "", imageCount = 1))))
        assertTrue(taskHeaderSubtitle(57, TaskReference(task, true)).endsWith("同步中"))
    }
    @Test fun unknownTimingIsNotZeroAndDatesUseDeviceTimeZone() {
        assertEquals("未记录", elapsedText(null)); assertEquals("未记录", elapsedText(-1))
        assertEquals("0秒", elapsedText(0)); assertEquals("1分5秒", elapsedText(65_000))
        assertEquals("1时1分1秒", elapsedText(3_661_000))
        assertEquals("未记录", clockText(null)); assertEquals("未记录", clockText(0))
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+08:00"))
            assertEquals("08:00:01", clockText(1000))
            assertEquals("1970-01-01 08:00:01", clockText(1000, true))
        } finally { TimeZone.setDefault(original) }
    }
}
