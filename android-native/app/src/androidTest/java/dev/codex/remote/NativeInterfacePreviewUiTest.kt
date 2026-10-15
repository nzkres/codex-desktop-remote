package dev.codex.remote

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class NativeInterfacePreviewUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun completionNeedsVisibleFinalResultAndPreviewCanClose() {
        var closed = false
        compose.setContent { HapiStyle(night = true) { NativeInterfacePreview { closed = true } } }
        compose.onNodeWithTag("native-interface-preview").assertExists()
        compose.onNodeWithTag("preview-complete").performClick()
        compose.onNodeWithTag("preview-main-row").assertTextContains("未读", substring = true)
        compose.onNodeWithTag("preview-main-row").performClick()
        compose.onNodeWithTag("preview-transcript").performScrollToKey("preview-final")
        compose.onNodeWithText("返回列表").performClick()
        compose.onNodeWithTag("preview-main-row").assertTextContains("已读", substring = true)
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { check(closed) }
    }
}
