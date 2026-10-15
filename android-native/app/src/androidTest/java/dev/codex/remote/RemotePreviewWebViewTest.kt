package dev.codex.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

// Requires an actual Android WebView; desktop Chromium is not a replacement.
class RemotePreviewWebViewTest {
    @get:Rule val compose = createComposeRule()
    private fun activity(context: Context): Activity = when (context) {
        is Activity -> context
        is ContextWrapper -> activity(context.baseContext)
        else -> error("activity_missing")
    }
    private fun webView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) webView(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun evaluate(view: WebView, expression: String): String {
        val latch = CountDownLatch(1); val result = AtomicReference<String>()
        compose.runOnUiThread { view.evaluateJavascript(expression) { result.set(it); latch.countDown() } }
        assertTrue("WebView JS timed out", latch.await(8, TimeUnit.SECONDS))
        return result.get()
    }
    @Test fun signedLocalDocumentRendersAndExecutesInlineAnimationWithNetworkStillBlocked() {
        var context: Context? = null
        val status = AtomicReference(PreviewRenderStatus(PreviewRenderPhase.Loading))
        val html = """<h1 id="fixture">预览 R3</h1><script>window.ticks=0;setInterval(()=>window.ticks++,30);</script>"""
        val document = PreviewDocument("fixture", "fixture", 3, html, "fixture-binding", Long.MAX_VALUE)
        compose.setContent {
            context = LocalContext.current
            HapiStyle(night = true) { IsolatedPreviewView(document, onStatus = { status.set(it) }) }
        }
        compose.waitUntil(15000) { status.get().phase != PreviewRenderPhase.Loading }
        assertEquals("Internal data document must not receive synthetic 403", PreviewRenderPhase.Visible, status.get().phase)
        var browser: WebView? = null
        compose.runOnIdle { browser = webView(activity(context!!).window.decorView) }
        val view = requireNotNull(browser)
        assertEquals("\"预览 R3\"", evaluate(view, "document.getElementById('fixture').textContent"))
        compose.waitUntil(5000) { evaluate(view, "window.ticks").toIntOrNull()?.let { it > 1 } == true }
        compose.runOnIdle {
            assertTrue(view.settings.blockNetworkLoads)
            assertFalse(view.settings.allowFileAccess)
            assertFalse(view.settings.allowContentAccess)
            assertFalse(view.settings.domStorageEnabled)
        }
        assertEquals("true", evaluate(view, "typeof window.openai === 'undefined'"))
        assertEquals("true", evaluate(view, "document.querySelector('meta[http-equiv=\"Content-Security-Policy\"]').content.includes(\"connect-src 'none'\")"))
    }
}
