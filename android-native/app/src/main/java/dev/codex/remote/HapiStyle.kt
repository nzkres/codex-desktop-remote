package dev.codex.remote

// Light palette adapted from tiann/hapi Android ui/theme (AGPL-3.0).
// Night palette is an original editor-inspired companion; see android-native/README.md.
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class AppPalette(
    val background: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val card: Color,
    val divider: Color,
    val green: Color,
    val codeHeader: Color,
    val diffAdd: Color,
    val diffRemove: Color,
    val errorSurface: Color,
    val errorInk: Color,
    val warningSurface: Color,
    val questionSurface: Color,
    val isNight: Boolean,
)

private val lightPalette = AppPalette(
    background = Color.White,
    ink = Color(0xFF20242C),
    muted = Color(0xFF6B7280),
    faint = Color(0xFF8E8E93),
    card = Color(0xFFF5F6F7),
    divider = Color(0xFFE2E5E9),
    green = Color(0xFF0069DB),
    codeHeader = Color(0xFFECEFF2),
    diffAdd = Color(0xFFE6FFED),
    diffRemove = Color(0xFFFFEEF0),
    errorSurface = Color(0xFFFFEDEA),
    errorInk = Color(0xFFA52620),
    warningSurface = Color(0xFFFFF7E6),
    questionSurface = Color(0xFFEFF5FF),
    isNight = false,
)

private val nightPalette = AppPalette(
    background = Color(0xFF0D1117),
    ink = Color(0xFFE6EDF3),
    muted = Color(0xFF9BA8BA),
    faint = Color(0xFF7D8999),
    card = Color(0xFF161D27),
    divider = Color(0xFF293443),
    green = Color(0xFF7EE0B0),
    codeHeader = Color(0xFF171F2B),
    diffAdd = Color(0xFF173A31),
    diffRemove = Color(0xFF42252E),
    errorSurface = Color(0xFF3A2228),
    errorInk = Color(0xFFFF9DA6),
    warningSurface = Color(0xFF3A3020),
    questionSurface = Color(0xFF1C2C43),
    isNight = true,
)

val LocalAppPalette = compositionLocalOf { lightPalette }

// Keep old call sites readable while making every color respond to the selected theme.
object HapiPalette {
    val background: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.background
    val ink: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.ink
    val muted: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.muted
    val faint: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.faint
    val card: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.card
    val divider: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.divider
    val green: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.green
    val codeHeader: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.codeHeader
    val diffAdd: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.diffAdd
    val diffRemove: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.diffRemove
}

private val hapiLight = lightColorScheme(
    primary = lightPalette.green,
    secondary = Color(0xFF566985),
    tertiary = Color(0xFF386569),
    background = lightPalette.background,
    surface = lightPalette.background,
    onSurface = lightPalette.ink,
    outlineVariant = lightPalette.divider,
)

private val editorNight = darkColorScheme(
    primary = Color(0xFF77B9FF),
    onPrimary = Color(0xFF07141E),
    secondary = nightPalette.green,
    background = nightPalette.background,
    surface = nightPalette.background,
    surfaceContainer = nightPalette.card,
    onSurface = nightPalette.ink,
    outlineVariant = nightPalette.divider,
)

@Composable
fun HapiStyle(night: Boolean, content: @Composable () -> Unit) {
    val appearance = rememberChatAppearance()
    CompositionLocalProvider(LocalAppPalette provides if (night) nightPalette else lightPalette, LocalChatAppearance provides appearance) {
        MaterialTheme(colorScheme = if (night) editorNight else hapiLight, content = content)
    }
}

@Composable
fun HapiReadingColumn(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 752.dp).fillMaxWidth().padding(horizontal = 16.dp), content = content)
    }
}
