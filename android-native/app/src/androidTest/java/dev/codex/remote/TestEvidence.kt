package dev.codex.remote

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Expand from the non-link side; the underlined title area now opens task details. */
fun ComposeContentTestRule.revealChatHeader() {
    val header = onNodeWithTag("chat-header")
    if (header.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
        onNodeWithTag("header-loading", useUnmergedTree = true).performTouchInput { click() }
}

/** AGP collects this directory before uninstalling the test APK. Synthetic data only. */
fun captureTestScreen(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val storage = PlatformTestStorageRegistry.getInstance()
    val device = UiDevice.getInstance(instrumentation)
    // Compose's test clock can finish layout before the emulator compositor presents it.
    // Dumping the idle accessibility hierarchy first lets the native frame catch up;
    // taking the bitmap first previously saved the sending frame under a collapsed label.
    device.waitForIdle(3_000)
    storage.openOutputFile("$name.xml").use { device.dumpWindowHierarchy(it) }
    val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
    storage.openOutputFile("$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    // A Compose semantics test can still pass behind a system ANR modal. Save the evidence,
    // then reject that covered frame rather than accepting it as visual verification.
    check(!device.hasObject(By.res("android", "aerr_close"))) { "System error dialog covers the test app; inspect $name.png" }
}
