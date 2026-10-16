package dev.codex.remote

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MobileCreateTest {
    @get:Rule val compose=createComposeRule()

    @Test fun creationRequestSurvivesReopenAndIsolatedByComputerWithoutTouchingExistingCredentials() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val a="create-test-a-"+UUID.randomUUID();val b="create-test-b-"+UUID.randomUUID()
        val credentials=Credentials(context)
        try {
            val first=credentials.prepareCreate(a,"project-a","Title","Unsent message")
            assertEquals(first,Credentials(context).prepareCreate(a,"project-a","Title","Unsent message"))
            val other=credentials.prepareCreate(b,"project-a","Title","Unsent message")
            assertNotEquals(first.id,other.id)
            try {credentials.prepareCreate(a,"project-b","Title","Unsent message");fail("Must retain original request")}
            catch(_:IllegalStateException){}
            credentials.finishCreate(b,first.id)
            assertEquals(other,credentials.pendingCreate(b))
        } finally {
            credentials.pendingCreate(a)?.let{credentials.finishCreate(a,it.id)}
            credentials.pendingCreate(b)?.let{credentials.finishCreate(b,it.id)}
        }
    }

    @Test fun pendingDialogKeepsOriginalInputAndHasNoUnsupportedModeControls() {
        var received:List<String>?=null
        val pending=PendingConversationCreate("fixed","project-a","Existing title","Keep this draft")
        val busy=mutableStateOf(false)
        compose.setContent { HapiStyle(true) {
            NewTaskDialog(listOf(Project("project-a","Project A")),busy.value,{},"Computer A",pending,"等待核对") { p,t,m,text -> received=listOf(p,t,m,text) }
        } }
        compose.onNodeWithText("核对并继续").performClick()
        assertEquals(listOf("project-a","Existing title","default","Keep this draft"),received)
        compose.onNodeWithText("Plan").assertDoesNotExist()
        compose.onNodeWithText("Keep this draft").assertIsNotEnabled()
        compose.runOnIdle{busy.value=true}
        compose.onNodeWithTag("confirm-new-conversation").assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsNotEnabled()
    }
}
