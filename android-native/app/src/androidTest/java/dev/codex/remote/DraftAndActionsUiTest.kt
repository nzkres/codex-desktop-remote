package dev.codex.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DraftAndActionsUiTest {
    @get:Rule(order = 0) val timeout: Timeout = Timeout.builder().withTimeout(90, TimeUnit.SECONDS).build()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val names = mutableListOf<String>()
    private fun store(): DraftStore {
        val name = "draft-fixture-${UUID.randomUUID()}"; names += name
        return DraftStore(context.getSharedPreferences(name, Context.MODE_PRIVATE))
    }
    private fun session(id: String) = Session(id, "fixture", "对话 $id", "COMPLETED", null, "default", 0)
    private fun content() = Conversation(emptyList(), Observation("completed", 0, "gateway", ""), emptyList(), emptyList())
    @After fun cleanup() { compose.mainClock.autoAdvance = true; names.forEach { context.deleteSharedPreferences(it) } }

    @Test fun encryptedDraftsRoundTripIsolateAndProtectAgainstLateReceipts() {
        val scope = draftScope("https://fixture.invalid/${UUID.randomUUID()}", "fixture", "not-a-real-token")
        names += "conversation-drafts-$scope"
        val first = DraftStore.open(context, scope)
        val raw = "\n  DRAFT_PRIVATE_FIXTURE\n第二行\n\n  最后一行  "
        first.write("a", raw); first.write("b", "另一个对话")
        val sent = first.read("a")!!
        val restored = DraftStore.open(context, scope)
        assertEquals(raw, restored.read("a")?.text)
        assertEquals("另一个对话", restored.read("b")?.text)
        restored.write("a", "替换内容"); restored.write("a", raw)
        first.clearIfUnchanged("a", sent)
        assertEquals(raw, DraftStore.open(context, scope).read("a")?.text)
        restored.clearIfUnchanged("a", restored.read("a"))
        assertNull(DraftStore.open(context, scope).read("a"))
        assertEquals("b", restored.latest(setOf("a", "b")))
        assertNull(restored.latest(setOf("missing")))
        val otherScope = draftScope("https://fixture.invalid", "other", "not-a-real-token")
        names += "conversation-drafts-$otherScope"
        assertNull(DraftStore.open(context, otherScope).read("b"))
        // Flush only this fixture's pending writes, then inspect ciphertext, never real user data.
        context.getSharedPreferences("conversation-drafts-$scope", Context.MODE_PRIVATE).edit().commit()
        val disk = File(context.applicationInfo.dataDir, "shared_prefs/conversation-drafts-$scope.xml").readText()
        assertFalse(disk.contains("另一个对话")); assertFalse(disk.contains("DRAFT_PRIVATE_FIXTURE"))
    }

    @Test fun questionDraftsIsolateRevisionAndTurnInterruptedSubmissionUnknown() {
        val scope = draftScope("https://fixture.invalid/terminal-a", "fixture", "synthetic")
        names += "question-drafts-$scope"
        val request = QuestionRequest("a".repeat(64), listOf(Question("choice", "方式", "选哪个", emptyList(), false, true)),
            "b".repeat(64), "turn", "async")
        val store = QuestionDraftStore.open(context, scope)
        store.save("thread", request, QuestionDraft(mapOf("choice" to "原文\n第二行"), setOf("choice"), "submitting"))
        val restored = QuestionDraftStore.open(context, scope)
        assertEquals("原文\n第二行", restored.read("thread", request).answers["choice"])
        assertEquals("unknown", restored.read("thread", request).state)
        restored.reconcile("thread", listOf(request.copy(status = "pending")))
        assertEquals("unknown", restored.read("thread", request).state) // A poll must not authorize replay.
        assertTrue(restored.read("thread-2", request).answers.isEmpty())
        assertTrue(restored.read("thread", request.copy(revision = "c".repeat(64))).answers.isEmpty())
        val otherScope = draftScope("https://fixture.invalid/terminal-b", "fixture", "synthetic")
        names += "question-drafts-$otherScope"
        assertTrue(QuestionDraftStore.open(context, otherScope).read("thread", request).answers.isEmpty())
        restored.reconcile("thread", listOf(request.copy(status = "closed", notice = "电脑已回答")))
        assertEquals("closed", QuestionDraftStore.open(context, scope).read("thread", request).state)
        restored.reconcile("thread", listOf(request.copy(status = "accepted", notice = "Desktop 已记录")))
        assertEquals("closed", restored.read("thread", request).state) // Terminal state never regresses.
        restored.save("accepted-thread", request, QuestionDraft(mapOf("choice" to "原答案"), state = "unknown"))
        restored.reconcile("accepted-thread", listOf(request.copy(status = "accepted", notice = "Desktop 已记录")))
        assertEquals("accepted", QuestionDraftStore.open(context, scope).read("accepted-thread", request).state)
    }

    @Test fun questionDraftWriteFailureKeepsVisibleUnsavedTextAndDisablesSubmit() {
        val editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, _ ->
            when (method.name) { "commit" -> false; "putString" -> proxy; else -> proxy }
        } as SharedPreferences.Editor
        val prefs = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, _ ->
            when (method.name) { "getAll" -> emptyMap<String, Any>(); "edit" -> editor; else -> null }
        } as SharedPreferences
        val store = QuestionDraftStore(prefs)
        val request = QuestionRequest("a".repeat(64), listOf(Question("reason", "原因", "请填写", emptyList(), false, true)), "b".repeat(64))
        var submitted = false
        compose.setContent { HapiStyle(false) {
            QuestionCard(request, false, store, "thread") { _, _ -> submitted = true }
        } }
        compose.onNodeWithTag("question-answer-reason").performTextInput("未落盘的文字")
        compose.onNodeWithText("答案未能加密保存，仅保留在当前页面；请检查手机存储，勿提交。").assertIsDisplayed()
        compose.onNodeWithTag("question-answer-reason").assertTextContains("未落盘的文字")
        compose.onNodeWithText("提交").assertIsNotEnabled()
        assertFalse(submitted)
        assertTrue(store.read("thread", request).answers.isEmpty())
    }

    @Test fun submittingQuestionShowsProgressAndPreventsDuplicateTap() {
        val name = "question-progress-${UUID.randomUUID()}"; names += name
        val store = QuestionDraftStore(context.getSharedPreferences(name, Context.MODE_PRIVATE))
        val request = QuestionRequest("a".repeat(64), listOf(Question("choice", "方式", "原题",
            listOf(QuestionOption("方案一", "完整说明")), false, false)), "b".repeat(64))
        store.save("thread", request, QuestionDraft(mapOf("choice" to "方案一"), state = "submitting"))
        compose.setContent { HapiStyle(false) { QuestionCard(request, false, store, "thread") { _, _ -> fail("Duplicate answer") } } }
        compose.onAllNodesWithText("正在提交").assertCountEquals(2)
        compose.onNodeWithText("方案一").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("question-submit").assertIsNotEnabled()
    }

    @Test fun draftSurvivesSwitchingPagesAndConversationsWithSingleLinePreviews() {
        val drafts = store(); val selected = mutableStateOf<String?>("a")
        val tasks = listOf(session("a"), session("b"))
        compose.setContent { HapiStyle(false) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val id = selected.value
                if (id != null) key(id) {
                    ChatScreen(id, session(id), "fixture", content(), "", false, false, {}, { selected.value = null }, {}, { null }, {}, {}, {}, { _, _ -> }, { _, _ -> }, draftStore = drafts)
                } else Column {
                    Box(Modifier.weight(1f)) { SessionList(tasks, emptyList(), emptyList(), { selected.value = it }, {}, drafts) }
                    DraftResumeBar("对话 a", drafts.read("a")?.text.orEmpty()) { selected.value = "a" }
                }
            }
        } }
        val raw = "第一行\n\n  保留缩进的第二行"
        compose.onNodeWithTag("composer-editor").performTextInput(raw)
        compose.runOnIdle { selected.value = null }
        compose.onNodeWithTag("draft-preview", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("第一行 保留缩进的第二行", useUnmergedTree = true).assertIsDisplayed()
        captureTestScreen("48-draft-list-and-resume")
        compose.onNodeWithText("对话 b").performClick()
        compose.onNodeWithTag("composer-editor").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("composer-editor").performTextInput("B 独立草稿")
        compose.runOnIdle { selected.value = null }
        compose.onNodeWithTag("draft-resume").performClick()
        compose.onNodeWithTag("composer-editor").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(raw)))
        assertEquals("B 独立草稿", drafts.read("b")?.text)
    }

    @Test fun failedSendRetainsDraftAndLateSuccessDoesNotEraseNewEditing() {
        val drafts = store(); val selected = mutableStateOf(true)
        var pending = CompletableDeferred<String?>()
        var sends = 0
        compose.setContent { HapiStyle(false) {
            if (selected.value) Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                ChatScreen("a", session("a"), "fixture", content(), "", false, false, {}, {}, {},
                    { sends++; pending.await() }, {}, {}, {}, { _, _ -> }, { _, _ -> }, draftStore = drafts)
            }
        } }
        compose.onNodeWithTag("composer-editor").performTextInput("未确认不能删除")
        compose.onNodeWithContentDescription("发送消息").performClick()
        compose.runOnIdle { pending.completeExceptionally(java.io.IOException("fixture")) }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("发送消息").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("未确认不能删除", drafts.read("a")?.text)
        compose.runOnIdle { pending = CompletableDeferred() }
        compose.onNodeWithContentDescription("发送消息").performClick()
        compose.runOnIdle { selected.value = false }
        compose.runOnIdle { drafts.write("a", "离开页面后写的新草稿"); pending.complete("turn") }
        compose.waitUntil(5000) { pending.isCompleted }
        compose.runOnIdle { selected.value = true }
        compose.onNodeWithTag("composer-editor").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("离开页面后写的新草稿")))
        compose.runOnIdle { pending = CompletableDeferred() }
        compose.onNodeWithContentDescription("发送消息").performClick()
        compose.runOnIdle { pending.complete("next-turn") }
        compose.waitUntil(5000) { drafts.read("a") == null }
        assertEquals(3, sends)
    }

    @Test fun modeAndCreateActionsShareStyleAndRemainSafelyDisabled() {
        val mode = mutableStateOf("default"); val running = mutableStateOf(false)
        val dialog = mutableStateOf(false); val busy = mutableStateOf(false)
        var creates = 0
        compose.setContent { HapiStyle(false) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                GlassActionButton("新对话", { dialog.value = true }, Modifier.testTag("new-conversation"), icon = "plus")
                Box(Modifier.weight(1f)) {
                    ChatScreen("a", session("a").copy(mode = mode.value, activeTurnId = if (running.value) "t" else null), "fixture",
                        content(), "", false, false, {}, {}, {}, { null }, {}, { mode.value = it }, {}, { _, _ -> }, { _, _ -> })
                }
            }
            if (dialog.value) NewTaskDialog(listOf(Project("fixture", "示例项目")), busy.value, { dialog.value = false }) { _, _, _, _ -> creates++ }
        } }
        compose.onNodeWithTag("composer-mode").performClick()
        compose.onNodeWithText("Plan · 先规划再实施").performClick()
        assertEquals("plan", mode.value)
        compose.onNodeWithTag("composer-mode").assertContentDescriptionEquals("Plan · 规划")
        captureTestScreen("49-glass-agent-plan-action")
        compose.runOnIdle { running.value = true }
        compose.onNodeWithTag("composer-mode").assertIsNotEnabled()
        compose.onNodeWithTag("new-conversation").performClick()
        compose.onNodeWithTag("confirm-new-conversation").assertIsEnabled()
        captureTestScreen("50-glass-create-dialog")
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithTag("confirm-new-conversation").assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsNotEnabled()
        assertEquals(0, creates)
    }

    @Test fun glassPreviewContainsBackdropTextAndClarityChangesRealPixels() {
        val clarity = mutableStateOf(0f); val night = mutableStateOf(false)
        compose.setContent { HapiStyle(night.value) {
            CompositionLocalProvider(LocalChatAppearance provides ChatAppearance(clarity = clarity.value)) {
                Column(Modifier.fillMaxSize().background(LocalAppPalette.current.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(18.dp)) { GlassAppearancePreview() }
            }
        } }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; clarity.value = 0f }
            compose.onNodeWithTag("glass-preview-background-text").assertTextContains("让内容清楚，让界面轻盈。", substring = true)
            val before = compose.onNodeWithTag("glass-clarity-preview").captureToImage().toPixelMap()
            captureTestScreen("51-glass-text-${if (dark) "night" else "light"}-mist")
            compose.runOnIdle { clarity.value = 1f }
            val after = compose.onNodeWithTag("glass-clarity-preview").captureToImage().toPixelMap()
            var changed = 0
            for (y in 0 until before.height) for (x in 0 until before.width) if (before[x, y] != after[x, y]) changed++
            assertTrue("Actual backdrop blur/tint must change, not only the slider number", changed > before.width * before.height / 20)
            captureTestScreen("52-glass-text-${if (dark) "night" else "light"}-clear")
        }
    }

    @Test fun glassActionTintDoesNotBecomeAnOpaqueShadowSlab() {
        val night = mutableStateOf(false)
        var surface = androidx.compose.ui.graphics.Color.White
        compose.setContent { HapiStyle(night.value) {
            val background = LocalAppPalette.current.background
            SideEffect { surface = background }
            Column(Modifier.fillMaxSize().background(LocalAppPalette.current.background)
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(18.dp)) {
                GlassActionButton("新对话", {}, Modifier.width(240.dp).testTag("tint-action"), icon = "plus")
            }
        } }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark }
            // runOnIdle waits BEFORE its block; this mutation schedules a new composition.
            // Wait for its SideEffect before reading the background for the new frame.
            compose.waitForIdle()
            val base = surface
            val pixels = compose.onNodeWithTag("tint-action").captureToImage().toPixelMap()
            // Both samples are inside the capsule but outside its central label and icon.
            val upper = pixels[pixels.width / 10, pixels.height * 3 / 10].compositeOver(base)
            val middle = pixels[pixels.width / 10, pixels.height / 2].compositeOver(base)
            captureTestScreen("53-glass-action-${if (dark) "night" else "light"}-thin-tint")
            assertTrue("Thin tint must not darken into a solid elevation shadow: night=$dark base=$base sample=$upper",
                kotlin.math.abs(upper.luminance() - base.luminance()) < .13f)
            assertTrue("No bright horizontal seam through the capsule: night=$dark upper=$upper middle=$middle",
                kotlin.math.abs(upper.luminance() - middle.luminance()) < .04f)
        }
    }
}
