package dev.codex.remote

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class QuotaActivityPreviewUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun anotherDeviceStartsAccountAnimationWithoutChangingDeviceSelection() {
        compose.mainClock.autoAdvance = false
        compose.setContent { HapiStyle(night = true) { QuotaActivityPreview({}, {}) } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("quota-run-home").performClick()
        compose.mainClock.advanceTimeBy(120)
        compose.onNodeWithText("启动过渡 · 逐渐提亮与加速").assertExists()
        compose.onNodeWithTag("quota-device-studio").performClick()
        compose.mainClock.advanceTimeBy(120)
        compose.onNodeWithText("空闲 · 静止").assertExists()
        compose.onNodeWithTag("quota-device-home").performClick()
        compose.mainClock.advanceTimeBy(5500)
        compose.onNodeWithText("运行中 · 呼吸与旋转").assertExists()
    }
}
