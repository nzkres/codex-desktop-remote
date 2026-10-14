package dev.codex.remote

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TerminalSkinUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun preferencesSurviveReopenAndAllFontsLoadOffline() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="terminal-skin-test"
        try {
            val prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE)
            prefs.edit().clear().putString("skin_font","MaShan").putString("skin_glass","Frost")
                .putString("skin_palette","Warm").putString("skin_effect","Snake")
                .putFloat("skin_speed",1.75f).putFloat("skin_brightness",.5f)
                .putBoolean("skin_shadow",false).putBoolean("skin_idle_still",true).commit()
            val skin=readChatAppearance(context.getSharedPreferences(name,Context.MODE_PRIVATE)).skin
            assertEquals(GlassSkin.Frost,skin.glass); assertEquals(GridEffect.Snake,skin.effect)
            assertEquals(GridPalette.Warm,skin.palette); assertEquals(1.75f,skin.speed)
            assertEquals(.5f,skin.brightness); assertFalse(skin.textShadow); assertTrue(skin.idleStill)
            BodyFont.entries.forEach { font -> font.resource?.let { assertNotNull(androidx.core.content.res.ResourcesCompat.getFont(context,it)) } }
        } finally { context.deleteSharedPreferences(name) }
    }
    @Test fun unavailableModelAndModeRemainExplicitWithoutSending() {
        var changed=false
        compose.setContent { HapiStyle(true) {
            CompositionLocalProvider(LocalChatAppearance provides ChatAppearance(reduceMotion=true)) {
                ChatScreen("skin-fixture",Session("skin-fixture","fixture","接口文档整理","COMPLETED",null,"default",0),"fixture",
                    Conversation(emptyList(),Observation("completed",0,"gateway",""),emptyList(),emptyList()),
                    "",false,true,{}, {}, {}, {null}, {}, {changed=true}, {}, {_,_->}, {_,_->})
            }
        } }
        compose.onNodeWithTag("composer-mode").performClick()
        compose.onNodeWithText("由 Desktop 管理").assertIsDisplayed()
        compose.onNodeWithText("计划模式").assertIsNotEnabled()
        assertFalse(changed)
        captureTestScreen("terminal-model-capability")
    }
    @Test fun compactGlassOptionsKeepDescriptionAndSelectionTogether() {
        val selected=mutableStateOf(false)
        compose.setContent { HapiStyle(true) {
            Column(Modifier.fillMaxSize()) {
                DecisionSurface { DecisionOption("蓝色",selected.value,true,"冷色强调，适合终端界面。") {selected.value=true} }
            }
        } }
        compose.onNodeWithText("冷色强调，适合终端界面。",substring=true).performClick()
        assertTrue(selected.value)
        captureTestScreen("terminal-compact-question")
    }
}
