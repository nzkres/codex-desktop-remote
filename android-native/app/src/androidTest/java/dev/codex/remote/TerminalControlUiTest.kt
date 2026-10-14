package dev.codex.remote

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalControlUiTest {
    @get:Rule val compose = createComposeRule()
    private class Fixture : TerminalControl {
        var changes = 0
        var creates = 0
        var failure = false
        var pending: CompletableDeferred<Unit>? = null
        override suspend fun snapshot(): TerminalSnapshot {
            if (failure) error("中继尚未启用远程终端管理，需要升级中继。")
            return TerminalSnapshot(listOf(RemoteTerminal("a".repeat(32), "合成测试电脑", "active", true)), emptyList())
        }
        override suspend fun createPairing(): PairingCode {
            creates++
            pending?.await()
            return PairingCode("ABCDEF123456", System.currentTimeMillis() / 1000 + 300)
        }
        override suspend fun confirm(id: String, name: String) {}
        override suspend fun cancel(id: String) {}
        override suspend fun change(id: String, action: String, name: String) { changes++ }
    }
    @Test fun choosingComputerIsExplicitWithoutLegacyEntry() {
        val fixture = Fixture()
        var selected: TaskTarget? = null
        compose.setContent { HapiStyle(false) { TerminalScreen(fixture, onSelect = { selected = it }) } }
        compose.onNodeWithContentDescription("查看这台电脑任务").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("a".repeat(32), selected?.id) }
        captureTestScreen("terminal-task-target-selection")
        compose.onNodeWithContentDescription("原电脑 · 旧通道").assertDoesNotExist()
        compose.runOnIdle { assertEquals("a".repeat(32), selected?.id) }
    }
    @Test fun terminalManagementRemainsReadableAcrossThemesAndLargeText() {
        val fixture = Fixture()
        val dark = mutableStateOf(false)
        val scale = mutableStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale.value)) {
                HapiStyle(dark.value) { TerminalScreen(fixture) }
            }
        }
        for ((night, fontScale) in listOf(false to 1f, true to 1f, true to 1.5f)) {
            compose.runOnIdle { dark.value = night; scale.value = fontScale }
            compose.onNodeWithText("合成测试电脑").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("移除").performScrollTo().assertIsDisplayed()
            captureTestScreen("terminal-management-${if (night) "dark" else "light"}-$fontScale")
        }
    }
    @Test fun removeRequiresConfirmationAndDoesNotChangeChatEntry() {
        val fixture = Fixture()
        compose.setContent { HapiStyle(false) { TerminalScreen(fixture) } }
        compose.onNodeWithText("合成测试电脑").assertIsDisplayed()
        compose.onNodeWithText("连接器在线 · 执行能力尚未验证").assertIsDisplayed()
        compose.onNodeWithContentDescription("移除").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, fixture.changes) }
        compose.onNodeWithText("返回").performClick()
        compose.runOnIdle { assertEquals(0, fixture.changes) }
        compose.onNodeWithContentDescription("移除").performClick()
        compose.onNodeWithText("确认", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(1, fixture.changes) }
    }
    @Test fun unavailableServerIsNotAnEmptyTerminalList() {
        val fixture = Fixture().apply { failure = true }
        compose.setContent { HapiStyle(true) { TerminalScreen(fixture) } }
        compose.onNodeWithText("中继尚未启用远程终端管理，需要升级中继。").assertIsDisplayed()
        compose.onNodeWithContentDescription("添加电脑").assertIsNotEnabled()
        compose.onNodeWithText("还没有已登记电脑。现有聊天连接不会被自动迁移。").assertDoesNotExist()
    }
    @Test fun inFlightPairingDisablesDuplicateCreation() {
        val pending = CompletableDeferred<Unit>()
        val fixture = Fixture().apply { this.pending = pending }
        compose.setContent { HapiStyle(false) { TerminalScreen(fixture) } }
        compose.onNodeWithContentDescription("添加电脑").performClick()
        compose.onNodeWithContentDescription("添加电脑").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, fixture.creates); pending.complete(Unit) }
        compose.onNodeWithText("ABCDEF123456").assertIsDisplayed()
        compose.onNodeWithContentDescription("添加电脑").assertIsNotEnabled()
    }
}
