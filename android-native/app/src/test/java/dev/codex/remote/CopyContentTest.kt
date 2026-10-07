package dev.codex.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CopyContentTest {
    private fun message(text: String, truncated: Boolean = false) = MessageItem("reply", "agentMessage", text, "", "", "", "", null, emptyList(), truncated)

    @Test fun preservesMarkdownUnicodeWhitespaceAndCodeExactly() {
        val text = "  ## 标题 🧊\n\n```sh\necho hi\n\nexit 0\n```\n"
        assertEquals(text, contentCopyText(message(text)))
    }

    @Test fun commandCopiesCommandAndFullOutputWithoutVisualLabels() {
        val item = message("").copy(type = "commandExecution", command = "echo hi", output = "hi\n\n", cwd = "/repo", exitCode = 0)
        assertEquals("echo hi\n\nhi\n\n", contentCopyText(item))
        assertEquals("echo hi", contentCopyText(item.copy(output = "")))
    }

    @Test fun fileCopyIncludesEveryPathAndExactDiff() {
        val item = message("").copy(type = "fileChange", changes = listOf(Change("a.kt", "-old\n+new\n"), Change("b.kt", "+next")))
        assertEquals("a.kt\n-old\n+new\n\n\nb.kt\n+next", contentCopyText(item))
    }

    @Test fun completeContentNeverDownloads() = runBlocking {
        assertEquals("完整", resolveCopyText(message("完整")) { error("Must not fetch") })
    }

    @Test fun truncatedContentLoadsExactlyOnceAndCopiesFullText() = runBlocking {
        var reads = 0
        val preview = message("预览…", true)
        assertEquals("完整的消息", resolveCopyText(preview) { reads++; assertEquals(preview.id, it.id); it.copy(text = "完整的消息", truncated = false) })
        assertEquals(1, reads)
    }

    @Test fun wrongOrStillTruncatedResponsesAreNeverCopied() = runBlocking {
        val preview = message("预览", true)
        for (response in listOf(preview, preview.copy(id = "other", truncated = false), preview.copy(type = "userMessage", truncated = false))) {
            try { resolveCopyText(preview) { response }; fail("Incomplete or mismatched content must fail") }
            catch (_: CopyContentException) { }
        }
    }

    @Test fun rejectsEmptyAndOversizedContentWithoutTruncating() = runBlocking {
        for (text in listOf("", "x".repeat(MAX_COPY_CHARACTERS + 1))) {
            try { resolveCopyText(message(text)) { it }; fail("Must reject instead of copying a partial payload") }
            catch (_: CopyContentException) { }
        }
        assertEquals(MAX_COPY_CHARACTERS, resolveCopyText(message("x".repeat(MAX_COPY_CHARACTERS))) { it }.length)
    }

    @Test fun failedDownloadPropagatesWithoutFallingBackToPreview() = runBlocking {
        try { resolveCopyText(message("不要复制预览", true)) { throw java.io.IOException("offline") }; fail("Must propagate") }
        catch (_: java.io.IOException) { }
    }
}
