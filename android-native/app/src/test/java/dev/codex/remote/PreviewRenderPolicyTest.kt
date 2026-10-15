package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test

class PreviewRenderPolicyTest {
    @Test fun webViewInternalMainDocumentCanUseItsLocalDataDecoder() {
        assertTrue(previewLocalResource("data"))
        assertTrue(previewLocalResource("DATA"))
    }
    @Test fun remoteAndDeviceResourcesRemainBlocked() {
        listOf(null, "", "http", "https", "file", "content", "ftp", "javascript", "blob", "about", "intent")
            .forEach { assertFalse("must block $it", previewLocalResource(it)) }
    }
    @Test fun fetchedAndVisibleAreDifferentStates() {
        assertTrue(PreviewRenderStatus(PreviewRenderPhase.Loading).notice(3).contains("正在显示"))
        assertEquals("预览已显示 · R3", PreviewRenderStatus(PreviewRenderPhase.Visible).notice(3))
    }
    @Test fun renderFailureDoesNotExposeDocumentUrlOrClaimSuccess() {
        val notice = PreviewRenderStatus(PreviewRenderPhase.Failed, -2).notice(3)
        assertTrue(notice.contains("页面显示失败")); assertTrue(notice.contains("-2"))
        assertFalse(notice.contains("预览已显示")); assertFalse(notice.contains("data:"))
    }
}
